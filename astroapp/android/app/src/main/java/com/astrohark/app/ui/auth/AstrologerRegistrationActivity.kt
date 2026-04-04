package com.astrohark.app.ui.auth

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astrohark.app.data.api.ApiClient
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.utils.Localization
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import java.util.*

class AstrologerRegistrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                AstrologerRegistrationScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstrologerRegistrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var isTamil by remember { mutableStateOf(true) }

    // Form State
    var realName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var dob by remember { mutableStateOf("") }
    var tob by remember { mutableStateOf("") }
    var pob by remember { mutableStateOf("") }
    var cell1 by remember { mutableStateOf("") }
    var cell2 by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var aadhar by remember { mutableStateOf("") }
    var pan by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var profession by remember { mutableStateOf("") }
    var bankDetails by remember { mutableStateOf("") }
    var upiName by remember { mutableStateOf("") }
    var upiNumber by remember { mutableStateOf("") }

    val navy = Color(0xFF000B18)
    val royalBlue = Color(0xFF001F3F)
    val cyan = Color(0xFF7FDBFF)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = Localization.get("join_as_astrologer", isTamil), 
                        style = MaterialTheme.typography.titleLarge,
                        color = CosmicAppTheme.colors.accent, 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = CosmicAppTheme.colors.accent)
                    }
                },
                actions = {
                    TextButton(onClick = { isTamil = !isTamil }) {
                        Text(
                            text = if (isTamil) "English" else "தமிழ்", 
                            color = CosmicAppTheme.colors.accent, 
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CosmicAppTheme.colors.bgStart)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CosmicAppTheme.backgroundBrush)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AstroDimens.Medium),
                verticalArrangement = Arrangement.spacedBy(AstroDimens.Medium),
                contentPadding = PaddingValues(bottom = 32.dp, top = 16.dp)
            ) {
                item { SectionTitle(Localization.get("basic_info", isTamil), CosmicAppTheme.colors.accent) }
                item { CustomTextField(value = realName, onValueChange = { realName = it }, label = Localization.get("real_name", isTamil)) }
                item { CustomTextField(value = displayName, onValueChange = { displayName = it }, label = Localization.get("display_name", isTamil)) }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${Localization.get("gender", isTamil)}:", color = CosmicAppTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(AstroDimens.Medium))
                        RadioButton(selected = gender == "Male", onClick = { gender = "Male" }, colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent, unselectedColor = CosmicAppTheme.colors.textSecondary))
                        Text(Localization.get("male", isTamil), color = CosmicAppTheme.colors.textPrimary)
                        Spacer(Modifier.width(AstroDimens.Medium))
                        RadioButton(selected = gender == "Female", onClick = { gender = "Female" }, colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent, unselectedColor = CosmicAppTheme.colors.textSecondary))
                        Text(Localization.get("female", isTamil), color = CosmicAppTheme.colors.textPrimary)
                    }
                }

                item { SectionTitle(Localization.get("birth_details", isTamil), CosmicAppTheme.colors.accent) }
                item {
                    val cal = Calendar.getInstance()
                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            dob = "$dayOfMonth/${month + 1}/$year"
                        },
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                    )
                    ReadOnlyTextField(
                        value = dob,
                        onClick = { datePickerDialog.show() },
                        label = Localization.get("dob", isTamil)
                    )
                }
                item {
                    val timePickerDialog = android.app.TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            tob = String.format("%02d:%02d", hourOfDay, minute)
                        },
                        12, 0, false
                    )
                    ReadOnlyTextField(
                        value = tob,
                        onClick = { timePickerDialog.show() },
                        label = Localization.get("tob", isTamil)
                    )
                }
                item { CustomTextField(value = pob, onValueChange = { pob = it }, label = Localization.get("pob", isTamil)) }

                item { SectionTitle(Localization.get("contact_details", isTamil), CosmicAppTheme.colors.accent) }
                item { CustomTextField(value = cell1, onValueChange = { cell1 = it }, label = "${Localization.get("phone_number", isTamil)} 1 *", keyboardType = KeyboardType.Phone) }
                item { CustomTextField(value = cell2, onValueChange = { cell2 = it }, label = "${Localization.get("phone_number", isTamil)} 2", keyboardType = KeyboardType.Phone) }
                item { CustomTextField(value = whatsapp, onValueChange = { whatsapp = it }, label = Localization.get("whatsapp_number", isTamil), keyboardType = KeyboardType.Phone) }
                item { CustomTextField(value = email, onValueChange = { email = it }, label = Localization.get("email_address", isTamil), keyboardType = KeyboardType.Email) }
                item { CustomTextField(value = address, onValueChange = { address = it }, label = Localization.get("full_address", isTamil), singleLine = false) }

                item { SectionTitle(Localization.get("professional_details", isTamil), CosmicAppTheme.colors.accent) }
                item { CustomTextField(value = aadhar, onValueChange = { aadhar = it }, label = Localization.get("aadhar_number", isTamil)) }
                item { CustomTextField(value = pan, onValueChange = { pan = it }, label = Localization.get("pan_number", isTamil)) }
                item { CustomTextField(value = experience, onValueChange = { experience = it }, label = Localization.get("experience_years", isTamil), keyboardType = KeyboardType.Number) }
                item { CustomTextField(value = profession, onValueChange = { profession = it }, label = Localization.get("occupation", isTamil)) }

                item { SectionTitle(Localization.get("payment_details", isTamil), CosmicAppTheme.colors.accent) }
                item { CustomTextField(value = bankDetails, onValueChange = { bankDetails = it }, label = Localization.get("bank_details", isTamil), singleLine = false) }
                item { CustomTextField(value = upiName, onValueChange = { upiName = it }, label = "UPI Name") }
                item { CustomTextField(value = upiNumber, onValueChange = { upiNumber = it }, label = Localization.get("upi_id", isTamil)) }

                item {
                    com.astrohark.app.ui.theme.components.AstroButton(
                        text = Localization.get("register_now", isTamil),
                        onClick = {
                            if (realName.isBlank() || cell1.isBlank()) {
                                Toast.makeText(context, Localization.get("please_fill_required", isTamil), Toast.LENGTH_SHORT).show()
                                return@AstroButton
                            }
                            isLoading = true

                            val data = JsonObject().apply {
                                addProperty("realName", realName)
                                addProperty("displayName", displayName)
                                addProperty("gender", gender)
                                addProperty("dob", dob)
                                addProperty("tob", tob)
                                addProperty("pob", pob)
                                addProperty("phone1", cell1)
                                addProperty("phone2", cell2)
                                addProperty("whatsapp", whatsapp)
                                addProperty("email", email)
                                addProperty("address", address)
                                addProperty("aadhar", aadhar)
                                addProperty("pan", pan)
                                addProperty("experience", experience)
                                addProperty("profession", profession)
                                addProperty("bankDetails", bankDetails)
                                addProperty("upiName", upiName)
                                addProperty("upiId", upiNumber)
                                addProperty("role", "astrologer")
                            }

                            scope.launch {
                                try {
                                    val response = ApiClient.api.registerAstrologer(data)
                                    if (response.isSuccessful) {
                                        Toast.makeText(context, if(isTamil) "விண்ணப்பம் வெற்றிகரமாகச் சமர்ப்பிக்கப்பட்டது!" else "Application submitted successfully!", Toast.LENGTH_LONG).show()
                                        onBack()
                                    } else {
                                        Toast.makeText(context, "Failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, color: Color) {
    Text(
        text = title,
        color = color,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadOnlyTextField(
    value: String,
    onClick: () -> Unit,
    label: String
) {
    Box(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label, color = CosmicAppTheme.colors.textSecondary) },
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            readOnly = true,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = CosmicAppTheme.colors.textPrimary,
                disabledBorderColor = CosmicAppTheme.colors.cardStroke.copy(alpha = 0.5f),
                disabledLabelColor = CosmicAppTheme.colors.textSecondary,
                disabledContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(AstroDimens.RadiusMedium)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = CosmicAppTheme.colors.textSecondary) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CosmicAppTheme.colors.accent,
            unfocusedBorderColor = CosmicAppTheme.colors.cardStroke.copy(alpha = 0.5f),
            focusedLabelColor = CosmicAppTheme.colors.accent,
            cursorColor = CosmicAppTheme.colors.accent,
            focusedTextColor = CosmicAppTheme.colors.textPrimary,
            unfocusedTextColor = CosmicAppTheme.colors.textPrimary
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        shape = RoundedCornerShape(AstroDimens.RadiusMedium)
    )
}
