package com.astrohark.app.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astrohark.app.R
import com.astrohark.app.data.api.ApiClient
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AstroDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SuperWalletActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager
    private var offerPercentage by mutableDoubleStateOf(0.0)
    private var bannerTitle by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

        offerPercentage = intent.getDoubleExtra("offerPercentage", 0.0)
        bannerTitle = intent.getStringExtra("bannerTitle") ?: "Super Wallet Offer"

        setContent {
            CosmicAppTheme {
                SuperWalletScreen(
                    title = bannerTitle,
                    offerPercent = offerPercentage,
                    onBack = { finish() },
                    onPay = { amount ->
                        initiatePayment(amount)
                    }
                )
            }
        }
    }

    private fun initiatePayment(amount: Int) {
        val user = tokenManager.getUserSession()
        if (user == null || user.userId == null) {
            Toast.makeText(this, "Please login to recharge", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, com.astrohark.app.ui.payment.PaymentActivity::class.java)
        intent.putExtra("amount", amount.toDouble())
        intent.putExtra("isSuperWallet", true)
        intent.putExtra("offerPercentage", offerPercentage)
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperWalletScreen(
    title: String,
    offerPercent: Double,
    onBack: () -> Unit,
    onPay: (Int) -> Unit
) {
    val pinkDeep = Color(0xFFDB2777)
    val pinkLight = Color(0xFFFDF2F8)
    val pinkGradient = Brush.verticalGradient(listOf(Color(0xFFDB2777), Color(0xFFF472B6)))

    val rechargeOptions = listOf(100, 500, 1000, 2000)
    var selectedAmount by remember { mutableIntStateOf(100) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Super Wallet", color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = CosmicAppTheme.colors.accent)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CosmicAppTheme.colors.bgStart)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmicAppTheme.backgroundBrush)
                .padding(padding)
                .padding(AstroDimens.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Offer Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AstroDimens.RadiusLarge),
                color = CosmicAppTheme.colors.cardBg,
                border = BorderStroke(1.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(AstroDimens.Large),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = CosmicAppTheme.colors.accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text(
                            text = "${offerPercent.toInt()}% Bonus Value",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = CosmicAppTheme.colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = title,
                        color = CosmicAppTheme.colors.textPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Exclusive Promotional Offer",
                        color = CosmicAppTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Select Recharge Amount", color = CosmicAppTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Column(Modifier.selectableGroup()) {
                val rechargeOptions = listOf(100, 500, 1000, 2000)
                rechargeOptions.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { amount ->
                            val selected = selectedAmount == amount
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(70.dp)
                                    .selectable(
                                        selected = selected,
                                        onClick = { selectedAmount = amount },
                                        role = Role.RadioButton
                                    ),
                                shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                                border = if (selected) BorderStroke(2.dp, CosmicAppTheme.colors.accent) else BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f)),
                                color = if (selected) CosmicAppTheme.colors.accent else CosmicAppTheme.colors.cardBg
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "₹$amount",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) CosmicAppTheme.colors.bgStart else CosmicAppTheme.colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                color = CosmicAppTheme.colors.cardBg,
                border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f))
            ) {
                Column(Modifier.padding(AstroDimens.Large)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Base Recharge", color = CosmicAppTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text("₹$selectedAmount", color = CosmicAppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Promotion Bonus", color = CosmicAppTheme.colors.accent, style = MaterialTheme.typography.bodyMedium)
                        val bonus = (selectedAmount * offerPercent / 100).toInt()
                        Text("+₹$bonus", color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(Modifier.padding(vertical = AstroDimens.Medium), color = CosmicAppTheme.colors.cardStroke.copy(alpha = 0.1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total Credit", color = CosmicAppTheme.colors.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val total = selectedAmount + (selectedAmount * offerPercent / 100).toInt()
                        Text("₹$total", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = CosmicAppTheme.colors.accent)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            com.astrohark.app.ui.theme.components.AstroButton(
                text = "PAY ₹$selectedAmount & GET ₹${selectedAmount + (selectedAmount * offerPercent / 100).toInt()}",
                onClick = { onPay(selectedAmount) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(16.dp), tint = CosmicAppTheme.colors.textSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Secure SSL Encrypted Payment", style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.textSecondary)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
