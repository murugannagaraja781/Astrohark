package com.astrohark.app.data.remote

import android.util.Log
import com.astrohark.app.utils.Constants
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketManager {
    private const val TAG = "SocketManager"
    private var socket: Socket? = null
    private var initialized = false
    private var currentUserId: String? = null
    private var isRegistered = false

    fun init() {
        if (initialized) return

        try {
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                timeout = 20000
                transports = arrayOf("websocket", "polling")
            }
            val url = Constants.SERVER_URL ?: "http://10.0.2.2:3000"
            socket = IO.socket(url, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected: ${socket?.id()}")
                isRegistered = false // Reset on new connection
                if (currentUserId != null) {
                    registerUser(currentUserId!!)
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket disconnected")
            }

            socket?.connect()
            initialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun ensureConnection() {
        if (socket == null) {
            init()
        }
        if (socket?.connected() != true) {
            socket?.connect()
        }
    }

    fun registerUser(userId: String, callback: ((Boolean) -> Unit)? = null) {
        currentUserId = userId
        val data = JSONObject()
        data.put("userId", userId)

        socket?.emit("register", data, Ack { args ->
            val success = if (args != null && args.isNotEmpty()) {
                val response = args[0] as? JSONObject
                response?.optBoolean("ok") == true
            } else {
                false
            }
            isRegistered = success
            Log.d(TAG, "User registered: $userId, success=$success")
            callback?.invoke(success)
        })
    }

    fun getSocket(): Socket? {
        if (socket == null && !initialized) {
            init()
        }
        return socket
    }

    fun requestSession(toUserId: String, type: String, birthData: JSONObject? = null, callback: ((JSONObject?) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("toUserId", toUserId)
            put("fromUserId", currentUserId) // Always send for robustness
            put("type", type)
            if (birthData != null) {
                put("birthData", birthData)
            }
        }
        socket?.emit("request-session", payload, Ack { args ->
            if (args != null && args.isNotEmpty()) {
                callback?.invoke(args[0] as? JSONObject)
            } else {
                callback?.invoke(null)
            }
        })
    }

    fun onSessionAnswered(listener: (JSONObject) -> Unit) {
        socket?.off("session-answered")
        socket?.on("session-answered") { args ->
            try {
                if (args != null && args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        listener(data)
                    } else {
                         Log.w(TAG, "session-answered received with non-JSON payload: ${args[0]}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in session-answered listener", e)
            }
        }
    }

    fun onSignal(listener: (JSONObject) -> Unit) {
        socket?.on("ping") { args ->
            if (args != null && args.isNotEmpty()) {
                socket?.emit("pong", args[0])
            }
        }
        socket?.off("signal")
        socket?.on("signal") { args ->
            try {
                if (args != null && args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        listener(data)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in signal listener", e)
            }
        }
    }

    fun emitSignal(data: JSONObject) {
        ensureConnection()
        socket?.emit("signal", data)
    }

    /**
     * Ensures the socket is connected and user is registered before emitting a critical signal.
     */
    fun emitReliable(event: String, payload: JSONObject, ack: Ack? = null) {
        if (socket == null || !socket!!.connected()) {
            Log.w(TAG, "Socket not connected. Attempting reconnect before emitting $event")
            ensureConnection()
            // Using once listener to wait for connection
            socket?.once(Socket.EVENT_CONNECT) {
                if (currentUserId != null) {
                    registerUser(currentUserId!!) {
                        socket?.emit(event, payload, ack)
                    }
                } else {
                    socket?.emit(event, payload, ack)
                }
            }
        } else {
            socket?.emit(event, payload, ack)
        }
    }

    fun onMessageStatus(listener: (JSONObject) -> Unit) {
        socket?.off("message-status")
        socket?.on("message-status") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                if (data != null) {
                    listener(data)
                }
            }
        }
    }

    fun endSession(sessionId: String?) {
        val payload = JSONObject()
        if (sessionId != null) {
            payload.put("sessionId", sessionId)
        }
        socket?.emit("end-session", payload)
    }

    fun cancelCall(sessionId: String?, toUserId: String?) {
        val payload = JSONObject().apply {
            put("sessionId", sessionId)
            put("toUserId", toUserId)
        }
        socket?.emit("cancel-call", payload)
    }

    fun getHistory(sessionId: String, callback: ((List<JSONObject>) -> Unit)) {
        val payload = JSONObject().apply {
            put("sessionId", sessionId)
        }
        socket?.emit("get-history", payload, Ack { args ->
             val list = mutableListOf<JSONObject>()
             if (args != null && args.isNotEmpty()) {
                 val response = args[0] as? JSONObject
                 if (response?.optBoolean("ok") == true) {
                     val msgs = response.optJSONArray("messages")
                     if (msgs != null) {
                        for (i in 0 until msgs.length()) {
                            list.add(msgs.getJSONObject(i))
                        }
                     }
                 }
             }
             callback(list)
        })
    }

    fun onSessionEnded(listener: (JSONObject?) -> Unit) {
        socket?.off("session-ended")
        socket?.on("session-ended") { args ->
            try {
                Log.d(TAG, "SocketEvent: session-ended received")
                val data = if (args != null && args.isNotEmpty()) args[0] as? JSONObject else null
                listener(data)
            } catch (e: Exception) {
                Log.e(TAG, "Error in session-ended listener", e)
            }
        }
    }

    fun onCallCancelled(listener: (JSONObject) -> Unit) {
        socket?.off("call-cancelled")
        socket?.on("call-cancelled") { args ->
            Log.d(TAG, "SocketEvent: call-cancelled received")
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                if (data != null) {
                    listener(data)
                }
            }
        }
    }

    fun onSessionEndedWithSummary(listener: (reason: String, deducted: Double, earned: Double, duration: Int) -> Unit) {
        onSessionEnded { data ->
            var reason = "ended"
            var deducted = 0.0
            var earned = 0.0
            var duration = 0

            if (data != null) {
                reason = data.optString("reason", "ended") ?: "ended"
                val summary = data.optJSONObject("summary")
                if (summary != null) {
                    deducted = summary.optDouble("deducted", 0.0)
                    earned = summary.optDouble("earned", 0.0)
                    duration = summary.optInt("duration", 0)
                }
            }
            listener(reason, deducted, earned, duration)
        }
    }

    data class BillingInfo(
        val startTime: Long,
        val clientBalance: Double,
        val ratePerMinute: Double,
        val availableMinutes: Int
    )

    fun onBillingStarted(listener: (BillingInfo) -> Unit) {
        socket?.on("billing-started") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                val startTime = data?.optLong("startTime", System.currentTimeMillis()) ?: System.currentTimeMillis()
                val clientBalance = data?.optDouble("clientBalance", 0.0) ?: 0.0
                val ratePerMinute = data?.optDouble("ratePerMinute", 10.0) ?: 10.0
                val availableMinutes = data?.optInt("availableMinutes", 0) ?: 0
                Log.d(TAG, "Billing started. Available: $availableMinutes mins, Balance: ₹$clientBalance")
                listener(BillingInfo(startTime, clientBalance, ratePerMinute, availableMinutes))
            }
        }
    }

    fun onWalletUpdate(listener: (JSONObject) -> Unit) {
        socket?.on("wallet-update") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                if (data != null) {
                    listener(data)
                }
            }
        }
    }

    fun off(event: String) {
        socket?.off(event)
    }

    fun onConnect(listener: () -> Unit) {
        if (socket?.connected() == true) {
            listener()
        } else {
            socket?.on(Socket.EVENT_CONNECT) {
                listener()
            }
        }
    }

    fun updateServiceStatus(userId: String, service: String, isEnabled: Boolean) {
        val data = JSONObject().apply {
            put("userId", userId)
            put("service", service)
            put("isEnabled", isEnabled)
        }
        socket?.emit("update-service-status", data)
    }

    fun onAstrologerUpdate(listener: (JSONObject) -> Unit) {
        socket?.on("astrologer-update") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                if (data != null) {
                    listener(data)
                }
            }
        }
    }

    fun onIncomingSession(listener: (JSONObject) -> Unit) {
        socket?.off("incoming-session")
        socket?.on("incoming-session") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                if (data != null) {
                    Log.d(TAG, "Incoming session received: $data")
                    listener(data)
                }
            }
        }
    }

    fun offIncomingSession() {
        socket?.off("incoming-session")
    }

    fun updateProfile(updates: JSONObject, callback: ((JSONObject?) -> Unit)? = null) {
        socket?.emit("update-profile", updates, Ack { args ->
            if (args != null && args.isNotEmpty()) {
                callback?.invoke(args[0] as? JSONObject)
            } else {
                callback?.invoke(null)
            }
        })
    }

    fun requestWithdrawal(amount: Double, callback: ((JSONObject?) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("amount", amount)
        }
        socket?.emit("request-withdrawal", payload, Ack { args ->
            if (args != null && args.isNotEmpty()) {
                callback?.invoke(args[0] as? JSONObject)
            } else {
                callback?.invoke(null)
            }
        })
    }

    fun getMyWithdrawals(callback: ((List<JSONObject>) -> Unit)) {
        socket?.emit("get-my-withdrawals", null, Ack { args ->
            val list = mutableListOf<JSONObject>()
            if (args != null && args.isNotEmpty()) {
                val response = args[0] as? JSONObject
                if (response?.optBoolean("ok") == true) {
                    val arr = response.optJSONArray("withdrawals")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            list.add(arr.getJSONObject(i))
                        }
                    }
                }
            }
            callback(list)
        })
    }

    fun logout(callback: ((Boolean) -> Unit)? = null) {
        socket?.emit("logout", null, Ack { args ->
            val success = if (args != null && args.isNotEmpty()) {
                val response = args[0] as? JSONObject
                response?.optBoolean("ok") == true
            } else {
                false
            }
            callback?.invoke(success)
        })
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        initialized = false
    }

    fun removeChatListeners() {
        socket?.off("chat-message")
        socket?.off("message-status")
        socket?.off("typing")
        socket?.off("stop-typing")
    }
}
