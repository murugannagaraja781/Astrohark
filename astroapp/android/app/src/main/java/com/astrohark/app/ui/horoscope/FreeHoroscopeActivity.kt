package com.astrohark.app.ui.horoscope

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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrohark.app.ui.theme.*
import com.google.gson.JsonObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

class FreeHoroscopeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CosmicAppTheme {
                FreeHoroscopeScreen(
                    onBackClick = { finish() },
                    onGenerateChart = { data -> launchChart(data) }
                )
            }
        }
    }

    private fun launchChart(data: BirthData) {
        // Prepare payload as JSON String for VipChartActivity
        val payload = JsonObject().apply {
            addProperty("name", data.name)
            addProperty("day", data.day)
            addProperty("month", data.month)
            addProperty("year", data.year)
            addProperty("hour", data.hour)
            addProperty("minute", data.minute)
            addProperty("gender", data.gender)
            addProperty("country", data.country)
            addProperty("state", data.state)
            addProperty("city", data.city)
            addProperty("timezone", data.timezone)
            addProperty("latitude", data.latitude)
            addProperty("longitude", data.longitude)
        }

        val intent = Intent(this, com.astrohark.app.ui.chart.VipChartActivity::class.java).apply {
            putExtra("birthData", payload.toString())
        }
        startActivity(intent)
        // do not finish() so user can come back
    }
}

data class BirthData(
    val name: String,
    val day: Int,
    val month: Int,
    val year: Int,
    val hour: Int,
    val minute: Int,
    val gender: String,
    val country: String,
    val state: String,
    val city: String,
    val timezone: Double,
    val latitude: Double,
    val longitude: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeHoroscopeScreen(
    onBackClick: () -> Unit,
    onGenerateChart: (BirthData) -> Unit
) {
    val context = LocalContext.current

    // Form State
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
    var countryName by remember { mutableStateOf("") }
    var stateName by remember { mutableStateOf("") }
    var cityName by remember { mutableStateOf("") }
    var timezoneId by remember { mutableStateOf<String?>(null) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var timezone by remember { mutableStateOf<Double?>(null) }

    // Location Picker
    val placeLauncher = rememberLauncherForActivityResult(
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

            cityName = if (cityRes.isNotBlank()) cityRes else fullName
            stateName = stateRes
            countryName = countryRes
            timezoneId = tzId?.takeIf { it.isNotBlank() }
            latitude = latRes.takeIf { it != 0.0 }
            longitude = lonRes.takeIf { it != 0.0 }

             // Compute timezone immediately
            val computed = computeTimezoneOffsetHours(timezoneId, day, month, year, hour, minute)
            if (computed != null) timezone = computed
         }
    }

    val computedTimezone = remember(timezoneId, day, month, year, hour, minute) {
        computeTimezoneOffsetHours(timezoneId, day, month, year, hour, minute)
    }
    val timezoneOffset = computedTimezone ?: timezone
    val timezoneDisplay = timezoneOffset?.let { formatUtcOffset(it) } ?: ""

    val launchLocationPicker = {
        val intent = Intent(context, com.astrohark.app.ui.city.CitySearchActivity::class.java)
        placeLauncher.launch(intent)
    }

    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicAppTheme.backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Free Horoscope",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = CosmicAppTheme.colors.accent
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = CosmicAppTheme.colors.textPrimary
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding() // Ensure keyboard doesn't hide fields
                    .verticalScroll(rememberScrollState())
                    .padding(AstroDimens.Small),
                verticalArrangement = Arrangement.spacedBy(AstroDimens.Small)
            ) {
                // Main Form Card
                com.astrohark.app.ui.theme.components.AstroCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = AstroDimens.Small, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Enter Your Birth Details",
                            style = MaterialTheme.typography.titleMedium,
                            color = CosmicAppTheme.colors.accent,
                            fontWeight = FontWeight.Bold
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
                            disabledPlaceholderColor = Color.White.copy(alpha = 0.5f),
                            focusedLabelColor = CosmicAppTheme.colors.accent,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                            disabledLabelColor = CosmicAppTheme.colors.accent.copy(alpha = 0.5f)
                        )

                        // Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Full Name", fontSize = 14.sp) }, 
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                            colors = textFieldColors
                        )

                        // Gender
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Gender:", style = MaterialTheme.typography.bodySmall, color = CosmicAppTheme.colors.textPrimary)
                            Spacer(Modifier.width(8.dp))
                            RadioButton(
                                selected = gender == "Male",
                                onClick = { gender = "Male" },
                                colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent)
                            )
                            Text("Male", style = MaterialTheme.typography.bodySmall, color = CosmicAppTheme.colors.textSecondary)
                            Spacer(Modifier.width(12.dp))
                            RadioButton(
                                selected = gender == "Female",
                                onClick = { gender = "Female" },
                                colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent)
                            )
                            Text("Female", style = MaterialTheme.typography.bodySmall, color = CosmicAppTheme.colors.textSecondary)
                        }

                        // Date of Birth
                        Text("Date of Birth", style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.accent)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AstroDimens.XSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(value = day, onValueChange = { if (it.length <= 2) day = it }, placeholder = { Text("DD", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            OutlinedTextField(value = month, onValueChange = { if (it.length <= 2) month = it }, placeholder = { Text("MM", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            OutlinedTextField(value = year, onValueChange = { if (it.length <= 4) year = it }, placeholder = { Text("YYYY", fontSize = 12.sp) }, modifier = Modifier.weight(1.3f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            IconButton(
                                onClick = {
                                    val cal = Calendar.getInstance()
                                    DatePickerDialog(context, { _, py, pm, pd ->
                                        year = py.toString(); month = (pm + 1).toString(); day = pd.toString()
                                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Rounded.CalendarToday, "Pick", tint = CosmicAppTheme.colors.accent, modifier = Modifier.size(24.dp))
                            }
                        }

                        // Time of Birth
                        Text("Time of Birth", style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.accent)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AstroDimens.XSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(value = hour, onValueChange = { if (it.length <= 2) hour = it }, placeholder = { Text("HH", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            OutlinedTextField(value = minute, onValueChange = { if (it.length <= 2) minute = it }, placeholder = { Text("MM", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(50.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(AstroDimens.RadiusSmall), colors = textFieldColors)
                            TextButton(onClick = { amPm = if (amPm == "AM") "PM" else "AM" }, modifier = Modifier.height(44.dp)) {
                                Text(amPm, color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            IconButton(
                                onClick = {
                                    TimePickerDialog(context, { _, ph, pm ->
                                        val hTyped = if (ph > 12) (ph - 12) else if (ph == 0) 12 else ph
                                        hour = hTyped.toString(); minute = String.format("%02d", pm); amPm = if (ph >= 12) "PM" else "AM"
                                    }, 12, 0, false).show()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Rounded.AccessTime, "Pick", tint = CosmicAppTheme.colors.accent, modifier = Modifier.size(24.dp))
                            }
                        }

                        // City
                        Box(modifier = Modifier.fillMaxWidth().clickable { launchLocationPicker() }) {
                            OutlinedTextField(
                                value = cityName,
                                onValueChange = {},
                                placeholder = { Text("City of Birth", fontSize = 14.sp) },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = CosmicAppTheme.colors.accent, modifier = Modifier.size(22.dp)) },
                                shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                                colors = textFieldColors
                            )
                        }

                        // Timezone Display (Simplified)
                        if (timezoneDisplay.isNotBlank()) {
                            Text(
                                text = "Timezone: $timezoneDisplay",
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmicAppTheme.colors.textSecondary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Generate Button
                        com.astrohark.app.ui.theme.components.AstroButton(
                            text = "Generate Rasi Chart",
                            onClick = {
                                if (validateInputs(name, day, month, year, hour, minute, cityName, timezoneOffset)) {
                                    isLoading = true
                                    val h = hour.toIntOrNull() ?: 0
                                    val hour24 = if (amPm == "PM" && h < 12) h + 12
                                                else if (amPm == "AM" && h == 12) 0
                                                else h

                                    onGenerateChart(BirthData(
                                        name = name,
                                        day = day.toIntOrNull() ?: 0,
                                        month = month.toIntOrNull() ?: 0,
                                        year = year.toIntOrNull() ?: 0,
                                        hour = hour24,
                                        minute = minute.toIntOrNull() ?: 0,
                                        gender = gender,
                                        country = countryName,
                                        state = stateName,
                                        city = cityName,
                                        timezone = timezoneOffset ?: 5.5,
                                        latitude = latitude ?: 0.0,
                                        longitude = longitude ?: 0.0
                                    ))
                                } else {
                                    Toast.makeText(context, "Please fill all details", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp).shadow(8.dp, RoundedCornerShape(AstroDimens.RadiusMedium)),
                            isLoading = isLoading
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun validateInputs(
    name: String,
    day: String,
    month: String,
    year: String,
    hour: String,
    minute: String,
    city: String,
    timezone: Double?
): Boolean {
    return name.isNotBlank() &&
            day.isNotBlank() &&
            month.isNotBlank() &&
            year.isNotBlank() &&
            hour.isNotBlank() &&
            minute.isNotBlank() &&
            city.isNotBlank() &&
            timezone != null
}


private fun computeTimezoneOffsetHours(
    timezoneId: String?,
    day: String,
    month: String,
    year: String,
    hour: String,
    minute: String
): Double? {
    if (timezoneId.isNullOrBlank()) return null
    val tz = TimeZone.getTimeZone(timezoneId)
    // Basic filter for invalid timezone IDs if necessary

    val dayInt = day.toIntOrNull()
    val monthInt = month.toIntOrNull()
    val yearInt = year.toIntOrNull()
    val hourInt = hour.toIntOrNull() ?: 0
    val minuteInt = minute.toIntOrNull() ?: 0

    val offsetMillis = if (dayInt != null && monthInt != null && yearInt != null) {
        val cal = Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, yearInt)
            set(Calendar.MONTH, (monthInt - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, dayInt.coerceIn(1, 31))
            set(Calendar.HOUR_OF_DAY, hourInt.coerceIn(0, 23))
            set(Calendar.MINUTE, minuteInt.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        tz.getOffset(cal.timeInMillis)
    } else {
        tz.rawOffset
    }

    return offsetMillis / 3600000.0
}

private fun formatUtcOffset(offsetHours: Double): String {
    val totalMinutes = (offsetHours * 60).roundToInt()
    val sign = if (totalMinutes >= 0) "+" else "-"
    val absMinutes = abs(totalMinutes)
    val hours = absMinutes / 60
    val minutes = absMinutes % 60
    return "UTC$sign${"%02d".format(hours)}:${"%02d".format(minutes)}"
}
