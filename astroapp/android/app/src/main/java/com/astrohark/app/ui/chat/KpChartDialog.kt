package com.astrohark.app.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import com.google.gson.JsonObject
import com.astrohark.app.data.api.ApiClient

@Composable
fun KpChartDialog(
    birthData: JSONObject,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var kpData by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(birthData) {
        scope.launch {
            try {
                isLoading = true
                val payload = JsonObject().apply {
                    addProperty("date", String.format("%04d-%02d-%02d", birthData.optInt("year"), birthData.optInt("month"), birthData.optInt("day")))
                    addProperty("time", String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute")))
                    addProperty("lat", birthData.optDouble("latitude"))
                    addProperty("lon", birthData.optDouble("longitude"))
                }
                
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getKpChart(payload)
                }

                if (response.isSuccessful && response.body() != null) {
                    val rawJson = response.body().toString()
                    val jsonObj = JSONObject(rawJson)
                    if (jsonObj.optBoolean("success")) {
                        kpData = jsonObj.optJSONObject("data")
                    } else {
                        error = "Failed to generate KP Chart."
                    }
                } else {
                    error = "Server Error: ${response.code()}"
                }
            } catch (e: Exception) {
                error = e.localizedMessage
            } finally {
                isLoading = false
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFCF5E5) // Parchment Light
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "KP Rasi Chart",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B0000)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = Color.Gray)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6)),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE6C280))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "ஜாதகர் விவரங்கள் (Client Details):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B0000)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(text = "பெயர் (Name): ${birthData.optString("name", "User")}", fontSize = 11.sp, color = Color.Black)
                                val dateStr = String.format("%02d-%02d-%04d", birthData.optInt("day"), birthData.optInt("month"), birthData.optInt("year"))
                                Text(text = "தேதி (Date): $dateStr", fontSize = 11.sp, color = Color.Black)
                            }
                            Column {
                                val timeStr = String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute"))
                                Text(text = "நேரம் (Time): $timeStr", fontSize = 11.sp, color = Color.Black)
                                Text(text = "ஊர் (Place): ${birthData.optString("place", birthData.optString("birthPlace", "-"))}", fontSize = 11.sp, color = Color.Black)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF8B0000))
                    }
                } else if (error != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        Text(text = "Error: $error", color = Color.Red)
                    }
                } else if (kpData != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        DynamicKpRasiKadam(kpData!!)
                        PlanetIndicatorsTable(kpData!!)
                        BhavaIndicatorsTable(kpData!!)
                    }
                }
            }
        }
    }
}

val planetShortNames = mapOf(
    "Sun" to "சூரி", "Moon" to "சந்", "Mars" to "செவ்",
    "Mercury" to "புத", "Jupiter" to "குரு", "Venus" to "சுக்",
    "Saturn" to "சனி", "Rahu" to "ராகு", "Ketu" to "கேது"
)

val nakshatraTamil = mapOf(
    "Ashwini" to "அஸ்வினி", "Bharani" to "பரணி", "Krittika" to "கிருத்திகை", 
    "Rohini" to "ரோகிணி", "Mrigashira" to "மிருகசீரிஷம்", "Ardra" to "திருவாதிரை", 
    "Punarvasu" to "புனர்பூசம்", "Pushya" to "பூசம்", "Ashlesha" to "ஆயில்யம்", 
    "Magha" to "மகம்", "Purva Phalguni" to "பூரம்", "Uttara Phalguni" to "உத்திரம்", 
    "Hasta" to "அஸ்தம்", "Chitra" to "சித்திரை", "Swati" to "சுவாதி", 
    "Vishakha" to "விசாகம்", "Anuradha" to "அனுஷம்", "Jyeshtha" to "கேட்டை", 
    "Mula" to "மூலம்", "Purva Ashadha" to "பூராடம்", "Uttara Ashadha" to "உத்திராடம்", 
    "Shravana" to "திருவோணம்", "Dhanishta" to "அவிட்டம்", "Shatabhisha" to "சதயம்", 
    "Purva Bhadrapada" to "பூரட்டாதி", "Uttara Bhadrapada" to "உத்திரட்டாதி", "Revati" to "ரேவதி"
)

fun formatKpDegree(longitude: Double): String {
    val degInSign = longitude % 30
    val deg = degInSign.toInt()
    val min = ((degInSign - deg) * 60).toInt()
    return String.format("%02d°%02d", deg, min)
}

@Composable
fun DynamicKpRasiKadam(kpData: JSONObject) {
    val signNames = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
    val gridMap = listOf(11, 0, 1, 2, 10, -1, -1, 3, 9, -1, -1, 4, 8, 7, 6, 5) // South Indian Grid Layout
    
    val planetsArray = kpData.optJSONArray("planets") ?: JSONArray()
    val housesArray = kpData.optJSONArray("houses") ?: JSONArray()

    val gridItemsByRasi = mutableMapOf<String, MutableList<Pair<String, Color>>>()

    val planetColors = mapOf(
        "Sun" to Color(0xFFFF5722), // Orange
        "Moon" to Color(0xFF2196F3), // Blue
        "Mars" to Color(0xFFE91E63), // Pink/Red
        "Mercury" to Color(0xFF4CAF50), // Green
        "Jupiter" to Color(0xFFFFC107), // Gold
        "Venus" to Color(0xFF9C27B0), // Purple
        "Saturn" to Color(0xFF3F51B5), // Dark Blue
        "Rahu" to Color(0xFF607D8B), // Grey
        "Ketu" to Color(0xFF795548) // Brown
    )

    // Fill planets
    for (i in 0 until planetsArray.length()) {
        val p = planetsArray.optJSONObject(i)
        if (p != null) {
            val rasi = p.optString("rasi")
            val name = p.optString("name")
            val abbr = planetShortNames[name] ?: name.take(2)
            val lon = p.optDouble("longitude")
            val label = "$abbr ${formatKpDegree(lon)}"
            val color = planetColors[name] ?: Color.DarkGray
            gridItemsByRasi.getOrPut(rasi) { mutableListOf() }.add(Pair(label, color))
        }
    }

    // Fill the 12 house cusps (bhavas) with Roman numerals
    for (i in 0 until housesArray.length()) {
        val h = housesArray.optJSONObject(i)
        if (h != null) {
            val bhava = h.optInt("bhava")
            val rasi = h.optString("rasi")
            val lon = h.optDouble("longitude")
            val label = "$bhava ${formatKpDegree(lon)}"
            gridItemsByRasi.getOrPut(rasi) { mutableListOf() }.add(Pair(label, Color(0xFF8B0000)))
        }
    }

    val TraditionalRed = Color(0xFF8B0000)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(2.dp, TraditionalRed, RoundedCornerShape(4.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cellW = w / 4
            val cellH = h / 4

            for (i in 1..3) {
                if (i == 2) {
                    drawLine(TraditionalRed, Offset(i * cellW, 0f), Offset(i * cellW, cellH), strokeWidth = 1.dp.toPx())
                    drawLine(TraditionalRed, Offset(i * cellW, 3 * cellH), Offset(i * cellW, h), strokeWidth = 1.dp.toPx())
                } else {
                    drawLine(TraditionalRed, Offset(i * cellW, 0f), Offset(i * cellW, h), strokeWidth = 1.dp.toPx())
                }
            }

            for (i in 1..3) {
                if (i == 2) {
                    drawLine(TraditionalRed, Offset(0f, i * cellH), Offset(cellW, i * cellH), strokeWidth = 1.dp.toPx())
                    drawLine(TraditionalRed, Offset(3 * cellW, i * cellH), Offset(w, i * cellH), strokeWidth = 1.dp.toPx())
                } else {
                    drawLine(TraditionalRed, Offset(0f, i * cellH), Offset(w, i * cellH), strokeWidth = 1.dp.toPx())
                }
            }
            
            // Draw center boundary box
            val rectPath = Path().apply {
                moveTo(cellW, cellH)
                lineTo(3 * cellW, cellH)
                lineTo(3 * cellW, 3 * cellH)
                lineTo(cellW, 3 * cellH)
                close()
            }
            drawPath(rectPath, TraditionalRed, style = Stroke(width = 2.dp.toPx()))
        }

        Column(Modifier.fillMaxSize()) {
            for (row in 0..3) {
                Row(Modifier.weight(1f)) {
                    for (col in 0..3) {
                        val pos = row * 4 + col
                        val signIdx = gridMap[pos]

                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (signIdx != -1) {
                                val signEn = signNames[signIdx]
                                val items = gridItemsByRasi[signEn] ?: emptyList()

                                Column(
                                    Modifier.fillMaxSize().padding(2.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items.forEach { (text, color) ->
                                        Text(text = text, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (pos == 5) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    // Center of chart is spanning 2x2.
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun romanNumeral(num: Int): String {
    return when(num) {
        1 -> "I"
        2 -> "II"
        3 -> "III"
        4 -> "IV"
        5 -> "V"
        6 -> "VI"
        7 -> "VII"
        8 -> "VIII"
        9 -> "IX"
        10 -> "X"
        11 -> "XI"
        12 -> "XII"
        else -> num.toString()
    }
}

fun calculateKbConnection(x: Int, y: Int): String {
    if (x <= 0 || y <= 0) return "-"
    var prevY = y - 1
    if (prevY == 0) prevY = 12
    var prevX = x - 1
    if (prevX == 0) prevX = 12
    
    return when {
        x == prevY -> "$x"
        y == prevX -> "$y"
        x == y -> "$x"
        else -> "$y, $x"
    }
}

@Composable
fun PlanetIndicatorsTable(kpData: JSONObject) {
    val planetsArray = kpData.optJSONArray("planets") ?: JSONArray()
    val planetsList = mutableListOf<JSONObject>()
    for (i in 0 until planetsArray.length()) {
        val p = planetsArray.optJSONObject(i)
        if (p != null) planetsList.add(p)
    }

    val planetTamilNames = mapOf(
        "Sun" to "சூரியன்", "Moon" to "சந்திரன்", "Mars" to "செவ்வாய்",
        "Mercury" to "புதன்", "Jupiter" to "குரு", "Venus" to "சுக்கிரன்",
        "Saturn" to "சனி", "Rahu" to "ராகு", "Ketu" to "கேது"
    )
    val planetShortNames = mapOf(
        "Sun" to "சூரி", "Moon" to "சந்", "Mars" to "செவ்",
        "Mercury" to "புத", "Jupiter" to "குரு", "Venus" to "சுக்",
        "Saturn" to "சனி", "Rahu" to "ராகு", "Ketu" to "கேது"
    )
    val sortedKeys = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")

    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("கிரக குறி காட்டிகள்", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B0000))
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(Modifier.fillMaxWidth().background(Color.LightGray).padding(8.dp)) {
            Text("கிரகம்", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("நட்சத்திரம்", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("நற். அதிபதி", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("தொடர்பு", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        
        for (key in sortedKeys) {
            val p = planetsList.find { it.optString("name") == key }
            if (p != null) {
                val x = p.optInt("bhavaOccupied", 0)
                val planetName = "${planetTamilNames[key] ?: key} $x"
                val nakName = p.optString("nakshatra", "-")
                val nakTamilName = nakshatraTamil[nakName] ?: nakName
                val nakshatraPada = "$nakTamilName ${p.optInt("pada", 0)}"
                
                val starLordEn = p.optString("starLord", "")
                val y = p.optInt("starLordBhava", 0)
                val slName = "${planetShortNames[starLordEn] ?: starLordEn} ${if (y > 0) "$y ல்" else ""}"
                
                val connection = calculateKbConnection(x, y)
                
                Row(Modifier.fillMaxWidth().border(0.5.dp, Color.Gray).padding(8.dp)) {
                    Text(planetName, Modifier.weight(1.2f), fontSize = 12.sp)
                    Text(nakshatraPada, Modifier.weight(1f), fontSize = 12.sp)
                    Text(slName, Modifier.weight(1f), fontSize = 12.sp)
                    Text(connection, Modifier.weight(1f), fontSize = 12.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BhavaIndicatorsTable(kpData: JSONObject) {
    val housesArray = kpData.optJSONArray("houses") ?: JSONArray()
    val housesList = mutableListOf<JSONObject>()
    for (i in 0 until housesArray.length()) {
        val h = housesArray.optJSONObject(i)
        if (h != null) housesList.add(h)
    }

    val planetShortNames = mapOf(
        "Sun" to "சூரி", "Moon" to "சந்", "Mars" to "செவ்",
        "Mercury" to "புத", "Jupiter" to "குரு", "Venus" to "சுக்",
        "Saturn" to "சனி", "Rahu" to "ராகு", "Ketu" to "கேது"
    )

    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("பாவக குறி காட்டிகள்", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B0000))
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(Modifier.fillMaxWidth().background(Color.LightGray).padding(8.dp)) {
            Text("பாவம்", Modifier.weight(0.7f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("நட்சத்திரம்", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("நற். அதிபதி", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("நின்ற அதிபதி", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("தொடர்பு", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        
        for (i in 1..12) {
            val h = housesList.find { it.optInt("bhava") == i }
            if (h != null) {
                val cuspName = "$i"
                val nakName = h.optString("nakshatra", "-")
                val nakTamilName = nakshatraTamil[nakName] ?: nakName
                val nakshatraPada = "$nakTamilName ${h.optInt("pada", 0)}"
                
                val starLordEn = h.optString("starLord", "")
                val bhavaThodarbu = h.optJSONArray("bhavaThodarbu")
                val aHouse = bhavaThodarbu?.optInt(0, 0) ?: 0
                val bHouse = bhavaThodarbu?.optInt(1, 0) ?: 0
                
                val aStr = "${planetShortNames[starLordEn] ?: starLordEn} ${if (aHouse > 0) "$aHouse" else ""}"
                
                val ninraEn = h.optString("ninraNatchathiraAthipathi", "")
                val bStr = "${planetShortNames[ninraEn] ?: ninraEn} ${if (bHouse > 0) "$bHouse" else ""}"
                
                val connection = calculateKbConnection(aHouse, bHouse)
                
                Row(Modifier.fillMaxWidth().border(0.5.dp, Color.Gray).padding(8.dp)) {
                    Text(cuspName, Modifier.weight(0.7f), fontSize = 12.sp)
                    Text(nakshatraPada, Modifier.weight(1f), fontSize = 12.sp)
                    Text(aStr, Modifier.weight(1f), fontSize = 12.sp)
                    Text(bStr, Modifier.weight(1f), fontSize = 12.sp)
                    Text(connection, Modifier.weight(1f), fontSize = 12.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
