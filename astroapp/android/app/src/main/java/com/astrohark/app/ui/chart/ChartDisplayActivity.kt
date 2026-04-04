package com.astrohark.app.ui.chart

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.astrohark.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ChartDisplayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val birthDataStr = intent.getStringExtra("birthData")
        var birthData: JSONObject? = null

        if (birthDataStr != null) {
            try {
                birthData = JSONObject(birthDataStr)
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid Birth Data", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        } else {
            Toast.makeText(this, "No Birth Data Provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            CosmicAppTheme {
                ChartDisplayScreen(
                    birthData = birthData!!,
                    onFetchChart = { bData -> fetchChartHtml(bData) }
                )
            }
        }
    }

    private suspend fun fetchChartHtml(birthData: JSONObject): String? = withContext(Dispatchers.IO) {
        try {
            val apiInterface = com.astrohark.app.data.api.ApiClient.api
            val dateStr = String.format("%04d-%02d-%02d", birthData.optInt("year"), birthData.optInt("month"), birthData.optInt("day"))
            val timeStr = String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute"))

            val payload = com.google.gson.JsonObject().apply {
                addProperty("date", dateStr)
                addProperty("time", timeStr)
                addProperty("lat", birthData.optDouble("latitude"))
                addProperty("lng", birthData.optDouble("longitude"))
                addProperty("timezone", birthData.optDouble("timezone", 5.5))
            }

            val response = apiInterface.getRasiEngBirthChart(payload)
            if (response.isSuccessful && response.body() != null) {
                val jsonResponse = JSONObject(response.body().toString())
                if (jsonResponse.has("data")) {
                    val data = jsonResponse.getJSONObject("data")
                    generateHtml(data, birthData)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generateHtml(data: JSONObject, inputData: JSONObject): String {
        val planets = data.getJSONObject("rawPlanets")
        val panchangam = data.getJSONObject("panchangam")
        val dasha = data.optJSONObject("dasha")
        val lagna = data.getJSONObject("lagna")
        val navamsa = data.getJSONObject("navamsa").getJSONObject("planets")

        val signMap = mutableMapOf<String, Int>()
        // Add variations to be safe
        listOf("Pisces", "Pis").forEach { signMap[it] = 0 }
        listOf("Aries", "Ari").forEach { signMap[it] = 1 }
        listOf("Taurus", "Tau").forEach { signMap[it] = 2 }
        listOf("Gemini", "Gem").forEach { signMap[it] = 3 }
        listOf("Aquarius", "Aqu").forEach { signMap[it] = 4 }
        listOf("Cancer", "Can").forEach { signMap[it] = 5 }
        listOf("Capricorn", "Cap").forEach { signMap[it] = 6 }
        listOf("Leo", "Leo").forEach { signMap[it] = 7 }
        listOf("Sagittarius", "Sag").forEach { signMap[it] = 8 }
        listOf("Scorpio", "Sco").forEach { signMap[it] = 9 }
        listOf("Libra", "Lib").forEach { signMap[it] = 10 }
        listOf("Virgo", "Vir").forEach { signMap[it] = 11 }

        // Add case-insensitive matching
        val normalizedSignMap = signMap.mapKeys { it.key.lowercase() }

        val rasiBoxes = Array(12) { StringBuilder() }
        val lagnaSign = lagna.getString("name")
        signMap[lagnaSign]?.let { idx -> rasiBoxes[idx].append("<div class='planet lagna'>Lagna</div>") }

        val planetKeys = planets.keys()
        while(planetKeys.hasNext()) {
            val pName = planetKeys.next() as String
            val pData = planets.getJSONObject(pName)
            val pSign = pData.getString("sign").lowercase()
            val pNameTamil = pData.optString("nameTamil", pName).take(2)
            normalizedSignMap[pSign]?.let { idx ->
                rasiBoxes[idx].append("<div class='planet'>$pNameTamil</div>")
            }
        }

        val navamsaBoxes = Array(12) { StringBuilder() }
        val nKeys = navamsa.keys()
        while(nKeys.hasNext()) {
             val pName = nKeys.next() as String
             val pData = navamsa.getJSONObject(pName)
             val pSign = pData.getString("navamsaSign").lowercase()
             val pNameTamil = planets.optJSONObject(pName)?.optString("nameTamil", pName)?.take(2) ?: pName.take(2)
             normalizedSignMap[pSign]?.let { idx ->
                 navamsaBoxes[idx].append("<div class='planet'>$pNameTamil</div>")
             }
        }

        return """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 16px; background: #0B0805; color: #F5F2F0; }
                    h2, h3 { text-align: center; color: #FFB300; margin: 24px 0 12px 0; border-bottom: 2px solid #3E2723; padding-bottom: 8px; font-weight: 300; letter-spacing: 1px; }
                    .chart-container {
                        display: grid;
                        grid-template-columns: 1fr 1fr 1fr 1fr;
                        grid-template-rows: 1fr 1fr 1fr 1fr;
                        gap: 1px;
                        background: #FFB300;
                        border: 4px solid #FFB300;
                        width: 100%;
                        aspect-ratio: 1 / 1;
                        margin-bottom: 30px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                        border-radius: 4px;
                        overflow: hidden;
                    }
                    .box { background: #1C140E; padding: 4px; font-size: 11px; display: flex; flex-wrap: wrap; align-content: center; justify-content: center; min-height: 40px; border: 0.5px solid #3E2723; }
                    .b0 { grid-column: 1; grid-row: 1; }
                    .b1 { grid-column: 2; grid-row: 1; }
                    .b2 { grid-column: 3; grid-row: 1; }
                    .b3 { grid-column: 4; grid-row: 1; }
                    .b4 { grid-column: 1; grid-row: 2; }
                    .center-box { grid-column: 2 / span 2; grid-row: 2 / span 2; background: #0B0805; display: flex; align-items: center; justify-content: center; font-weight: bold; color: #FFB300; font-size: 20px; text-transform: uppercase; letter-spacing: 2px; }
                    .b5 { grid-column: 4; grid-row: 2; }
                    .b6 { grid-column: 1; grid-row: 3; }
                    .b7 { grid-column: 4; grid-row: 3; }
                    .b8 { grid-column: 1; grid-row: 4; }
                    .b9 { grid-column: 2; grid-row: 4; }
                    .b10 { grid-column: 3; grid-row: 4; }
                    .b11 { grid-column: 4; grid-row: 4; }
                    .planet { background: #3E2723; padding: 2px 6px; margin: 2px; border-radius: 4px; color: #FFB300; font-weight: bold; border: 1px solid #FFB300; font-size: 10px; }
                    .planet.lagna { background: #FFB300; color: #1C140E; border-color: #FFB300; }
                    .info-table { width: 100%; border-collapse: collapse; margin-top: 20px; border: 1px solid #3E2723; background: #1C140E; border-radius: 12px; overflow: hidden; }
                    .info-table td, .info-table th { border: 1px solid #3E2723; padding: 12px; text-align: left; font-size: 13px; }
                    .info-table th { background-color: #3E2723; color: #FFB300; font-weight: 600; }
                    .highlight { background-color: #3E2723; font-weight: bold; border: 2px solid #FFB300; color: #fff; }
                </style>
            </head>
            <body>
                <h3>${inputData.optString("name")}'s Chart</h3>
                <p style="text-align:center; font-size:12px;">${inputData.optString("city")} | ${inputData.optInt("day")}-${inputData.optInt("month")}-${inputData.optInt("year")}</p>

                <h3>Rasi Chart</h3>
                <div class="chart-container">
                    <div class="box b0">${rasiBoxes[0]}</div>
                    <div class="box b1">${rasiBoxes[1]}</div>
                    <div class="box b2">${rasiBoxes[2]}</div>
                    <div class="box b3">${rasiBoxes[3]}</div>
                    <div class="box b4">${rasiBoxes[4]}</div>
                    <div class="center-box">RASI</div>
                    <div class="box b5">${rasiBoxes[5]}</div>
                    <div class="box b6">${rasiBoxes[6]}</div>
                    <div class="box b7">${rasiBoxes[7]}</div>
                    <div class="box b8">${rasiBoxes[8]}</div>
                    <div class="box b9">${rasiBoxes[9]}</div>
                    <div class="box b10">${rasiBoxes[10]}</div>
                    <div class="box b11">${rasiBoxes[11]}</div>
                </div>

                <h3>Navamsa Chart</h3>
                <div class="chart-container">
                    <div class="box b0">${navamsaBoxes[0]}</div>
                    <div class="box b1">${navamsaBoxes[1]}</div>
                    <div class="box b2">${navamsaBoxes[2]}</div>
                    <div class="box b3">${navamsaBoxes[3]}</div>
                    <div class="box b4">${navamsaBoxes[4]}</div>
                    <div class="center-box">NAVAMSA</div>
                    <div class="box b5">${navamsaBoxes[5]}</div>
                    <div class="box b6">${navamsaBoxes[6]}</div>
                    <div class="box b7">${navamsaBoxes[7]}</div>
                    <div class="box b8">${navamsaBoxes[8]}</div>
                    <div class="box b9">${navamsaBoxes[9]}</div>
                    <div class="box b10">${navamsaBoxes[10]}</div>
                    <div class="box b11">${navamsaBoxes[11]}</div>
                </div>

                <h3>Panchangam</h3>
                <table class="info-table">
                    <tr><th>Tithi</th><td>${panchangam.optString("tithi")}</td></tr>
                    <tr><th>Nakshatra</th><td>${panchangam.optString("nakshatra")}</td></tr>
                    <tr><th>Yoga</th><td>${panchangam.optString("yoga")}</td></tr>
                    <tr><th>Karana</th><td>${panchangam.optString("karana")}</td></tr>
                </table>
                ${if(dasha != null) """
                <h3>Current Dasha</h3>
                <table class="info-table highlight">
                    <tr><th>Lord</th><td>${dasha.optString("currentLord")}</td></tr>
                    <tr><th>Bhukti</th><td>${dasha.optString("bhuktiName")}</td></tr>
                    <tr><th>Ends At</th><td>${dasha.optString("endsAt").take(10)}</td></tr>
                    <tr><th>Remaining</th><td>${String.format("%.1f", dasha.optDouble("remainingYearsInCurrentDasha"))} Years</td></tr>
                </table>
                """ else ""}

                <h3>நவகிரக பாதசாரம் (Navagraha Pathasaram)</h3>
                <table class="info-table">
                    <thead>
                        <tr>
                            <th>கிரகம்</th>
                            <th>ராசி</th>
                            <th>பாகை</th>
                            <th>நட்சத்திரம்</th>
                            <th>பாதம்</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${run {
                            val sb = StringBuilder()
                            val keys = listOf("Ascendant", "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")
                            keys.forEach { p ->
                                val pData = planets.optJSONObject(p)
                                if (pData != null) {
                                    val name = if(p == "Ascendant") "லக்னம்" else pData.optString("nameTamil", p)
                                    val rasiColor = "#000"
                                    val rasi = pData.optString("signTamil", pData.optString("sign"))
                                    val deg = pData.optString("degreeFormatted", "")
                                    val naks = pData.optString("nakshatra", "")
                                    val pada = pData.optInt("nakshatraPada", 1)
                                    sb.append("<tr><td>$name</td><td>$rasi</td><td>$deg</td><td>$naks</td><td>$pada</td></tr>")
                                }
                            }
                            sb.toString()
                        }}
                    </tbody>
                </table>

            </body>
            </html>
        """
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartDisplayScreen(
    birthData: JSONObject,
    onFetchChart: suspend (JSONObject) -> String?
) {
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = onFetchChart(birthData)
        if (result != null) {
            htmlContent = result
        } else {
            failed = true
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Birth Chart Analysis") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { /* finish handled in Activity or via back handler */ }) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = CosmicAppTheme.colors.accent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CosmicAppTheme.colors.bgStart,
                    titleContentColor = CosmicAppTheme.colors.textPrimary
                )
            )
        },
        containerColor = CosmicAppTheme.colors.bgStart
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CosmicAppTheme.colors.accent
                )
            } else if (failed) {
                 Text(
                     text = "Failed to load chart data.",
                     color = Color.Red,
                     modifier = Modifier.align(Alignment.Center)
                 )
            } else if (htmlContent != null) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = WebViewClient()
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, htmlContent!!, "text/html", "utf-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
