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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    onFetchMatch = { bData -> fetchMatchHtml(bData) }
                )
            }
        }
    }

    private suspend fun fetchMatchHtml(birthData: JSONObject): String? = withContext(Dispatchers.IO) {
        try {
            val apiInterface = ApiClient.api
            val cGender = birthData.optString("gender")
            val pData = birthData.optJSONObject("partner")

            if (pData == null) {
                android.util.Log.e("MatchDisplay", "Partner data is null")
                return@withContext null
            }

            fun extract(json: JSONObject): com.google.gson.JsonObject {
                return com.google.gson.JsonObject().apply {
                    // Handle both formats: dob string or day/month/year
                    val dob = if (json.has("dob")) {
                        json.getString("dob")
                    } else {
                        val y = json.optInt("year", 2000)
                        val m = json.optInt("month", 1)
                        val d = json.optInt("day", 1)
                        String.format("%04d-%02d-%02d", y, m, d)
                    }

                    val tob = if (json.has("tob")) {
                        json.getString("tob")
                    } else {
                        val h = json.optInt("hour", 12)
                        val min = json.optInt("minute", 0)
                        String.format("%02d:%02d", h, min)
                    }

                    addProperty("dob", dob)
                    addProperty("tob", tob)
                    addProperty("lat", json.optDouble("latitude", 13.0827))
                    addProperty("lng", json.optDouble("longitude", 80.2707))
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
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        padding: 20px;
                        background-color: #0B0805;
                        color: #F5F2F0;
                        line-height: 1.6;
                    }
                    .card {
                        background: #1C140E;
                        padding: 24px;
                        border-radius: 18px;
                        border: 1px solid #3E2723;
                        margin-bottom: 24px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                    }
                    h2 { color: #FFB300; text-align: center; font-weight: 300; margin-top: 0; letter-spacing: 1px; border-bottom: 1px solid #3E2723; padding-bottom: 12px; }
                    .score-box {
                        text-align: center;
                        font-size: 36px;
                        font-weight: 800;
                        color: #FFB300;
                        margin: 24px 0;
                        padding: 20px;
                        background: #0B0805;
                        border-radius: 16px;
                        border: 2px solid #FFB300;
                    }
                    .info-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 14px 0;
                        border-bottom: 1px solid #3E2723;
                    }
                    .info-label { color: #A58B74; font-size: 13px; }
                    .info-value { font-weight: bold; color: #FFB300; }

                    table { width: 100%; border-collapse: separate; border-spacing: 0 12px; margin-top: 10px; }
                    th { text-align: left; padding: 12px; color: #666; font-size: 11px; text-transform: uppercase; }
                    td {
                        background: #0B0805;
                        padding: 16px;
                        border-radius: 12px;
                        font-weight: 400;
                        border: 1px solid #3E2723;
                    }
                    .good { color: #4CAF50; font-weight: bold; }
                    .bad { color: #EF5350; font-weight: bold; }
                    .verdict {
                        text-align: center;
                        font-size: 18px;
                        font-weight: bold;
                        padding: 16px;
                        border-radius: 12px;
                        margin-top: 16px;
                        border: 1px solid currentColor;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                    }
                    .verdict-advisable { background: rgba(76, 175, 80, 0.1); color: #4CAF50; }
                    .verdict-not { background: rgba(239, 83, 80, 0.1); color: #EF5350; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>Marriage Compatibility</h2>
                    <div id="content">Analyzing stars...</div>
                </div>

                <div class="card" id="dosha-card" style="display:none;">
                    <h2>Dosha Analysis</h2>
                    <div id="dosha-content"></div>
                </div>

                <script>
                    try {
                        const root = $jsonResponse;
                        const data = root.data; // Access the nested data object
                        let html = '';

                        if (data) {
                            html += '<div class="info-row"><span class="info-label">Boy Star</span><span class="info-value">' + data.boy.nakshatra + ' (' + data.boy.rasi + ')</span></div>';
                            html += '<div class="info-row"><span class="info-label">Girl Star</span><span class="info-value">' + data.girl.nakshatra + ' (' + data.girl.rasi + ')</span></div>';

                            html += '<div class="score-box">' + (data.totalScore || 0) + ' / ' + (data.maxScore || 36) + '</div>';

                            const verdictClass = data.verdict === 'Advisable' ? 'verdict-advisable' : 'verdict-not';
                            html += '<div class="verdict ' + verdictClass + '">' + data.verdict + '</div>';

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

                                    html += '<tr><td>' + name + '</td><td class="' + cls + '" style="text-align:right">' + icon + ' (' + score + '/' + max + ')</td></tr>';
                                });
                                html += '</table>';
                            }
                            document.getElementById('content').innerHTML = html;

                            // Dosha
                            let dHtml = '';
                            const formatDosha = (label, d) => {
                                const cls = d.hasDosha ? 'bad' : 'good';
                                return '<div class="info-row"><span class="info-label">' + label + '</span><span class="' + cls + '">' + (d.hasDosha ? 'Dosha Found' : 'No Dosha') + '</span></div>' +
                                       '<div style="font-size:12px; color:#888; margin-bottom:10px;">' + (d.desc || d.details || '') + '</div>';
                            };
                            dHtml += formatDosha('Male', data.boyDosha);
                            dHtml += formatDosha('Female', data.girlDosha);

                            if (data.sandhi) {
                                dHtml += '<div class="info-row"><span class="info-label">Dasha Sandhi</span><span class="info-value">' + (data.sandhi.hasSandhi ? 'Overlap Detected' : 'Safe') + '</span></div>';
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
    onFetchMatch: suspend (JSONObject) -> String?
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
                title = { Text("Compatibility Match") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { /* navigate back handled by activity launcher */ }) {
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
                 Text(
                     text = htmlContent ?: "Failed to load match data.",
                     color = Color.Red,
                     modifier = Modifier.align(Alignment.Center).padding(16.dp),
                     textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
