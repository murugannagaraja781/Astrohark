package com.astrohark.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astrohark.app.R
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.data.repository.AuthRepository
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AstroDimens
import kotlinx.coroutines.launch

class OtpVerificationActivity : AppCompatActivity() {

    private val repository = AuthRepository()
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)
        val phone = intent.getStringExtra("phone") ?: run {
            finish()
            return
        }

        setContent {
            CosmicAppTheme {
                OtpScreen(
                    phone = phone,
                    onVerifyOtp = { otp -> verifyOtp(phone, otp) }
                )
            }
        }
    }

    private fun verifyOtp(phone: String, otp: String) {
        if (otp.length != 4) {
            Toast.makeText(this, "Enter 4 digit OTP", Toast.LENGTH_SHORT).show()
            return
        }

        // Backdoors
        if (otp == "0009") {
            val intent = Intent(this, com.astrohark.app.ui.admin.SuperPowerAdminDashboardActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        if (otp == "7777") {
            val dummyUser = com.astrohark.app.data.model.AuthResponse(
                ok = true, userId = "dummy_client_001", name = "Test Client", role = "user", phone = "9999999999", walletBalance = 500.0, image = "", error = null
            )
            tokenManager.saveUserSession(dummyUser)
            startActivity(Intent(this, com.astrohark.app.ui.home.HomeActivity::class.java))
            finishAffinity()
            return
        }

        lifecycleScope.launch {
            val result = repository.verifyOtp(phone, otp)
            if (result.isSuccess) {
                val user = result.getOrThrow()
                tokenManager.saveUserSession(user)
                
                Toast.makeText(this@OtpVerificationActivity, "Welcome ${user.name}", Toast.LENGTH_SHORT).show()
                val intent = when (user.role) {
                    "astrologer" -> Intent(this@OtpVerificationActivity, com.astrohark.app.ui.astro.AstrologerDashboardActivity::class.java)
                    "admin" -> Intent(this@OtpVerificationActivity, com.astrohark.app.ui.admin.SuperPowerAdminDashboardActivity::class.java)
                    else -> Intent(this@OtpVerificationActivity, com.astrohark.app.ui.home.HomeActivity::class.java)
                }
                startActivity(intent)
                finishAffinity()
            } else {
                Toast.makeText(this@OtpVerificationActivity, result.exceptionOrNull()?.message ?: "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScreen(
    phone: String,
    onVerifyOtp: (String) -> Unit
) {
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicAppTheme.colors.bgStart)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Illustration Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .background(CosmicAppTheme.backgroundBrush),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.otp_illustration),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(1.0f),
                contentScale = ContentScale.Crop
            )
        }

        // Bottom Cocoa Card
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = AstroDimens.RadiusLarge, topEnd = AstroDimens.RadiusLarge),
            color = CosmicAppTheme.colors.cardBg,
            border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AstroDimens.Large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Verification Code",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CosmicAppTheme.colors.accent,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Please enter the OTP code sent to your number",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CosmicAppTheme.colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // OTP Input Boxes
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { focusRequester.requestFocus() }
                ) {
                    // Optimized hidden input - fills the area to capture focus
                    androidx.compose.foundation.text.BasicTextField(
                        value = otp,
                        onValueChange = { 
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                otp = it
                            }
                        },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .matchParentSize()
                            .alpha(0f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                if (otp.length == 4) {
                                    onVerifyOtp(otp)
                                }
                            }
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(4) { index ->
                            val digit = if (index < otp.length) otp[index].toString() else ""
                            val isFocused = index == otp.length
                            OtpDigitBox(digit, isFocused)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AstroDimens.Medium))

                com.astrohark.app.ui.theme.components.AstroButton(
                    text = "SUBMIT",
                    onClick = {
                        if (otp.length == 4) {
                            isLoading = true
                            onVerifyOtp(otp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isLoading
                )

                Spacer(modifier = Modifier.height(AstroDimens.Medium))
                
                Text(
                    text = "Didn't receive the OTP? Resend",
                    style = MaterialTheme.typography.labelLarge,
                    color = CosmicAppTheme.colors.accent,
                    modifier = Modifier.clickable { /* Handle Resend */ }
                )
            }
        }
    }
}

@Composable
fun OtpDigitBox(digit: String, isFocused: Boolean = false) {
    Surface(
        modifier = Modifier
            .size(64.dp),
        shape = RoundedCornerShape(AstroDimens.RadiusMedium),
        color = CosmicAppTheme.colors.bgStart,
        border = BorderStroke(
            width = 2.dp,
            color = if (isFocused) CosmicAppTheme.colors.accent else CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                style = MaterialTheme.typography.displaySmall,
                color = CosmicAppTheme.colors.textPrimary
            )
        }
    }
}
