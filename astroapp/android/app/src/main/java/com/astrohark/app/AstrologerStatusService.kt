package com.astrohark.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.data.remote.SocketManager
import com.astrohark.app.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * AstrologerStatusService - Keeps the Astrologer ONLINE even when the app is in background.
 * This is a Foreground Service that shows a persistent notification with the Astrologer's profile photo.
 */
class AstrologerStatusService : Service() {

    companion object {
        private const val TAG = "AstroStatusService"
        private const val CHANNEL_ID = "astrologer_status_channel"
        private const val NOTIFICATION_ID = 2005

        @Volatile
        private var cachedAvatarBitmap: Bitmap? = null
        @Volatile
        private var cachedUserId: String? = null
        @Volatile
        private var cachedImageUrl: String? = null

        fun startService(context: Context, userId: String, imageUrl: String? = null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val isForeground = activityManager?.appTasks?.any {
                        it.taskInfo?.topActivity?.packageName == context.packageName
                    } ?: true
                    if (!isForeground) {
                        Log.w(TAG, "App not in foreground, skipping startForegroundService to prevent crash")
                        return
                    }
                }
                val intent = Intent(context, AstrologerStatusService::class.java).apply {
                    putExtra("userId", userId)
                    if (!imageUrl.isNullOrBlank()) {
                        putExtra("imageUrl", imageUrl)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AstrologerStatusService: ${e.message}")
            }
        }

        fun updateAvatar(context: Context, imageUrl: String) {
            try {
                val intent = Intent(context, AstrologerStatusService::class.java).apply {
                    putExtra("action", "UPDATE_AVATAR")
                    putExtra("imageUrl", imageUrl)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update avatar in AstrologerStatusService: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, AstrologerStatusService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop AstrologerStatusService: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentUserId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        val passedImageUrl = intent?.getStringExtra("imageUrl")
        val userIdExtra = intent?.getStringExtra("userId")
        if (!userIdExtra.isNullOrBlank()) {
            currentUserId = userIdExtra
        }

        Log.d(TAG, "Service Started for user: $currentUserId, action: $action")

        if (action == "UPDATE_AVATAR") {
            loadAndSetAstrologerAvatar(currentUserId, passedImageUrl)
            return START_STICKY
        }

        try {
            val notification = createNotification("You are Currently ONLINE 🟢", "Awaiting incoming calls/chats...")

            // Android 14+ FGS Type requirements
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    val type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                              android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    startForeground(NOTIFICATION_ID, notification, type)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting FGS with types: ${e.message}")
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForeground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        // Asynchronously load the online astrologer's photo and update notification
        loadAndSetAstrologerAvatar(currentUserId, passedImageUrl)

        // Ensure Socket is alive and registered
        if (currentUserId != null) {
            try {
                SocketManager.init()
                SocketManager.registerUser(currentUserId!!)
            } catch (e: Exception) {
                Log.e(TAG, "Socket init error: ${e.message}")
            }
        }

        return START_STICKY
    }

    private fun createNotification(title: String, content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.app_icon_final)
            .apply {
                val avatar = cachedAvatarBitmap
                if (avatar != null && !avatar.isRecycled) {
                    setLargeIcon(avatar)
                }
            }
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)

        return builder.build()
    }

    private fun loadAndSetAstrologerAvatar(userId: String?, explicitImageUrl: String?) {
        serviceScope.launch {
            try {
                // 1. Resolve raw URL: check passed argument, then local user session
                var rawUrl = explicitImageUrl?.takeIf { it.isNotBlank() }
                if (rawUrl.isNullOrBlank()) {
                    val session = TokenManager(this@AstrologerStatusService).getUserSession()
                    rawUrl = session?.image?.takeIf { it.isNotBlank() }
                }

                // 2. Fallback to API if not in session
                if (rawUrl.isNullOrBlank() && !userId.isNullOrBlank()) {
                    try {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .build()
                        val request = Request.Builder()
                            .url("${Constants.SERVER_URL}/api/user/$userId")
                            .build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val respStr = response.body?.string()
                            if (!respStr.isNullOrBlank()) {
                                val json = JSONObject(respStr)
                                val userObj = if (json.has("user")) json.optJSONObject("user") else json
                                rawUrl = userObj?.optString("image", "")?.takeIf { it.isNotBlank() }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch user image from API: ${e.message}")
                    }
                }

                if (rawUrl.isNullOrBlank()) {
                    Log.d(TAG, "No astrologer image found for userId: $userId")
                    return@launch
                }

                val fullUrl = when {
                    rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
                    rawUrl.startsWith("/") -> "${Constants.SERVER_URL}$rawUrl"
                    else -> "${Constants.SERVER_URL}/$rawUrl"
                }

                if (cachedUserId == userId && cachedImageUrl == fullUrl && cachedAvatarBitmap != null && !cachedAvatarBitmap!!.isRecycled) {
                    Log.d(TAG, "Avatar already cached for $userId")
                    return@launch
                }

                val downloadedBitmap = downloadBitmap(fullUrl)
                if (downloadedBitmap != null) {
                    val circular = getCircularBitmap(downloadedBitmap)
                    cachedAvatarBitmap = circular
                    cachedUserId = userId
                    cachedImageUrl = fullUrl

                    val manager = getSystemService(NotificationManager::class.java)
                    val updatedNotification = createNotification("You are Currently ONLINE 🟢", "Awaiting incoming calls/chats...")
                    manager.notify(NOTIFICATION_ID, updatedNotification)
                    Log.d(TAG, "Astrologer avatar updated successfully in notification")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading astrologer avatar: ${e.message}", e)
            }
        }
    }

    private fun downloadBitmap(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.instanceFollowRedirects = true
            connection.connect()
            val inputStream = connection.inputStream
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            connection.disconnect()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download avatar from $imageUrl: ${e.message}")
            null
        }
    }

    private fun getCircularBitmap(src: Bitmap): Bitmap {
        if (src.width <= 0 || src.height <= 0) return src
        val size = Math.min(src.width, src.height)
        val maxDimension = 256
        val finalSize = Math.min(size, maxDimension).coerceAtLeast(1)

        val output = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        val srcRect = Rect(
            (src.width - size) / 2,
            (src.height - size) / 2,
            (src.width + size) / 2,
            (src.height + size) / 2
        )
        val destRect = RectF(0f, 0f, finalSize.toFloat(), finalSize.toFloat())

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(finalSize / 2f, finalSize / 2f, finalSize / 2f, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, srcRect, destRect, paint)

        return output
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Astrologer Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps you online for consultations"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "Service Destroyed")
        // User Request: Do NOT set offline on destruction.
        // This allows the astrologer to stay online even if the app is killed.
    }
}
