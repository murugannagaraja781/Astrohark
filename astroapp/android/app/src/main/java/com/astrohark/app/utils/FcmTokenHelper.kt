package com.astrohark.app.utils

import android.util.Log
import com.astrohark.app.data.api.ApiService
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FcmTokenHelper {
    private const val TAG = "FcmTokenHelper"

    /**
     * Fetches the current FCM registration token and registers it with the backend server.
     * This should be called immediately on app launch (if user is logged in) and
     * upon successful user login/registration.
     */
    fun registerFcmToken(userId: String) {
        if (userId.isBlank()) {
            Log.w(TAG, "registerFcmToken called with blank userId, skipping")
            return
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            if (!token.isNullOrBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Log.d(TAG, "Registering FCM token for userId: $userId")
                        val result = ApiService.register(Constants.SERVER_URL, userId, token)
                        if (result.success) {
                            Log.d(TAG, "FCM token registered successfully on backend for $userId")
                        } else {
                            Log.e(TAG, "Backend failed to register FCM token for $userId: ${result.error}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Network or server error while registering FCM token for $userId", e)
                    }
                }
            } else {
                Log.w(TAG, "Fetched FCM token is null or blank")
            }
        }
    }
}
