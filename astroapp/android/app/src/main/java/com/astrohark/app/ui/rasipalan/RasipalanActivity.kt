
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            val response = withContext(Dispatchers.IO) {
                ApiClient.api.getRasipalan()
            }
            if (response.isSuccessful && response.body() != null) {
                val fullList = response.body()!!
                // Filter if targetSignId is valid
                dataList = if (targetSignId != -1) {
                    fullList.filter { it.signId == targetSignId }
                } else {
                    fullList
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("Rasipalan", "Error fetching data", e)
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = CosmicAppTheme.colors.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                     containerColor = CosmicAppTheme.colors.bgStart,
                     titleContentColor = CosmicAppTheme.colors.textPrimary
                )
            )
        },
        containerColor = CosmicAppTheme.colors.bgStart
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = ChocolateBrown
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(AstroDimens.Medium),
                    verticalArrangement = Arrangement.spacedBy(AstroDimens.Medium)
                ) {
                    items(dataList) { item ->
                        PremiumRasipalanCard(item)
                    }

                    item {
                        Spacer(modifier = Modifier.height(AstroDimens.Small))
                        Text(
                            text = "More Insights",
                            style = MaterialTheme.typography.titleLarge,
                            color = CosmicAppTheme.colors.accent,
                            modifier = Modifier.padding(vertical = AstroDimens.Small)
                        )
                    }

                    // Coming Soon Sections
                    item { ComingSoonCard("Weekly Rasi") }
                    item { ComingSoonCard("Monthly Rasi") }
                    item { ComingSoonCard("Yearly Rasi") }
                }
            }
        }
    }
}

@Composable
fun PremiumRasipalanCard(item: RasipalanItem) {
    com.astrohark.app.ui.theme.components.AstroCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(AstroDimens.Large)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.signNameTa ?: item.signNameEn ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CosmicAppTheme.colors.textPrimary
                )
                Text(
                    text = item.date ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = CosmicAppTheme.colors.accent
                )
            }

            Spacer(modifier = Modifier.height(AstroDimens.Medium))

            // Short daily message
            Text(
                text = item.prediction?.ta ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = CosmicAppTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(AstroDimens.Medium))
            HorizontalDivider(color = CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(AstroDimens.Medium))

            // 3 Status Indicators
            StatusIndicatorRow("தொழில் (Career)", item.details?.career)
            StatusIndicatorRow("நிதி (Finance)", item.details?.finance)
            StatusIndicatorRow("ஆரோக்கியம் (Health)", item.details?.health)

            Spacer(modifier = Modifier.height(AstroDimens.Medium))

            // Lucky Section
            Surface(
                color = Color.Black.copy(alpha = 0.1f),
                shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                border = BorderStroke(1.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(AstroDimens.Medium).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    LuckyStat("அதிர்ஷ்ட எண்", item.lucky?.number ?: "-")
                    LuckyStat("அதிர்ஷ்ட நிறம்", item.lucky?.color?.ta ?: "-")
                }
            }
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
    Card(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CocoaCardStart.copy(alpha = 0.4f)),
        border = BorderStroke(0.5.dp, ChocolateBrown.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = ChocolateBrown.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = MysticTextPrimary.copy(alpha = 0.8f))
                Text(text = "Feature under preparation", style = MaterialTheme.typography.labelSmall, color = MysticTextSecondary.copy(alpha = 0.6f))
            }
        }
    }
}
