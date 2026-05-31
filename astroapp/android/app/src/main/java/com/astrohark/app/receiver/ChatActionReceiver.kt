package com.astrohark.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.astrohark.app.ui.chat.ChatActivity

/**
 * Receiver for Accept / Reject actions from the full‑screen chat notification.
 *
 * ACTION_ACCEPT_CHAT  – launches ChatActivity (same extras as the full‑screen intent).
 * ACTION_REJECT_CHAT  – cancels the notification and optionally notifies the backend.
 */
class ChatActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ACCEPT_CHAT = "com.astrohark.ACTION_ACCEPT_CHAT"
        const val ACTION_REJECT_CHAT = "com.astrohark.ACTION_REJECT_CHAT"
        private const val TAG = "ChatActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val callerId = intent.getStringExtra("callerId") ?: return
        val sessionId = intent.getStringExtra("sessionId") ?: return

        when (action) {
            ACTION_ACCEPT_CHAT -> {
                Log.d(TAG, "User accepted chat request from $callerId")
                // Re‑use the same extras as the full‑screen intent to open ChatActivity
                val chatIntent = Intent(context, ChatActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("toUserId", callerId)
                    putExtra("sessionId", sessionId)
                    putExtra("isNewRequest", true)
                }
                context.startActivity(chatIntent)
            }
            ACTION_REJECT_CHAT -> {
                Log.d(TAG, "User rejected chat request from $callerId")
                // Dismiss the notification
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(callerId.hashCode())
                // TODO: inform backend about rejection if needed
            }
            else -> {
                Log.w(TAG, "Unknown action received: $action")
            }
        }
    }
}
