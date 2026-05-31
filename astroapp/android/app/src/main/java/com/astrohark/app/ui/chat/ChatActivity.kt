package com.astrohark.app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import com.astrohark.app.utils.VoiceRecorder
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrohark.app.R
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.data.remote.SocketManager
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AstroDimens
import com.astrohark.app.ui.common.ModernSummaryDialog
import com.astrohark.app.utils.SoundManager
import org.json.JSONObject
import java.util.UUID

data class ChatMessage(val id: String, val text: String?, val isSent: Boolean, var status: String? = "sent", val timestamp: Long = 0, val type: String? = "text", val fileUrl: String? = "")

class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var audioPlayer: ChatAudioPlayer
    private var toUserId: String? = null
    private var sessionId: String? = null
    private var clientBirthData by mutableStateOf<JSONObject?>(null)
    private var sessionDuration by mutableStateOf("00:00")
    private var remainingTime by mutableStateOf("")
    private var chatDurationSeconds = 0
    private var remainingSeconds = 0
    private var timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isTimerStarted = false
    private val timerRunnable = object : Runnable {
        override fun run() {
            chatDurationSeconds++
            val minutes = chatDurationSeconds / 60
            val seconds = chatDurationSeconds % 60
            sessionDuration = String.format("%02d:%02d", minutes, seconds)

            if (remainingSeconds > 0) {
                remainingSeconds--
                val remMins = remainingSeconds / 60
                val remSecs = remainingSeconds % 60
                remainingTime = String.format("%02d:%02d", remMins, remSecs)
            } else if (remainingSeconds == 0 && remainingTime.isNotEmpty()) {
                remainingTime = "00:00"
            }

            timerHandler.postDelayed(this, 1000)
        }
    }

    private val editIntakeLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
             val dataStr = result.data?.getStringExtra("birthData")
             if (dataStr != null) {
                 try {
                     val newData = JSONObject(dataStr)
                     clientBirthData = newData
                     Toast.makeText(this, "Details Updated", Toast.LENGTH_SHORT).show()
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("toUserId", toUserId)
                         put("birthData", newData)
                     })
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }
    }

    private var hasEmittedAnswer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.astrohark.app.utils.FullScreenHelper.enableFullScreen(this)
        try {
            // Ensure socket is initialized and connected
            com.astrohark.app.data.remote.SocketManager.init()
            com.astrohark.app.data.remote.SocketManager.ensureConnection()
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            
            handleIntent(intent)

            val role = TokenManager(this).getUserSession()?.role

            // --- GLOBAL STATE FIX: Mark chat as active to prevent incoming calls during session ---
            com.astrohark.app.utils.CallState.isCallActive = true
            com.astrohark.app.utils.CallState.currentSessionId = sessionId
            
            audioPlayer = ChatAudioPlayer(this)
            
            setContent {
                CosmicAppTheme {
                    ChatScreen(
                        viewModel = viewModel,
                        audioPlayer = audioPlayer,
                        sessionDuration = sessionDuration,
                        title = intent?.getStringExtra("toUserName") ?: "Chat",
                        onBack = { finish() },
                        onEndChat = { endChat() },
                        onEditIntake = {
                            val intent = Intent(this, com.astrohark.app.ui.intake.IntakeActivity::class.java)
                            intent.putExtra("isEditMode", true)
                            intent.putExtra("existingData", clientBirthData?.toString())
                            if (role == "astrologer") {
                                intent.putExtra("targetUserId", toUserId)
                            }
                            editIntakeLauncher.launch(intent)
                        },
                        onViewChart = {
                            if (clientBirthData != null) {
                                if (role == "astrologer" && toUserId != null && sessionId != null) {
                                    val payload = org.json.JSONObject().apply {
                                        put("messageId", java.util.UUID.randomUUID().toString())
                                        put("sessionId", sessionId)
                                        put("toUserId", toUserId)
                                        put("content", org.json.JSONObject().apply {
                                            put("type", "system-chart-viewing")
                                            put("text", "chart_opened")
                                        })
                                    }
                                    viewModel.sendMessage(payload)
                                }
                                val intent = Intent(this, com.astrohark.app.ui.chart.VipChartActivity::class.java)
                                intent.putExtra("birthData", clientBirthData.toString())
                                startActivity(intent)
                            } else {
                                 Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSessionFinished = { finishSessionAndNavigate() },
                        isAstrologer = role == "astrologer",
                        toUserId = toUserId,
                        sessionId = sessionId,
                        remainingTime = remainingTime,
                        clientBirthData = clientBirthData
                    )
                }
            }
            setupObservers()
            isTimerStarted = true
            timerHandler.post(timerRunnable)

            val myUserId = TokenManager(this).getUserSession()?.userId
            if (role == "astrologer" && myUserId != null) {
                com.astrohark.app.AstrologerStatusService.startService(this, myUserId)
            }

            // --- STABILITY FIX: Immediate registration and answer emission ---
            if (myUserId != null) {
                SocketManager.registerUser(myUserId) {
                    if (pendingAccept && !hasEmittedAnswer && sessionId != null && toUserId != null) {
                        pendingAccept = false
                        hasEmittedAnswer = true
                        viewModel.acceptSession(sessionId!!, toUserId!!)
                        android.util.Log.d("ChatActivity", "Immediate answer-session emitted for $sessionId")
                    }
                }
            }

            // Listen for client birth data updates during session
            com.astrohark.app.data.remote.SocketManager.getSocket()?.on("client-birth-chart") { args ->
                if (args != null && args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    val updatedData = data?.optJSONObject("birthData")
                    if (updatedData != null) {
                        runOnUiThread {
                            clientBirthData = updatedData
                            if (role == "client") {
                                Toast.makeText(this@ChatActivity, "Astrologer updated your birth details", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@ChatActivity, "Client updated their birth details", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "CRITICAL SETUP ERROR", e)
            Toast.makeText(this, "Session Setup Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.let {
            setIntent(it)
            handleIntent(it)
        }
    }

    private var pendingAccept = false

    private fun handleIntent(intent: Intent?) {
        toUserId = intent?.getStringExtra("toUserId")
        sessionId = intent?.getStringExtra("sessionId")
        val birthDataStr = intent?.getStringExtra("birthData")
        if (!birthDataStr.isNullOrEmpty()) {
             try {
                val obj = JSONObject(birthDataStr)
                if (obj.length() > 0) clientBirthData = obj
             } catch (e: Exception) { e.printStackTrace() }
        }
        if (sessionId == null) {
            finish()
            return
        }
        val isNewRequest = intent?.getBooleanExtra("isNewRequest", false) == true
        if (isNewRequest && sessionId != null && toUserId != null) {
            SoundManager.playAcceptSound()
            pendingAccept = true // Will emit in onResume after socket registration
        }
        if (sessionId != null) {
              viewModel.loadHistory(sessionId!!)
              viewModel.joinSessionSafe(sessionId!!)
        }
    }

    private fun setupObservers() {
        viewModel.sessionSummary.observe(this) { summary ->
            timerHandler.removeCallbacks(timerRunnable)
            // Show summary or just finish
            Toast.makeText(this, "Chat Completed. Duration: ${summary.duration}s", Toast.LENGTH_LONG).show()
            finishSessionAndNavigate()
        }
        viewModel.sessionEnded.observe(this) { ended ->
            if (ended) {
                android.util.Log.d("ChatActivity", "sessionEnded observed. Summary: ${viewModel.sessionSummary.value}")
                // If summary is null, we can finish immediately.
                // If it's not null, sessionSummary observer will handle it.
                if (viewModel.sessionSummary.value == null) {
                    Toast.makeText(this, "Chat Ended", Toast.LENGTH_SHORT).show()
                    finishSessionAndNavigate()
                }
            }
        }
        viewModel.availableMinutes.observe(this) { mins ->
            android.util.Log.d("ChatActivity", "availableMinutes update: $mins")
            remainingSeconds = (mins * 60)
            val remMins = remainingSeconds / 60
            val remSecs = remainingSeconds % 60
            remainingTime = String.format("%02d:%02d", remMins, remSecs)

            // Auto-end if balance hits 0
            if (mins <= 0 && isTimerStarted) {
                android.util.Log.w("ChatActivity", "Balance reached 0. Ending session.")
                Toast.makeText(this, "Balance Exhausted. Ending...", Toast.LENGTH_SHORT).show()
                endChat()
            }
        }
    }

    private fun finishSessionAndNavigate() {
        // Clear all notifications
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancelAll()

        val userSession = TokenManager(this).getUserSession()
        val intent = if (userSession?.role == "astrologer") {
            android.content.Intent(this, com.astrohark.app.ui.astro.AstrologerDashboardActivity::class.java)
        } else {
            android.content.Intent(this, com.astrohark.app.MainActivity::class.java)
        }
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun endChat() {
        android.util.Log.d("ChatActivity", "endChat clicked. SessionId: $sessionId")
        if (sessionId != null) {
            Toast.makeText(this, "Ending Chat...", Toast.LENGTH_SHORT).show()
            viewModel.endSession(sessionId!!)
            // We wait for the session-ended event from socket for both sides to finish gracefully
        } else {
             Toast.makeText(this, "Error: Session ID is null", Toast.LENGTH_SHORT).show()
             finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-synchronize on resume to catch any messages missed during multitasking
        sessionId?.let {
            viewModel.loadHistory(it)
            viewModel.joinSessionSafe(it)
        }

        viewModel.startListeners()
        val myUserId = TokenManager(this).getUserSession()?.userId
        if (myUserId != null) {
            SocketManager.registerUser(myUserId) {
                // Socket registered - now emit pending accept if any
                if (pendingAccept && sessionId != null && toUserId != null) {
                    pendingAccept = false
                    viewModel.acceptSession(sessionId!!, toUserId!!)
                    android.util.Log.d("ChatActivity", "Emitted acceptSession after socket registration")
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // We no longer stop listeners here to allow background reception while multi-tasking
    }

    override fun finish() {
        // Reset CallState
        com.astrohark.app.utils.CallState.isCallActive = false
        com.astrohark.app.utils.CallState.currentSessionId = null
        super.finish()
    }

    override fun onDestroy() {
        com.astrohark.app.utils.CallState.isCallActive = false
        com.astrohark.app.utils.CallState.currentSessionId = null
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        viewModel.stopListeners()
        audioPlayer.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    audioPlayer: ChatAudioPlayer,
    sessionDuration: String,
    title: String,
    onBack: () -> Unit,
    onEndChat: () -> Unit,
    onEditIntake: () -> Unit,
    onViewChart: () -> Unit,
    onSessionFinished: () -> Unit,
    isAstrologer: Boolean,
    toUserId: String?,
    sessionId: String?,
    remainingTime: String,
    clientBirthData: JSONObject? = null
) {
    val messages by viewModel.history.observeAsState(emptyList())
    val isTyping by viewModel.typingStatus.observeAsState(false)
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // Reply State
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }

    // Recording State
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf("00:00") }
    var recordingTimerJob: kotlinx.coroutines.Job? by remember { mutableStateOf(null) }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                android.widget.Toast.makeText(context, "Microphone permission is required to send voice notes.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        if (inputStream != null && toUserId != null && sessionId != null) {
                            val tempFile = File.createTempFile("upload", ".jpg", context.cacheDir)
                            tempFile.outputStream().use { os -> inputStream.copyTo(os) }
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Uploading image...", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            viewModel.uploadFileAndSend(tempFile, "image", sessionId, toUserId)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    )

    // History Visibility State
    // Filter messages: Show all messages by default to ensure no data loss
    val displayedMessages = remember(messages) { messages }

    LaunchedEffect(displayedMessages.size) {
        if (displayedMessages.isNotEmpty()) listState.animateScrollToItem(displayedMessages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = CosmicAppTheme.colors.accent,
                            maxLines = 1
                        )
                         if (isTyping) {
                             Text(
                                 text = "Typing...",
                                 style = MaterialTheme.typography.labelSmall,
                                 color = Color(0xFF00C853), // Green for typing
                                 fontWeight = FontWeight.Bold
                             )
                         } else if (remainingTime.isNotEmpty() && remainingTime != "00:00") {
                              Text(
                                  text = "Time: $remainingTime",
                                  style = MaterialTheme.typography.labelSmall,
                                  color = Color.Black,
                                  fontWeight = FontWeight.Bold
                              )
                         } else {
                              Text(
                                  text = "Online",
                                  style = MaterialTheme.typography.labelSmall,
                                  color = CosmicAppTheme.colors.textSecondary
                              )
                         }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = CosmicAppTheme.colors.accent
                        )
                    }
                },
                actions = {
                    Text(
                        text = sessionDuration,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isAstrologer) Color.Black else Color.Red,
                        modifier = Modifier.padding(end = AstroDimens.Medium)
                    )
                    IconButton(onClick = onEditIntake) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Intake",
                            tint = CosmicAppTheme.colors.accent
                        )
                    }
                    TextButton(onClick = onEndChat) {
                        Text(
                            text = "End",
                            color = Color.Red,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CosmicAppTheme.colors.bgStart,
                    titleContentColor = CosmicAppTheme.colors.textPrimary
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                replyingTo = replyingTo,
                onTextChange = {
                    inputText = it
                    if (toUserId != null) viewModel.sendTyping(toUserId)
                },
                onCancelReply = { replyingTo = null },
                onSend = {
                    if (inputText.isNotBlank() && toUserId != null && sessionId != null) {
                         var finalText = inputText
                         if (replyingTo != null) {
                             // Prepend Reply Quote
                             val snippet = (replyingTo!!.text ?: "").take(50).replace("\n", " ")
                             finalText = "> Replying to: $snippet\n$inputText"
                         }

                         val payload = org.json.JSONObject().apply {
                            put("toUserId", toUserId)
                            put("sessionId", sessionId)
                            put("messageId", java.util.UUID.randomUUID().toString())
                            put("timestamp", System.currentTimeMillis())
                            put("content", org.json.JSONObject().put("text", finalText))
                         }
                         viewModel.sendMessage(payload)
                         com.astrohark.app.utils.SoundManager.playSentSound()
                         inputText = ""
                         replyingTo = null
                         viewModel.sendStopTyping(toUserId)
                    }
                },
                onAttachImage = {
                    imagePickerLauncher.launch("image/*")
                },
                onViewChart = if (isAstrologer) onViewChart else null,
                clientBirthData = clientBirthData,
                isRecording = isRecording,
                recordingDuration = recordingDuration,
                onStartRecording = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        voiceRecorder.startRecording()
                        isRecording = true
                        recordingDuration = "00:00"
                        recordingTimerJob = coroutineScope.launch {
                            var seconds = 0
                            while (isRecording) {
                                delay(1000)
                                seconds++
                                recordingDuration = String.format("%02d:%02d", seconds / 60, seconds % 60)
                            }
                        }
                    } else {
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = {
                    if (isRecording) {
                        isRecording = false
                        recordingTimerJob?.cancel()
                        val durationMs = voiceRecorder.stopRecording()
                        val durationSec = (durationMs / 1000).toInt()
                        
                        if (durationMs > 1000) {
                            val audioPath = voiceRecorder.currentOutputFile
                            val file = File(audioPath)
                            if (file.exists() && toUserId != null && sessionId != null) {
                                coroutineScope.launch {
                                    try {
                                            if (file.length() == 0L) {
                                                withContext(Dispatchers.Main) {
                                                    android.widget.Toast.makeText(context, "Error: Audio file is empty. Try recording again.", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                                return@launch
                                            }
                                            
                                            withContext(Dispatchers.Main) {
                                                android.widget.Toast.makeText(context, "Sending voice message...", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            delay(1000) // Increase delay to ensure file is completely written and unlocked by OS
                                            
                                            val durStr = String.format("%02d:%02d", durationSec / 60, durationSec % 60)
                                            viewModel.uploadFileAndSend(file, "audio", sessionId, toUserId, durStr)
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "Failed to send: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        e.printStackTrace()
                                    }
                                }
                            }
                        } else {
                            voiceRecorder.cancelRecording()
                            android.widget.Toast.makeText(context, "Recording too short", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // WhatsApp Style Background with Astrology Doodles
            androidx.compose.foundation.Image(
                painter = painterResource(id = com.astrohark.app.R.drawable.astrology_chat_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = 0.5f // Subtle watermark
            )

            // Modern Summary Overlay
            val summary by viewModel.sessionSummary.observeAsState()
            if (summary != null) {
                ModernSummaryDialog(
                    title = "Chat Summary",
                    duration = summary!!.duration,
                    amount = if (isAstrologer) summary!!.earned else summary!!.deducted,
                    isAstrologer = isAstrologer,
                    onDismiss = { onSessionFinished() }
                )
            }
            
            val isAstrologerViewingChart by viewModel.isAstrologerViewingChart.observeAsState(false)

            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = !isAstrologer && isAstrologerViewingChart,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF9C4)) // Light yellow
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "நான் உங்கள் ஜாதகத்தை பகுப்பாய்வு செய்கிறேன்...",
                            color = Color(0xFFE65100), // Dark orange/yellow text
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(AstroDimens.Medium),
                    verticalArrangement = Arrangement.spacedBy(AstroDimens.Small),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {


                if (displayedMessages.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                             Text(
                                 text = "No messages yet",
                                 color = Color.Gray,
                                 fontSize = 16.sp
                             )
                        }
                    }
                }

                items(displayedMessages) { msg ->
                    ChatBubble(msg, isAstrologer, audioPlayer, onReply = { replyingTo = msg })
                }
                if (isTyping) item { TypingBubble() }
            }
            } // Close Column
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(msg: ChatMessage, amIAstrologer: Boolean, audioPlayer: ChatAudioPlayer, onReply: () -> Unit) {
    val isMe = msg.isSent
    val bubbleColor = if (isMe) Color(0xFFE7FFDB) else Color.White // WhatsApp Colors
    val align = if (isMe) Alignment.End else Alignment.Start
    val shape = if (isMe) {
        RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)
    } else {
        RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)
    }

    // Swipe State
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.StartToEnd) {
                onReply()
                return@rememberSwipeToDismissBoxState false // Snap back
            }
            return@rememberSwipeToDismissBoxState false
        }
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val color = Color.Transparent
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Only show icon when swiping
                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                         Icon(Icons.Default.Send, contentDescription = "Reply", tint = Color.Gray)
                    }
                }
            },
            content = {
                Surface(
                    color = bubbleColor,
                    shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                    border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f)),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onReply
                        )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {

                        var displayText = msg.text ?: ""
                        // Check if this is a reply message
                        if (displayText.contains("> Replying to:")) {
                            // Robust splitting
                            val parts = displayText.split("\n", limit = 2)
                            if (parts.size >= 1 && parts[0].startsWith("> Replying to:")) {
                                val quoteText = parts[0].removePrefix("> Replying to: ").trim()
                                if (parts.size > 1) displayText = parts[1] else displayText = ""

                                // WhatsApp Style Quote Block
                                Surface(
                                    color = Color.Black.copy(alpha = 0.05f), // Slightly dimmed inside bubble
                                    shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(4.dp)
                                                .background(Color(0xFFE6C15A))
                                        )
                                        // Quote Content
                                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(
                                                text = "Replying to:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = CosmicAppTheme.colors.accent,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = quoteText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Black.copy(alpha = 0.7f),
                                                maxLines = 3
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (msg.type == "image") {
                            val rawUrl = msg.fileUrl ?: ""
                            val cleanImageUrl = if (rawUrl.startsWith("http")) {
                                rawUrl
                            } else if (rawUrl.startsWith("/")) {
                                "${com.astrohark.app.utils.Constants.SERVER_URL}$rawUrl"
                            } else {
                                "${com.astrohark.app.utils.Constants.SERVER_URL}/$rawUrl"
                            }
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val imageRequest = androidx.compose.runtime.remember(cleanImageUrl) {
                                coil.request.ImageRequest.Builder(context)
                                    .data(cleanImageUrl)
                                    .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                                    .crossfade(true)
                                    .build()
                            }
                            coil.compose.AsyncImage(
                                model = imageRequest,
                                contentDescription = "Image",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(200.dp)
                                    .padding(bottom = 4.dp)
                                    .background(Color.Gray, RoundedCornerShape(8.dp))
                            )
                        } else if (msg.type == "audio" || displayText.startsWith("[VOICE]:")) {
                            val audioUrl = if (msg.type == "audio") (msg.fileUrl ?: "") else displayText.substringAfter("[VOICE]:").split("|").getOrNull(0)?.replace("\\", "/") ?: ""
                            
                            // Parse duration correctly instead of forcing 0:00
                            var duration = "0:00"
                            if (displayText.startsWith("[VOICE]:")) {
                                duration = displayText.substringAfter("[VOICE]:").split("|").getOrNull(1) ?: "00:00"
                            } else if (displayText.contains("|")) {
                                duration = displayText.split("|").getOrNull(1) ?: "00:00"
                            }
                            
                            val fullAudioUrl = if (audioUrl.startsWith("http")) {
                                audioUrl
                            } else if (audioUrl.startsWith("/")) {
                                "${com.astrohark.app.utils.Constants.SERVER_URL}$audioUrl"
                            } else {
                                "${com.astrohark.app.utils.Constants.SERVER_URL}/$audioUrl"
                            }
                            
                            AudioPlayerBubble(
                                audioUrl = fullAudioUrl,
                                durationStr = duration,
                                isMe = isMe,
                                audioPlayer = audioPlayer
                            )
                        } else {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = CosmicAppTheme.colors.textPrimary
                            )
                        }

                        if (isMe) {
                            Row(
                                modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                                 verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when(msg.status) {
                                    "read" -> Icons.Default.DoneAll
                                    "delivered" -> Icons.Default.DoneAll
                                    else -> Icons.Default.Check
                                }
                                Icon(icon, null, tint = if(msg.status=="read") Color(0xFF34B7F1) else Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun TypingBubble() {
    Surface(
        color = CosmicAppTheme.colors.cardBg,
        shape = RoundedCornerShape(AstroDimens.RadiusMedium),
        border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f)),
        modifier = Modifier.padding(horizontal = AstroDimens.Medium, vertical = 4.dp)
    ) {
        Text(
            text = "Typing...",
            modifier = Modifier.padding(horizontal = AstroDimens.Medium, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = CosmicAppTheme.colors.textSecondary,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
fun ChatInputBar(
    text: String,
    replyingTo: ChatMessage?,
    onTextChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    onAttachImage: () -> Unit,
    onViewChart: (() -> Unit)?,
    clientBirthData: JSONObject? = null,
    isRecording: Boolean = false,
    recordingDuration: String = "00:00",
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {}
) {
    val colors = CosmicAppTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (replyingTo != null) {
                Card(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Replying to", fontSize = 10.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                            Text(replyingTo.text ?: "", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                        }
                        IconButton(onClick = onCancelReply, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Cancel", tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            
            // Main Input Container (Glass Card)
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(32.dp))
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onViewChart != null) {
                        val isReady = clientBirthData != null
                        IconButton(
                            onClick = onViewChart,
                            modifier = Modifier.size(40.dp).background(if(isReady) colors.accent.copy(alpha=0.1f) else Color.Transparent, CircleShape)
                        ) {
                            if (isReady) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_chart),
                                    contentDescription = "Chart",
                                    tint = colors.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Waiting",
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Attach Image Button
                    IconButton(
                        onClick = onAttachImage,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Attachment,
                            contentDescription = "Attach Image",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    if (isRecording) {
                        // Recording UI
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = recordingDuration,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Recording...",
                                color = Color.Gray,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(AstroDimens.RadiusLarge),
                            placeholder = { 
                                Text("Type a message...", style = MaterialTheme.typography.bodyMedium, color = CosmicAppTheme.colors.textSecondary.copy(alpha = 0.5f)) 
                            },
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = CosmicAppTheme.colors.accent
                            )
                        )
                    }

                    // Send / Mic Button
                    val isSendButton = text.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(colors.accent, Color(0xFFD4700B))
                                ),
                                CircleShape
                            )
                            .pointerInput(isSendButton) {
                                if (isSendButton) {
                                    detectTapGestures(
                                        onTap = { onSend() }
                                    )
                                } else {
                                    detectTapGestures(
                                        onPress = {
                                            onStartRecording()
                                            tryAwaitRelease()
                                            onStopRecording()
                                        }
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isSendButton) Icons.Default.Send else Icons.Default.Mic, 
                            contentDescription = if (isSendButton) "Send" else "Record", 
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
