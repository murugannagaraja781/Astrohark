package com.astrohark.app.ui.chart

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.astrohark.app.data.api.ApiClient
import com.astrohark.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MatchDisplayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val birthDataStr = intent.getStringExtra("birthData")
        var birthData: JSONObject? = null

        if (birthDataStr != null) {
            try {
                birthData = JSONObject(birthDataStr)
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid Data", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        } else {
            Toast.makeText(this, "No Data Received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            CosmicAppTheme {
                MatchDisplayScreen(
                    birthData = birthData!!,
                    onFetchMatch = { bData -> fetchMatchHtml(bData) },
                    onBack = { finish() }
                )
            }
        }
    }

    private suspend fun fetchMatchHtml(birthData: JSONObject): String? = withContext(Dispatchers.IO) {
        try {
            val apiInterface = ApiClient.api
            val cGender = birthData.optString("gender")
            val pData = birthData.optJSONObject("partnerData") ?: birthData.optJSONObject("partner")

            if (pData == null) {
                android.util.Log.e("MatchDisplay", "Partner data is null")
                return@withContext null
            }

            fun extract(json: JSONObject): com.google.gson.JsonObject {
                val y = json.optInt("year", 1990)
                val m = json.optInt("month", 1)
                val d = json.optInt("day", json.optInt("date", 1))
                val h = json.optInt("hour", 12)
                val minVal = json.optInt("minute", 0)

                return com.google.gson.JsonObject().apply {
                    addProperty("name", json.optString("name", "User"))
                    addProperty("day", d)
                    addProperty("month", m)
                    addProperty("year", y)
                    addProperty("hour", h)
                    addProperty("min", minVal)
                    addProperty("minute", minVal)
                    addProperty("lat", json.optDouble("latitude", 13.0827))
                    addProperty("lng", json.optDouble("longitude", 80.2707))
                    addProperty("latitude", json.optDouble("latitude", 13.0827))
                    addProperty("longitude", json.optDouble("longitude", 80.2707))
                    addProperty("timezone", json.optDouble("timezone", 5.5))
                    
                    // Unified format for wider compatibility
                    addProperty("dob", String.format("%04d-%02d-%02d", y, m, d))
                    addProperty("tob", String.format("%02d:%02d", h, minVal))
                }
            }

            val boyData: com.google.gson.JsonObject
            val girlData: com.google.gson.JsonObject

            if (cGender.equals("Male", ignoreCase = true)) {
                boyData = extract(birthData)
                girlData = extract(pData)
            } else {
                girlData = extract(birthData)
                boyData = extract(pData)
            }

            android.util.Log.d("MatchDisplay", "Boy: $boyData, Girl: $girlData")

            val payload = com.google.gson.JsonObject().apply {
                add("boyData", boyData)
                add("girlData", girlData)
            }

            val response = apiInterface.getRasiEngMatching(payload)
            if (response.isSuccessful && response.body() != null) {
                val jsonResponse = response.body()!!.toString()
                android.util.Log.d("MatchDisplay", "API Response: ${jsonResponse.take(200)}")
                generateMatchHtml(jsonResponse)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown API Error"
                android.util.Log.e("MatchDisplay", "API Error: ${response.code()} - $errorMsg")
                "ERROR: API returned ${response.code()}: $errorMsg"
            }
        } catch (e: Exception) {
            android.util.Log.e("MatchDisplay", "Exception during fetch: ${e.message}", e)
            "ERROR: ${e.localizedMessage ?: "Unknown Exception"}"
        }
    }

    private fun generateMatchHtml(jsonResponse: String): String {
        return """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;800&display=swap');
                    body {
                        font-family: 'Inter', sans-serif;
                        padding: 16px;
                        background-color: #0B0805;
                        color: #F5F2F0;
                        line-height: 1.5;
                        margin: 0;
                    }
                    .card {
                        background: #1C140E;
                        padding: 20px;
                        border-radius: 20px;
                        border: 1px solid #3E2723;
                        margin-bottom: 20px;
                        box-shadow: 0 8px 25px rgba(0,0,0,0.6);
                    }
                    h2 { 
                        color: #FF7F00; 
                        text-align: center; 
                        font-weight: 600; 
                        margin-top: 0; 
                        font-size: 18px;
                        letter-spacing: 0.5px; 
                        border-bottom: 1px solid rgba(255,127,0,0.2); 
                        padding-bottom: 12px; 
                        text-transform: uppercase;
                    }
                    .score-box {
                        text-align: center;
                        font-size: 42px;
                        font-weight: 800;
                        color: #FF7F00;
                        margin: 20px 0;
                        padding: 15px;
                        background: rgba(255,127,0,0.05);
                        border-radius: 16px;
                        border: 1px solid rgba(255,127,0,0.3);
                    }
                    .info-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 12px 0;
                        border-bottom: 1px solid rgba(165,139,116,0.1);
                    }
                    .info-label { color: #A58B74; font-size: 13px; }
                    .info-value { font-weight: 600; color: #FF7F00; font-size: 14px; }

                    table { width: 100%; border-collapse: separate; border-spacing: 0 8px; margin-top: 10px; }
                    td {
                        background: #0B0805;
                        padding: 14px;
                        border-radius: 12px;
                        font-size: 13px;
                        border: 1px solid #3E2723;
                    }
                    .good { color: #4CAF50; font-weight: 600; }
                    .bad { color: #EF5350; font-weight: 600; }
                    .verdict {
                        text-align: center;
                        font-size: 16px;
                        font-weight: 800;
                        padding: 14px;
                        border-radius: 12px;
                        margin-top: 16px;
                        border: 1px solid currentColor;
                        text-transform: uppercase;
                    }
                    .verdict-advisable { background: rgba(76, 175, 80, 0.1); color: #4CAF50; }
                    .verdict-not { background: rgba(239, 83, 80, 0.1); color: #EF5350; }
                    
                    .dosha-tag {
                        display: inline-block;
                        padding: 4px 8px;
                        border-radius: 6px;
                        font-size: 11px;
                        font-weight: bold;
                        margin-left: 8px;
                    }
                    .dosha-bad { background: rgba(239, 83, 80, 0.2); color: #EF5350; }
                    .dosha-good { background: rgba(76, 175, 80, 0.2); color: #4CAF50; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>திருமணப் பொருத்தம்</h2>
                    <div id="content">
                        <div style="text-align:center; padding: 40px; color: #A58B74;">நட்சத்திரங்களை ஆய்வு செய்கிறது...</div>
                    </div>
                </div>

                <div class="card" id="dosha-card" style="display:none;">
                    <h2>தோஷ ஆய்வு</h2>
                    <div id="dosha-content"></div>
                </div>

                <script>
                    try {
                        const root = $jsonResponse;
                        const data = root.data;
                        let html = '';

                        if (data) {
                            html += '<div class="info-row"><span class="info-label">ஆண் நட்சத்திரம்</span><span class="info-value">' + (data.boy?.nakshatra || '-') + ' (' + (data.boy?.rasi || '-') + ')</span></div>';
                            html += '<div class="info-row"><span class="info-label">பெண் நட்சத்திரம்</span><span class="info-value">' + (data.girl?.nakshatra || '-') + ' (' + (data.girl?.rasi || '-') + ')</span></div>';

                            html += '<div class="score-box">' + (data.totalScore || 0) + ' <span style="font-size:14px; color:#A58B74; font-weight:400">/ ' + (data.maxScore || 36) + '</span></div>';

                            let verdictTxt = data.verdict;
                            if(verdictTxt === 'Advisable') verdictTxt = 'பொருத்தம் உண்டு';
                            else if(verdictTxt === 'Not Advisable') verdictTxt = 'பொருத்தம் இல்லை';
                            
                            const verdictClass = data.verdict === 'Advisable' ? 'verdict-advisable' : 'verdict-not';
                            html += '<div class="verdict ' + verdictClass + '">' + verdictTxt + '</div>';

                            const list = data.poruthams;
                            if (Array.isArray(list)) {
                                html += '<table>';
                                list.forEach(item => {
                                    const name = item.name;
                                    const score = item.score;
                                    const max = item.max;
                                    const isMatch = score > 0;
                                    const cls = isMatch ? 'good' : 'bad';
                                    const icon = isMatch ? '✓' : '✗';

                                    html += '<tr><td><span style="color:#A58B74; font-size:12px">' + name + '</span></td><td class="' + cls + '" style="text-align:right">' + icon + ' <span style="font-size:11px">(' + score + '/' + max + ')</span></td></tr>';
                                });
                                html += '</table>';
                            }
                            document.getElementById('content').innerHTML = html;

                            // Dosha
                            let dHtml = '';
                            const formatDosha = (label, d) => {
                                if (!d) return '';
                                const cls = d.hasDosha ? 'dosha-bad' : 'dosha-good';
                                const dLabel = label === 'Male' ? 'ஆண்' : 'பெண்';
                                const dStatus = d.hasDosha ? 'உள்ளது' : 'இல்லை';
                                return '<div class="info-row"><span class="info-label">' + dLabel + ' செவ்வாய் தோஷம்</span><span class="dosha-tag ' + cls + '">' + dStatus + '</span></div>' +
                                       '<div style="font-size:11px; color:#A58B74; margin-top:4px; margin-bottom:12px; padding-left:2px;">' + (d.desc || d.details || '') + '</div>';
                            };
                            dHtml += formatDosha('Male', data.boyDosha);
                            dHtml += formatDosha('Female', data.girlDosha);

                            if (data.sandhi) {
                                const cls = data.sandhi.hasSandhi ? 'dosha-bad' : 'dosha-good';
                                const sStatus = data.sandhi.hasSandhi ? 'உள்ளது' : 'இல்லை';
                                dHtml += '<div class="info-row" style="border-top: 1px solid rgba(255,255,255,0.05); margin-top:8px;"><span class="info-label">தசா சந்தி</span><span class="dosha-tag ' + cls + '">' + sStatus + '</span></div>';
                                if (data.sandhi.verdict) {
                                     dHtml += '<div style="font-size:11px; color:#A58B74; margin-top:4px;">' + data.sandhi.verdict + '</div>';
                                }
                            }

                            document.getElementById('dosha-content').innerHTML = dHtml;
                            document.getElementById('dosha-card').style.display = 'block';
                        }
                    } catch(e) {
                         document.getElementById('content').innerText = 'Error: ' + e.message;
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDisplayScreen(
    birthData: JSONObject,
    onFetchMatch: suspend (JSONObject) -> String?,
    onBack: () -> Unit
) {
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = onFetchMatch(birthData)
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
                title = { Text("Compatibility Match", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
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
            } else if (failed || htmlContent?.startsWith("ERROR:") == true) {
                 Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                      androidx.compose.material3.Icon(
                          imageVector = androidx.compose.material.icons.Icons.Default.ErrorOutline,
                          contentDescription = null,
                          tint = Color.Red,
                          modifier = Modifier.size(48.dp)
                      )
                      Spacer(Modifier.height(16.dp))
                      Text(
                          text = htmlContent?.replace("ERROR: ", "") ?: "Failed to load match data.",
                          color = Color.Red,
                          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                          fontWeight = FontWeight.Medium
                      )
                 }
            } else if (htmlContent != null) {
                 AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = WebViewClient()
                            setBackgroundColor(0) // Transparent background
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, htmlContent!!, "text/html", "utf-8", null)
                    },
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0B0805))
                )
            }
        }
    }
}
