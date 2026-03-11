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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
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

    // Vibrant Green Theme (Matching IntakeActivity)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepJungle, EmeraldGreen, MagicMint)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Free Horoscope",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Main Form Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Enter Your Birth Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = RoyalMidnightBlue
                        )
                        Text(
                            text = "Fill in the details below to generate your personalized Rasi chart.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoyalMidnightBlue.copy(alpha = 0.7f)
                        )

                        Spacer(Modifier.height(8.dp))

                        // Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PeacockGreen,
                                focusedLabelColor = PeacockGreen,
                                cursorColor = PeacockGreen,
                                focusedTextColor = RoyalMidnightBlue,
                                unfocusedTextColor = RoyalMidnightBlue
                            )
                        )

                        // Gender
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Gender:", fontWeight = FontWeight.SemiBold, color = RoyalMidnightBlue)
                            Spacer(Modifier.width(16.dp))
                            RadioButton(
                                selected = gender == "Male",
                                onClick = { gender = "Male" },
                                colors = RadioButtonDefaults.colors(selectedColor = PeacockGreen)
                            )
                            Text("Male", color = RoyalMidnightBlue)
                            Spacer(Modifier.width(16.dp))
                            RadioButton(
                                selected = gender == "Female",
                                onClick = { gender = "Female" },
                                colors = RadioButtonDefaults.colors(selectedColor = PeacockGreen)
                            )
                            Text("Female", color = RoyalMidnightBlue)
                        }

                        // Date of Birth Split
                        Text("Date of Birth", fontWeight = FontWeight.SemiBold, color = RoyalMidnightBlue)
                        OutlinedTextField(
                            value = if (day.isNotBlank() && month.isNotBlank() && year.isNotBlank()) "$day/$month/$year" else "",
                            onValueChange = {},
                            label = { Text("Select Date") },
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val cal = Calendar.getInstance()
                                    val d = day.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
                                    val m = (month.toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1
                                    val y = year.toIntOrNull() ?: cal.get(Calendar.YEAR)
                                    DatePickerDialog(context, { _, py, pm, pd ->
                                        year = py.toString()
                                        month = (pm + 1).toString()
                                        day = pd.toString()
                                    }, y, m, d).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { Icon(Icons.Default.AutoAwesome, "Pick", tint = PeacockGreen) },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = RoyalMidnightBlue,
                                disabledBorderColor = Color.Gray,
                                disabledLabelColor = RoyalMidnightBlue,
                                disabledContainerColor = Color.Transparent
                            )
                        )

                        // Time of Birth Split
                        Text("Time of Birth", fontWeight = FontWeight.SemiBold, color = RoyalMidnightBlue)
                        OutlinedTextField(
                            value = if (hour.isNotBlank() && minute.isNotBlank()) "$hour:$minute $amPm" else "",
                            onValueChange = {},
                            label = { Text("Select Time") },
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val h = hour.toIntOrNull() ?: 12
                                    val m = minute.toIntOrNull() ?: 0
                                    TimePickerDialog(context, { _, ph, pm ->
                                        hour = if (ph > 12) (ph - 12).toString() else if (ph == 0) "12" else ph.toString()
                                        minute = String.format("%02d", pm)
                                        amPm = if (ph >= 12) "PM" else "AM"
                                    }, h, m, false).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { Icon(Icons.Default.AutoFixHigh, "Pick", tint = PeacockGreen) },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = RoyalMidnightBlue,
                                disabledBorderColor = Color.Gray,
                                disabledLabelColor = RoyalMidnightBlue,
                                disabledContainerColor = Color.Transparent
                            )
                        )

                        // Place of Birth (Auto-Select)
                        Text("Place of Birth", fontWeight = FontWeight.SemiBold, color = RoyalMidnightBlue)

                        // City (Read-only + Picker)
                        OutlinedTextField(
                             value = cityName,
                             onValueChange = {},
                             label = { Text("City") },
                             readOnly = true,
                             enabled = false,
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .clickable { launchLocationPicker() },
                             trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = PeacockGreen) },
                             shape = RoundedCornerShape(12.dp),
                             colors = OutlinedTextFieldDefaults.colors(
                                 disabledTextColor = RoyalMidnightBlue,
                                 disabledBorderColor = Color.Gray,
                                 disabledLabelColor = RoyalMidnightBlue,
                                 disabledContainerColor = Color.Transparent
                             )
                        )

                        // Timezone Display
                        OutlinedTextField(
                            value = timezoneDisplay,
                            onValueChange = {},
                            label = { Text("Timezone") },
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = RoyalMidnightBlue,
                                disabledBorderColor = Color.Gray,
                                disabledLabelColor = RoyalMidnightBlue,
                                disabledContainerColor = Color.Transparent
                            )
                        )


                        Spacer(Modifier.height(16.dp))

                        // Generate Button
                        Button(
                            onClick = {
                                if (validateInputs(name, day, month, year, hour, minute, cityName, timezoneOffset)) {
                                    isLoading = true

                                    val h = hour.toIntOrNull() ?: 0
                                    val hour24 = if (amPm == "PM" && h < 12) h + 12
                                                else if (amPm == "AM" && h == 12) 0
                                                else h

                                    val birthData = BirthData(
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
                                    )
                                    onGenerateChart(birthData)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    spotColor = PeacockGreen
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    "Generate Rasi Chart",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalMidnightBlue
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
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
