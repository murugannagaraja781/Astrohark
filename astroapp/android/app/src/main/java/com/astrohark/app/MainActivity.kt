package com.astrohark.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.astrohark.app.data.api.ApiService
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.ui.home.HomeActivity
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.utils.Constants
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity - Splash / Entry Dispatcher
 * Checks login status and redirects user.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var tokenManager: TokenManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Log.d(TAG, "Notification permission granted")
        proceedToNextScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

        handleIntent(intent)
        checkInstallReferrer()

        setContent {
            CosmicAppTheme {
                SplashScreen()
            }
        }

        // Upload FCM Token
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            val session = tokenManager.getUserSession()
            if (session != null && token != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ApiService.register(Constants.SERVER_URL, session.userId!!, token)
                        Log.d(TAG, "Token uploaded successfully on launch")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to upload token", e)
                    }
                }
            }
        }

        // Add a small delay for splash effect or to ensure permissions logic runs
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000)
            checkPermissionsAndProceed()
        }
    }

    private fun checkPermissionsAndProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                proceedToNextScreen()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            proceedToNextScreen()
        }
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && (data.scheme == "astrohark" || data.scheme == "astro5") && data.host == "referral") {
            val code = data.lastPathSegment
            if (!code.isNullOrBlank()) {
                Log.d(TAG, "Captured deep link referral code: $code")
                tokenManager.savePendingReferral(code)
            }
        }
    }

    private fun checkInstallReferrer() {
        // Only check if user is NOT logged in and we don't already have a pending referral
        if (tokenManager.isLoggedIn() || tokenManager.getPendingReferral() != null) return

        val referrerClient = InstallReferrerClient.newBuilder(this).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        try {
                            val response: ReferrerDetails = referrerClient.installReferrer
                            val referrerUrl = response.installReferrer
                            Log.d(TAG, "Install Referrer URL: $referrerUrl")
                            
                            // Example: utm_source=google-play&utm_medium=organic&referrer=MYCODE
                            if (referrerUrl != null && referrerUrl.contains("referrer=")) {
                                val code = referrerUrl.split("referrer=").lastOrNull()?.split("&")?.firstOrNull()
                                if (!code.isNullOrBlank()) {
                                    Log.d(TAG, "Captured install referral code: $code")
                                    tokenManager.savePendingReferral(code)
                                }
                            }
                            referrerClient.endConnection()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading referrer details", e)
                        }
                    }
                    else -> Log.d(TAG, "InstallReferrer setup finished with code: $responseCode")
                }
            }
            override fun onInstallReferrerServiceDisconnected() {
                // Try again later if needed
            }
        })
    }

    private fun proceedToNextScreen() {
        val session = tokenManager.getUserSession()
        if (session != null) {
            Log.d(TAG, "User logged in: ${session.role}")
            when (session.role) {
                "astrologer" -> {
                    startActivity(Intent(this, com.astrohark.app.ui.astro.AstrologerDashboardActivity::class.java))
                }
                "admin" -> {
                    // Placeholder for now, typically native or webview
                    startActivity(Intent(this, com.astrohark.app.ui.guest.GuestDashboardActivity::class.java))
                }
                else -> { // "user" or default
                    startActivity(Intent(this, HomeActivity::class.java))
                }
            }
        } else {
            Log.d(TAG, "User not logged in, going to Guest Dashboard")
            startActivity(Intent(this, com.astrohark.app.ui.guest.GuestDashboardActivity::class.java))
        }
        finish()
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF140F0A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "App Logo",
                modifier = Modifier.size(150.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(
                color = Color(0xFFFF7F00)
            )
        }
    }
}
