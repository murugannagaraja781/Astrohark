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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrohark.app.R
import com.astrohark.app.data.repository.AuthRepository
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AstroDimens
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                LoginScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val repository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

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
                painter = painterResource(id = R.drawable.login_illustration),
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
                    text = "Welcome to Astrohark!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CosmicAppTheme.colors.accent,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Securely verify with your mobile number",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CosmicAppTheme.colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                // Log in or Sign up Divider
                Row(
                    modifier = Modifier.padding(vertical = AstroDimens.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f))
                    Text(
                        text = "Log in or Sign up",
                        style = MaterialTheme.typography.labelSmall,
                        color = CosmicAppTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f))
                }

                // Phone Input with Country Code
                Surface(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                    color = CosmicAppTheme.colors.bgStart,
                    border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = AstroDimens.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🇮🇳", fontSize = 24.sp)
                        Text("+91", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp), color = CosmicAppTheme.colors.textPrimary)
                        Icon(Icons.Default.ArrowDropDown, null, tint = CosmicAppTheme.colors.textPrimary)
                        
                        Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.6f).background(CosmicAppTheme.colors.cardStroke).padding(horizontal = 8.dp))
                        
                        TextField(
                            value = phoneNumber,
                            onValueChange = { if (it.length <= 10) phoneNumber = it.filter { char -> char.isDigit() } },
                            placeholder = { Text("Enter Mobile number", color = CosmicAppTheme.colors.textSecondary.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = CosmicAppTheme.colors.accent,
                                focusedTextColor = CosmicAppTheme.colors.textPrimary,
                                unfocusedTextColor = CosmicAppTheme.colors.textPrimary
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AstroDimens.Medium))

                // Send OTP Button
                com.astrohark.app.ui.theme.components.AstroButton(
                    text = "SEND OTP",
                    onClick = {
                        if (phoneNumber.length < 10) {
                            Toast.makeText(context, "Enter 10 digit number", Toast.LENGTH_SHORT).show()
                            return@AstroButton
                        }
                        isLoading = true
                        scope.launch {
                            try {
                                val fullPhone = "91${phoneNumber.trim()}"
                                val result = repository.sendOtp(fullPhone)
                                if (result.isSuccess) {
                                    val intent = Intent(context, OtpVerificationActivity::class.java)
                                    intent.putExtra("phone", fullPhone)
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isLoading
                )

                // Or Divider
                Row(
                    modifier = Modifier.padding(vertical = AstroDimens.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f))
                    Text(
                        text = "Or",
                        style = MaterialTheme.typography.labelSmall,
                        color = CosmicAppTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f))
                }

                // Email Button
                OutlinedButton(
                    onClick = { /* Handle Email Login */ },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                    border = BorderStroke(1.dp, CosmicAppTheme.colors.accent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicAppTheme.colors.accent)
                ) {
                    Icon(Icons.Default.Email, null, tint = CosmicAppTheme.colors.accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue with Email ID", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = AstroDimens.Large, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("By signing up, you agree to our ", style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.textSecondary)
                    Text("Terms of Use", style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.accent, modifier = Modifier.clickable { })
                    Text(" and ", style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.textSecondary)
                    Text("Privacy Policy", style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.accent, modifier = Modifier.clickable { })
                }
            }
        }
    }
}
