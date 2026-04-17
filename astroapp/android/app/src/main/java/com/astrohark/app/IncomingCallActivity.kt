package com.astrohark.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.astrohark.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.delay
import com.astrohark.app.data.remote.SocketManager
import com.astrohark.app.utils.CallState

import org.json.JSONObject

/**
 * IncomingCallActivity - Full-screen incoming call UI
 */
class IncomingCallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "IncomingCallActivity"
        private const val CALL_TIMEOUT_MS = 30_000L // Reject call after 30 seconds
        
        // GLOBAL STATE TRACKERS (must span across instance recreations)
        var isServiceStarted = false
    }
    
    private var hasEmittedAnswer = false
    private var hasStartedTransition = false

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shouldStopServiceOnDestroy = true
    private var callerId: String = ""
    private var callerName: String = ""
    private var callId: String = ""
    private var callType: String = "audio"
    private var birthData: String? = null
    private var callerImage: String? = null
    private var hasStartedTransition = false

    // Broadcast receiver to stop ringing when FCM cancel arrives
    private val callControlReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.astrohark.app.ACTION_CANCEL_CALL") {
                Log.d(TAG, "ACTION_CANCEL_CALL received via broadcast")
                onCallRejected()
            }
        }
    }

    // Auto-reject call after timeout
    private val timeoutRunnable = Runnable {
        Log.d(TAG, "Call timeout - auto rejecting")
        onCallRejected()
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- CRITICAL FIX: Reset global emission flag for this NEW call instance ---
        hasEmittedAnswer = false
        hasStartedTransition = false
        
        try {
            processIntent(intent)

            // CRITICAL FIX: If already in a call, ignore new incoming intent to avoid crash
            if (!CallState.canReceiveCall(callId)) {
                Log.w(TAG, "Already in an active call, rejecting new session: $callId")
                finish()
                return
            }

            // Mark as active immediately to block other incoming calls while ringing
            CallState.isCallActive = true
            CallState.currentSessionId = callId

            setupWindowFlags()

            try {
                startCallForegroundService()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
            }

            try {
                startRingtone()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ringtone", e)
            }

            try {
                startVibration()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start vibration", e)
            }

            ContextCompat.registerReceiver(
                this,
                callControlReceiver,
                android.content.IntentFilter("com.astrohark.app.ACTION_CANCEL_CALL"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            handler.postDelayed(timeoutRunnable, CALL_TIMEOUT_MS)

            // Ensure socket is connecting AND user is registered early
            try {
                SocketManager.init()
                // Early registration helps ensure we are in our personal room for signaling
                com.astrohark.app.data.local.TokenManager(this).getUserSession()?.userId?.let { uid ->
                    SocketManager.registerUser(uid)
                }
                
                // Wait for socket to be connected before setting up listeners
                SocketManager.onConnect {
                    com.astrohark.app.data.local.TokenManager(this@IncomingCallActivity).getUserSession()?.userId?.let { uid ->
                        SocketManager.registerUser(uid)
                    }
                }

                // Listen for session end (caller cancelled while ringing)
                SocketManager.onSessionEnded { _ ->
                    runOnUiThread {
                        Log.d(TAG, "Session ended by caller while ringing")
                        onCallRejected()
                    }
                }

                SocketManager.onCallCancelled { _ ->
                    runOnUiThread {
                        Log.d(TAG, "Call explicitly cancelled by caller")
                        onCallRejected()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket initialization error in IncomingCallActivity", e)
            }

            setContent {
                CosmicAppTheme {
                    IncomingCallScreen(
                        callerName = callerName,
                        callerId = if (callerId == "Unknown" && callId.isNotEmpty()) "Room: $callId" else "Calling from: $callerId",
                        callType = callType,
                        onAccept = { onCallAccepted() },
                        onReject = { onCallRejected() }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL CRASH in onCreate", e)
            android.widget.Toast.makeText(this, "Call Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent?.let {
            setIntent(it)
            processIntent(it)
            // Refresh content via Re-composition
            setContent {
                CosmicAppTheme {
                    IncomingCallScreen(
                        callerName = callerName,
                        callerId = if (callerId == "Unknown" && callId.isNotEmpty()) "Room: $callId" else "Calling from: $callerId",
                        callType = callType,
                        onAccept = { onCallAccepted() },
                        onReject = { onCallRejected() }
                    )
                }
            }
            // Reset flags for new call
            hasEmittedAnswer = false
            hasStartedTransition = false
            
            // Reset timeout
            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, CALL_TIMEOUT_MS)
        }
    }

    private fun processIntent(intent: Intent?) {
        if (intent == null) return
        callerId = intent.getStringExtra("callerId") ?: "Unknown"
        callerName = intent.getStringExtra("callerName") ?: callerId
        callId = intent.getStringExtra("callId") ?: "" // Room ID
        callType = intent.getStringExtra("callType") ?: "audio"
        birthData = intent.getStringExtra("birthData")
        Log.d(TAG, "Processing Call Intent: $callerName ($callId) Type: $callType")

        // Cancel notification on new call
        clearAllCallNotifications()
    }

    private fun clearAllCallNotifications() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(9999) // FCM Incoming
        notificationManager.cancel(1001) // Foreground Service
        notificationManager.cancel(1002) // Generic FCM
    }

    private fun startCallForegroundService() {
        if (!isServiceStarted) {
            try {
                val serviceIntent = Intent(this, CallForegroundService::class.java).apply {
                    putExtra("callerName", callerName)
                    putExtra("callId", callId)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                isServiceStarted = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
            }
        }
    }

    private fun startRingtone() {
        try {
            var ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            ringtoneUri?.let { uri ->
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build()
                    )
                    setDataSource(this@IncomingCallActivity, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "Ringtone started using URI: $uri")
            } ?: run {
                Log.e(TAG, "Ringtone URI is null, cannot start audio")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone", e)
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null) {
                Log.w(TAG, "Vibrator service not available")
                return
            }

            val pattern = longArrayOf(0, 500, 500) // delay, vibrate, sleep, repeat

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }

            Log.d(TAG, "Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    private fun stopRingtoneAndVibration() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        Log.d(TAG, "Ringtone and vibration stopped")
    }

    private fun onCallAccepted() {
        if (hasEmittedAnswer || hasStartedTransition) return // Safety block
        
        // Force socket connection if it's lagging
        if (SocketManager.getSocket()?.connected() != true) {
            Log.w(TAG, "Socket disconnected during accept. Attempting forced reconnect...")
            SocketManager.init()
            SocketManager.getSocket()?.connect()
        }
        
        Log.d(TAG, "Call accepted: $callId")
        
        stopRingtoneAndVibration()
        handler.removeCallbacks(timeoutRunnable)

        // --- STABILITY FIX: Emit answer-session with Ack and wait for it before transition ---
        if (!hasEmittedAnswer) {
            hasEmittedAnswer = true
            
            // Prepare CallActivity intent early
            val intent: Intent
            if (callType == "chat") {
                intent = Intent(this, com.astrohark.app.ui.chat.ChatActivity::class.java).apply {
                    putExtra("sessionId", callId)
                    putExtra("toUserId", callerId)
                    putExtra("toUserName", callerName)
                    putExtra("isNewRequest", true)
                    putExtra("birthData", birthData)
                }
            } else {
                intent = Intent(this, com.astrohark.app.ui.call.CallActivity::class.java).apply {
                    putExtra("sessionId", callId)
                    putExtra("partnerId", callerId)
                    putExtra("partnerName", callerName)
                    putExtra("isInitiator", false)
                    putExtra("isNewRequest", true) // Signal to CallActivity to NOT re-emit answer-session
                    putExtra("callType", callType)
                    putExtra("birthData", birthData)
                    putExtra("role", "astrologer")
                }
            }

            try {
                val payload = JSONObject().apply {
                    put("sessionId", callId)
                    put("toUserId", callerId)
                    put("type", callType)
                    put("accept", true)
                }
                
                Log.d(TAG, "Attempting to emit answer-session for $callId...")
                SocketManager.emitReliable("answer-session", payload, io.socket.client.Ack { args ->
                    Log.d(TAG, "Server acknowledged answer-session. Proceeding to CallActivity.")
                    runOnUiThread {
                        startCallActivity(intent, callId)
                    }
                })
                
                // Backup timer: If server doesn't respond in 1.5 seconds, PROCEED ANYWAY
                // Reduced from 3s to 1.5s for faster experience
                handler.postDelayed({
                    if (!hasStartedTransition) {
                        Log.w(TAG, "Signaling ack delay. Forcing transition for responsiveness.")
                        startCallActivity(intent, callId)
                    }
                }, 1500)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to emit answer-session on accept", e)
                // Fallback: proceed anyway so user isn't stuck on ringing screen
                startCallActivity(intent, callId)
            }
        }
    }

    private fun startCallActivity(intent: Intent, callId: String) {
        if (isDestroyed || isFinishing || hasStartedTransition) return
        hasStartedTransition = true
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("isNewRequest", true) // Signal to CallActivity to NOT re-emit answer-session
        
        if (!CallState.isCallActive || CallState.currentSessionId == callId) {
            Log.d("CALL_DEBUG", "Proceeding with transition for $callId")
            CallState.isCallActive = true
            CallState.currentSessionId = callId
            
            try {
                startActivity(intent)
                Log.d("CALL_DEBUG", "startActivity called successfully")
                shouldStopServiceOnDestroy = false
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start activity", e)
                CallState.isCallActive = false
                Toast.makeText(this, "Transition failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("CALL_DEBUG", "Ignoring transition: Call already active for ${CallState.currentSessionId}")
        }
    }

    private fun onCallRejected() {
        Log.d(TAG, "Call rejected: $callId")
        
        // --- Emit Rejection to Server ---
        if (!hasEmittedAnswer) {
            hasEmittedAnswer = true
            try {
                val payload = JSONObject().apply {
                    put("sessionId", callId)
                    put("toUserId", callerId)
                    put("type", callType)
                    put("accept", false)
                }
                
                Log.d(TAG, "Attempting to emit rejection for $callId...")
                SocketManager.emitReliable("answer-session", payload, io.socket.client.Ack {
                    Log.d(TAG, "Server acknowledged rejection for $callId")
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to emit rejection", e)
            }
        }

        stopRingtoneAndVibration()
        handler.removeCallbacks(timeoutRunnable)

        // Reset CallState
        if (CallState.currentSessionId == callId) {
            CallState.isCallActive = false
            CallState.currentSessionId = null
        }

        // Stop foreground service
        stopService(Intent(this, CallForegroundService::class.java))

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(callControlReceiver)
        } catch (e: Exception) {
            // Might not be registered if activity died early
        }
        stopRingtoneAndVibration()
        handler.removeCallbacks(timeoutRunnable)

        if (shouldStopServiceOnDestroy) {
            Log.d(TAG, "onDestroy: Resetting CallState (Reject/Timeout/Abrupt exit)")
            if (CallState.currentSessionId == callId) {
                CallState.isCallActive = false
                CallState.currentSessionId = null
            }
            
            if (isServiceStarted) {
                stopService(Intent(this, CallForegroundService::class.java))
                clearAllCallNotifications()
                isServiceStarted = false
            }
        }

        Log.d(TAG, "IncomingCallActivity destroyed")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - user must accept or reject
        Log.d(TAG, "Back pressed - ignoring (user must accept or reject)")
    }
}

@Composable
fun IncomingCallScreen(
    callerName: String,
    callerId: String,
    callType: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val isSocketConnected by produceState(initialValue = false) {
         while (true) {
             value = SocketManager.getSocket()?.connected() == true
             delay(500)
         }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicAppTheme.backgroundBrush)
    ) {
        // Decorative background elements (optional, but adds premium feel)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            val typeLabel = when(callType) {
                "chat" -> "Astrohark Chat Request"
                "video" -> "Incoming Video Call"
                else -> "Incoming Audio Call"
            }

            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = CosmicAppTheme.colors.accent,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = callerName,
                style = MaterialTheme.typography.displaySmall,
                color = CosmicAppTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (callerId == "Unknown" && callerName != "Unknown") "Astrohark User" else callerId,
                color = CosmicAppTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Avatar Section
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Pulsing rings
                repeat(2) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == 0) 180.dp else 220.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(CosmicAppTheme.colors.accent.copy(alpha = pulseAlpha / (index + 1)))
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = CosmicAppTheme.colors.cardBg,
                    modifier = Modifier.size(140.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.5f))
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Caller",
                        tint = CosmicAppTheme.colors.accent,
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onReject,
                        containerColor = Color(0xFFFF4B4B),
                        contentColor = Color.White,
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, "Decline", modifier = Modifier.size(34.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Decline", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }

                // Accept Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        // Removed strict isSocketConnected guard to allow acceptance attempts even during minor lags
                        onClick = { onAccept() },
                        containerColor = if (isSocketConnected) Color(0xFF25D366) else Color.Gray, // WhatsApp Green
                        contentColor = Color.White,
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp)
                    ) {
                        if (!isSocketConnected) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(Icons.Default.Call, "Accept", modifier = Modifier.size(34.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isSocketConnected) "Accept" else "Connecting...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
