package com.astrohark.app.ui.astro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.astrohark.app.ui.theme.CosmicAppTheme

class PermissionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                PermissionScreen(onBack = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning from system settings
    }
}

@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    
    // State for permissions
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAudioPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) 
    }
    var hasCameraPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) 
    }
    var hasNotificationPermission by remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        } else {
            mutableStateOf(true)
        }
    }

    // Update states periodically or based on lifecycle
    LaunchedEffect(Unit) {
        while(true) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        containerColor = CosmicAppTheme.colors.bgStart,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicAppTheme.headerBrush)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "App Permissions",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(CosmicAppTheme.backgroundBrush)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "For the best experience as an Astrologer, please enable the following permissions. These are required to receive calls even when the app is closed.",
                color = CosmicAppTheme.colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 1. Overlay Permission
            PermissionItem(
                title = "Display Over Other Apps",
                description = "Required to show the incoming call screen when you are using other apps.",
                icon = Icons.Default.Layers,
                isGranted = hasOverlayPermission,
                onEnable = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )

            // 2. Audio Permission
            PermissionItem(
                title = "Microphone Access",
                description = "Required for audio calls and voice consultation.",
                icon = Icons.Default.Mic,
                isGranted = hasAudioPermission,
                onEnable = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )

            // 3. Camera Permission
            PermissionItem(
                title = "Camera Access",
                description = "Required for video consultation.",
                icon = Icons.Default.Videocam,
                isGranted = hasCameraPermission,
                onEnable = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )

            // 4. Notification Permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = "Notifications",
                    description = "Required to alert you about new chat and call requests.",
                    icon = Icons.Default.Notifications,
                    isGranted = hasNotificationPermission,
                    onEnable = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CosmicAppTheme.colors.accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("I have enabled all", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onEnable: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CosmicAppTheme.colors.cardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.1f) 
                        else CosmicAppTheme.colors.accent.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF4CAF50) else CosmicAppTheme.colors.accent
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.textPrimary, fontSize = 16.sp)
                Text(description, fontSize = 12.sp, color = CosmicAppTheme.colors.textSecondary)
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            if (isGranted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Enabled", tint = Color(0xFF4CAF50))
            } else {
                TextButton(onClick = onEnable) {
                    Text("ENABLE", color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
