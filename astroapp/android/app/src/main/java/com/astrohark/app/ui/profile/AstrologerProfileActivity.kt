package com.astrohark.app.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrohark.app.R
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.theme.AstroDimens
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import org.json.JSONObject

class AstrologerProfileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val astroName = intent.getStringExtra("astro_name") ?: "Astrologer"
        val astroExp = intent.getStringExtra("astro_exp") ?: "5"
        val astroSkills = intent.getStringExtra("astro_skills") ?: "Vedic, Tarot"
        val astroId = intent.getStringExtra("astro_id") ?: ""
        val astroImage = intent.getStringExtra("astro_image") ?: ""
        val astroPrice = intent.getIntExtra("astro_price", 15)
        val isChatOnline = intent.getBooleanExtra("is_chat_online", false)
        val isAudioOnline = intent.getBooleanExtra("is_audio_online", false)
        val isVideoOnline = intent.getBooleanExtra("is_video_online", false)
        val astroOrders = intent.getIntExtra("astro_orders", 1000)
        val astroProfession = intent.getStringExtra("astro_profession") ?: ""
        val astroRating = intent.getFloatExtra("astro_rating", 4.9f)
        val astroLanguages = intent.getStringExtra("astro_languages") ?: "Tamil, English"

        setContent {
            CosmicAppTheme {
                AstrologerProfileScreen(
                    id = astroId,
                    name = astroName,
                    exp = astroExp,
                    skills = astroSkills,
                    image = astroImage,
                    price = astroPrice,
                    orders = astroOrders,
                    profession = astroProfession,
                    rating = astroRating,
                    languages = astroLanguages,
                    isChatOnline = isChatOnline,
                    isAudioOnline = isAudioOnline,
                    isVideoOnline = isVideoOnline,
                    onBack = { finish() },
                    onAction = { type ->
                        val intent = android.content.Intent(this, com.astrohark.app.ui.intake.IntakeActivity::class.java).apply {
                            putExtra("partnerId", astroId)
                            putExtra("partnerName", astroName)
                            putExtra("partnerImage", astroImage)
                            putExtra("type", type)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstrologerProfileScreen(
    id: String,
    name: String,
    exp: String,
    skills: String,
    image: String,
    price: Int,
    orders: Int,
    profession: String,
    rating: Float,
    languages: String,
    isChatOnline: Boolean,
    isAudioOnline: Boolean,
    isVideoOnline: Boolean,
    onBack: () -> Unit,
    onAction: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val peacockTeal = Color(0xFFE87A1E)
    val yellowAccent = Color(0xFFFFD54F)
    val isTamil = java.util.Locale.getDefault().language == "ta"
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("astro_subscriptions", android.content.Context.MODE_PRIVATE)
    var isSubscribed by remember {
        mutableStateOf(sharedPrefs.getStringSet("subscribed_list", emptySet())?.contains(id) == true)
    }

    var reviewsList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(id) {
        val socket = com.astrohark.app.data.remote.SocketManager.getSocket()
        if (socket == null) {
            com.astrohark.app.data.remote.SocketManager.init()
        }
        com.astrohark.app.data.remote.SocketManager.getSocket()?.emit("get-my-reviews", JSONObject().apply {
            put("astrologerId", id)
        }, io.socket.client.Ack { args ->
            try {
                val res = args[0] as JSONObject
                if (res.optBoolean("ok")) {
                    val arr = res.optJSONArray("reviews") ?: org.json.JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    reviewsList = list
                }
            } catch (_: Exception) {}
        })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isTamil) "விவரக்குறிப்பு" else "Profile", color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = CosmicAppTheme.colors.accent)
                    }
                },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        try {
                            val shareMessage = if (isTamil) {
                                "Astrohark செயலியில் உள்ள ஜோதிடர் $name என்பவரின் விவரங்களை உங்களுடன் பகிர்ந்து கொள்கிறேன். இவருடன் பேச அல்லது ஆலோசிக்க செயலியை பதிவிறக்கம் செய்ய: https://play.google.com/store/apps/details?id=com.astrohark.app"
                            } else {
                                "Check out Astrologer $name on Astrohark App! Consult with them here: https://play.google.com/store/apps/details?id=com.astrohark.app"
                            }
                            
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                                type = "text/plain"
                            }
                            
                            val shareIntent = Intent.createChooser(sendIntent, if (isTamil) "வாட்ஸ்அப்பில் பகிரவும்" else "Share via")
                            context.startActivity(shareIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, "Share", tint = CosmicAppTheme.colors.accent)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CosmicAppTheme.colors.bgStart)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .background(CosmicAppTheme.backgroundBrush)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                // Header with Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(CosmicAppTheme.backgroundBrush)
                )

                // Avatar
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .align(Alignment.BottomCenter)
                        .shadow(8.dp, CircleShape)
                ) {
                    val imageUrl = if (image.startsWith("http")) image
                                  else if (image.isNotEmpty()) {
                                      val path = if (image.startsWith("/")) image else "/${image}"
                                      "${com.astrohark.app.utils.Constants.SERVER_URL}$path"
                                  }
                                  else ""
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(CosmicAppTheme.colors.bgStart)
                            .border(3.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.5f), CircleShape),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = R.drawable.ic_person_placeholder),
                        placeholder = painterResource(id = R.drawable.ic_person_placeholder)
                    )
                    // Verified Badge
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .background(CosmicAppTheme.colors.bgStart, CircleShape)
                            .border(2.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.3f), CircleShape)
                            .padding(2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = CosmicAppTheme.colors.textPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top=4.dp)) {
                    Text("★ %.1f".format(rating), color = Color(0xFFFFC107), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "| $orders ${if (isTamil) "ஆர்டர்கள்" else "Orders"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CosmicAppTheme.colors.textSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(AstroDimens.RadiusSmall),
                    color = CosmicAppTheme.colors.accent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        text = "₹$price/min",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = CosmicAppTheme.colors.accent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Stats Section
                Row(
                   modifier = Modifier
                       .fillMaxWidth()
                       .padding(vertical = AstroDimens.Medium)
                       .clip(RoundedCornerShape(AstroDimens.RadiusMedium))
                       .background(CosmicAppTheme.colors.cardBg)
                       .border(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f), RoundedCornerShape(AstroDimens.RadiusMedium))
                       .padding(AstroDimens.Medium),
                   horizontalArrangement = Arrangement.SpaceEvenly,
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(icon = Icons.Default.Chat, value = "49k Mins")
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(CosmicAppTheme.colors.cardStroke.copy(alpha=0.3f)))
                    StatItem(icon = Icons.Default.Call, value = "31k Mins")
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(CosmicAppTheme.colors.cardStroke.copy(alpha=0.3f)))
                    StatItem(icon = Icons.Default.CheckCircle, value = "$exp Years")
                }

                // Actions Button Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isChatOnline) {
                        ActionButton(
                            icon = Icons.Default.Chat,
                            label = if (isTamil) "அரட்டை" else "Chat",
                            color = CosmicAppTheme.colors.accent,
                            isEnabled = true,
                            onClick = { onAction("chat") }
                        )
                    }

                    if (isAudioOnline) {
                        ActionButton(
                            icon = Icons.Default.Call,
                            label = if (isTamil) "அழைப்பு" else "Call",
                            color = CosmicAppTheme.colors.accent,
                            isEnabled = true,
                            onClick = { onAction("audio") }
                        )
                    }

                    if (isVideoOnline) {
                        ActionButton(
                            icon = androidx.compose.material.icons.Icons.Rounded.VideoCall,
                            label = if (isTamil) "நேரலை" else "Video",
                            color = CosmicAppTheme.colors.accent,
                            isEnabled = true,
                            onClick = { onAction("video") }
                        )
                    }
                }

                // About Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicAppTheme.colors.cardBg),
                    shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                    border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(AstroDimens.Medium)) {
                        Text(if (isTamil) "ஜோதிடர் பற்றி" else "About Astrologer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.accent)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (profession.isNotEmpty()) profession
                                   else "$name is highly experienced in $skills. Dedicated to providing accurate guidance and helping clients find clarity in life's complex situations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmicAppTheme.colors.textPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Skills & Expertise Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicAppTheme.colors.cardBg),
                    shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                    border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(AstroDimens.Medium)) {
                        Text(if (isTamil) "நிபுணத்துவம்" else "Expertise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.accent)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            skills.split(",").forEach { skill ->
                                Box(
                                    modifier = Modifier
                                        .background(CosmicAppTheme.colors.accent.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                                        .border(1.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(skill.trim(), color = CosmicAppTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // Languages Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicAppTheme.colors.cardBg),
                    shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                    border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(AstroDimens.Medium)) {
                        Text(if (isTamil) "மொழிகள்" else "Languages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.accent)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            languages.split(",").forEach { lang ->
                                Box(
                                    modifier = Modifier
                                        .background(CosmicAppTheme.colors.accent.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                                        .border(1.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(lang.trim(), color = CosmicAppTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // Subscribe/Follow Button
                val btnText = if (isSubscribed) {
                    if (isTamil) "தொடர்கிறது (Subscribed)" else "Subscribed"
                } else {
                    if (isTamil) "$name-ஐ தொடரவும்" else "Subscribe to $name"
                }

                Button(
                    onClick = {
                        val currentSet = sharedPrefs.getStringSet("subscribed_list", emptySet()) ?: emptySet()
                        val newSet = currentSet.toMutableSet()
                        if (isSubscribed) {
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("astrologer_$id")
                            newSet.remove(id)
                            isSubscribed = false
                            Toast.makeText(context, if (isTamil) "தொடர்வது நிறுத்தப்பட்டது" else "Unsubscribed from $name", Toast.LENGTH_SHORT).show()
                        } else {
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("astrologer_$id")
                            newSet.add(id)
                            isSubscribed = true
                            Toast.makeText(context, if (isTamil) "தொடரப்பட்டது! ஜோதிடர் ஆன்லைனுக்கு வரும்போது உங்களுக்கு அறிவிக்கப்படும்" else "Subscribed! You will be notified when $name goes online.", Toast.LENGTH_SHORT).show()
                        }
                        sharedPrefs.edit().putStringSet("subscribed_list", newSet).apply()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSubscribed) Color.Gray else Color(0xFFE87A1E)),
                    shape = RoundedCornerShape(AstroDimens.RadiusMedium)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSubscribed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = btnText,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }

                // Ratings & Reviews Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicAppTheme.colors.cardBg),
                    shape = RoundedCornerShape(AstroDimens.RadiusMedium),
                    border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(AstroDimens.Medium)) {
                        Text(if (isTamil) "மதிப்பீடுகள் மற்றும் மதிப்புரைகள்" else "Ratings & Reviews", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.accent)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (reviewsList.isEmpty()) {
                            Text(
                                if (isTamil) "மதிப்புரைகள் எதுவும் இல்லை" else "No reviews available yet.",
                                color = CosmicAppTheme.colors.textSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            reviewsList.forEach { review ->
                                val userName = review.optString("userName", "User")
                                val r = review.optInt("rating", 5)
                                val comment = review.optString("comment", "")
                                val reply = review.optString("astrologerReply", "")

                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(userName, fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.textPrimary, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text("★".repeat(r), color = Color(0xFFFFC107), fontSize = 12.sp)
                                    }
                                    Text(comment, color = CosmicAppTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                                    
                                    if (reply.isNotEmpty() && reply != "null") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp, start = 8.dp)
                                                .background(CosmicAppTheme.colors.accent.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = "${if (isTamil) "ஜோதிடரின் பதில்" else "Reply"}: $reply",
                                                color = CosmicAppTheme.colors.accent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Divider(modifier = Modifier.padding(top = 8.dp), color = CosmicAppTheme.colors.cardStroke.copy(alpha = 0.1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = CosmicAppTheme.colors.accent, modifier = Modifier.size(24.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.textPrimary, modifier = Modifier.padding(top=4.dp))
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, color: Color, isEnabled: Boolean, onClick: () -> Unit) {
    val finalColor = if (isEnabled) color else Color.Gray
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = isEnabled,
            modifier = Modifier
                .size(56.dp)
                .background(finalColor.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, finalColor.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = finalColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = finalColor)
    }
}

