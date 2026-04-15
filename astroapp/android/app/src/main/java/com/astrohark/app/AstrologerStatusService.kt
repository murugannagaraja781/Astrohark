package com.astrohark.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.astrohark.app.data.remote.SocketManager

/**
 * AstrologerStatusService - Keeps the Astrologer ONLINE even when the app is in background.
 * This is a Foreground Service that shows a persistent notification.
 */
class AstrologerStatusService : Service() {

    companion object {
        private const val TAG = "AstroStatusService"
        private const val CHANNEL_ID = "astrologer_status_channel"
        private const val NOTIFICATION_ID = 2005
        
        fun startService(context: Context, userId: String) {
            val intent = Intent(context, AstrologerStatusService::class.java).apply {
                putExtra("userId", userId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AstrologerStatusService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service Created")
    }

    private var currentUserId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentUserId = intent?.getStringExtra("userId")
        
        Log.d(TAG, "Service Started for user: $currentUserId")

        val notification = createNotification("You are Currently Online", "Awaiting incoming calls/chats...")
        
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

        // Ensure Socket is alive and registered
        if (currentUserId != null) {
            SocketManager.init()
            SocketManager.registerUser(currentUserId!!)
        }

        return START_STICKY 
    }

    private fun createNotification(title: String, content: String): Notification {
        // Find MainActivity path programmatically or use static
        val notificationIntent = Intent(this, MainActivity::class.java) 
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher) // Fallback to app icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
        
        return builder.build()
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
        Log.d(TAG, "Service Destroyed")
        
        // Notify server to go offline on service destruction
        currentUserId?.let { uid ->
            Thread {
                try {
                    val client = okhttp3.OkHttpClient()
                    listOf("chat", "audio", "video").forEach { serviceType ->
                        val url = "${com.astrohark.app.utils.Constants.SERVER_URL}/api/astrologer/service-toggle"
                        val body = okhttp3.FormBody.Builder()
                            .add("astrologerId", uid)
                            .add("serviceType", serviceType)
                            .add("status", "false")
                            .build()
                        val request = okhttp3.Request.Builder().url(url).post(body).build()
                        client.newCall(request).execute()
                    }
                    Log.d(TAG, "Successfully notified server: OFFLINE for $uid")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to notify server of offline status on destroy", e)
                }
            }.start()
        }
    }
}
