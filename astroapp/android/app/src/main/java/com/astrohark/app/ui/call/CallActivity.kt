package com.astrohark.app.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import com.astrohark.app.data.remote.SocketManager
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.data.model.AuthResponse
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AstroDimens
import com.astrohark.app.ui.common.ModernSummaryDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.astrohark.app.utils.CallState
import org.json.JSONObject
import org.webrtc.*
import java.util.LinkedList

class CallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CallActivity"
        private const val PERMISSION_REQ_CODE = 101

        private val pendingIceCandidates = LinkedList<IceCandidate>()

        private var iceServers = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:turn.astrohark.com:3478?transport=udp")
                .setUsername("webrtcuser").setPassword("strongpassword123").createIceServer()
        )
    }

    // Views (WebRTC Renderers) - Created programmatically
    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var localView: SurfaceViewRenderer

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private lateinit var eglBase: EglBase

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private var isInitiator = false
    private var partnerId: String? = null
    private var sessionId: String? = null
    private var clientBirthData by mutableStateOf<JSONObject?>(null)

    private lateinit var tokenManager: TokenManager
    private var session: AuthResponse? = null

    // Compose State
    private var callDurationSeconds by mutableStateOf(0)
    private var statusText by mutableStateOf("Connecting...")
    private var isBillingActive by mutableStateOf(false)
    private var isMutedState by mutableStateOf(false)
    private var isVideoEnabledState by mutableStateOf(true) // For camera toggle
    private var isSpeakerOnState by mutableStateOf(false) // For audio toggle
    private var isEditingIntake by mutableStateOf(false) // Track when edit form is open
    private var remainingTime by mutableStateOf("") // Available time from wallet
    private var isRecordingState by mutableStateOf(false)
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    // Summary State
    data class SimpleSummary(val duration: Int, val amount: Double, val reason: String)
    private var callSummary by mutableStateOf<SimpleSummary?>(null)

    private var isWebRTCInitialized = false

    // Proximity Sensor for Audio Calls
    private var proximityWakeLock: android.os.PowerManager.WakeLock? = null
    private var sensorManager: android.hardware.SensorManager? = null
    private val sensorListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            if (callType == "audio" && !isSpeakerOnState) {
                val distance = event.values[0]
                val isNear = distance < event.sensor.maximumRange
                if (isNear) {
                    // Turn screen off
                    if (proximityWakeLock?.isHeld == false) proximityWakeLock?.acquire()
                } else {
                    // Turn screen on
                    if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
                }
            }
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    // Helper state for formatted time
    private val formattedDuration: String
        get() {
            val minutes = callDurationSeconds / 60
            val seconds = callDurationSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    private val editIntakeLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        // Delay resetting isEditingIntake to give socket time to stabilize after foreground switch
        timerHandler.postDelayed({
            isEditingIntake = false
        }, 3000)

        // Ensure socket is connected after returning from edit
        ensureSocketConnected()

        if (result.resultCode == RESULT_OK) {
             val dataStr = result.data?.getStringExtra("birthData")
             if (dataStr != null) {
                 try {
                     val newData = JSONObject(dataStr)
                     clientBirthData = newData
                     Toast.makeText(this, "Details Updated", Toast.LENGTH_SHORT).show()
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("toUserId", partnerId)
                         put("birthData", newData)
                     })
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }

    // Check ICE connection state and restart if needed
        checkAndRestoreConnection()
    }

    private val matchLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        timerHandler.postDelayed({ isEditingIntake = false }, 3000)
        ensureSocketConnected()
        if (result.resultCode == RESULT_OK) {
             val dataStr = result.data?.getStringExtra("birthData")
             if (dataStr != null) {
                 try {
                     val newData = JSONObject(dataStr)
                     clientBirthData = newData
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("toUserId", partnerId)
                         put("birthData", newData)
                     })
                     val matchIntent = android.content.Intent(this, com.astrohark.app.ui.chart.MatchDisplayActivity::class.java)
                     matchIntent.putExtra("birthData", newData.toString())
                     startActivity(matchIntent)
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }
    }

    // Logic internal state
    private var callType: String = "video"
    private var partnerName: String? = null

    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var listenersInitialized = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            callDurationSeconds++
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isEditingIntake", isEditingIntake)
        outState.putString("clientBirthData", clientBirthData?.toString())
        outState.putInt("callDurationSeconds", callDurationSeconds)
        outState.putString("sessionId", sessionId)
        outState.putString("partnerId", partnerId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (savedInstanceState != null) {
                isEditingIntake = savedInstanceState.getBoolean("isEditingIntake")
                val birthDataStr = savedInstanceState.getString("clientBirthData")
                if (!birthDataStr.isNullOrEmpty()) {
                    clientBirthData = JSONObject(birthDataStr)
                }
                callDurationSeconds = savedInstanceState.getInt("callDurationSeconds")
                sessionId = savedInstanceState.getString("sessionId")
                partnerId = savedInstanceState.getString("partnerId")
            }

            // --- GLOBAL STATE FIX: Mark call as active to prevent duplicate starts ---
            val incomingSessionId = intent.getStringExtra("sessionId") ?: savedInstanceState?.getString("sessionId")
            if (incomingSessionId.isNullOrEmpty()) {
                Log.e(TAG, "CRITICAL: Session ID is missing, cannot start call")
                Toast.makeText(this, "Session Link Expired", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            sessionId = incomingSessionId
            partnerId = intent.getStringExtra("partnerId") ?: savedInstanceState?.getString("partnerId") ?: "unknown"
            partnerName = intent.getStringExtra("partnerName") ?: intent.getStringExtra("userName") ?: partnerId
            
            CallState.isCallActive = true
            CallState.currentSessionId = sessionId
            
            // Initialize WebRTC Core as early as possible
            try {
                if (!::eglBase.isInitialized) eglBase = EglBase.create()
                val options = PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
                PeerConnectionFactory.initialize(options)
            } catch (e: Exception) { Log.e(TAG, "WebRTC Core Init Failed", e) }

            // Fetch TURN/STUN Config from Server
            fetchWebRTCConfig()

            // Initialize WebRTC Views Programmatically
            localView = SurfaceViewRenderer(this)
            remoteView = SurfaceViewRenderer(this)
            isInitiator = intent.getBooleanExtra("isInitiator", false)
            val rawType = intent.getStringExtra("type") ?: intent.getStringExtra("callType") ?: "video"
            val lowerType = rawType.lowercase()
            callType = if (lowerType == "audio" || lowerType == "voice" || lowerType == "call") "audio" else "video"

            // Initial state sync
            isVideoEnabledState = (callType == "video")
            isSpeakerOnState = (callType == "video") // Default speaker on for video, off for audio (earpiece)

            val birthDataStr = intent.getStringExtra("birthData")
            if (!birthDataStr.isNullOrEmpty()) {
                 try {
                    val obj = JSONObject(birthDataStr)
                    if (obj.length() > 0) clientBirthData = obj
                 } catch (e: Exception) { e.printStackTrace() }
            }

            tokenManager = TokenManager(this)
            session = tokenManager.getUserSession()
            val role = session?.role

            // Set Content
            setContent {
                CosmicAppTheme {
                    CallScreen(
                        remoteRenderer = remoteView,
                        localRenderer = localView,
                        partnerName = partnerName ?: "Unknown",
                        duration = formattedDuration,
                        statusText = statusText,
                        isBillingActive = isBillingActive,
                        callType = callType,
                        isMuted = isMutedState,
                        isVideoEnabled = isVideoEnabledState,
                        isSpeakerOn = isSpeakerOnState,
                        role = role ?: "user",
                        remainingTime = remainingTime,
                        onToggleMic = { toggleMic() },
                        onToggleCamera = { toggleCamera() },
                        onToggleSpeaker = { toggleSpeaker() },
                        onEndCall = { endCall() },
                        onEditIntake = { openEditIntake() },
                        onShowRasi = { showRasiChart() },
                        onShowMatch = { showMatchDisplay() },
                        isRecording = isRecordingState,
                        onToggleRecording = { toggleRecording() },
                        isReady = isWebRTCInitialized,
                        clientBirthData = clientBirthData,
                        summary = callSummary,
                        onDismissSummary = { finish() }
                    )
                }
            }

            // --- Socket Init ---
            try {
                SocketManager.init()
                session?.userId?.let { uid ->
                    SocketManager.registerUser(uid)
                    if (SocketManager.getSocket()?.connected() != true) {
                        SocketManager.getSocket()?.connect()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket init failed", e)
            }

            // Initialize Proximity WakeLock for Audio Calls
            try {
                val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (powerManager.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                        proximityWakeLock = powerManager.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "astrohark:ProximityLock")
                    }
                }
                sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
            } catch (e: Exception) {
                Log.e(TAG, "Proximity lock init failed", e)
            }

            // Start Timer Delay
            timerHandler.postDelayed(timerRunnable, 1000)

            // Initialize Call Logic after a small delay to ensure configs are ready
            lifecycleScope.launch {
                if (iceServers.size <= 2) { 
                    delay(800) 
                }
                if (checkPermissions()) {
                    startCallLimit()
                } else {
                    ActivityCompat.requestPermissions(
                        this@CallActivity,
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                        PERMISSION_REQ_CODE
                    )
                }
            }

            // Start Remaining Time Countdown (for astrologers only)
            if (role == "astrologer") {
                lifecycleScope.launch {
                    while (isActive) {
                        delay(1000)
                        if (remainingTime.isNotEmpty() && remainingTime != "00:00") {
                            val parts = remainingTime.split(":")
                            if (parts.size == 2) {
                                val mins = parts[0].toIntOrNull() ?: 0
                                val secs = parts[1].toIntOrNull() ?: 0
                                val totalSecs = mins * 60 + secs - 1
                                if (totalSecs > 0) {
                                    remainingTime = String.format("%02d:%02d", totalSecs / 60, totalSecs % 60)
                                } else {
                                    remainingTime = "00:00"
                                    endCall() // Auto-end
                                }
                            }
                        }
                    }
                }
                
                // Fetch wallet and calculate initial remaining time
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val client = okhttp3.OkHttpClient()
                        val request = okhttp3.Request.Builder()
                            .url("${com.astrohark.app.utils.Constants.SERVER_URL}/api/user/${partnerId}")
                            .build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val json = JSONObject(response.body?.string() ?: "{}")
                            val walletBalance = json.optDouble("walletBalance", 0.0)
                            val ratePerMin = 10.0
                            val totalMinutes = (walletBalance / ratePerMin).toInt()
                            remainingTime = String.format("%02d:%02d", totalMinutes, 0)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch wallet balance", e)
                    }
                }
                
                // START FOREGROUND STATUS SERVICE
                session?.userId?.let { uid ->
                    com.astrohark.app.AstrologerStatusService.startService(this, uid)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FAILED TO START CALL ACTIVITY", e)
            android.widget.Toast.makeText(this, "Setup Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (callType == "audio") {
            sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)?.let {
                sensorManager?.registerListener(sensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        ensureSocketConnected()
    }

    override fun onPause() {
        super.onPause()
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock?.release()
        }
        sensorManager?.unregisterListener(sensorListener)
    }

    private fun toggleMic() {
        val newMute = !isMutedState
        isMutedState = newMute
        localAudioTrack?.setEnabled(!newMute)
        Toast.makeText(this, if (newMute) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun toggleCamera() {
        val enabled = localVideoTrack?.enabled() ?: true
        val newEnabled = !enabled
        localVideoTrack?.setEnabled(newEnabled)
        isVideoEnabledState = newEnabled
        Toast.makeText(this, if (newEnabled) "Camera ON" else "Camera OFF", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSpeaker() {
        val newSpeaker = !isSpeakerOnState
        isSpeakerOnState = newSpeaker
        setSpeakerphoneOn(newSpeaker)
        Toast.makeText(this, if (newSpeaker) "Speaker ON" else "Speaker OFF", Toast.LENGTH_SHORT).show()
    }

    private fun setSpeakerphoneOn(on: Boolean) {
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = on
    }

    private fun openEditIntake() {
        isEditingIntake = true // Mark that we're editing
        val intent = android.content.Intent(this, com.astrohark.app.ui.intake.IntakeActivity::class.java)
        intent.putExtra("isEditMode", true)
        intent.putExtra("existingData", clientBirthData?.toString())
        if (tokenManager.getUserSession()?.role == "astrologer") {
            intent.putExtra("targetUserId", partnerId)
        }
        editIntakeLauncher.launch(intent)
    }

    private fun toggleRecording() {
        if (isRecordingState) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        try {
            val dir = File(getExternalFilesDir(null), "Recordings")
            if (!dir.exists()) dir.mkdirs()
            val safeSessionId = sessionId ?: "unknown_session"
            audioFile = File(dir, "Rec_${safeSessionId}_${System.currentTimeMillis()}.mp3")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath ?: throw Exception("Failed to create file path"))
                prepare()
                start()
            }
            isRecordingState = true
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecordingState = false
            Toast.makeText(this, "Saved to ${audioFile?.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Stop recording failed", e)
            isRecordingState = false
            mediaRecorder = null
        }
    }

    private fun startBackgroundService() {
        val serviceIntent = android.content.Intent(this, com.astrohark.app.CallForegroundService::class.java).apply {
            action = "ACTION_START_CALL"
            putExtra("partnerName", partnerName)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopBackgroundService() {
        val serviceIntent = android.content.Intent(this, com.astrohark.app.CallForegroundService::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }
        startService(serviceIntent)
    }

    /**
     * Ensure socket is connected after returning from background activity
     */
    private fun ensureSocketConnected() {
        val socket = SocketManager.getSocket()
        if (socket == null || !socket.connected()) {
            Log.d(TAG, "Socket disconnected - reconnecting...")
            SocketManager.init()
            // Re-setup listeners after reconnect
            setupSocketListeners()
            // Re-join session room
            SocketManager.getSocket()?.emit("rejoin-session", JSONObject().apply {
                put("sessionId", sessionId)
            })
        } else {
            Log.d(TAG, "Socket still connected")
        }
    }

    /**
     * Check ICE connection state and attempt restart if connection is unstable
     */
    private fun checkAndRestoreConnection() {
        try {
            val iceState = peerConnection.iceConnectionState()
            Log.d(TAG, "ICE Connection State after edit: $iceState")

            when (iceState) {
                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.w(TAG, "ICE connection unstable - requesting restart")
                    statusText = "Reconnecting..."
                    // Request ICE restart by creating a new offer with iceRestart option
                    if (isInitiator) {
                        restartIce()
                    }
                }
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    Log.d(TAG, "ICE connection stable")
                    statusText = ""
                }
                else -> {
                    Log.d(TAG, "ICE state: $iceState - monitoring...")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection state", e)
        }
    }

    /**
     * Restart ICE connection if it becomes unstable
     */

    private fun restartIce() {
        try {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (callType == "video") {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
            }

            peerConnection.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    desc?.let {
                        peerConnection.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                sendSignal(JSONObject().apply {
                                    put("type", "offer")
                                    put("sdp", desc.description)
                                })
                                Log.d(TAG, "ICE restart offer sent")
                            }
                            override fun onSetFailure(s: String?) { Log.e(TAG, "ICE restart setLocal fail: $s") }
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, desc)
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(s: String?) { Log.e(TAG, "ICE restart create fail: $s") }
                override fun onSetFailure(s: String?) {}
            }, constraints)
        } catch (e: Exception) {
            Log.e(TAG, "ICE restart failed", e)
        }
    }

    private fun fetchWebRTCConfig() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Consolidation: Using standard route
                val response = com.astrohark.app.data.api.ApiClient.api.getWebRTCConfig()
                if (response.isSuccessful) {
                    val config = response.body()
                    if (config != null && config.has("ok") && config.get("ok").asBoolean) {
                        val stun = if (config.has("stunServer")) config.get("stunServer").asString else "stun:stun.l.google.com:19302"
                        val turn = if (config.has("turnServer")) config.get("turnServer").asString else "turn.astrohark.com"
                        val port = if (config.has("turnPort")) config.get("turnPort").asString else "3478"
                        val user = if (config.has("turnUsername")) config.get("turnUsername").asString else "webrtcuser"
                        val pass = if (config.has("turnPassword")) config.get("turnPassword").asString else "strongpassword123"

                        val newIceServers = mutableListOf<PeerConnection.IceServer>()
                        newIceServers.add(PeerConnection.IceServer.builder(stun).createIceServer())
                        
                        // Add UDP and TCP variants for high compatibility
                        newIceServers.add(PeerConnection.IceServer.builder("turn:$turn:$port?transport=udp")
                            .setUsername(user).setPassword(pass).createIceServer())
                        newIceServers.add(PeerConnection.IceServer.builder("turn:$turn:$port?transport=tcp")
                            .setUsername(user).setPassword(pass).createIceServer())
                        newIceServers.add(PeerConnection.IceServer.builder("turns:$turn:5349")
                            .setUsername(user).setPassword(pass).createIceServer())

                        iceServers = newIceServers
                        Log.d(TAG, "✓ WebRTC: ICE Config cached for all subsequent sessions")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ WebRTC: Failed to fetch config: ${e.message}")
            }
        }
    }

    private fun checkPermissions(): Boolean {
         val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        return if (callType == "audio") hasAudio else (hasAudio && hasCamera)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_CODE) {
             var allGranted = true
             if (grantResults.isNotEmpty()) {
                 for (result in grantResults) {
                     if (result != PackageManager.PERMISSION_GRANTED) {
                         allGranted = false
                         break
                     }
                 }
             } else {
                 allGranted = false
             }
             if (allGranted) {
                 startCallLimit()
             } else {
                 Toast.makeText(this, "Permissions required for call", Toast.LENGTH_LONG).show()
                 finish()
             }
        }
    }

    private fun startCallLimit() {
        if (!initWebRTC()) return
        startBackgroundService()
        setupSocketListeners()

        val myUserId = session?.userId
        if (myUserId == null) {
            Log.e(TAG, "Cannot start call: userId is null")
            finish()
            return
        }

        if (isInitiator) {
            statusText = "Calling..."

            SocketManager.registerUser(myUserId) { success ->
                if (success) {
                    runOnUiThread {
                        Log.d(TAG, "Initiator: Sending session-connect for $sessionId")
                        val connectPayload = JSONObject().apply {
                             put("sessionId", sessionId)
                        }
                        SocketManager.getSocket()?.emit("session-connect", connectPayload)
                    }
                }
            }
        } else {
            statusText = "Connecting..."
            SocketManager.registerUser(myUserId) { success ->
                if (success) {
                    runOnUiThread {
                        val isNewRequest = intent.getBooleanExtra("isNewRequest", false)
                        if (isNewRequest) {
                            Log.d(TAG, "Recipient: session already answered in previous activity. Skipping redundant answer-session.")
                        } else {
                            Log.d(TAG, "Recipient: Sending answer-session (accept: true) for $sessionId")
                            val payload = JSONObject().apply {
                                put("sessionId", sessionId)
                                put("toUserId", partnerId)
                                put("accept", true)
                            }
                            SocketManager.getSocket()?.emit("answer-session", payload)
                        }

                        Log.d(TAG, "Recipient: Sending session-connect for $sessionId")
                        val connectPayload = JSONObject().apply {
                            put("sessionId", sessionId)
                        }
                        SocketManager.getSocket()?.emit("session-connect", connectPayload)
                    }
                }
            }
        }
    }

    private fun initWebRTC(): Boolean {
        if (isWebRTCInitialized) return true
        try {
            if (!::eglBase.isInitialized) eglBase = EglBase.create()
            
            // Only create factory if it hasn't been created yet
            if (!::peerConnectionFactory.isInitialized) {
                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                    .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                    .createPeerConnectionFactory()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "CRITICAL: WebRTC Factory creation failed", t)
            runOnUiThread {
                Toast.makeText(this, "Hardware engine failed. Please restart app.", Toast.LENGTH_LONG).show()
            }
            return false
        }

        if (callType == "video") {
            remoteView.init(eglBase.eglBaseContext, null)
            remoteView.setEnableHardwareScaler(true)
            remoteView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)

            localView.init(eglBase.eglBaseContext, null)
            localView.setEnableHardwareScaler(true)
            localView.setMirror(true)
            localView.setZOrderMediaOverlay(true)
            localView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        } else {
             setSpeakerphoneOn(false) // Audio call default
        }

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        if (callType == "video") {
            videoCapturer = try {
                createCameraCapturer(Camera2Enumerator(this))
            } catch (e: Exception) {
                try {
                    createCameraCapturer(Camera1Enumerator(true))
                } catch (e1: Exception) {
                    null
                }
            }

            if (videoCapturer != null) {
                try {
                    val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                    val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
                    videoCapturer!!.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
                    videoCapturer!!.startCapture(640, 480, 30)

                    localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
                    localVideoTrack?.setEnabled(true)
                    localVideoTrack?.addSink(localView)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera capture", e)
                }
            }
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                runOnUiThread {
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            statusText = "" // Hide status
                            // Auto-start recording for astrologers
                            val myRole = TokenManager(this@CallActivity).getUserSession()?.role
                            if (myRole == "astrologer" && !isRecordingState) {
                                startRecording()
                            }
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            if (!isEditingIntake) {
                                Toast.makeText(this@CallActivity, "Connection Unstable", Toast.LENGTH_SHORT).show()
                            }
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            if (!isEditingIntake) {
                                Toast.makeText(this@CallActivity, "Connection Failed", Toast.LENGTH_SHORT).show()
                                endCall()
                            } else {
                                Log.d(TAG, "ICE Failed while editing intake - ignoring to allow reconnect")
                                statusText = "Reconnecting..."
                            }
                        }
                        else -> {}
                    }
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    val signalData = JSONObject().apply {
                         put("type", "candidate")
                         put("candidate", JSONObject().apply {
                             put("candidate", candidate.sdp)
                             put("sdpMid", candidate.sdpMid)
                             put("sdpMLineIndex", candidate.sdpMLineIndex)
                         })
                    }
                    val payload = JSONObject().apply {
                        put("toUserId", partnerId)
                        put("signal", signalData)
                    }
                    sendSignal(payload)
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                if (stream != null && stream.videoTracks.isNotEmpty() && callType == "video") {
                    val remoteVideoTrack = stream.videoTracks[0]
                    runOnUiThread {
                        remoteVideoTrack.setEnabled(true)
                        remoteVideoTrack.addSink(remoteView)
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack && callType == "video") {
                    runOnUiThread {
                        track.setEnabled(true)
                        track.addSink(remoteView)
                    }
                }
            }

            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })

        if (pc == null) {
            Log.e(TAG, "Failed to create PeerConnection")
            runOnUiThread {
                Toast.makeText(this, "Failed to initialize call. Please try again.", Toast.LENGTH_LONG).show()
            }
            finish()
            return false
        }

        peerConnection = pc

        localAudioTrack?.let { peerConnection.addTrack(it, listOf("mediaStream")) }
        localVideoTrack?.let { peerConnection.addTrack(it, listOf("mediaStream")) }

        isWebRTCInitialized = true
        return true
    }

    private fun setupSocketListeners() {
        if (listenersInitialized) return
        listenersInitialized = true

        SocketManager.onSignal { data ->
            runOnUiThread {
                handleSignal(data)
            }
        }

        SocketManager.getSocket()?.on("client-birth-chart") { args ->
            try {
                val data = args[0] as JSONObject
                val bData = data.optJSONObject("birthData")
                if (bData != null) {
                    clientBirthData = bData
                    runOnUiThread {
                        val myRole = TokenManager(this@CallActivity).getUserSession()?.role
                        if (myRole == "client") {
                            Toast.makeText(this@CallActivity, "Astrologer updated your birth details", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@CallActivity, "Client updated their birth details", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        SocketManager.onBillingStarted { info ->
            runOnUiThread {
                Log.d(TAG, "Billing started event received. Initiator: $isInitiator")
                statusText = "Connecting to ${partnerName}..."
                isBillingActive = true
                if (isInitiator && ::peerConnection.isInitialized) {
                    // Small delay to ensure other side is also ready for offer
                    timerHandler.postDelayed({
                        createOffer()
                    }, 1000)
                }
                
                // Temporary status for billing start
                androidx.core.os.HandlerCompat.postDelayed(android.os.Handler(android.os.Looper.getMainLooper()), {
                   if(statusText.contains("Connecting to")) statusText = "" 
                }, null, 5000)
            }
        }

        SocketManager.onSessionEndedWithSummary { reason, deducted, earned, duration ->
            runOnUiThread {
                timerHandler.removeCallbacks(timerRunnable)
                isBillingActive = false
                val amount = if (tokenManager.getUserSession()?.role == "astrologer") earned else deducted
                callSummary = SimpleSummary(duration, amount, reason)
            }
        }

        SocketManager.getSocket()?.on(io.socket.client.Socket.EVENT_DISCONNECT) {
             runOnUiThread {
                 // Don't end call if user is editing intake form
                 if (!isEditingIntake) {
                     statusText = "Reconnecting..."
                     // Don't finish immediately, let it attempt reconnection
                     // Only finish if session is explicitly ended by server
                     Log.d(TAG, "Socket disconnected - waiting for reconnect or session end")
                 } else {
                     Log.d(TAG, "Socket disconnected while editing - will reconnect")
                 }
             }
        }
    }

    private fun drainRemoteCandidates() {
        if (pendingIceCandidates.isNotEmpty()) {
            for (candidate in pendingIceCandidates) {
                peerConnection.addIceCandidate(candidate)
            }
            pendingIceCandidates.clear()
        }
    }

    private fun handleSignal(data: JSONObject) {
        val signal = data.optJSONObject("signal") ?: data
        var type = signal.optString("type")
        if (type.isEmpty() && signal.has("candidate")) type = "candidate"

        when (type) {
            "offer" -> {
                val descriptionStr = signal.optJSONObject("sdp")?.optString("sdp") ?: signal.optString("sdp")
                if (descriptionStr.isNotEmpty() && ::peerConnection.isInitialized) {
                    val mungedSdp = optimizeSdp(descriptionStr)
                    peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            createAnswer()
                            drainRemoteCandidates()
                        }
                    }, SessionDescription(SessionDescription.Type.OFFER, mungedSdp))
                }
            }
            "answer" -> {
                val descriptionStr = signal.optJSONObject("sdp")?.optString("sdp") ?: signal.optString("sdp")
                if (descriptionStr.isNotEmpty() && ::peerConnection.isInitialized) {
                    val mungedSdp = optimizeSdp(descriptionStr)
                    peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            drainRemoteCandidates()
                        }
                    }, SessionDescription(SessionDescription.Type.ANSWER, mungedSdp))
                }
            }
            "candidate" -> {
                val candidateJson = signal.optJSONObject("candidate") ?: signal
                val sdpMid = candidateJson.optString("sdpMid")
                val sdpMLineIndex = candidateJson.optInt("sdpMLineIndex", -1)
                val sdp = candidateJson.optString("candidate")

                if (sdp.isNotEmpty() && sdpMLineIndex != -1 && ::peerConnection.isInitialized) {
                    val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                    if (peerConnection.remoteDescription == null) {
                        pendingIceCandidates.add(candidate)
                    } else {
                        peerConnection.addIceCandidate(candidate)
                    }
                }
            }
        }
    }

    private fun createOffer() {
        if (!::peerConnection.isInitialized) return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if(callType == "video") "true" else "false"))
        }

        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (!::peerConnection.isInitialized || desc == null) return
                val mungedSdp = optimizeSdp(desc.description)
                val mungedDesc = SessionDescription(desc.type, mungedSdp)
                
                peerConnection.setLocalDescription(SimpleSdpObserver(), mungedDesc)
                val signalData = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", mungedSdp)
                }
                val payload = JSONObject().apply {
                    put("toUserId", partnerId)
                    put("signal", signalData)
                }
                sendSignal(payload)
                Log.d(TAG, "Opus-prioritized offer sent")
            }
        }, constraints)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if(callType == "video") "true" else "false"))
        }

        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                val mungedSdp = optimizeSdp(desc.description)
                val mungedDesc = SessionDescription(desc.type, mungedSdp)
                
                peerConnection.setLocalDescription(SimpleSdpObserver(), mungedDesc)
                val signalData = JSONObject().apply {
                    put("type", "answer")
                    put("sdp", mungedSdp)
                }
                val payload = JSONObject().apply {
                    put("toUserId", partnerId)
                    put("signal", signalData)
                }
                sendSignal(payload)
                Log.d(TAG, "Opus-prioritized answer sent")
            }
        }, constraints)
    }

    private fun sendSignal(payload: JSONObject) {
        payload.put("sessionId", sessionId)
        SocketManager.getSocket()?.emit("signal", payload)
    }

    private fun endCall() {
        stopBackgroundService()
        // Send BOTH signals to ensure termination regardless of call state (ringing vs ongoing)
        SocketManager.endSession(sessionId)
        SocketManager.cancelCall(sessionId, partnerId)
        finish()
    }

    override fun finish() {
        // Ensure state is cleared even if finished via system back or other means
        CallState.isCallActive = false
        CallState.currentSessionId = null
        stopBackgroundService()
        super.finish()
    }

    override fun onDestroy() {
        if (isRecordingState) {
            try { stopRecording() } catch (e: Exception) { e.printStackTrace() }
        }
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        SocketManager.off("signal")
        SocketManager.off("session-ended")
        SocketManager.off("billing-started")
        SocketManager.off("client-birth-chart")
        SocketManager.getSocket()?.off(io.socket.client.Socket.EVENT_DISCONNECT)
        try {
            if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
            proximityWakeLock = null
        } catch (e: Exception) {}

        try {
            if (::peerConnection.isInitialized) {
                peerConnection.dispose() 
            }
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            
            if (::localView.isInitialized) {
                localView.clearImage()
                localView.release()
            }
            if (::remoteView.isInitialized) {
                remoteView.clearImage()
                remoteView.release()
            }
            
            if (::peerConnectionFactory.isInitialized) peerConnectionFactory.dispose()
            if (::eglBase.isInitialized) eglBase.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Error destroying WebRTC resources", e)
        }
        stopBackgroundService()
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    private fun showRasiChart() {
        if (clientBirthData != null) {
            val intent = android.content.Intent(this, com.astrohark.app.ui.chart.VipChartActivity::class.java)
            intent.putExtra("birthData", clientBirthData.toString())
            startActivity(intent)
        } else {
            Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMatchDisplay() {
        val hasPartner = clientBirthData?.has("partnerData") == true || clientBirthData?.has("partner") == true
        
        if (hasPartner) {
            // Show result directly
            val matchIntent = android.content.Intent(this, com.astrohark.app.ui.chart.MatchDisplayActivity::class.java)
            matchIntent.putExtra("birthData", clientBirthData.toString())
            startActivity(matchIntent)
        } else {
            // Open form to fill
            isEditingIntake = true
            val intent = android.content.Intent(this, com.astrohark.app.ui.intake.IntakeActivity::class.java)
            intent.putExtra("isEditMode", true)
            intent.putExtra("isMatching", true) 
            intent.putExtra("existingData", clientBirthData?.toString() ?: "{}")
            if (TokenManager(this).getUserSession()?.role == "astrologer") {
                intent.putExtra("targetUserId", partnerId)
            }
            matchLauncher.launch(intent)
        }
    }

    private fun optimizeSdp(sdp: String): String {
        try {
            val lines = sdp.split("\n").map { it.trim() }
            val newLines = lines.toMutableList()
            
            // 1. Audio Optimization (Opus)
            val audioMLineIndex = lines.indexOfFirst { it.startsWith("m=audio") }
            if (audioMLineIndex != -1) {
                val elements = lines[audioMLineIndex].split(" ").toMutableList()
                val opusPT = lines.find { it.contains("a=rtpmap:") && it.contains("opus/48000") }
                    ?.substringAfter("a=rtpmap:")?.substringBefore(" ")
                if (opusPT != null && elements.size > 3) {
                    elements.remove(opusPT)
                    elements.add(3, opusPT)
                    newLines[audioMLineIndex] = elements.joinToString(" ")
                    
                    // Add FEC/DTX to fmtp for jitter resilience
                    val fmtpIndex = newLines.indexOfFirst { it.startsWith("a=fmtp:$opusPT") }
                    if (fmtpIndex != -1) {
                        val fmtpLine = newLines[fmtpIndex]
                        if (!fmtpLine.contains("useinbandfec=1")) {
                            newLines[fmtpIndex] = "$fmtpLine;useinbandfec=1;usedtx=1"
                        }
                    }
                }
            }

            // 2. Video Optimization (H264 > VP8) + Bitrate
            val videoMLineIndex = lines.indexOfFirst { it.startsWith("m=video") }
            if (videoMLineIndex != -1) {
                val elements = lines[videoMLineIndex].split(" ").toMutableList()
                
                // Find potential payload types
                val h264PT = lines.find { it.contains("a=rtpmap:") && it.contains("H264/90000") }
                    ?.substringAfter("a=rtpmap:")?.substringBefore(" ")
                val vp8PT = lines.find { it.contains("a=rtpmap:") && it.contains("VP8/90000") }
                    ?.substringAfter("a=rtpmap:")?.substringBefore(" ")

                if (h264PT != null && elements.size > 3) {
                    elements.remove(h264PT)
                    elements.add(3, h264PT)
                } else if (vp8PT != null && elements.size > 3) {
                    elements.remove(vp8PT)
                    elements.add(3, vp8PT)
                }
                newLines[videoMLineIndex] = elements.joinToString(" ")
                
                // Add Bitrate suggestion (b=AS:2000 for ~2Mbps)
                if (!lines.any { it.startsWith("b=AS:") }) {
                    newLines.add(videoMLineIndex + 1, "b=AS:2000")
                }
            }
            
            val finalSdp = newLines.joinToString("\r\n")
            Log.d(TAG, "✓ SDP Optimized: Audio(Opus), Video(H264/VP8), Bitrate(2000kbps)")
            return finalSdp
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error optimizing SDP", e)
            return sdp
        }
    }
}

// openHelper for simplified observer
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

@Composable
fun CallScreen(
    remoteRenderer: SurfaceViewRenderer,
    localRenderer: SurfaceViewRenderer,
    partnerName: String,
    duration: String,
    statusText: String,
    isBillingActive: Boolean,
    callType: String,
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    isSpeakerOn: Boolean,
    role: String,
    remainingTime: String,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    onEditIntake: () -> Unit,
    onShowRasi: () -> Unit,
    onShowMatch: () -> Unit,
    isRecording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    isReady: Boolean,
    clientBirthData: JSONObject? = null,
    summary: CallActivity.SimpleSummary? = null,
    onDismissSummary: () -> Unit = {}
) {
    val colors = com.astrohark.app.ui.theme.CosmicAppTheme.colors
    val context = LocalContext.current
    
    // Modern Summary Overlay
    if (summary != null) {
        ModernSummaryDialog(
            title = if (summary.reason == "insufficient_funds") "Low Balance" else "Call Summary",
            duration = summary.duration,
            amount = summary.amount,
            isAstrologer = role == "astrologer",
            onDismiss = onDismissSummary
        )
    }

    BackHandler {
        Toast.makeText(context, "Call irugum pothu back button vela seiyathu. Mudika 'End Call' azhuthavum", Toast.LENGTH_LONG).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicAppTheme.backgroundBrush)
    ) {
        // Remote View Layer (Full Screen)
        if (callType == "video" && isReady) {
            AndroidView(
                factory = { remoteRenderer },
                modifier = Modifier.fillMaxSize()
            )
        } else if (callType == "video" && !isReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE6C15A))
                Text(
                    text = "Initializing Camera...", 
                    color = Color.White.copy(alpha = 0.6f), 
                    modifier = Modifier.padding(top = 80.dp),
                    fontSize = 14.sp
                )
            }
        } else {
            // Audio Call UI Placeholder
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = CosmicAppTheme.colors.cardBg,
                        modifier = Modifier.size(160.dp),
                        border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User",
                            tint = CosmicAppTheme.colors.accent.copy(alpha = 0.5f),
                            modifier = Modifier.padding(40.dp).fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Encrypted Audio Call",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        // Top Info Bar Area (Glassmorphic)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AstroDimens.Medium)
                .padding(top = 32.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(AstroDimens.RadiusLarge))
                .background(CosmicAppTheme.colors.cardBg.copy(alpha = 0.8f))
                .border(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f), RoundedCornerShape(AstroDimens.RadiusLarge))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partnerName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = CosmicAppTheme.colors.accent,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = duration,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (isRecording) {
                            Spacer(modifier = Modifier.width(12.dp))
                            RecordingIndicator()
                        }
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    if (role == "astrologer" && remainingTime.isNotEmpty() && remainingTime != "00:00") {
                        Surface(
                            color = Color.Red.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = remainingTime,
                                color = Color.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (statusText.isNotEmpty()) {
                        Text(
                            text = statusText,
                            color = Color(0xFF25D366), // Signal Green
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Local Video (PIP) with Glow
        if (callType == "video") {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = AstroDimens.Medium, bottom = 180.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(AstroDimens.RadiusMedium))
                    .border(2.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.4f), RoundedCornerShape(AstroDimens.RadiusMedium))
                    .shadow(12.dp, RoundedCornerShape(AstroDimens.RadiusMedium), spotColor = CosmicAppTheme.colors.accent)
                    .background(Color.Black)
            ) {
                if (isReady) {
                    AndroidView(
                        factory = { localRenderer },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Bottom Controls Container (Glassmorphic Panel)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(AstroDimens.Medium)
                .fillMaxWidth()
                .clip(RoundedCornerShape(AstroDimens.RadiusLarge))
                .background(CosmicAppTheme.colors.cardBg.copy(alpha = 0.9f))
                .border(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f), RoundedCornerShape(AstroDimens.RadiusLarge))
                .padding(AstroDimens.Large)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AstroDimens.Medium)
            ) {
                // Media Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlBtnItem(onClick = onToggleMic, icon = if (!isMuted) Icons.Default.Mic else Icons.Default.MicOff, label = "Mute", active = !isMuted)
                    if (callType == "video") {
                        ControlBtnItem(onClick = onToggleCamera, icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, label = "Camera", active = isVideoEnabled)
                    }
                    ControlBtnItem(onClick = onToggleSpeaker, icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff, label = "Speaker", active = isSpeakerOn)
                }

                // Actions & End Call Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Actions (Astrologer only)
                    if (role == "astrologer") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ControlBtnItem(onClick = onShowRasi, icon = Icons.Default.GridView, label = "Chart", active = true)
                            
                            val hasPartner = clientBirthData?.has("partnerData") == true || clientBirthData?.has("partner") == true
                            ControlBtnItem(
                                onClick = onShowMatch, 
                                icon = if (hasPartner) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                                label = "Match", 
                                active = hasPartner
                            )
                        }
                    } else {
                        ControlBtnItem(onClick = onEditIntake, icon = Icons.Default.EditNote, label = "Intake", active = false)
                    }

                    // Centered End Call Button (Prominent)
                    Surface(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(16.dp, CircleShape, spotColor = Color.Red),
                        shape = CircleShape,
                        color = Color(0xFFFF4B4B)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CallEnd, "End Call", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    // Right Actions
                    if (role == "astrologer") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ControlBtnItem(onClick = onEditIntake, icon = Icons.Default.EditNote, label = "Edit", active = false)
                            ControlBtnItem(
                                onClick = onToggleRecording,
                                icon = if (isRecording) Icons.Default.StopCircle else Icons.Default.RadioButtonChecked,
                                label = if (isRecording) "Stop" else "Rec",
                                active = isRecording
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ControlBtnItem(onClick: () -> Unit, icon: Any, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val bgColor = if (active) CosmicAppTheme.colors.accent.copy(alpha = 0.2f) else CosmicAppTheme.colors.cardBg
        val tintColor = if (active) CosmicAppTheme.colors.accent else CosmicAppTheme.colors.textSecondary
        val borderColor = if (active) CosmicAppTheme.colors.accent.copy(alpha = 0.4f) else CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f)

        Surface(
            onClick = onClick,
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = bgColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (icon) {
                    is ImageVector -> Icon(icon, null, tint = tintColor, modifier = Modifier.size(24.dp))
                    is Int -> Icon(painterResource(icon), null, tint = tintColor, modifier = Modifier.size(24.dp))
                }
            }
        }
        Text(
            text = label, 
            fontSize = 11.sp, 
            fontWeight = FontWeight.Medium, 
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Red.copy(alpha = 0.1f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer(alpha = alpha)
                .background(Color.Red, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "LIVE REC",
            color = Color.Red,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
