package com.astrohark.app.ui.wallet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astrohark.app.MainActivity
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AstroDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaymentStatusActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

        // Handle Deep Link
        val data = intent.data
        val status = data?.getQueryParameter("status")
        val txnId = data?.getQueryParameter("txnId")

        val isSuccess = status == "success"

        if (isSuccess) {
            refreshWalletBalance()
        }

        setContent {
            CosmicAppTheme {
                PaymentStatusScreen(
                    isSuccess = isSuccess,
                    txnId = txnId,
                    onGoHome = {
                        // Navigate to Wallet page after payment success
                        val intent = Intent(this, WalletActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = com.astrohark.app.data.api.ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    tokenManager.saveUserSession(user)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun PaymentStatusScreen(
    isSuccess: Boolean,
    txnId: String?,
    onGoHome: () -> Unit
) {
    val successColor = Color(0xFF4CAF50)
    val failureColor = Color(0xFFD32F2F)
    val iconColor = if (isSuccess) successColor else failureColor
    val icon: ImageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error
    val title = if (isSuccess) "Payment Successful!" else "Payment Failed"
    val message = if (isSuccess) "Your wallet has been recharged.\nTxn ID: ${txnId ?: "N/A"}" else "Transaction could not be completed."

    Surface(
        color = CosmicAppTheme.colors.bgStart,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AstroDimens.Large),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = CosmicAppTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CosmicAppTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            com.astrohark.app.ui.theme.components.AstroButton(
                text = "Return to Wallet",
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
