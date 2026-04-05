package com.astrohark.app.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.astrohark.app.R
import com.astrohark.app.data.api.ApiClient
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AstroDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.ArrayList

class WalletActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager
    private val transactionsState = mutableStateListOf<JSONObject>()
    private var balanceState by mutableDoubleStateOf(0.0)
    private var superBalanceState by mutableDoubleStateOf(0.0)
    private var bannerTitle by mutableStateOf<String?>(null)
    private var bannerSubtitle by mutableStateOf<String?>(null)
    private var ctaText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

        updateBalanceFromSession()

        bannerTitle = intent.getStringExtra("bannerTitle")
        bannerSubtitle = intent.getStringExtra("bannerSubtitle")
        ctaText = intent.getStringExtra("ctaText")

        setContent {
            CosmicAppTheme {
                WalletScreen(
                    balance = balanceState,
                    superBalance = superBalanceState,
                    transactions = transactionsState,
                    bannerTitle = bannerTitle,
                    bannerSubtitle = bannerSubtitle,
                    ctaText = ctaText,
                    onAddMoney = { amount, promo ->
                        if (amount < 1) {
                            Toast.makeText(this, getString(R.string.enter_valid_amount), Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(this, com.astrohark.app.ui.payment.PaymentActivity::class.java)
                            intent.putExtra("amount", amount.toDouble())
                            if (promo != null) {
                                intent.putExtra("promoCode", promo)
                            }
                            startActivity(intent)
                        }
                    },
                    onRefreshHistory = { loadPaymentHistory() }
                )
            }
        }

        loadPaymentHistory()
    }

    override fun onResume() {
        super.onResume()
        refreshWalletBalance()
        loadPaymentHistory()

        com.astrohark.app.data.remote.SocketManager.onWalletUpdate { data ->
            runOnUiThread {
                val newBalance = data.optDouble("balance", 0.0)
                val newSuperBalance = data.optDouble("superBalance", 0.0)
                tokenManager.updateWalletBalance(newBalance)
                tokenManager.updateSuperWalletBalance(newSuperBalance)
                balanceState = newBalance
                superBalanceState = newSuperBalance
            }
        }
    }

    override fun onPause() {
        super.onPause()
        com.astrohark.app.data.remote.SocketManager.off("wallet-update")
    }

    private fun updateBalanceFromSession() {
        val user = tokenManager.getUserSession()
        balanceState = user?.walletBalance ?: 0.0
        superBalanceState = user?.superWalletBalance ?: 0.0
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    runOnUiThread {
                        tokenManager.saveUserSession(user)
                        balanceState = user.walletBalance ?: 0.0
                        superBalanceState = user.superWalletBalance ?: 0.0
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadPaymentHistory() {
        val userId = tokenManager.getUserSession()?.userId ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://astrohark.com/api/payment/history/$userId")
                    .get()
                    .build()

                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val json = JSONObject(body ?: "{}")
                        val data = json.optJSONArray("data")

                        val newTransactions = ArrayList<JSONObject>()
                        if (data != null) {
                            for (i in 0 until data.length()) {
                                newTransactions.add(data.getJSONObject(i))
                            }
                        }

                        runOnUiThread {
                            transactionsState.clear()
                            transactionsState.addAll(newTransactions)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    balance: Double,
    superBalance: Double = 0.0,
    transactions: List<JSONObject>,
    bannerTitle: String? = null,
    bannerSubtitle: String? = null,
    ctaText: String? = null,
    onAddMoney: (Int, String?) -> Unit,
    onRefreshHistory: () -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var couponInput by remember { mutableStateOf("") }
    var appliedCoupon by remember { mutableStateOf<String?>(null) }
    var couponBonus by remember { mutableStateOf(0.0) }
    var couponMessage by remember { mutableStateOf<String?>(null) }
    var isCouponLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val colors = CosmicAppTheme.colors
    val goldPrimary = colors.accent
    val successGreen = Color(0xFF22C55E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicAppTheme.backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.wallet_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = CosmicAppTheme.colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { (context as ComponentActivity).finish() }) {
                            Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, "Back", tint = CosmicAppTheme.colors.accent)
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefreshHistory) {
                            Icon(Icons.Rounded.History, "Refresh", tint = CosmicAppTheme.colors.accent)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF0F0B1E))
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Promotional Banner
                if (!bannerTitle.isNullOrEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                            color = CosmicAppTheme.colors.cardBg,
                            border = BorderStroke(1.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(AstroDimens.Medium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = CosmicAppTheme.colors.accent.copy(alpha = 0.15f)
                                ) {
                                    Icon(Icons.Rounded.AddCircle, null, tint = CosmicAppTheme.colors.accent, modifier = Modifier.padding(8.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bannerTitle!!, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.textPrimary)
                                    if (!bannerSubtitle.isNullOrEmpty()) {
                                        Text(bannerSubtitle!!, style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.textSecondary)
                                    }
                                }
                                Button(
                                    onClick = {
                                        if (appliedCoupon == "WELCOME50") {
                                            appliedCoupon = null
                                            couponInput = ""
                                            couponBonus = 0.0
                                            couponMessage = null
                                        } else {
                                            if (amountInput.isEmpty()) amountInput = "500"
                                            val amt = amountInput.toDoubleOrNull() ?: 500.0
                                            appliedCoupon = "WELCOME50"
                                            couponInput = "WELCOME50"
                                            couponBonus = amt * 0.5
                                            couponMessage = "✅ Applied: ₹${couponBonus.toInt()} Bonus"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (appliedCoupon == "WELCOME50") successGreen else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                                    modifier = Modifier.height(36.dp),
                                    border = BorderStroke(1.dp, if (appliedCoupon == "WELCOME50") successGreen else CosmicAppTheme.colors.accent)
                                ) {
                                    Text(
                                        text = if (appliedCoupon == "WELCOME50") stringResource(R.string.applied) else stringResource(R.string.apply),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (appliedCoupon == "WELCOME50") Color.White else CosmicAppTheme.colors.accent
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Balance Card (Premium Black/Gold Aesthetic)
                item {
                    val cardGradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E1E2C),
                            Color(0xFF2D2D44)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = goldPrimary.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(24.dp))
                            .background(cardGradient)
                            .border(1.dp, goldPrimary.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    ) {
                        // Decorative Elements
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                            // Chip Icon Placeholder
                            Box(
                                modifier = Modifier
                                    .size(40.dp, 30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(goldPrimary.copy(alpha = 0.6f))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .align(Alignment.TopStart)
                            )
                            
                            Icon(
                                Icons.Rounded.AccountBalanceWallet, 
                                null, 
                                tint = goldPrimary.copy(alpha = 0.1f), 
                                modifier = Modifier.size(120.dp).align(Alignment.BottomEnd).offset(x = 20.dp, y = 20.dp)
                            )

                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Spacer(modifier = Modifier.width(44.dp))
                                    Text(
                                        "ASTROHARK PLATINUM", 
                                        style = MaterialTheme.typography.labelMedium,
                                        color = goldPrimary.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                }
                                
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    Text(
                                        stringResource(R.string.total_balance).uppercase(), 
                                        color = Color.White.copy(0.6f), 
                                        fontSize = 11.sp, 
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        "₹ ${"%,.2f".format(balance)}", 
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontWeight = FontWeight.Black, 
                                            fontSize = 38.sp,
                                            letterSpacing = 1.sp
                                        ), 
                                        color = Color.White
                                    )
                                    if (superBalance > 0.0) {
                                        Text(
                                            "SUPER WALLET: ₹ ${superBalance.toInt()}", 
                                            color = goldPrimary, 
                                            fontSize = 12.sp, 
                                            fontWeight = FontWeight.ExtraBold, 
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
                                    Text(
                                        stringResource(R.string.valid_user).uppercase(), 
                                        color = Color.White.copy(0.4f), 
                                        fontSize = 10.sp, 
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).graphicsLayer(alpha = 0.6f),
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(goldPrimary)
                                    )
                                }
                            }
                        }
                    }
                }


                // 3. Recharge & Trust Section
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().shadow(AstroDimens.ElevationMedium, RoundedCornerShape(AstroDimens.RadiusLarge)),
                        shape = RoundedCornerShape(AstroDimens.RadiusLarge),
                        color = CosmicAppTheme.colors.cardBg,
                        border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(0.15f))
                    ) {
                        Column(modifier = Modifier.padding(AstroDimens.Medium), verticalArrangement = Arrangement.spacedBy(AstroDimens.Medium)) {
                            Text(stringResource(R.string.recharge_wallet), color = CosmicAppTheme.colors.accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)

                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                                listOf(100, 500, 1000, 2000).forEach { amount ->
                                    val isSelected = amountInput == amount.toString()
                                    Surface(
                                        onClick = { amountInput = amount.toString() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                                        color = if (isSelected) CosmicAppTheme.colors.accent else CosmicAppTheme.colors.bgStart,
                                        border = BorderStroke(1.dp, if (isSelected) CosmicAppTheme.colors.accent else CosmicAppTheme.colors.cardStroke.copy(0.2f))
                                    ) {
                                        Text(
                                            text = "₹$amount", 
                                            modifier = Modifier.padding(vertical = 10.dp), 
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold, 
                                            color = if (isSelected) CosmicAppTheme.colors.bgStart else CosmicAppTheme.colors.textPrimary, 
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                                label = { Text(stringResource(R.string.enter_amount), color = CosmicAppTheme.colors.textSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CosmicAppTheme.colors.accent, 
                                    unfocusedBorderColor = CosmicAppTheme.colors.cardStroke.copy(0.3f), 
                                    focusedTextColor = CosmicAppTheme.colors.textPrimary, 
                                    unfocusedTextColor = CosmicAppTheme.colors.textPrimary,
                                    focusedContainerColor = CosmicAppTheme.colors.bgStart,
                                    unfocusedContainerColor = CosmicAppTheme.colors.bgStart
                                ),
                                prefix = { Text("₹ ", color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold) },
                                singleLine = true
                            )

                            // Trust Badges
                            Column(modifier = Modifier.fillMaxWidth().background(CosmicAppTheme.colors.bgStart.copy(0.5f), RoundedCornerShape(AstroDimens.RadiusSmall)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.AccountBalanceWallet, null, tint = successGreen, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.trust_secure_payment), color = CosmicAppTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.History, null, tint = CosmicAppTheme.colors.accent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.trust_rbi_verified), color = CosmicAppTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            // Coupon
                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = couponInput,
                                    onValueChange = { couponInput = it.uppercase() },
                                    placeholder = { Text("COUPON", color = CosmicAppTheme.colors.textSecondary.copy(0.5f), style = MaterialTheme.typography.bodyMedium) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CosmicAppTheme.colors.accent, 
                                        unfocusedBorderColor = CosmicAppTheme.colors.cardStroke.copy(0.3f), 
                                        focusedTextColor = CosmicAppTheme.colors.textPrimary, 
                                        unfocusedTextColor = CosmicAppTheme.colors.textPrimary,
                                        focusedContainerColor = CosmicAppTheme.colors.bgStart,
                                        unfocusedContainerColor = CosmicAppTheme.colors.bgStart
                                    ),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (couponInput.isEmpty()) return@Button
                                        val amt = amountInput.toDoubleOrNull() ?: 0.0
                                        if (amt < 1) { couponMessage = "Enter amount first"; return@Button }
                                        if (couponInput == "WELCOME50") {
                                            appliedCoupon = couponInput
                                            couponBonus = amt * 0.5
                                            couponMessage = "✅ Applied: ₹${couponBonus.toInt()} Bonus"
                                        } else {
                                            appliedCoupon = null
                                            couponBonus = 0.0
                                            couponMessage = "❌ Invalid Code"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicAppTheme.colors.bgStart),
                                    shape = RoundedCornerShape(AstroDimens.RadiusSmall), 
                                    modifier = Modifier.height(54.dp),
                                    border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f))
                                ) {
                                    Text("APPLY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.accent)
                                }
                            }
                            if (couponMessage != null) Text(couponMessage!!, color = if (appliedCoupon != null) successGreen else Color.Red, style = MaterialTheme.typography.labelSmall)

                            // Summary
                            val tc = amountInput.toIntOrNull() ?: 0
                            if (tc > 0) {
                                HorizontalDivider(color = CosmicAppTheme.colors.cardStroke.copy(0.1f))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Wallet Credit:", color = CosmicAppTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                                        Text("₹$tc", color = CosmicAppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    if (appliedCoupon != null) {
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                            Text("Bonus Credit:", color = successGreen, style = MaterialTheme.typography.bodyMedium)
                                            Text("+ ₹${couponBonus.toInt()}", color = successGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        val total = tc + (tc * 0.18).toInt()
                                        Text("Total (incl. GST):", color = CosmicAppTheme.colors.accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("₹$total", color = CosmicAppTheme.colors.accent, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                    }
                                }
                            }

                            com.astrohark.app.ui.theme.components.AstroButton(
                                text = stringResource(R.string.invest_now),
                                onClick = {
                                    val amt = amountInput.toIntOrNull() ?: 0
                                    val pay = amt + (amt * 0.18).toInt()
                                    if (pay >= 1) onAddMoney(pay, appliedCoupon)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 4. History
                item {
                    Text(stringResource(R.string.recent_transactions), color = CosmicAppTheme.colors.accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }

                items(transactions) { tx ->
                    val amt = tx.optDouble("amount", 0.0)
                    val status = tx.optString("status", "pending")
                    val date = tx.optString("createdAt", "").take(10)
                    Surface(
                        modifier = Modifier.fillMaxWidth(), 
                        shape = RoundedCornerShape(AstroDimens.RadiusMedium), 
                        color = CosmicAppTheme.colors.cardBg,
                        border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(0.1f))
                    ) {
                        Row(modifier = Modifier.padding(AstroDimens.Medium), verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = if(status=="success") successGreen.copy(0.15f) else Color.Red.copy(0.15f)) {
                                Icon(if(status=="success") Icons.Rounded.AccountBalanceWallet else Icons.Rounded.History, null, tint = if(status=="success") successGreen else Color.Red, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if(status=="success") "Recharge Success" else "Payment $status", color = CosmicAppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(date, style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.textSecondary)
                            }
                            Text("₹${amt.toInt()}", style = MaterialTheme.typography.titleMedium, color = if(status=="success") CosmicAppTheme.colors.accent else CosmicAppTheme.colors.textPrimary, fontWeight = FontWeight.Black)
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
