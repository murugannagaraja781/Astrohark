package com.astrohark.app.ui.intake

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import com.astrohark.app.ui.theme.*
import com.astrohark.app.utils.Localization
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.data.remote.SocketManager
import com.astrohark.app.ui.chat.ChatActivity
import com.astrohark.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

class IntakeActivity : ComponentActivity() {

    private var partnerId: String? = null
    private var type: String? = null
    private var partnerName: String? = null
    private var partnerImage: String? = null
    private var isEditMode = false
    private var existingData: JSONObject? = null
    private var targetUserId: String? = null

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(this)

        partnerId = intent.getStringExtra("partnerId")
        type = intent.getStringExtra("type")
        partnerName = intent.getStringExtra("partnerName") ?: "Astrologer"
        partnerImage = intent.getStringExtra("partnerImage")
        isEditMode = intent.getBooleanExtra("isEditMode", false)
        targetUserId = intent.getStringExtra("targetUserId")

        val dataStr = intent.getStringExtra("existingData")
        if (dataStr != null) {
            try { existingData = JSONObject(dataStr) } catch(e: Exception){}
        }

        setContent {
            CosmicAppTheme {
                IntakeScreen(
                    partnerId = partnerId,
                    partnerName = partnerName!!,
                    partnerImage = partnerImage,
                    callType = type,
                    isEditMode = isEditMode,
                    existingData = existingData,
                    targetUserId = targetUserId,
                    tokenManager = tokenManager,
                    onClose = { finish() },
                    onSessionConnected = { sessionId, callType ->
                        navigateToSession(sessionId, callType)
                    },
                    onUnanswered = {
                        Toast.makeText(this, "Astrologer is busy. Please try again later.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun navigateToSession(sessionId: String, type: String) {
        if (type == "chat") {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("sessionId", sessionId)
                putExtra("toUserId", partnerId)
                putExtra("toUserName", partnerName)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, com.astrohark.app.ui.call.CallActivity::class.java).apply {
                putExtra("sessionId", sessionId)
                putExtra("partnerId", partnerId)
                putExtra("partnerName", partnerName)
                putExtra("isInitiator", true)
                putExtra("callType", type)
            }
            startActivity(intent)
        }
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeScreen(
    partnerId: String?,
    partnerName: String,
    partnerImage: String?,
    callType: String?,
    isEditMode: Boolean,
    existingData: JSONObject?,
    targetUserId: String?,
    tokenManager: TokenManager,
    onClose: () -> Unit,
    onSessionConnected: (String, String) -> Unit,
    onUnanswered: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Form State
    var isTamil by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }

    // Date
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    // Time
    var hour by remember { mutableStateOf("") }
    var minute by remember { mutableStateOf("") }
    var amPm by remember { mutableStateOf("AM") }
    var unknownTime by remember { mutableStateOf(false) }

    // Place
    var countryName by remember { mutableStateOf("") }
    var stateName by remember { mutableStateOf("") }
    var cityName by remember { mutableStateOf("") }
    var timezoneId by remember { mutableStateOf<String?>(null) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var timezone by remember { mutableStateOf<Double?>(null) }

    // Additional
    var occupation by remember { mutableStateOf("") }
    var maritalStatus by remember { mutableStateOf("Single") }
    var topic by remember { mutableStateOf("General") }

    // Logic State
    var isWaiting by remember { mutableStateOf(false) }
    var waitTimeLeft by remember { mutableStateOf(30) }
    var waitingSessionId by remember { mutableStateOf<String?>(null) }
    var activeCitySearchTarget by remember { mutableStateOf("client") }

    val specificCityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val d = result.data!!
            val fullName = d.getStringExtra("name") ?: ""
            val cityRes = d.getStringExtra("city") ?: ""
            val stateRes = d.getStringExtra("state") ?: ""
            val countryRes = d.getStringExtra("country") ?: ""
            val tzId = d.getStringExtra("timezoneId")
            val latRes = d.getDoubleExtra("lat", 0.0)
            val lonRes = d.getDoubleExtra("lon", 0.0)

            val parsed = if (cityRes.isBlank() && stateRes.isBlank() && countryRes.isBlank()) {
                parsePlaceName(fullName)
            } else {
                Triple(cityRes, stateRes, countryRes)
            }
            
            cityName = parsed.first
            stateName = parsed.second
            countryName = parsed.third
            timezoneId = tzId?.takeIf { it.isNotBlank() }
            latitude = latRes
            longitude = lonRes
        }
    }

    val timezoneDisplay = remember(timezoneId) { timezoneId ?: "" }

    val launchLocationPicker = {
        activeCitySearchTarget = "client"
        specificCityLauncher.launch(Intent(context, com.astrohark.app.ui.city.CitySearchActivity::class.java))
    }

    LaunchedEffect(Unit) {
        if (existingData != null) {
            val d = existingData!!
            name = d.optString("name")
            cityName = d.optString("city")
            day = d.optInt("day", 0).toString().takeIf { it != "0" } ?: ""
            month = d.optInt("month", 0).toString().takeIf { it != "0" } ?: ""
            year = d.optInt("year", 0).toString().takeIf { it != "0" } ?: ""
            hour = d.optInt("hour", 12).toString()
            minute = d.optInt("minute", 0).toString()
        }
    }

    fun submit() {
        if (name.isBlank() || cityName.isBlank() || day.isBlank() || month.isBlank() || year.isBlank()) {
            Toast.makeText(context, "Please fill required fields", Toast.LENGTH_SHORT).show()
            return
        }
        val hour24 = if (amPm == "PM" && hour.toInt() < 12) hour.toInt() + 12 else if (amPm == "AM" && hour.toInt() == 12) 0 else hour.toInt()
        
        val birthData = JSONObject().apply {
            put("name", name)
            put("gender", gender)
            put("day", day.toInt())
            put("month", month.toInt())
            put("year", year.toInt())
            put("hour", hour24)
            put("minute", minute.toInt())
            put("city", cityName)
            put("state", stateName)
            put("country", countryName)
            put("latitude", latitude)
            put("longitude", longitude)
            put("timezone", timezone ?: 5.5)
        }
        
        if (partnerId != null && callType != null) {
            SocketManager.init()
            SocketManager.requestSession(partnerId, callType, birthData) { response ->
                if (response?.optBoolean("ok") == true) {
                    waitingSessionId = response.optString("sessionId")
                    scope.launch { isWaiting = true }
                } else {
                    scope.launch { Toast.makeText(context, response?.optString("error") ?: "Failed", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF140F0A), Color(0xFF0B0805))))) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(Localization.get("premium_consultation", isTamil), color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        TextButton(onClick = { isTamil = !isTamil }) {
                            Text(if (isTamil) "English" else "தமிழ்", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF140F0A), shadowElevation = 8.dp) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { submit() },
                            modifier = Modifier.fillMaxWidth().height(48.dp).shadow(8.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7F00))
                        ) {
                            Text(if (isEditMode) "UPDATE DETAILS" else "START CONSULTATION", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C140E)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(Localization.get("personal_details", isTamil), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = CosmicAppTheme.colors.accent)
                        
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(Localization.get("full_name", isTamil)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, 
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CosmicAppTheme.colors.accent,
                                focusedLabelColor = CosmicAppTheme.colors.accent
                            )
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${Localization.get("gender", isTamil)}:", color = CosmicAppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(16.dp))
                            RadioButton(selected = gender == "Male", onClick = { gender = "Male" }, colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent))
                            Text(Localization.get("male", isTamil), color = CosmicAppTheme.colors.textSecondary)
                            Spacer(Modifier.width(16.dp))
                            RadioButton(selected = gender == "Female", onClick = { gender = "Female" }, colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent))
                            Text(Localization.get("female", isTamil), color = CosmicAppTheme.colors.textSecondary)
                        }

                        // Date of Birth
                        Text(Localization.get("dob", isTamil), style = MaterialTheme.typography.titleSmall, color = CosmicAppTheme.colors.accent)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = day, onValueChange = { if(it.length <= 2) day = it }, label = { Text("DD") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = month, onValueChange = { if(it.length <= 2) month = it }, label = { Text("MM") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = year, onValueChange = { if(it.length <= 4) year = it }, label = { Text("YYYY") }, modifier = Modifier.weight(1.5f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            IconButton(onClick = {
                                val cal = Calendar.getInstance()
                                DatePickerDialog(context, { _, py, pm, pd ->
                                    year = py.toString()
                                    month = (pm + 1).toString()
                                    day = pd.toString()
                                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                            }) {
                                Icon(Icons.Default.AutoFixHigh, "Pick", tint = CosmicAppTheme.colors.accent)
                            }
                        }

                        // Time of Birth
                        Text(Localization.get("tob", isTamil), style = MaterialTheme.typography.titleSmall, color = CosmicAppTheme.colors.accent)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = hour, onValueChange = { if(it.length <= 2) hour = it }, label = { Text("HH") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = minute, onValueChange = { if(it.length <= 2) minute = it }, label = { Text("MM") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            TextButton(onClick = { amPm = if (amPm == "AM") "PM" else "AM" }) {
                                Text(amPm, color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = {
                                TimePickerDialog(context, { _, ph, pm ->
                                    val hTyped = if (ph > 12) (ph - 12) else if (ph == 0) 12 else ph
                                    hour = hTyped.toString()
                                    minute = String.format("%02d", pm)
                                    amPm = if (ph >= 12) "PM" else "AM"
                                }, 12, 0, false).show()
                            }) {
                                Icon(Icons.Default.AutoAwesome, "Pick", tint = CosmicAppTheme.colors.accent)
                            }
                        }

                        // City (Read-only + Picker)
                        Text(Localization.get("pob", isTamil), style = MaterialTheme.typography.titleSmall, color = CosmicAppTheme.colors.accent)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { launchLocationPicker() }
                        ) {
                            OutlinedTextField(
                                value = cityName,
                                onValueChange = {},
                                label = { Text(Localization.get("city", isTamil)) },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = CosmicAppTheme.colors.accent) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color.White,
                                    disabledLabelColor = CosmicAppTheme.colors.accent,
                                    disabledBorderColor = CosmicAppTheme.colors.accent.copy(alpha = 0.5f),
                                    disabledTrailingIconColor = CosmicAppTheme.colors.accent
                                )
                            )
                        }
                    }
                }
            }
        }

        if (isWaiting) {
            Dialog(onDismissRequest = { isWaiting = false }) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1C140E), RoundedCornerShape(24.dp)).padding(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFFF7F00))
                        Spacer(Modifier.height(16.dp))
                        Text("Connecting to Astrologer...", color = Color.White)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { isWaiting = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                            Text("Cancel Request", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun buildPlaceName(city: String, state: String, country: String): String {
    return listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
}

private fun parsePlaceName(place: String): Triple<String, String, String> {
    if (place.isBlank()) return Triple("", "", "")
    val parts = place.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return Triple(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: "", parts.getOrNull(2) ?: "")
}
