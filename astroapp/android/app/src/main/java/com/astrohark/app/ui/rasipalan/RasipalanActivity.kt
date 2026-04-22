
package com.astrohark.app.ui.rasipalan

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.astrohark.app.data.api.ApiClient
import com.astrohark.app.data.model.RasipalanItem
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AstroDimens
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Divider as HorizontalDivider

// Status Colors (Linked to Theme if possible, or standardized)
private val GoodGlow = Color(0xFF22C55E)
private val ModerateAmber = Color(0xFFF59E0B)
private val WeakRed = Color(0xFFEF4444)

class RasipalanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val signId = intent.getIntExtra("signId", -1)
        val signName = intent.getStringExtra("signName") ?: "Daily Rasi Palan"

        setContent {
            CosmicAppTheme {
                RasipalanScreen(
                    targetSignId = signId,
                    displayTitle = signName,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RasipalanScreen(targetSignId: Int, displayTitle: String, onBack: () -> Unit) {
    var dataList by remember { mutableStateOf<List<RasipalanItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val response = withContext(Dispatchers.IO) { ApiClient.api.getRasipalan() }
            if (response.isSuccessful && response.body() != null) {
                val fullList = response.body()!!
                dataList = if (targetSignId != -1) fullList.filter { it.signId == targetSignId } else fullList
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF140F0A), Color(0xFF0B0805))))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFFFFD700)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(dataList) { item ->
                            PremiumRasipalanCard(item)
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Future Predictions",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        item { ComingSoonCard("Weekly Outlook") }
                        item { ComingSoonCard("Monthly Forecast") }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumRasipalanCard(item: RasipalanItem) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C140E)),
        border = BorderStroke(1.dp, Color(0xFFFF7F00).copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.signNameTa ?: item.signNameEn ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = item.date ?: "2024-04-13",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFD700)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // Main Prediction Text
            Text(
                text = item.prediction?.ta ?: "",
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Detailed Status Blocks
            PremiumStatusItem("தொழில் (Career)", item.details?.career)
            PremiumStatusItem("நிதி (Finance)", item.details?.finance)
            PremiumStatusItem("ஆரோக்கியம் (Health)", item.details?.health)

            Spacer(modifier = Modifier.height(20.dp))

            // Lucky Highlights Section
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    LuckyStat("அதிர்ஷ்ட எண்", item.lucky?.number ?: "-")
                    LuckyStat("அதிர்ஷ்ட நிறம்", item.lucky?.color?.ta ?: "-")
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    LuckyStat("அதிர்ஷ்ட நேரம்", item.lucky?.luckyTime ?: "-")
                    LuckyStat("அமிர்த நேரம்", item.lucky?.unluckyTime ?: "-")
                }
            }
        }
    }
}

@Composable
fun PremiumStatusItem(label: String, detail: String?) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFFFD700),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            color = Color.Black.copy(alpha = 0.2f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = detail ?: "Optimistic outlook for today.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun StatusIndicatorRow(label: String, status: String?) {
    val text = status ?: "Moderate"
    val isLongText = text.length > 20

    if (isLongText) {
        Column(
            modifier = Modifier.padding(vertical = AstroDimens.Small).fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = CosmicAppTheme.colors.accent,
                modifier = Modifier.padding(bottom = AstroDimens.XSmall)
            )
            Surface(
                color = CosmicAppTheme.colors.bgStart.copy(alpha = 0.3f),
                shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.1f))
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(AstroDimens.Small),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CosmicAppTheme.colors.textPrimary
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.padding(vertical = AstroDimens.Small).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = CosmicAppTheme.colors.textSecondary
            )
            StatusChip(text)
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (color, label) = when {
        status.contains("Good", ignoreCase = true) ||
        status.contains("Active", ignoreCase = true) ||
        status.contains("High", ignoreCase = true) ||
        status.contains("Growth", ignoreCase = true) ||
        status.contains("Excellent", ignoreCase = true) -> GoodGlow to status

        status.contains("Weak", ignoreCase = true) ||
        status.contains("Low", ignoreCase = true) ||
        status.contains("Bad", ignoreCase = true) ||
        status.contains("Critical", ignoreCase = true) -> WeakRed to status

        else -> ModerateAmber to status
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun LuckyStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.textSecondary)
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = CosmicAppTheme.colors.accent)
    }
}

@Composable
fun ComingSoonCard(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        shape = RoundedCornerShape(AstroDimens.RadiusMedium),
        color = CosmicAppTheme.colors.cardBg.copy(alpha = 0.4f),
        border = BorderStroke(0.5.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = CosmicAppTheme.colors.accent.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = CosmicAppTheme.colors.textPrimary.copy(alpha = 0.8f))
                Text(text = "Feature under preparation", style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.textSecondary.copy(alpha = 0.6f))
            }
        }
    }
}
