package com.astrohark.app.ui.astro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.data.remote.SocketManager
import com.astrohark.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class AstrologerHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenManager = TokenManager(this)
        val session = tokenManager.getUserSession()
        val userId = session?.userId ?: ""
        val initialFilter = intent.getStringExtra("filter_type") ?: "all"

        setContent {
            CosmicAppTheme {
                HistoryScreen(userId = userId, initialFilter = initialFilter, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(userId: String, initialFilter: String = "all", onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEarningsMode = initialFilter == "earnings"

    var sessions by remember { mutableStateOf<List<SessionHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFilterTab by remember { mutableIntStateOf(if (initialFilter == "call") 1 else 0) }
    var selectedCallItem by remember { mutableStateOf<SessionHistoryItem?>(null) }

    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawAmount by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        withContext(Dispatchers.IO) {
            try {
                val myRole = TokenManager(context).getUserSession()?.role ?: "client"
                val client = okhttp3.OkHttpClient()

                val request = okhttp3.Request.Builder()
                    .url("https://astrohark.com/api/astrology/history/$userId")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    if (json.optBoolean("ok")) {
                        val array = json.optJSONArray("sessions") ?: JSONArray()
                        val list = mutableListOf<SessionHistoryItem>()

                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val isAstro = myRole == "astrologer"
                            list.add(
                                SessionHistoryItem(
                                    id = obj.optString("sessionId"),
                                    partnerName = if (isAstro) obj.optString("clientName", "Unknown") else obj.optString("astrologerName", "Unknown"),
                                    type = obj.optString("type", "call"),
                                    startTime = if (obj.has("actualBillingStart") && obj.optLong("actualBillingStart") > 0) obj.optLong("actualBillingStart") else obj.optLong("startTime", 0),
                                    endTime = if (obj.has("sessionEndAt") && obj.optLong("sessionEndAt") > 0) obj.optLong("sessionEndAt") else obj.optLong("endTime", 0),
                                    duration = obj.optInt("duration", 0),
                                    amount = if (isAstro) obj.optDouble("totalEarned", 0.0) else obj.optDouble("totalCharged", 0.0),
                                    isEarned = isAstro,
                                    clientId = obj.optString("clientId").takeIf { it.isNotEmpty() } ?: obj.optString("fromUserId"),
                                    astrologerId = obj.optString("astrologerId").takeIf { it.isNotEmpty() } ?: obj.optString("toUserId")
                                )
                            )
                        }
                        sessions = list
                    } else {
                        error = "Failed to load history"
                    }
                } else {
                    error = "Server error: ${response.code}"
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    val totalEarned = remember(sessions) { sessions.sumOf { it.amount } }
    val callEarned = remember(sessions) { sessions.filter { it.type != "chat" }.sumOf { it.amount } }
    val chatEarned = remember(sessions) { sessions.filter { it.type == "chat" }.sumOf { it.amount } }
    val totalPaidSessions = remember(sessions) { sessions.count { it.amount > 0 } }

    val displaySessions = remember(sessions, selectedFilterTab, isEarningsMode) {
        val base = if (isEarningsMode) sessions.filter { it.amount > 0 } else sessions
        when (selectedFilterTab) {
            1 -> base.filter { it.type == "call" || it.type == "audio" || it.type == "video" }
            2 -> base.filter { it.type == "chat" }
            else -> base
        }
    }

    if (selectedCallItem != null) {
        CallDetailDialog(item = selectedCallItem!!, onDismiss = { selectedCallItem = null })
    }

    if (showWithdrawDialog) {
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = false },
            title = { Text("Request Withdrawal", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Total Earned: ₹${String.format("%.2f", totalEarned)}", color = Color(0xFFEAB308), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = withdrawAmount,
                        onValueChange = { if (it.all { char -> char.isDigit() }) withdrawAmount = it },
                        label = { Text("Enter Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Min. ₹500 required", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = withdrawAmount.toDoubleOrNull() ?: 0.0
                        if (amt < 500) {
                            Toast.makeText(context, "Minimum withdrawal is ₹500", Toast.LENGTH_SHORT).show()
                        } else {
                            SocketManager.requestWithdrawal(amt) { res ->
                                scope.launch(Dispatchers.Main) {
                                    if (res?.optBoolean("ok") == true) {
                                        Toast.makeText(context, "Withdrawal Requested Successfully", Toast.LENGTH_LONG).show()
                                        showWithdrawDialog = false
                                        withdrawAmount = ""
                                    } else {
                                        val err = res?.optString("error", "Error requesting withdrawal") ?: "Error"
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicAppTheme.colors.accent)
                ) {
                    Text("Request", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    val titleText = if (isEarningsMode) {
        if (selectedFilterTab == 1) "Call Earnings" else if (selectedFilterTab == 2) "Chat Earnings" else "Earnings History"
    } else {
        if (selectedFilterTab == 1) "Call History" else if (selectedFilterTab == 2) "Chat History" else "Consultation History"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CosmicAppTheme.colors.headerStart
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(CosmicAppTheme.backgroundBrush)
        ) {
            // Filter Tabs
            TabRow(
                selectedTabIndex = selectedFilterTab,
                containerColor = CosmicAppTheme.colors.headerStart,
                contentColor = CosmicAppTheme.colors.accent
            ) {
                val tabs = if (isEarningsMode) {
                    listOf("All Earnings", "Calls", "Chats")
                } else {
                    listOf("All History", "Calls", "Chats")
                }
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedFilterTab == index,
                        onClick = { selectedFilterTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedFilterTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedFilterTab == index) CosmicAppTheme.colors.accent else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = CosmicAppTheme.colors.accent)
                } else if (error != null) {
                    Text(text = error!!, color = Color.Red, modifier = Modifier.align(Alignment.Center))
                } else if (displaySessions.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isEarningsMode) {
                            EarningsSummaryCard(
                                totalEarned = totalEarned,
                                callEarned = callEarned,
                                chatEarned = chatEarned,
                                totalSessions = totalPaidSessions,
                                onWithdrawClick = { showWithdrawDialog = true }
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isEarningsMode) "No earnings recorded yet" else if (selectedFilterTab == 1) "No call history found" else if (selectedFilterTab == 2) "No chat history found" else "No history found",
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isEarningsMode) {
                            item {
                                EarningsSummaryCard(
                                    totalEarned = totalEarned,
                                    callEarned = callEarned,
                                    chatEarned = chatEarned,
                                    totalSessions = totalPaidSessions,
                                    onWithdrawClick = { showWithdrawDialog = true }
                                )
                            }
                        }
                        items(displaySessions) { session ->
                            HistoryCard(session, onCallClick = { selectedCallItem = it }, isEarningsMode = isEarningsMode)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EarningsSummaryCard(
    totalEarned: Double,
    callEarned: Double,
    chatEarned: Double,
    totalSessions: Int,
    onWithdrawClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = Color(0xFFFF7A00).copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFFDE047), Color(0xFFEAB308), Color(0xFFB45309))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Total Lifetime Earnings",
                            color = Color.Black.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "₹${String.format("%.2f", totalEarned)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "$totalSessions Sessions",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.Black.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column {
                            Text("Call Earnings", fontSize = 11.sp, color = Color.Black.copy(alpha = 0.65f), fontWeight = FontWeight.SemiBold)
                            Text("₹${String.format("%.2f", callEarned)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        }
                        Column {
                            Text("Chat Earnings", fontSize = 11.sp, color = Color.Black.copy(alpha = 0.65f), fontWeight = FontWeight.SemiBold)
                            Text("₹${String.format("%.2f", chatEarned)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        }
                    }
                    Button(
                        onClick = onWithdrawClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Withdraw", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CallDetailDialog(item: SessionHistoryItem, onDismiss: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val startTimeStr = if (item.startTime > 0) dateFormat.format(Date(item.startTime)) else "N/A"
    val totalSec = item.duration / 1000
    val mins = totalSec / 60
    val secs = totalSec % 60
    val duraText = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (item.type == "chat") Icons.Default.Chat else Icons.Default.Call,
                    contentDescription = null,
                    tint = CosmicAppTheme.colors.accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (item.isEarned) "Earnings Details" else if (item.type == "chat") "Chat Details" else "Call Details",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Client / User:", color = Color.Gray, fontSize = 13.sp)
                    Text(item.partnerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Type:", color = Color.Gray, fontSize = 13.sp)
                    Text(
                        if (item.type == "video") "Video Call" else if (item.type == "chat") "Chat Session" else "Voice Call",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Date & Time:", color = Color.Gray, fontSize = 13.sp)
                    Text(startTimeStr, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Duration:", color = Color.Gray, fontSize = 13.sp)
                    Text(duraText, fontWeight = FontWeight.Bold, color = Color(0xFF039BE5), fontSize = 14.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (item.isEarned) "Amount Earned:" else "Amount Charged:", color = Color.Gray, fontSize = 13.sp)
                    Text(
                        "${if (item.isEarned) "+ " else ""}₹${String.format("%.2f", item.amount)}",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (item.isEarned) Color(0xFF22C55E) else Color(0xFF1E3A8A),
                        fontSize = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CosmicAppTheme.colors.accent)
            ) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun HistoryCard(
    item: SessionHistoryItem,
    onCallClick: (SessionHistoryItem) -> Unit = {},
    isEarningsMode: Boolean = false
) {
    val colors = CosmicAppTheme.colors
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val startTimeStr = if (item.startTime > 0) dateFormat.format(Date(item.startTime)) else "N/A"
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (item.type == "chat") {
                    val intent = Intent(context, com.astrohark.app.ui.chat.ChatActivity::class.java).apply {
                        putExtra("sessionId", item.id)
                        putExtra("toUserId", if (item.isEarned) item.clientId else item.astrologerId)
                        putExtra("toUserName", item.partnerName)
                        putExtra("isHistoryMode", true)
                    }
                    context.startActivity(intent)
                } else {
                    onCallClick(item)
                }
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (item.type == "chat") Icons.Default.Chat else Icons.Default.Call,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.partnerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = if (item.type == "video") "Video Consultation" else if (item.type == "chat") "Chat Consultation" else "Voice Call Consultation",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (item.isEarned) "+ " else ""}₹${String.format("%.2f", item.amount)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = if (item.isEarned) Color(0xFF22C55E) else Color(0xFF1E3A8A)
                    )
                    if (item.isEarned) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF22C55E).copy(alpha = 0.15f),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "Earned",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF22C55E),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.cardStroke.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Date & Time", fontSize = 11.sp, color = colors.textSecondary)
                    Text(startTimeStr, fontSize = 13.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Duration", fontSize = 11.sp, color = colors.textSecondary)
                    val totalSec = item.duration / 1000
                    val mins = totalSec / 60
                    val secs = totalSec % 60
                    val duraText = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                    Text(duraText, fontSize = 13.sp, color = Color(0xFF039BE5), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

data class SessionHistoryItem(
    val id: String,
    val partnerName: String,
    val type: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Int,
    val amount: Double,
    val isEarned: Boolean,
    val clientId: String?,
    val astrologerId: String?
)
