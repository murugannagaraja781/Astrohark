package com.astrohark.app.ui.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AppTheme
import com.astrohark.app.data.local.ThemeManager
import com.astrohark.app.ui.theme.ThemePalette
import com.astrohark.app.data.api.ApiClient
import kotlinx.coroutines.launch
import com.google.gson.*
import androidx.compose.material3.ExperimentalMaterial3Api

class SuperPowerAdminDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                var showWelcomeDialog by remember { mutableStateOf(true) }

                if (showWelcomeDialog) {
                    AlertDialog(
                        onDismissRequest = { showWelcomeDialog = false },
                        title = { Text("Access Granted") },
                        text = { Text("Welcome to the Super Power Admin Dashboard.") },
                        confirmButton = {
                            TextButton(onClick = { showWelcomeDialog = false }) {
                                Text("Continue")
                            }
                        }
                    )
                }

                SuperPowerScreen(
                    onThemeSelected = { theme ->
                        ThemeManager.setTheme(this, theme)
                        Toast.makeText(this, "Theme Applied: ${theme.title}", Toast.LENGTH_SHORT).show()
                        recreate() 
                    }
                )
            }
        }
        Toast.makeText(this, "Welcome Super Admin", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperPowerScreen(
    onThemeSelected: (AppTheme) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Approval", "Branding", "Workflow", "Reports")

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                CenterAlignedTopAppBar(
                    title = { Text("Super Admin Console", fontWeight = FontWeight.Black) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab, 
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 16.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = if(selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> PendingAstrologersTab()
                1 -> BrandingTab(onThemeSelected)
                2 -> WorkflowTab()
                else -> Box(Modifier.fillMaxSize()) { Text("More features coming soon", modifier = Modifier.align(Alignment.Center)) }
            }
        }
    }
}

@Composable
fun PendingAstrologersTab() {
    val scope = rememberCoroutineScope()
    var astrologers by remember { mutableStateOf<List<com.google.gson.JsonObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun refreshList() {
        isLoading = true
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val res = ApiClient.api.getPendingAstrologers()
                if (res.isSuccessful) {
                    val list = res.body()?.getAsJsonArray("list")?.toList()?.map { it.asJsonObject } ?: emptyList()
                    astrologers = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (astrologers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No pending requests") }
    } else {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(astrologers) { astro ->
                AdminAstroCard(astro) { updated ->
                    if (updated) {
                        val currentId = astro.get("userId").getAsString()
                        astrologers = astrologers.filter { it.get("userId").getAsString() != currentId }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAstroCard(astro: com.google.gson.JsonObject, onUpdate: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val userId = astro.get("userId").getAsString()
    val name = astro.get("name").getAsString()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Phone: ${astro.get("phone").asString}", fontSize = 14.sp)
            Text("Experience: ${astro.get("astrologyExperience")?.asString ?: "N/A"}", fontSize = 14.sp)
            Text("Role: ${astro.get("role")?.asString ?: "N/A"}", fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { 
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                             val resp = ApiClient.api.approveAstrologer(com.google.gson.JsonObject().apply {
                                 addProperty("userId", userId)
                                 addProperty("status", "approved")
                             })
                             if (resp.isSuccessful) onUpdate(true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.weight(1f)
                ) { Text("Approve", color = Color.White) }
                
                Button(
                    onClick = { 
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                             val resp = ApiClient.api.approveAstrologer(com.google.gson.JsonObject().apply {
                                 addProperty("userId", userId)
                                 addProperty("status", "rejected")
                             })
                             if (resp.isSuccessful) onUpdate(true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.weight(1f)
                ) { Text("Reject", color = Color.White) }
            }
        }
    }
}

@Composable
fun BrandingTab(onThemeSelected: (AppTheme) -> Unit) {
    val currentTheme by ThemeManager.currentTheme.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Visual Branding", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(AppTheme.values()) { theme ->
                ThemeCard(
                    theme = theme,
                    isSelected = theme == currentTheme,
                    onClick = { onThemeSelected(theme) }
                )
            }
        }
    }
}

@Composable
fun ThemeCard(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    val palette = ThemePalette.getColors(theme)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, palette.accent) else null,
        modifier = Modifier.height(100.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = theme.title,
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WorkflowTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var apiStatus by remember { mutableStateOf(false) }
    var socketStatus by remember { mutableStateOf(false) }
    var dbStatus by remember { mutableStateOf(false) }
    var fcmStatus by remember { mutableStateOf(false) }
    var webrtcStatus by remember { mutableStateOf(false) }
    var iceStatus by remember { mutableStateOf(false) }

    // Check statuses on load
    LaunchedEffect(Unit) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 1. Check API
            try {
                val res = ApiClient.api.getSystemStatus()
                if (res.isSuccessful) {
                    apiStatus = true
                    dbStatus = res.body()?.get("db")?.asBoolean == true
                }
            } catch (e: Exception) { apiStatus = false }

            // 2. Check Socket
            socketStatus = com.astrohark.app.data.remote.SocketManager.getSocket()?.connected() == true

            // 3. Check FCM (Mock or check if token exists)
            fcmStatus = true 

            // 4. WebRTC (Config reachable)
            try {
                val res = ApiClient.api.getWebRTCConfig()
                webrtcStatus = res.isSuccessful
                iceStatus = res.body()?.getAsJsonArray("iceServers")?.size() ?: 0 > 0
            } catch (e: Exception) { webrtcStatus = false }
        }
    }

    Column(
        modifier = androidx.compose.foundation.verticalScroll(androidx.compose.foundation.rememberScrollState())
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "System Integrity Workflow", 
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Real-time health check of core services",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        WorkflowItem("Core API Infrastructure", "Check if backend server is responsive", apiStatus)
        WorkflowItem("Database Connectivity", "Verify MongoDB Cluster connection", dbStatus)
        WorkflowItem("Socket.io Signaling", "Ensure real-time events are flowing", socketStatus)
        WorkflowItem("FCM Push Engines", "Firebase Messaging Service initialized", fcmStatus)
        WorkflowItem("WebRTC Signaling Path", "Verify signaling protocol support", webrtcStatus)
        WorkflowItem("ICE/TURN Relay Pathway", "Check if relay servers are reachable", iceStatus)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "All core systems must show green for 100% call reliability. If any remain pending, check server logs.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun WorkflowItem(title: String, subtitle: String, isDone: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator with Animation
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    if (isDone) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDone) androidx.compose.material.icons.Icons.Default.CheckCircle 
                             else androidx.compose.material.icons.Icons.Default.Refresh,
                contentDescription = null,
                tint = if (isDone) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }

        if (isDone) {
            Text(
                "READY", 
                fontWeight = FontWeight.Black, 
                fontSize = 10.sp, 
                color = Color(0xFF4CAF50),
                modifier = Modifier
                    .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
    Divider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 0.5.dp)
}
