package com.astrohark.app.ui.chat

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.astrohark.app.data.local.entity.ChatMessageEntity
import com.astrohark.app.data.repository.ChatRepository
import com.astrohark.app.data.remote.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

// Data class for session summary
data class SessionSummary(
    val reason: String,
    val deducted: Double,
    val earned: Double,
    val duration: Int
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private val _messages = MutableLiveData<ChatMessage>()
    val messages: LiveData<ChatMessage> = _messages

    private val _history = MutableLiveData<List<ChatMessage>>()
    val history: LiveData<List<ChatMessage>> = _history

    private val _messageStatus = MutableLiveData<JSONObject>()
    val messageStatus: LiveData<JSONObject> = _messageStatus

    private val _typingStatus = MutableLiveData<Boolean>()
    val typingStatus: LiveData<Boolean> = _typingStatus

    private val _isAstrologerViewingChart = MutableLiveData<Boolean>()
    val isAstrologerViewingChart: LiveData<Boolean> = _isAstrologerViewingChart

    private val _sessionEnded = MutableLiveData<Boolean>()
    val sessionEnded: LiveData<Boolean> = _sessionEnded

    // Billing Events
    private val _billingStarted = MutableLiveData<Boolean>()
    val billingStarted: LiveData<Boolean> = _billingStarted

    private val _availableMinutes = MutableLiveData<Int>()
    val availableMinutes: LiveData<Int> = _availableMinutes

    private val _billingInfo = MutableLiveData<SocketManager.BillingInfo>()
    val billingInfo: LiveData<SocketManager.BillingInfo> = _billingInfo

    private val _sessionSummary = MutableLiveData<SessionSummary>()
    val sessionSummary: LiveData<SessionSummary> = _sessionSummary

    fun sendMessage(data: JSONObject) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Save to local DB first (optimistic)
                val msgId = data.getString("messageId")
                val content = data.getJSONObject("content")
                val text = content.optString("text", "")
                val type = content.optString("type", "text")
                val sessionId = data.optString("sessionId")
                val senderId = com.astrohark.app.data.local.TokenManager(getApplication()).getUserSession()?.userId ?: ""

                if (type != "system-chart-viewing") {
                    val entity = ChatMessageEntity(
                        messageId = msgId,
                        sessionId = sessionId,
                        text = text,
                        senderId = senderId,
                        timestamp = System.currentTimeMillis(),
                        status = "sent",
                        isSentByMe = true,
                        type = type,
                        fileUrl = content.optString("fileUrl", "")
                    )
                    repository.saveMessage(entity)
                }
            } catch (e: Exception) { e.printStackTrace() }

            repository.sendMessage(data)
        }
    }

    fun sendTyping(toUserId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendTyping(toUserId)
        }
    }

    fun sendStopTyping(toUserId: String) {
         viewModelScope.launch(Dispatchers.IO) {
            repository.sendStopTyping(toUserId)
        }
    }

    fun markDelivered(messageId: String, toUserId: String, sessionId: String) {
         viewModelScope.launch(Dispatchers.IO) {
            repository.markDelivered(messageId, toUserId, sessionId)
        }
    }

    fun markRead(messageId: String, toUserId: String, sessionId: String) {
         viewModelScope.launch(Dispatchers.IO) {
            repository.markRead(messageId, toUserId, sessionId)
        }
    }

    fun uploadFileAndSend(file: java.io.File, type: String, sessionId: String, toUserId: String, durationStr: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mediaType = (if (type == "image") "image/*" else "audio/*").toMediaTypeOrNull()
                val requestFile = okhttp3.RequestBody.create(mediaType, file)
                val body = okhttp3.MultipartBody.Part.createFormData("file", file.name, requestFile)
                val response = com.astrohark.app.data.api.ApiClient.api.uploadFile(body)
                if (response.isSuccessful) {
                    val urlElement = response.body()?.get("url")
                    val url = if (urlElement != null && !urlElement.isJsonNull) urlElement.asString else ""
                    if (url.isNotEmpty()) {
                        val payload = JSONObject().apply {
                            put("messageId", java.util.UUID.randomUUID().toString())
                            put("sessionId", sessionId)
                            put("toUserId", toUserId)
                            put("content", JSONObject().apply {
                                put("type", type)
                                put("fileUrl", url)
                                put("text", if (type == "image") "Sent an Image" else if (durationStr.isNotEmpty()) "Voice Message|$durationStr" else "Sent a Voice Message")
                            })
                        }
                        sendMessage(payload)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }



    fun acceptSession(sessionId: String, toUserId: String) {
        // Use default dispatcher (Main) for Coroutine to allow delay loop to work properly
        viewModelScope.launch {
             // Force connection
             if (SocketManager.getSocket()?.connected() != true) {
                 SocketManager.getSocket()?.connect()
             }

             // Wait for connection
             var connected = false
             repeat(20) {
                 if (SocketManager.getSocket()?.connected() == true) {
                     delay(500) // Ensure registration
                     repository.acceptSession(sessionId, toUserId)
                     connected = true
                     return@launch
                 }
                 delay(500)
             }

             // Fallback
             if (!connected) {
                 repository.acceptSession(sessionId, toUserId)
             }
        }
    }

    fun joinSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
             val payload = JSONObject().apply { put("sessionId", sessionId) }
             SocketManager.emitReliable("session-connect", payload)
        }
    }

    fun joinSessionSafe(sessionId: String) {
        viewModelScope.launch {
            // Force connection attempt to handle first-time connect issues
            SocketManager.getSocket()?.connect()

            // Wait for connection
            repeat(20) {
                if (SocketManager.getSocket()?.connected() == true) {
                    // Slight delay to ensure registration packet is sent first
                    delay(500)
                    joinSession(sessionId)
                    return@launch
                }
                delay(500)
            }
            // Fallback
             if (SocketManager.getSocket()?.connected() == true) {
                 joinSession(sessionId)
             }
        }
    }

    fun endSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SocketManager.endSession(sessionId)
        }
    }

    fun startListeners() {
        repository.listenIncoming { data ->
            try {
                val content = data.optJSONObject("content")
                val type = content?.optString("type") ?: data.optString("type", "text")

                if (type == "system-chart-viewing") {
                    _isAstrologerViewingChart.postValue(true)
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(15000)
                        _isAstrologerViewingChart.postValue(false)
                    }
                    return@listenIncoming
                }

                val text = content?.optString("text") ?: data.optString("text", "")
                val msgId = data.optString("messageId")
                val sessionId = data.optString("sessionId")
                val senderId = data.optString("fromUserId")

                val fileUrl = content?.optString("fileUrl") ?: data.optString("fileUrl", "")

                // Save to DB
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val entity = ChatMessageEntity(
                        messageId = msgId,
                        sessionId = sessionId,
                        text = text,
                        senderId = senderId,
                        timestamp = System.currentTimeMillis(),
                        status = "read",
                        isSentByMe = false,
                        type = type,
                        fileUrl = fileUrl
                    )
                    repository.saveMessage(entity)

                    // IMPORTANT: Emit read status back to sender for double tick
                    if (msgId.isNotEmpty() && senderId.isNotEmpty() && sessionId.isNotEmpty()) {
                        repository.markRead(msgId, senderId, sessionId)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            val msg = ChatMessage(msgId, text, false, "read", timestamp = System.currentTimeMillis(), type = type, fileUrl = fileUrl)
            _messages.postValue(msg)
            } catch (e: Exception) { e.printStackTrace() }
        }

        repository.listenMessageStatus { data ->
            _messageStatus.postValue(data)
            // Update the message status in history
            val msgId = data.optString("messageId")
            val status = data.optString("status")
            if (msgId.isNotEmpty() && status.isNotEmpty()) {
                val currentHistory = _history.value?.toMutableList() ?: mutableListOf()
                val index = currentHistory.indexOfFirst { it.id == msgId }
                if (index >= 0) {
                    currentHistory[index] = currentHistory[index].copy(status = status)
                    _history.postValue(currentHistory)
                }
                // Also update in local DB
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updateMessageStatus(msgId, status)
                }
            }
        }

        repository.listenTyping {
            _typingStatus.postValue(true)
        }

        repository.listenStopTyping {
            _typingStatus.postValue(false)
        }

        // Billing Started Listener
        SocketManager.onBillingStarted { info ->
            _billingStarted.postValue(true)
            _availableMinutes.postValue(info.availableMinutes)
            _billingInfo.postValue(info)
        }

        // Session Ended with Summary
        SocketManager.onSessionEndedWithSummary { reason, deducted, earned, duration ->
            _sessionSummary.postValue(SessionSummary(reason, deducted, earned, duration))
            _sessionEnded.postValue(true)
        }
    }

    fun stopListeners() {
        repository.removeListeners()
        SocketManager.off("billing-started")
        // session-ended is handled by onSessionEndedWithSummary which removes the old listener
    }

    fun loadHistory(sessionId: String) {
        // Observe Local DB immediately (Main Source of Truth)
        viewModelScope.launch(Dispatchers.IO) {
            repository.getMessages(sessionId).collect { entities ->
                val uiMessages = entities.map { entity ->
                    ChatMessage(
                        id = entity.messageId,
                        text = entity.text,
                        isSent = entity.isSentByMe,
                        status = entity.status,
                        timestamp = entity.timestamp,
                        type = entity.type,
                        fileUrl = entity.fileUrl
                    )
                }
                _history.postValue(uiMessages)
            }
        }

        // Fetch missing history from server in parallel
        viewModelScope.launch(Dispatchers.IO) {
             try {
                 repository.fetchHistoryFromServer(sessionId, limit = 50)
             } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private var isHistoryLoading = false
    private var isMoreHistoryAvailable = true

    fun loadMoreHistory(sessionId: String, oldestTimestamp: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isHistoryLoading || !isMoreHistoryAvailable) return@launch
            isHistoryLoading = true

            val success = repository.fetchHistoryFromServer(sessionId, limit = 10, before = oldestTimestamp)
            if (!success) {
                // Handle failure or no more messages logic if needed
            }
            isHistoryLoading = false
        }
    }
}
