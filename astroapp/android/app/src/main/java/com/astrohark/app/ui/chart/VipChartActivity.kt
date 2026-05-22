package com.astrohark.app.ui.chart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.astrohark.app.R
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// --- Aesthetic Constants (Premium Light Theme) ---
val ParchmentBase = Color(0xFFFCF6F0) // Light Cream
val ParchmentLight = Color(0xFFFFFFFF) // White
val ChocolateBrown = Color(0xFFFF7F00) // Brand Orange
val BorderColor = Color(0xFFE0D5C9)
val ChartLineColor = Color(0xFFD4B99F).copy(alpha = 0.8f)

// --- Tamil Translation Constants ---
val signTamil = mapOf(
    "Aries" to "மேஷம்", "Taurus" to "ரிஷபம்", "Gemini" to "மிதுனம்", "Cancer" to "கடகம்",
    "Leo" to "சிம்மம்", "Virgo" to "கன்னி", "Libra" to "துலாம்", "Scorpio" to "விருச்சிகம்",
    "Sagittarius" to "தனுசு", "Capricorn" to "மகரம்", "Aquarius" to "கும்பம்", "Pisces" to "மீனம்"
)

val planetTamil = mapOf(
    "Sun" to "சூரியன்", "Moon" to "சந்திரன்", "Mars" to "செவ்வாய்", "Mercury" to "புதன்",
    "Jupiter" to "குரு", "Venus" to "சுக்கிரன்", "Saturn" to "சனி", "Rahu" to "ராகு",
    "Ketu" to "கேது", "Ascendant" to "லக்னம்"
)

val planetAbbrTamil = mapOf(
    "Sun" to "சூரி", "Moon" to "சந்", "Mars" to "செவ்", "Mercury" to "புத",
    "Jupiter" to "குரு", "Venus" to "சுக்", "Saturn" to "சனி", "Rahu" to "ராகு",
    "Ketu" to "கேது", "Ascendant" to "லக்", "As" to "லக்", "Mandi" to "மாந்"
)
 
val dashaLevelTamil = mapOf(
    1 to "மகா தசை",
    2 to "புத்தி",
    3 to "அந்தரம்",
    4 to "பிரத்யந்தரம்",
    5 to "சூட்சமம்"
)

// --- Updated Data Models ---
data class ChartResponse(val success: Boolean, val data: ChartData)
data class ChartData(
    val planets: List<Planet>,
    val houses: HouseData,
    val panchanga: Panchanga,
    val dasha: List<DashaPeriod>,
    val transits: List<Transit>,
    val tamilDate: TamilDate?,
    val kpSignificators: KPSignificators? = null,
    val navamsa: NavamsaData? = null
)

data class Planet(
    val name: String,
    val signName: String,
    val signIndex: Int,
    val house: Int,
    val nakshatra: String,
    val nakshatraPada: Int,
    val degreeFormatted: String? = null,
    val signLord: String? = null,
    val starLord: String? = null,
    val subLord: String? = null,
    val isRetrograde: Boolean = false,
    val isCombust: Boolean = false
)

data class HouseData(
    val cusps: List<Double>,
    val details: List<HouseDetail>,
    val ascendantDetails: HouseDetail
)

data class HouseDetail(
    val signName: String,
    val signAbbr: String? = null,
    val nakshatra: String? = null,
    val signLord: String? = null,
    val starLord: String? = null,
    val subLord: String? = null,
    val degreeFormatted: String? = null
)

data class Panchanga(
    val tithi: PanchangaValue? = null,
    val nakshatra: PanchangaValue? = null,
    val yoga: PanchangaValue? = null,
    val karana: PanchangaValue? = null,
    val vara: PanchangaValue? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val moonSign: String? = null,
    val sunSign: String? = null
)

data class PanchangaValue(val name: String)
data class DashaPeriod(
    val lord: String,
    val start: String,
    val end: String,
    val level: Int,
    val subPeriods: List<DashaPeriod>? = null
)
data class Transit(val name: String, val signName: String, val isRetrograde: Boolean)
data class TamilDate(val day: Int, val month: String, val year: String)
data class NavamsaData(val planets: List<Planet>? = null)
data class KPSignificators(val planetView: List<KPPlanet>?, val houseView: List<KPHouse>?)
data class KPPlanet(val name: String, val levelA: List<Int>, val levelB: List<Int>, val levelC: List<Int>, val levelD: List<Int>)
data class KPHouse(val house: Int, val level1: List<String>, val level2: List<String>, val level3: List<String>, val level4: List<String>, val lord: String)

class VipChartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val birthDataStr = intent.getStringExtra("birthData") ?: "{}"
        val birthData = JSONObject(birthDataStr)

        setContent {
            CosmicAppTheme {
                VipChartScreen(birthData) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipChartScreen(birthData: JSONObject, onBack: () -> Unit) {
    var chartState by remember { mutableStateOf<ChartData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val result = fetchFullChart(birthData)
                chartState = result
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "App Logo",
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Rasi & Navamsa Charts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ChocolateBrown)
                            Text(birthData.optString("name", "User"), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ChocolateBrown) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ParchmentLight)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(ParchmentLight)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ChocolateBrown)
                }
            } else if (chartState != null) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ParchmentLight,
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ChocolateBrown
                        )
                    }
                ) {
                    val tabs = listOf("Charts", "Planets", "Dasha Details", "Indicators", "Panchanga")
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 13.sp,
                                    fontWeight = if(selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if(selectedTab == index) ChocolateBrown else Color.Gray
                                )
                            }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> ChartsTab(chartState!!, birthData)
                        1 -> PlanetsTab(chartState!!)
                        2 -> DashaListTab(chartState!!.dasha)
                        3 -> IndicatorsTab(chartState!!)
                        4 -> PanchangaTab(chartState!!)
                    }
                }
            }
        }
    }
}

@Composable
fun ChartsTab(data: ChartData, birthData: JSONObject) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

        Text("Rasi Chart", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ChocolateBrown)
        Spacer(Modifier.height(8.dp))
        SouthIndianGridEnhanced(data.planets, data.houses.ascendantDetails.signName, "Rasi", birthData, data.panchanga.nakshatra?.name ?: "")

        Spacer(Modifier.height(32.dp))

        Text("Navamsa Chart (D9)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ChocolateBrown)
        Spacer(Modifier.height(8.dp))
        SouthIndianGridEnhanced(data.navamsa?.planets ?: emptyList(), "", "Navamsa", birthData, "")

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun SouthIndianGridEnhanced(planets: List<Planet>, ascSign: String, title: String, birthData: JSONObject, starName: String) {
    val signNames = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
    val gridMap = listOf(11, 0, 1, 2, 10, -1, -1, 3, 9, -1, -1, 4, 8, 7, 6, 5)
    val ascIdx = if (ascSign.isNotEmpty()) signNames.indexOf(ascSign) else -1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(ParchmentLight, RoundedCornerShape(8.dp))
            .border(2.dp, ChocolateBrown, RoundedCornerShape(8.dp))
    ) {
        // Decorative Borders for boxes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cellW = w / 4
            val cellH = h / 4

            // Vertical lines
            for (i in 1..3) {
                if (i == 2) {
                    // Skip center 2x2
                    drawLine(ChocolateBrown, Offset(i * cellW, 0f), Offset(i * cellW, cellH), strokeWidth = 1.dp.toPx())
                    drawLine(ChocolateBrown, Offset(i * cellW, 3 * cellH), Offset(i * cellW, h), strokeWidth = 1.dp.toPx())
                } else {
                    drawLine(ChocolateBrown, Offset(i * cellW, 0f), Offset(i * cellW, h), strokeWidth = 1.dp.toPx())
                }
            }

            // Horizontal lines
            for (i in 1..3) {
                if (i == 2) {
                    // Skip center 2x2
                    drawLine(ChocolateBrown, Offset(0f, i * cellH), Offset(cellW, i * cellH), strokeWidth = 1.dp.toPx())
                    drawLine(ChocolateBrown, Offset(3 * cellW, i * cellH), Offset(w, i * cellH), strokeWidth = 1.dp.toPx())
                } else {
                    drawLine(ChocolateBrown, Offset(0f, i * cellH), Offset(w, i * cellH), strokeWidth = 1.dp.toPx())
                }
            }

            // Central Area Decor (Pillar-like / Unified Center)
            val centralPadding = 2.dp.toPx()
            val rectPath = Path().apply {
                moveTo(cellW + centralPadding, cellH + centralPadding)
                lineTo(3 * cellW - centralPadding, cellH + centralPadding)
                lineTo(3 * cellW - centralPadding, 3 * cellH - centralPadding)
                lineTo(cellW + centralPadding, 3 * cellH - centralPadding)
                close()
            }

            // Draw a subtle background for the center "pillar" area
            drawPath(
                path = rectPath,
                brush = Brush.verticalGradient(listOf(Color(0xFFFFF9C4).copy(alpha = 0.5f), Color(0xFFFBC02D).copy(alpha = 0.1f)))
            )

            // Central Border (Thicker)
            drawPath(rectPath, ChocolateBrown, style = Stroke(width = 2.4.dp.toPx()))
        }

        // Contents
        Column(Modifier.fillMaxSize()) {
            for (row in 0..3) {
                Row(Modifier.weight(1f)) {
                    for (col in 0..3) {
                        val pos = row * 4 + col
                        val signIdx = gridMap[pos]

                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (signIdx != -1) {
                                val signEn = signNames[signIdx]
                                val occupants = mutableListOf<String>()
                                if (signEn == ascSign) occupants.add("As")
                                planets.filter { it.signName == signEn }.forEach { occupants.add(it.name) }

                                 Column(Modifier.fillMaxSize().padding(2.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    val fontSize = if (occupants.size > 3) 10.sp else 11.sp
                                    val lineHeight = if (occupants.size > 3) 11.sp else 13.sp

                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        occupants.forEach { pName ->
                                            Text(
                                                text = planetAbbrTamil[pName] ?: pName.take(3),
                                                fontSize = fontSize,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = when {
                                                    pName == "As" -> Color(0xFFD32F2F) // Deep Red for Asc
                                                    pName == "Sun" -> Color(0xFFE65100)
                                                    pName == "Moon" -> Color(0xFF1976D2)
                                                    else -> Color(0xFF3E2723) // Dark Brown for visibility
                                                },
                                                lineHeight = lineHeight
                                            )
                                        }
                                    }
                                }

                            } else if (pos == 5) {
                                // Central Info Display (Spans 2x2 area 5,6,9,10 but we use box 5 as anchor)
                                Box(modifier = Modifier.fillMaxSize().offset(x = 0.dp), contentAlignment = Alignment.Center) {
                                    // Spanning 2 cells
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlay central text over the 2x2 hole
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val dob = "${birthData.optInt("day")}-${getMonthName(birthData.optInt("month"))}-${birthData.optInt("year")}"
                val tob = String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute"))

                Text(dob, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Text(tob, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Spacer(Modifier.height(4.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ChocolateBrown)
                if (starName.isNotEmpty()) {
                    Text(starName, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        }

    }
}

fun getMonthName(m: Int): String = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[m]

fun getPlanetStatusTamil(planetName: String, signName: String): String {
    return when (planetName) {
        "Sun" -> when (signName) { "Aries" -> "உச்சம்"; "Libra" -> "நீசம்"; "Leo" -> "ஆட்சி"; "Sagittarius", "Pisces", "Scorpio", "Cancer" -> "நட்பு"; "Taurus", "Capricorn", "Aquarius" -> "பகை"; "Gemini", "Virgo" -> "சமம்"; else -> "சமம்" }
        "Moon" -> when (signName) { "Taurus" -> "உச்சம்"; "Scorpio" -> "நீசம்"; "Cancer" -> "ஆட்சி"; "Aries", "Leo", "Sagittarius", "Pisces" -> "நட்பு"; "Gemini", "Virgo", "Capricorn", "Aquarius", "Libra" -> "சமம்"; else -> "சமம்" }
        "Mars" -> when (signName) { "Capricorn" -> "உச்சம்"; "Cancer" -> "நீசம்"; "Aries", "Scorpio" -> "ஆட்சி"; "Leo", "Sagittarius", "Pisces" -> "நட்பு"; "Gemini", "Virgo" -> "பகை"; "Taurus", "Libra", "Aquarius" -> "சமம்"; else -> "சமம்" }
        "Mercury" -> when (signName) { "Virgo" -> "உச்சம்/ஆட்சி"; "Pisces" -> "நீசம்"; "Gemini" -> "ஆட்சி"; "Taurus", "Leo", "Libra" -> "நட்பு"; "Cancer" -> "பகை"; "Aries", "Scorpio", "Sagittarius", "Capricorn", "Aquarius" -> "சமம்"; else -> "சமம்" }
        "Jupiter" -> when (signName) { "Cancer" -> "உச்சம்"; "Capricorn" -> "நீசம்"; "Sagittarius", "Pisces" -> "ஆட்சி"; "Aries", "Leo", "Scorpio" -> "நட்பு"; "Taurus", "Gemini", "Virgo", "Libra" -> "பகை"; "Aquarius" -> "சமம்"; else -> "சமம்" }
        "Venus" -> when (signName) { "Pisces" -> "உச்சம்"; "Virgo" -> "நீசம்"; "Taurus", "Libra" -> "ஆட்சி"; "Gemini", "Capricorn", "Aquarius" -> "நட்பு"; "Cancer", "Leo" -> "பகை"; "Aries", "Scorpio", "Sagittarius" -> "சமம்"; else -> "சமம்" }
        "Saturn" -> when (signName) { "Libra" -> "உச்சம்"; "Aries" -> "நீசம்"; "Capricorn", "Aquarius" -> "ஆட்சி"; "Taurus", "Gemini", "Virgo" -> "நட்பு"; "Cancer", "Leo", "Scorpio" -> "பகை"; "Sagittarius", "Pisces" -> "சமம்"; else -> "சமம்" }
        "Rahu" -> when (signName) { "Taurus" -> "உச்சம்"; "Scorpio" -> "நீசம்"; "Virgo", "Aquarius" -> "ஆட்சி"; else -> "நட்பு" }
        "Ketu" -> when (signName) { "Scorpio" -> "உச்சம்"; "Taurus" -> "நீசம்"; "Pisces", "Aries" -> "ஆட்சி"; else -> "நட்பு" }
        else -> "-"
    }
}

@Composable
fun PlanetsTab(data: ChartData) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Planet Positions (Navagraha)", fontWeight = FontWeight.Bold, color = ChocolateBrown, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))

        // New Precise Table Grid
        Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF2E7D32)).padding(8.dp)) {
                listOf("Planet", "Nakshatra", "Pada", "Sign", "Status").forEach { head ->
                    Text(
                        text = head,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Rows
            data.planets.forEach { planet ->
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f))
                Row(modifier = Modifier.fillMaxWidth().background(ParchmentBase).padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Planet Name (Red)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = planetAbbrTamil[planet.name] ?: planet.name,
                            color = Color.Red,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (planet.isRetrograde) {
                            Text(" (வ)", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        if (planet.isCombust) {
                            Text(" (அ)", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Others in Blue
                    Text(text = planet.nakshatra.take(6), color = Color.Blue, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(text = planet.nakshatraPada.toString(), color = Color.Blue, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(text = (signTamil[planet.signName] ?: planet.signName).take(4), color = Color.Blue, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(text = getPlanetStatusTamil(planet.name, planet.signName), color = Color.Blue, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun PlanetDetailSub(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
    }
}

@Composable
fun DashaListTab(mahadashas: List<DashaPeriod>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().background(ChocolateBrown).padding(16.dp)) {
                Text("Vimshottari Dasha Bhukti Details", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        items(mahadashas) { md ->
            DashaNodeInternal(md)
        }
    }
}

@Composable
fun DashaNodeInternal(period: DashaPeriod) {
    var expanded by remember { mutableStateOf(false) }
    val hasSub = !period.subPeriods.isNullOrEmpty()
    val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    val isCurrent = todayStr >= period.start.take(10) && todayStr <= period.end.take(10)

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isCurrent) Color(0xFFFF7F00).copy(alpha = 0.1f) else Color.Transparent)
                .clickable(enabled = hasSub) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val levelIndent = (period.level - 1) * 20
            Spacer(Modifier.width(levelIndent.dp))

            // Icon/Prefix based on level
            val iconColor = when(period.level) {
                1 -> ChocolateBrown
                2 -> Color(0xFF2E7D32)
                3 -> Color(0xFF1976D2)
                else -> Color.DarkGray
            }

            Box(Modifier.size(32.dp).background(iconColor.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Text(planetAbbrTamil[period.lord] ?: period.lord.take(2), color = iconColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "${planetTamil[period.lord] ?: period.lord} " + (dashaLevelTamil[period.level] ?: when(period.level) {
                        1 -> "Maha Dasha"
                        2 -> "Bhukti"
                        3 -> "Antharam"
                        4 -> "Pratyantharam"
                        else -> "Sookshma"
                    }),
                    fontWeight = if(period.level == 1) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if(period.level == 1) 16.sp else 14.sp,
                    color = Color.DarkGray
                )
                Text("${period.start.take(10).replace("-", ".")} - ${period.end.take(10).replace("-", ".")}", fontSize = 11.sp, color = Color.Gray)
            }

            if (hasSub) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }

        if (expanded && hasSub) {
            period.subPeriods?.forEach { child ->
                DashaNodeInternal(child)
            }
            Divider(Modifier.padding(start = ((period.level) * 20).dp), color = Color.Gray.copy(0.1f))
        }
        if (period.level == 1) {
            Divider(color = Color.LightGray.copy(0.4f))
        }
    }
}

fun getKPSignificatorsForPlanet(planetName: String, data: ChartData): List<Int> {
    val planet = data.planets.find { it.name.equals(planetName, ignoreCase = true) } ?: return emptyList()
    val starLordName = planet.starLord ?: ""
    val starLordPlanet = data.planets.find { it.name.equals(starLordName, ignoreCase = true) }
    
    val significators = mutableSetOf<Int>()
    
    // Level 1: House occupied by star lord
    if (starLordPlanet != null) {
        significators.add(starLordPlanet.house)
    }
    
    // Level 2: Houses owned by star lord
    if (starLordName.isNotEmpty()) {
        data.houses.details.forEachIndexed { index, house ->
            if (house.signLord.equals(starLordName, ignoreCase = true)) {
                significators.add(index + 1)
            }
        }
    }
    
    // Level 3: House occupied by planet
    significators.add(planet.house)
    
    // Level 4: Houses owned by planet
    data.houses.details.forEachIndexed { index, house ->
        if (house.signLord.equals(planet.name, ignoreCase = true)) {
            significators.add(index + 1)
        }
    }
    
    return significators.sorted()
}

@Composable
fun IndicatorsTab(data: ChartData) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("12 பாவங்களின் நட்சத்திர குறிகாட்டிகள்", fontWeight = FontWeight.Bold, color = ChocolateBrown, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF2E7D32)).padding(8.dp)) {
                listOf("பாவம்", "நட்சத்திரம்", "நட். அதிபதி", "நின்ற நட். அதிபதி", "தொடர்பு").forEach { head ->
                    Text(
                        text = head,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            data.houses.details.forEachIndexed { index, house ->
                val bhavaNum = index + 1
                val nakshatra = house.nakshatra ?: ""
                val starLordEn = house.starLord ?: ""
                val starLordTa = planetTamil[starLordEn] ?: starLordEn
                val starLordPlanet = data.planets.find { it.name.equals(starLordEn, ignoreCase = true) }
                val starLordHouse = starLordPlanet?.house?.toString() ?: ""
                
                val starLordStoodStarLordEn = starLordPlanet?.starLord ?: ""
                val starLordStoodStarLordTa = planetAbbrTamil[starLordStoodStarLordEn] ?: starLordStoodStarLordEn
                val starLordStoodStarLordPlanet = data.planets.find { it.name.equals(starLordStoodStarLordEn, ignoreCase = true) }
                val starLordStoodStarLordHouse = starLordStoodStarLordPlanet?.house?.toString() ?: ""

                val col3 = if (starLordTa.isNotEmpty()) "$starLordTa $starLordHouse" else "-"
                val col4 = if (starLordStoodStarLordTa.isNotEmpty()) "$starLordStoodStarLordTa $starLordStoodStarLordHouse" else "-"
                
                // Proper KP connection for the house's star lord
                val significators = getKPSignificatorsForPlanet(starLordEn, data)
                val connection = if (significators.isNotEmpty()) significators.joinToString(",") else "-"
                
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f))
                Row(modifier = Modifier.fillMaxWidth().background(ParchmentBase).padding(vertical = 10.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(bhavaNum.toString(), color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                    Text(nakshatra, color = Color.Blue, fontSize = 10.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
                    Text(col3, color = Color.Blue, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(col4, color = Color.Blue, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(connection, color = Color.Blue, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("கிரக குறிகாட்டிகள்", fontWeight = FontWeight.Bold, color = ChocolateBrown, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF2E7D32)).padding(8.dp)) {
                listOf("கிரகம்", "நட்சத்திரம் பாதம்", "நட்சத்திர அதிபதி", "பாவ தொடர்பு").forEach { head ->
                    Text(
                        text = head,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            val displayPlanets = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")
            data.planets.filter { it.name in displayPlanets }.forEach { planet ->
                val planetTa = planetTamil[planet.name] ?: planet.name
                val planetHouse = planet.house
                val nak = "${planet.nakshatra} ${planet.nakshatraPada}"
                val starLordEn = planet.starLord ?: ""
                val starLordTa = planetTamil[starLordEn] ?: starLordEn
                val starLordPlanet = data.planets.find { it.name.equals(starLordEn, ignoreCase = true) }
                val starLordHouse = starLordPlanet?.house?.toString() ?: ""
                
                val col1 = "$planetTa $planetHouse"
                val col3 = if (starLordTa.isNotEmpty()) "$starLordTa ${starLordHouse}ல்" else "-"
                
                val significators = getKPSignificatorsForPlanet(planet.name, data)
                val connection = if (significators.isNotEmpty()) significators.joinToString(",") else "-"

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f))
                Row(modifier = Modifier.fillMaxWidth().background(ParchmentBase).padding(vertical = 10.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(col1, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(nak, color = Color.Blue, fontSize = 11.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
                    Text(col3, color = Color.Blue, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(connection, color = Color.Blue, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun PanchangaTab(data: ChartData) {
    val p = data.panchanga
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("பஞ்சாங்கம் (Panchanga)", fontWeight = FontWeight.Bold, color = ChocolateBrown, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))

        val items = listOf(
            "திதி (Tithi)" to (p.tithi?.name ?: "-"),
            "நட்சத்திரம் (Nakshatra)" to (p.nakshatra?.name ?: "-"),
            "யோகம் (Yoga)" to (p.yoga?.name ?: "-"),
            "கரணம் (Karana)" to (p.karana?.name ?: "-"),
            "வாரம் (Vara/Day)" to (p.vara?.name ?: "-"),
            "சூரிய உதயம் (Sunrise)" to (p.sunrise ?: "-"),
            "சூரிய அஸ்தம் (Sunset)" to (p.sunset ?: "-"),
            "சந்திர ராசி (Moon Sign)" to (signTamil[p.moonSign] ?: p.moonSign ?: "-"),
            "சூரிய ராசி (Sun Sign)" to (signTamil[p.sunSign] ?: p.sunSign ?: "-")
        )

        Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
            items.forEachIndexed { i, (label, value) ->
                if (i > 0) HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (i % 2 == 0) ParchmentBase else ParchmentLight)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.DarkGray)
                    Text(value, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ChocolateBrown, textAlign = TextAlign.End)
                }
            }
        }

        // Tamil Date
        data.tamilDate?.let { td ->
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ChocolateBrown.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("தமிழ் தேதி (Tamil Date)", fontWeight = FontWeight.Bold, color = ChocolateBrown, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("${td.day} ${td.month} ${td.year}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.DarkGray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Transit Table
        if (data.transits.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("தற்போதைய கோச்சாரம் (Current Transits)", fontWeight = FontWeight.Bold, color = ChocolateBrown, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF2E7D32)).padding(8.dp)) {
                    listOf("கிரகம்", "ராசி", "வக்கிரம்").forEach { h ->
                        Text(h, modifier = Modifier.weight(1f), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
                data.transits.forEachIndexed { i, t ->
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.4f))
                    Row(modifier = Modifier.fillMaxWidth().background(if (i % 2 == 0) ParchmentBase else ParchmentLight).padding(vertical = 10.dp, horizontal = 4.dp)) {
                        Text(planetTamil[t.name] ?: t.name, modifier = Modifier.weight(1f), color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(signTamil[t.signName] ?: t.signName, modifier = Modifier.weight(1f), color = Color.Blue, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Text(if (t.isRetrograde) "வக்கிரம்" else "-", modifier = Modifier.weight(1f), color = if (t.isRetrograde) Color.Red else Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

private suspend fun fetchFullChart(birthData: JSONObject): ChartData? = withContext(Dispatchers.IO) {
    try {
        val payload = com.google.gson.JsonObject().apply {
            addProperty("date", String.format("%04d-%02d-%02d", birthData.optInt("year"), birthData.optInt("month"), birthData.optInt("day")))
            addProperty("time", String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute")))
            addProperty("lat", birthData.optDouble("latitude"))
            addProperty("lng", birthData.optDouble("longitude"))
            addProperty("timezone", birthData.optDouble("timezone", 5.5))
        }

        val response = com.astrohark.app.data.api.ApiClient.api.getRasiEngBirthChart(payload)
        if (response.isSuccessful && response.body() != null) {
            val chartResponse = Gson().fromJson(response.body().toString(), ChartResponse::class.java)
            if (chartResponse.success) return@withContext chartResponse.data
        }
        null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
