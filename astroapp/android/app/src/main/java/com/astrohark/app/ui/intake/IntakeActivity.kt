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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
        val isMatchingFromIntent = intent.getBooleanExtra("isMatching", false)
        android.util.Log.d("IntakeActivity", "Init: isMatching=$isMatchingFromIntent")

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
                    isMatching = isMatchingFromIntent,
                    existingData = existingData,
                    targetUserId = targetUserId,
                    tokenManager = tokenManager,
                    onClose = { finish() },
                    onSessionConnected = { sessionId, callType ->
                        navigateToSession(sessionId, callType)
                    },
                    onUnanswered = {
                        Toast.makeText(this, "No response from astrologer", Toast.LENGTH_LONG).show()
                        finish()
                    },
                    saveForm = { saveFormData(it) },
                    loadForm = { loadSavedFormData() }
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

    private fun saveFormData(data: JSONObject) {
        try {
            val prefs = getSharedPreferences("AstroharkIntake", Context.MODE_PRIVATE)
            prefs.edit().putString("lastForm", data.toString()).apply()
        } catch (e: Exception) {}
    }

    private fun loadSavedFormData(): JSONObject? {
        return try {
            val prefs = getSharedPreferences("AstroharkIntake", Context.MODE_PRIVATE)
            val json = prefs.getString("lastForm", null)
            if (json != null) JSONObject(json) else null
        } catch (e: Exception) { null }
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
    isMatching: Boolean,
    existingData: JSONObject?,
    targetUserId: String?,
    tokenManager: TokenManager,
    onClose: () -> Unit,
    onSessionConnected: (String, String) -> Unit,
    onUnanswered: () -> Unit,
    saveForm: (JSONObject) -> Unit,
    loadForm: () -> JSONObject?
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

    // Place
    var cityName by remember { mutableStateOf("") }
    var timezoneId by remember { mutableStateOf<String?>(null) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var timezone by remember { mutableStateOf<Double?>(null) }

    // Partner State
    var pName by remember { mutableStateOf("") }
    var pGender by remember { mutableStateOf("Female") }
    var pDay by remember { mutableStateOf("") }
    var pMonth by remember { mutableStateOf("") }
    var pYear by remember { mutableStateOf("") }
    var pHour by remember { mutableStateOf("") }
    var pMinute by remember { mutableStateOf("") }
    var pAmPm by remember { mutableStateOf("AM") }
    var pCityName by remember { mutableStateOf("") }
    var pTimezoneId by remember { mutableStateOf<String?>(null) }
    var pLatitude by remember { mutableStateOf<Double?>(null) }
    var pLongitude by remember { mutableStateOf<Double?>(null) }
    var pTimezone by remember { mutableStateOf<Double?>(null) }

    var locationPickerTarget by remember { mutableStateOf("me") } // "me" or "partner"

    // Logic State
    var isWaiting by remember { mutableStateOf(false) }
    var waitingSessionId by remember { mutableStateOf<String?>(null) }
    var waitTimeLeft by remember { mutableStateOf(30) }

    // Location Picker
    val placeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val d = result.data!!
            val fullName = d.getStringExtra("name") ?: ""
            val cityRes = d.getStringExtra("city") ?: ""
            val tzId = d.getStringExtra("timezoneId") ?: ""
            val latRes = d.getDoubleExtra("lat", 0.0)
            val lonRes = d.getDoubleExtra("lon", 0.0)

            if (locationPickerTarget == "me") {
                cityName = if (cityRes.isNotBlank()) cityRes else fullName
                timezoneId = tzId
                latitude = latRes
                longitude = lonRes
            } else {
                pCityName = if (cityRes.isNotBlank()) cityRes else fullName
                pTimezoneId = tzId
                pLatitude = latRes
                pLongitude = lonRes
            }
        }
    }

    val launchLocationPicker = { target: String ->
        locationPickerTarget = target
        val intent = Intent(context, com.astrohark.app.ui.city.CitySearchActivity::class.java)
        placeLauncher.launch(intent)
    }

    LaunchedEffect(Unit) {
        var data = existingData
        if (data == null) {
            data = loadForm()
        }

        if (data != null) {
            val d = data!!
            name = d.optString("name")
            gender = d.optString("gender", "Male")
            cityName = d.optString("city")
            day = d.optInt("day", 0).toString().takeIf { it != "0" } ?: ""
            month = d.optInt("month", 0).toString().takeIf { it != "0" } ?: ""
            year = d.optInt("year", 0).toString().takeIf { it != "0" } ?: ""
            
            val h24 = d.optInt("hour", 12)
            if (h24 >= 12) {
                amPm = "PM"
                hour = (if (h24 > 12) h24 - 12 else 12).toString()
            } else {
                amPm = "AM"
                hour = (if (h24 == 0) 12 else h24).toString()
            }
            minute = d.optInt("minute", 0).toString().padStart(2, '0')
            
            latitude = d.optDouble("latitude", 0.0).takeIf { it != 0.0 }
            longitude = d.optDouble("longitude", 0.0).takeIf { it != 0.0 }
            timezoneId = d.optString("timezone").takeIf { it.isNotEmpty() }

            // Partner Data
            val pObj = d.optJSONObject("partnerData") ?: d.optJSONObject("partner")
            if (pObj != null) {
                pName = pObj.optString("name")
                pGender = pObj.optString("gender", "Female")
                val pDob = pObj.optString("dob", "")
                if (pDob.isNotEmpty()) {
                    val pParts = pDob.split("-")
                    if (pParts.size >= 3) {
                        pYear = pParts[0]
                        pMonth = pParts[1]
                        pDay = pParts[2]
                    }
                }
                val pTob = pObj.optString("tob", "")
                if (pTob.isNotEmpty()) {
                    val tParts = pTob.split(" ")
                    if (tParts.size >= 2) {
                        pAmPm = tParts[1]
                        val hParts = tParts[0].split(":")
                        if (hParts.size >= 2) {
                            pHour = hParts[0]
                            pMinute = hParts[1]
                        }
                    }
                }
                pCityName = pObj.optString("pob").ifEmpty { pObj.optString("city") }
                pLatitude = pObj.optDouble("lat", 0.0).takeIf { it != 0.0 } ?: pObj.optDouble("latitude", 0.0).takeIf { it != 0.0 }
                pLongitude = pObj.optDouble("lon", 0.0).takeIf { it != 0.0 } ?: pObj.optDouble("longitude", 0.0).takeIf { it != 0.0 }
                pTimezoneId = pObj.optString("timezone").ifEmpty { pObj.optString("tzId") }
            }
        }

        // Listen for astrologer response
        SocketManager.onSessionAnswered { response ->
            val accepted = response.optBoolean("accept") // Server emits 'accept', not 'ok'
            val sId = response.optString("sessionId")
            if (accepted && sId == waitingSessionId) {
                isWaiting = false
                onSessionConnected(sId, callType ?: "audio")
            } else if (!accepted && response.has("accept")) {
                isWaiting = false
                onUnanswered()
            }
        }
    }

    fun submit() {
        if (name.isBlank() || cityName.isBlank() || day.isBlank() || month.isBlank() || year.isBlank() || hour.isBlank() || minute.isBlank()) {
            Toast.makeText(context, "Please fill required fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (isMatching && (pName.isBlank() || pCityName.isBlank() || pDay.isBlank() || pMonth.isBlank() || pYear.isBlank() || pHour.isBlank() || pMinute.isBlank())) {
            Toast.makeText(context, "Please fill all partner details", Toast.LENGTH_SHORT).show()
            return
        }
        val hour24 = if (amPm == "PM" && hour.toInt() < 12) hour.toInt() + 12 else if (amPm == "AM" && hour.toInt() == 12) 0 else hour.toInt()
        
        val payload = JSONObject().apply {
            put("name", name)
            put("gender", gender)
            put("day", day.toInt())
            put("month", month.toInt())
            put("year", year.toInt())
            put("hour", hour24)
            put("minute", minute.toInt())
            put("city", cityName)
            put("latitude", latitude)
            put("longitude", longitude)
            put("timezone", timezoneId)
            put("isMatching", isMatching)
            if (isMatching) {
               val partner = JSONObject()
               partner.put("name", pName)
               partner.put("gender", pGender)
               partner.put("dob", "$pYear-$pMonth-$pDay")
               partner.put("tob", "$pHour:$pMinute $pAmPm")
               partner.put("pob", pCityName)
               partner.put("lat", pLatitude)
               partner.put("lon", pLongitude)
               partner.put("timezone", pTimezoneId)
               put("partnerData", partner)
            }
        }
        
        // Save for next time
        saveForm(payload)
        
        if (isEditMode) {
            val resultIntent = Intent()
            resultIntent.putExtra("birthData", payload.toString())
            (context as Activity).setResult(Activity.RESULT_OK, resultIntent)
            (context as Activity).finish()
            return
        }

        // If it was opened from Home Page for free matching
        if (isMatching && partnerId == null) {
            val intent = Intent(context, com.astrohark.app.ui.chart.MatchDisplayActivity::class.java)
            intent.putExtra("birthData", payload.toString())
            context.startActivity(intent)
            (context as Activity).finish()
            return
        }

        if (partnerId != null && callType != null) {
            SocketManager.init()
            SocketManager.requestSession(partnerId, callType, payload) { response ->
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
                // Moved button into scrollable column for better organization as requested
            }
        ) { padding ->
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = AstroDimens.Small, vertical = 12.dp), 
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C140E)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = AstroDimens.Small, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = Localization.get("personal_details", isTamil),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CosmicAppTheme.colors.accent
                        )
                        
                        val textFieldColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, 
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color.White,
                            focusedBorderColor = CosmicAppTheme.colors.accent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            disabledBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = CosmicAppTheme.colors.accent,
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                            disabledPlaceholderColor = Color.White.copy(alpha = 0.5f)
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text(Localization.get("full_name", isTamil), fontSize = 14.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp), // Increased height for better interaction
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                            colors = textFieldColors,
                            enabled = true // Explicitly set to true
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${Localization.get("gender", isTamil)}:", style = MaterialTheme.typography.bodySmall, color = CosmicAppTheme.colors.textPrimary)
                            Spacer(Modifier.width(8.dp))
                            RadioButton(selected = gender == "Male", onClick = { gender = "Male" }, colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent))
                            Text(Localization.get("male", isTamil), style = MaterialTheme.typography.bodySmall, color = CosmicAppTheme.colors.textSecondary)
                            Spacer(Modifier.width(12.dp))
                            RadioButton(selected = gender == "Female", onClick = { gender = "Female" }, colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent))
                            Text(Localization.get("female", isTamil), style = MaterialTheme.typography.bodySmall, color = CosmicAppTheme.colors.textSecondary)
                        }

                        Text(Localization.get("dob", isTamil), style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.accent)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AstroDimens.XSmall), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = day, onValueChange = { if(it.length <= 2) day = it }, placeholder = { Text("DD", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            OutlinedTextField(value = month, onValueChange = { if(it.length <= 2) month = it }, placeholder = { Text("MM", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            OutlinedTextField(value = year, onValueChange = { if(it.length <= 4) year = it }, placeholder = { Text("YYYY", fontSize = 12.sp) }, modifier = Modifier.weight(1.3f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            IconButton(onClick = {
                                val cal = Calendar.getInstance()
                                DatePickerDialog(context, com.astrohark.app.R.style.DialogPickerTheme, { _, py, pm, pd ->
                                    year = py.toString(); month = (pm + 1).toString(); day = pd.toString()
                                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                            }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.AutoFixHigh, "Pick", tint = CosmicAppTheme.colors.accent, modifier = Modifier.size(24.dp))
                            }
                        }

                        Text(Localization.get("tob", isTamil), style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.accent)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AstroDimens.XSmall), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = hour, onValueChange = { if(it.length <= 2) hour = it }, placeholder = { Text("HH", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            OutlinedTextField(value = minute, onValueChange = { if(it.length <= 2) minute = it }, placeholder = { Text("MM", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            TextButton(onClick = { amPm = if (amPm == "AM") "PM" else "AM" }, modifier = Modifier.height(44.dp)) {
                                Text(amPm, color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            IconButton(onClick = {
                                TimePickerDialog(context, com.astrohark.app.R.style.DialogPickerTheme, { _, ph, pm ->
                                    val hTyped = if (ph > 12) (ph - 12) else if (ph == 0) 12 else ph
                                    hour = hTyped.toString(); minute = String.format("%02d", pm); amPm = if (ph >= 12) "PM" else "AM"
                                }, 12, 0, false).show()
                            }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.AutoAwesome, "Pick", tint = CosmicAppTheme.colors.accent, modifier = Modifier.size(24.dp))
                            }
                        }

                        Text(Localization.get("pob", isTamil), style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.accent)
                        Box(modifier = Modifier.fillMaxWidth().clickable { launchLocationPicker("me") }) {
                            OutlinedTextField(
                                value = cityName,
                                onValueChange = {},
                                placeholder = { Text(Localization.get("city", isTamil), fontSize = 14.sp) },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = CosmicAppTheme.colors.accent, modifier = Modifier.size(22.dp)) },
                                shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                                colors = textFieldColors
                            )
                        }

                        if (isMatching) {
                            Spacer(Modifier.height(16.dp))
                            Divider(color = CosmicAppTheme.colors.accent.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(Modifier.height(16.dp))
                            
                            Text(
                                text = if (isTamil) "துணை விவரங்கள்" else "Partner Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CosmicAppTheme.colors.accent
                            )

                            OutlinedTextField(
                                value = pName,
                                onValueChange = { pName = it },
                                placeholder = { Text(if (isTamil) "துணையின் பெயர்" else "Partner's Full Name", fontSize = 14.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                                colors = textFieldColors,
                                enabled = true
                            )

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("${Localization.get("gender", isTamil)}:", style = MaterialTheme.typography.bodySmall, color = CosmicAppTheme.colors.textPrimary)
                                RadioButton(selected = pGender == "Male", onClick = { pGender = "Male" }, colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent))
                                Text(Localization.get("male", isTamil), style = MaterialTheme.typography.bodySmall, color = CosmicAppTheme.colors.textSecondary)
                                RadioButton(selected = pGender == "Female", onClick = { pGender = "Female" }, colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent))
                                Text(Localization.get("female", isTamil), style = MaterialTheme.typography.bodySmall, color = CosmicAppTheme.colors.textSecondary)
                            }

                            Text(Localization.get("dob", isTamil), style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.accent)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AstroDimens.XSmall), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = pDay, onValueChange = { if(it.length <= 2) pDay = it }, placeholder = { Text("DD", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                                OutlinedTextField(value = pMonth, onValueChange = { if(it.length <= 2) pMonth = it }, placeholder = { Text("MM", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                                OutlinedTextField(value = pYear, onValueChange = { if(it.length <= 4) pYear = it }, placeholder = { Text("YYYY", fontSize = 12.sp) }, modifier = Modifier.weight(1.3f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            }

                            Text(Localization.get("tob", isTamil), style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.accent)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AstroDimens.XSmall), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = pHour, onValueChange = { if(it.length <= 2) pHour = it }, placeholder = { Text("HH", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                                OutlinedTextField(value = pMinute, onValueChange = { if(it.length <= 2) pMinute = it }, placeholder = { Text("MM", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                                TextButton(onClick = { pAmPm = if (pAmPm == "AM") "PM" else "AM" }) {
                                    Text(pAmPm, color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(Localization.get("pob", isTamil), style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.accent)
                            Box(modifier = Modifier.fillMaxWidth().clickable { launchLocationPicker("partner") }) {
                                OutlinedTextField(
                                    value = pCityName,
                                    onValueChange = {},
                                    placeholder = { Text(Localization.get("city", isTamil), fontSize = 14.sp) },
                                    readOnly = true,
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = CosmicAppTheme.colors.accent, modifier = Modifier.size(22.dp)) },
                                    shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                                    colors = textFieldColors
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { submit() },
                            modifier = Modifier.fillMaxWidth().height(54.dp).shadow(8.dp, RoundedCornerShape(AstroDimens.RadiusMedium)),
                            shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7F00))
                        ) {
                            val btnText = if (isMatching) {
                                if (isTamil) "பொருத்தம் பார்க்க" else "START MATCHING"
                            } else if (isEditMode) {
                                if (isTamil) "விவரங்களை புதுப்பிக்க" else "UPDATE DETAILS"
                            } else {
                                if (isTamil) "ஆலோசனை தொடங்க" else "START CONSULTATION"
                            }
                            Text(btnText, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }

                        Text(
                            text = if (isTamil) "அனைத்து விவரங்களையும் சரிபார்க்கவும்" else "Please verify all details",
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmicAppTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (isWaiting) {
            LaunchedEffect(isWaiting) {
                waitTimeLeft = 30
                while (waitTimeLeft > 0 && isWaiting) {
                    delay(1000)
                    waitTimeLeft--
                }
                if (waitTimeLeft <= 0) {
                    onUnanswered()
                    isWaiting = false
                }
            }

            Dialog(onDismissRequest = { isWaiting = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .border(1.dp, Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))), RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C140E)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { waitTimeLeft / 30f },
                                modifier = Modifier.size(80.dp),
                                color = Color(0xFFFFD700),
                                strokeWidth = 6.dp,
                                trackColor = Color(0xFF332211)
                            )
                            Text(
                                text = waitTimeLeft.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = Localization.get("connecting_title", isTamil),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = Localization.get("connecting_subtitle", isTamil),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = { isWaiting = false },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBD2C2C))
                        ) {
                            Text(Localization.get("cancel_request", isTamil), color = Color.White, fontWeight = FontWeight.Bold)
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
