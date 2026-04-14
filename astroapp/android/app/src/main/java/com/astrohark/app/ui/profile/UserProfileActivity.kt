package com.astrohark.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.astrohark.app.R
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.data.remote.SocketManager
import com.astrohark.app.ui.components.CustomCurvedHeader
import com.astrohark.app.ui.components.CustomTextField
import com.astrohark.app.ui.components.SettingsItem
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AstroDimens
import com.astrohark.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class UserProfileActivity : ComponentActivity() {
    private lateinit var tokenManager: TokenManager

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

        setContent {
            CosmicAppTheme {
                UserProfileScreen(
                    tokenManager = tokenManager,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = tokenManager.getUserSession()

    var selectedTab by remember { mutableStateOf(0) } // 0: Personal Info, 1: Settings

    // Personal Info State
    var name by remember { mutableStateOf(session?.name ?: "") }
    var phone by remember { mutableStateOf(session?.phone ?: "") }
    var email by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var location by remember { mutableStateOf("") }
    var zipcode by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf(session?.image ?: "") }
    var isUploading by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploading = true
            uploadImage(context, it) { success, url ->
                isUploading = false
                if (success && url != null) {
                    imageUrl = url
                    Toast.makeText(context, "Photo Uploaded!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Upload Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CosmicAppTheme.backgroundBrush)) {
        // Standard Top Bar
        CenterAlignedTopAppBar(
            title = { Text("My Profile", color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = CosmicAppTheme.colors.accent)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CosmicAppTheme.colors.bgStart)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Main Content Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AstroDimens.Medium)
                    .padding(bottom = 32.dp),
                color = CosmicAppTheme.colors.cardBg,
                shape = RoundedCornerShape(AstroDimens.RadiusLarge),
                border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Image (Overlapping style)
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .offset(y = (-50).dp)
                            .shadow(8.dp, CircleShape)
                            .background(CosmicAppTheme.colors.bgStart, CircleShape)
                            .clip(CircleShape)
                            .border(2.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.5f), CircleShape)
                            .clickable { pickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUrl.isNotEmpty()) {
                            val fullUrl = if (imageUrl.startsWith("http")) imageUrl else "${Constants.SERVER_URL}${if (imageUrl.startsWith("/")) "" else "/"}$imageUrl"
                            AsyncImage(
                                model = fullUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = R.drawable.ic_person_placeholder),
                                error = painterResource(id = R.drawable.ic_person_placeholder)
                            )
                        } else {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(60.dp), tint = Color.Gray)
                        }

                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFE87A1E))
                        }
                    }

                    Text(
                        text = "Profile",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(y = (-40).dp)
                    )

                    Row(
                        modifier = Modifier.offset(y = (-35).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(16.dp), tint = CosmicAppTheme.colors.accent)
                        Text(
                            text = phone, 
                            style = MaterialTheme.typography.bodySmall, 
                            color = CosmicAppTheme.colors.textSecondary, 
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    // Tab Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-20).dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TabItem("Personal Info", selectedTab == 0) { selectedTab = 0 }
                        TabItem("Settings", selectedTab == 1) { selectedTab = 1 }
                    }

                    Spacer(modifier = Modifier.height(0.dp))

                    if (selectedTab == 0) {
                        // Personal Info Tab
                        Column {
                            CustomTextField("Name", name, { name = it }, "Enter your name")
                            CustomTextField("Phone Number", phone, { phone = it }, "9876543210")
                            CustomTextField("Email ID", email, { email = it }, "Enter your Email Id")
                            CustomTextField("Date of Birth", dob, { dob = it }, "DD/MM/YYYY")

                            // Gender Selection
                            Text("Gender : ", style = MaterialTheme.typography.labelSmall, color = CosmicAppTheme.colors.textSecondary, modifier = Modifier.padding(top = 16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = gender == "Male", onClick = { gender = "Male" }, colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent))
                                Text("Male", style = MaterialTheme.typography.bodyMedium, color = CosmicAppTheme.colors.textPrimary, modifier = Modifier.clickable { gender = "Male" })
                                Spacer(modifier = Modifier.width(16.dp))
                                RadioButton(selected = gender == "Female", onClick = { gender = "Female" }, colors = RadioButtonDefaults.colors(selectedColor = CosmicAppTheme.colors.accent))
                                Text("Female", style = MaterialTheme.typography.bodyMedium, color = CosmicAppTheme.colors.textPrimary, modifier = Modifier.clickable { gender = "Female" })
                            }

                            CustomTextField("Location", location, { location = it }, "City, State, Country")
                            CustomTextField("Zipcode", zipcode, { zipcode = it }, "517501")

                            Spacer(modifier = Modifier.height(24.dp))

                            com.astrohark.app.ui.theme.components.AstroButton(
                                text = "Submit",
                                onClick = {
                                    val updates = JSONObject().apply {
                                        put("name", name)
                                        put("phone", phone)
                                        put("image", imageUrl)
                                        put("email", email)
                                        put("dob", dob)
                                        put("gender", gender)
                                        put("location", location)
                                        put("zipcode", zipcode)
                                    }
                                    SocketManager.updateProfile(updates) { res ->
                                        if (res?.optBoolean("ok") == true) {
                                            val updatedUser = session?.copy(name = name, image = imageUrl, phone = phone)
                                            if (updatedUser != null) tokenManager.saveUserSession(updatedUser)
                                            scope.launch(Dispatchers.Main) {
                                                Toast.makeText(context, "Profile Updated!", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            scope.launch(Dispatchers.Main) {
                                                Toast.makeText(context, "Update Failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // Settings Tab
                        Column {
                            SettingsItem("FAQ") {}
                            SettingsItem("Feedbacks & Support") {
                                context.startActivity(Intent(context, com.astrohark.app.ui.support.FeedbackSupportActivity::class.java))
                            }
                            SettingsItem("Terms & Conditions") {}
                            SettingsItem("Privacy") {}
                            SettingsItem("About Us") {}
                            SettingsItem("Contact US") {}

                            Spacer(modifier = Modifier.height(32.dp))

                            OutlinedButton(
                                onClick = {
                                    val user = tokenManager.getUserSession()
                                    if (user?.role == "astrologer") {
                                        // Set all services to offline before logout
                                        val sessionId = user.userId
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val services = listOf("chat", "audio", "video")
                                                val client = okhttp3.OkHttpClient()
                                                services.forEach { serviceType ->
                                                    val url = "${com.astrohark.app.utils.Constants.SERVER_URL}/api/astrologer/service-toggle"
                                                    val body = okhttp3.FormBody.Builder()
                                                        .add("astrologerId", sessionId)
                                                        .add("serviceType", serviceType)
                                                        .add("status", "false")
                                                        .build()
                                                    val request = okhttp3.Request.Builder().url(url).post(body).build()
                                                    client.newCall(request).execute()
                                                }
                                            } catch (e: Exception) { e.printStackTrace() }
                                            
                                            // Finish logout on Main thread
                                            scope.launch(Dispatchers.Main) {
                                                tokenManager.clearSession()
                                                SocketManager.disconnect()
                                                val intent = Intent(context, com.astrohark.app.ui.auth.LoginActivity::class.java)
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                context.startActivity(intent)
                                            }
                                        }
                                    } else {
                                        tokenManager.clearSession()
                                        SocketManager.disconnect()
                                        val intent = Intent(context, com.astrohark.app.ui.auth.LoginActivity::class.java)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally).height(50.dp).padding(horizontal = 32.dp),
                                shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                            ) {
                                Text("Logout", color = Color.Red, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.PowerSettingsNew, null, tint = Color.Red, modifier = Modifier.padding(start = 8.dp))
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Follow us on", modifier = Modifier.align(Alignment.CenterHorizontally), color = Color.Gray, fontSize = 14.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Placeholders for social icons
                                SocialIcon(0)
                                SocialIcon(0)
                                SocialIcon(0)
                                SocialIcon(0)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("App version 7.0", modifier = Modifier.align(Alignment.CenterHorizontally), color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) CosmicAppTheme.colors.accent.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isSelected) CosmicAppTheme.colors.accent else CosmicAppTheme.colors.textSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SocialIcon(resId: Int) {
    // Show a Material icon as placeholder if resId is 0
    if (resId == 0) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = null,
            modifier = Modifier.size(40.dp).padding(8.dp),
            tint = Color.Gray
        )
    } else {
        Image(
            painter = painterResource(id = resId),
            contentDescription = null,
            modifier = Modifier.size(40.dp).padding(4.dp)
        )
    }
}

private fun uploadImage(context: android.content.Context, uri: Uri, callback: (Boolean, String?) -> Unit) {
    val client = OkHttpClient()
    val file = getFileFromUri(context, uri) ?: return callback(false, null)

    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", file.name, RequestBody.create("image/*".toMediaTypeOrNull(), file))
        .build()

    val request = Request.Builder()
        .url("${Constants.SERVER_URL}/upload")
        .post(requestBody)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: java.io.IOException) = callback(false, null)
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                if (json.optBoolean("ok")) callback(true, json.optString("url")) else callback(false, null)
            } else callback(false, null)
        }
    })
}

private fun getFileFromUri(context: android.content.Context, uri: Uri): File? {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    val file = File(context.cacheDir, "temp_profile_pic.jpg")
    val outputStream = FileOutputStream(file)
    inputStream.copyTo(outputStream)
    outputStream.close()
    inputStream.close()
    return file
}
