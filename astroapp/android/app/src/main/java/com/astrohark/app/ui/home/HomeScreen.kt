package com.astrohark.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.animation.core.*
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import com.astrohark.app.utils.Localization
import com.astrohark.app.data.model.Astrologer
import com.astrohark.app.data.model.AuthResponse
import com.astrohark.app.data.model.Banner
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.astrohark.app.R
import com.astrohark.app.ui.theme.*
import com.astrohark.app.ui.theme.components.*
import coil.compose.AsyncImage
import com.astrohark.app.data.api.ApiClient
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.data.model.Ritual
import com.astrohark.app.data.model.GridService


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerSection(
    banners: List<com.astrohark.app.data.model.Banner>,
    onBannerClick: (com.astrohark.app.data.model.Banner) -> Unit,
    onReferClick: () -> Unit,
    onShareClick: () -> Unit
) {
    // Total slides = 1 (Referral) + Dynamic Banners
    val totalSlides = banners.size + 1
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { totalSlides })

    // Auto-scroll logic
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000) // 5 seconds
            if (totalSlides > 1) {
                val nextPage = (pagerState.currentPage + 1) % totalSlides
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }


    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 8.dp) // Consistent with AstrologerCard vertical spacing (8dp top + 8dp bottom)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 14.dp), // Matched with AstrologerCard horizontal padding
            pageSpacing = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp) // Reduced by 25% (from 150dp to 112dp)
        ) { page ->
             val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
             val scale by animateFloatAsState(targetValue = if (pageOffset == 0f) 1f else 0.9f, label = "scale")
             val alpha by animateFloatAsState(targetValue = if (pageOffset == 0f) 1f else 0.6f, label = "alpha")

             if (page == 0) {
                 // --- SLIDE 1: REFERRAL POSTER ---
                 Card(
                     shape = RoundedCornerShape(AstroDimens.RadiusLarge),
                     colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F1)),
                     border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCCCC)),
                     elevation = CardDefaults.cardElevation(defaultElevation = AstroDimens.ElevationLarge),
                     modifier = Modifier
                         .graphicsLayer {
                             scaleX = scale
                             scaleY = scale
                             this.alpha = alpha
                         }
                         .fillMaxSize()
                         .clickable { onReferClick() }
                 ) {
                     Box(modifier = Modifier.fillMaxSize()) {
                         // Content Side
                         Column(
                             modifier = Modifier
                                 .fillMaxHeight()
                                 .fillMaxWidth(0.65f)
                                 .padding(AstroDimens.Large),
                             verticalArrangement = Arrangement.Center
                         ) {
                             Text(
                                 text = "Refer Your Friend & Earn Upto ₹5000",
                                 style = MaterialTheme.typography.titleSmall, // Reduced size
                                 fontWeight = FontWeight.Bold,
                                 color = Color.Black
                             )
                             Spacer(modifier = Modifier.height(6.dp)) // Reduced spacer
                             Surface(
                                 shape = RoundedCornerShape(50),
                                 color = Color(0xFFFF5252),
                                 modifier = Modifier.height(20.dp) // Reduced height
                             ) {
                                 Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                     Text("REFER & EARN", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black) // Reduced text size
                                 }
                             }
                             Spacer(modifier = Modifier.height(4.dp)) // Reduced spacer

                         }

                         // Share Icon (Top-right)
                         Box(
                             modifier = Modifier
                                 .align(Alignment.TopEnd)
                                 .padding(6.dp)
                                 .size(30.dp)
                                 .clip(CircleShape)
                                 .background(Color.White.copy(alpha = 0.5f))
                                 .clickable { onShareClick() },
                             contentAlignment = Alignment.Center
                         ) {
                             Icon(
                                 imageVector = androidx.compose.material.icons.Icons.Rounded.Share,
                                 contentDescription = "Share",
                                 tint = Color(0xFFFF5252),
                                 modifier = Modifier.size(16.dp)
                             )
                         }

                         // Decoration
                         Image(
                             painter = painterResource(id = com.astrohark.app.R.mipmap.ic_launcher_foreground),
                             contentDescription = null,
                             modifier = Modifier.align(Alignment.CenterEnd).size(82.dp).padding(end = 10.dp), // Reduced size by 25% (from 110dp to 82dp)
                             alpha = 0.8f
                         )
                     }
                 }
             } else {
                 // --- REST OF SLIDES: DYNAMIC BANNERS ---
                 val banner = banners[page - 1]

                 Card(
                     shape = RoundedCornerShape(AstroDimens.RadiusLarge),
                     elevation = CardDefaults.cardElevation(defaultElevation = AstroDimens.ElevationLarge),
                     border = androidx.compose.foundation.BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f)),
                     modifier = Modifier
                         .graphicsLayer {
                             scaleX = scale
                             scaleY = scale
                             this.alpha = alpha
                         }
                         .fillMaxSize()
                         .clickable { onBannerClick(banner) }
                 ) {
                     Box(modifier = Modifier.fillMaxSize()) {
                         // 1. Dynamic Background Image
                         val imageUrl = getImageUrl(banner.imageUrl)
                         AsyncImage(
                             model = imageUrl,
                             contentDescription = banner.title,
                             contentScale = ContentScale.Crop,
                             modifier = Modifier.fillMaxSize()
                         )

                         // 2. Gradient Overlay for Readability
                         Box(
                             modifier = Modifier
                                 .fillMaxSize()
                                 .background(
                                     Brush.horizontalGradient(
                                         listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                     )
                                 )
                         )

                         // 3. Content Text
                         Column(
                             modifier = Modifier
                                 .align(Alignment.CenterStart)
                                 .padding(AstroDimens.Medium)
                                 .fillMaxWidth(0.65f)
                         ) {
                             if (!banner.title.isNullOrEmpty()) {
                                 Text(
                                     text = banner.title,
                                     style = MaterialTheme.typography.titleSmall, // Reduced size
                                     color = Color.White
                                 )
                                 Spacer(modifier = Modifier.height(AstroDimens.XSmall))
                             }

                             if (!banner.subtitle.isNullOrEmpty()) {
                                 Text(
                                     text = banner.subtitle,
                                     style = MaterialTheme.typography.bodySmall,
                                     color = Color.White.copy(alpha=0.85f),
                                     maxLines = 1,
                                     overflow = TextOverflow.Ellipsis
                                 )
                                 Spacer(modifier = Modifier.height(AstroDimens.Small)) // Reduced spacer
                             }

                             // CTA Pill (Fixed Contrast & Overflow)
                             if (!banner.ctaText.isNullOrEmpty()) {
                                  val hPadding = when(banner.ctaButtonSize) {
                                      "medium" -> 16.dp
                                      "large" -> 20.dp
                                      else -> 10.dp
                                  }
                                  val vPadding = when(banner.ctaButtonSize) {
                                      "medium" -> 6.dp
                                      "large" -> 8.dp
                                      else -> 2.dp
                                  }
                                  val fontSize = when(banner.ctaButtonSize) {
                                      "medium" -> 11.sp
                                      "large" -> 13.sp
                                      else -> 9.sp
                                  }

                                  Surface(
                                      shape = RoundedCornerShape(50),
                                      color = (if (banner.offerPercentage > 0) Color(0xFFFFD700) else CosmicAppTheme.colors.accent),
                                      modifier = Modifier.padding(vertical = 2.dp)
                                  ) {
                                      Text(
                                          text = banner.ctaText,
                                          modifier = Modifier.padding(horizontal = hPadding, vertical = vPadding),
                                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = fontSize),
                                          color = Color.Black,
                                          maxLines = 1,
                                          overflow = TextOverflow.Ellipsis
                                      )
                                  }
                             }
                             Spacer(modifier = Modifier.height(AstroDimens.Small))

                         }
                     }
                 }
             }
         }


        Spacer(modifier = Modifier.height(AstroDimens.XSmall)) // Reduced from Medium to XSmall (4dp)

        // Indicators
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(totalSlides) { iteration ->
                val color = if (pagerState.currentPage == iteration) CosmicAppTheme.colors.accent else CosmicAppTheme.colors.accent.copy(alpha = 0.2f)
                val width by animateDpAsState(targetValue = if (pagerState.currentPage == iteration) 18.dp else 6.dp, label = "dotWidth")


                Box(
                    modifier = Modifier
                        .padding(AstroDimens.XSmall)
                        .height(6.dp)
                        .width(width)
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
        }
    }
}

// Global Image URL Helper
fun getImageUrl(path: String?): String {
    if (path.isNullOrEmpty()) return ""
    return if (path.startsWith("http")) path
    else "${com.astrohark.app.utils.Constants.SERVER_URL}${if (path.startsWith("/")) "" else "/"}$path"
}



// Data class wrapper for Rasi to be used in Compose
data class ComposeRasiItem(val id: Int, val name: String, val iconRes: Int, val color: Color)

// Local color definitions removed to use Theme aliases (White)

// Helper for Premium Sacred Cards
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicAppTheme.colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorResource(id = com.astrohark.app.R.color.surface_border)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Using custom shadow wrapper if possible, or high elevation
        modifier = modifier
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = Color.Black.copy(alpha = 0.25f),
                ambientColor = Color.Black.copy(alpha = 0.15f)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}





@Composable
fun HomeScreen(
    walletBalance: Double,
    superWalletBalance: Double = 0.0,
    horoscope: String,
    astrologers: List<Astrologer>,
    isLoading: Boolean,
    banners: List<com.astrohark.app.data.model.Banner>,
    rituals: List<Ritual> = emptyList(),
    services: List<GridService> = emptyList(),
    onBannerClick: (com.astrohark.app.data.model.Banner) -> Unit,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    onRasiClick: (ComposeRasiItem) -> Unit,
    onLogoutClick: () -> Unit,
    onDrawerItemClick: (String) -> Unit = {},
    onServiceClick: (String) -> Unit = {},
    onWalletClick: () -> Unit,
    isGuest: Boolean = false,
    referralCode: String? = null,
    isNewUser: Boolean = false,
    onApplyReferral: (String) -> Unit = {}
) {

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(1) }
    var selectedFilter by remember { mutableStateOf("Chat") }
    var searchQuery by remember { mutableStateOf("") }
    var showReferralDialog by remember { mutableStateOf(false) }
    var referralInput by remember { mutableStateOf("") }
    var isApplyingReferral by remember { mutableStateOf(false) }
    var selectedLiveAstro by remember { mutableStateOf<Astrologer?>(null) }
    var activeServiceView by remember { mutableStateOf<String?>("chat") }

    // Dynamic Share Link State (Placeholder until configured in Admin Dashboard)
    var shareLink by remember { mutableStateOf("https://astrohark.com") }

    // History State
    var historySessions by remember { mutableStateOf<List<SessionHistoryItem>>(emptyList()) }
    var isHistoryLoading by remember { mutableStateOf(false) }

    val tokenManager = remember { TokenManager(context) }
    val userSession by remember { mutableStateOf(tokenManager.getUserSession()) }

    // Fetch App Config (Share Link, Banner Toggle, BG Color)
    var showBanner by remember { mutableStateOf(true) }
    var appBackgroundColor by remember { mutableStateOf(Color(0xFFFEF9F3)) }

    LaunchedEffect(Unit) {
        try {
            val response = ApiClient.api.getAppConfig()
            if (response.isSuccessful) {
                val json = response.body()
                if (json != null && json.has("ok") && json.get("ok").asBoolean) {
                    val config = json.getAsJsonObject("config")
                    if (config.has("shareLink")) {
                        shareLink = config.get("shareLink").getAsString()
                    }
                    if (config.has("showBanner")) {
                        showBanner = config.get("showBanner").getAsBoolean()
                    }
                    if (config.has("appBackgroundColor")) {
                        val colorStr = config.get("appBackgroundColor").getAsString()
                        try {
                            appBackgroundColor = Color(android.graphics.Color.parseColor(colorStr))
                        } catch (e: Exception) {
                            appBackgroundColor = Color(0xFFFEF9F3)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fetch History when tab 5 is selected
    LaunchedEffect(selectedTab) {
        if (selectedTab == 5 && !isGuest) {
            val userId = userSession?.userId ?: return@LaunchedEffect
            val myRole = userSession?.role ?: "client"
            isHistoryLoading = true

            try {
                val response = ApiClient.api.getPaymentHistory(userId)
                if (response.isSuccessful) {
                    val json = response.body()
                    if (json != null && json.has("ok") && json.get("ok").asBoolean) {
                         val array = json.getAsJsonArray("data")
                         val list = mutableListOf<SessionHistoryItem>()

                         for (i in 0 until array.size()) {
                             val obj = array.get(i).asJsonObject
                             val isAstro = myRole == "astrologer"
                             list.add(
                                 SessionHistoryItem(
                                     id = if (obj.has("_id")) obj.get("_id").asString else "unknown",
                                     partnerName = if (isAstro) {
                                         if (obj.has("userName")) obj.get("userName").asString else "Unknown"
                                     } else {
                                         if (obj.has("astrologerName")) obj.get("astrologerName").asString else "Unknown"
                                     },
                                     type = if (obj.has("type")) obj.get("type").asString else "call",
                                     startTime = if (obj.has("createdAt")) {
                                         try {
                                             // Expecting ISO string or long, but server usually gives ISO for createdAt
                                             // For now, let's just parse it if it's a long or 0
                                              if (obj.get("createdAt").isJsonPrimitive && obj.get("createdAt").asJsonPrimitive.isNumber)
                                                  obj.get("createdAt").asLong
                                              else 0L
                                         } catch (e: Exception) { 0L }
                                     } else 0L,
                                     endTime = if (obj.has("endTime") && obj.get("endTime").isJsonPrimitive && obj.get("endTime").asJsonPrimitive.isNumber) obj.get("endTime").asLong else 0L,
                                     duration = if (obj.has("duration")) obj.get("duration").asInt else 0,
                                     amount = if (obj.has("amount")) obj.get("amount").asDouble else 0.0,
                                     isEarned = isAstro
                                 )
                             )
                         }
                         historySessions = list
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isHistoryLoading = false
            }
        }
    }


    // Language State (Default English)
    var isTamil by rememberSaveable { mutableStateOf(false) }

    // Logic to filter astrologers based on selection
    val filteredAstros = remember(astrologers, selectedFilter, searchQuery) {
        val baseList = if (selectedFilter == "All") {
            astrologers
        } else {
            astrologers.filter { astro ->
                when (selectedFilter) {
                    "Chat" -> astro.isChatOnline || true // Show all chat-capable astros
                    "Call" -> astro.isAudioOnline || true
                    "Video" -> astro.isVideoOnline || true
                    else -> (astro.skills.any { it.contains(selectedFilter, ignoreCase = true) } ||
                            astro.name.contains(selectedFilter, ignoreCase = true))
                }
            }
        }

        val searchedList = if (searchQuery.isEmpty()) {
            baseList
        } else {
            baseList.filter { astro ->
                astro.name.contains(searchQuery, ignoreCase = true) ||
                (astro.userId != null && astro.userId.contains(searchQuery, ignoreCase = true)) ||
                astro.skills.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }

        // User Request: Show offline astros too.
        // We sort by online status so online ones are always at top.
        searchedList.sortedWith(compareByDescending<com.astrohark.app.data.model.Astrologer> { it.isOnline }
            .thenBy { it.isBusy })
    }


    var showLowBalanceDialog by remember { mutableStateOf(false) }

    if (showLowBalanceDialog) {
        AlertDialog(
            onDismissRequest = { showLowBalanceDialog = false },
            title = { Text(Localization.get("low_balance", isTamil), fontWeight = FontWeight.Bold, color = Color.Red) },
            text = {
                Column {
                    Text(Localization.get("low_balance_msg", isTamil), color = CosmicAppTheme.colors.textPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${Localization.get("wallet_balance", isTamil)}: ₹${walletBalance.toInt()}", fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.accent)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLowBalanceDialog = false
                        onBannerClick(com.astrohark.app.data.model.Banner(id = "", imageUrl = "")) // Open default wallet via banner logic
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                ) {
                    Text("RECHARGE NOW", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLowBalanceDialog = false }) {
                    Text("LATER", color = CosmicAppTheme.colors.textSecondary)
                }
            },
            containerColor = CosmicAppTheme.colors.cardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }


    if (showReferralDialog) {
        AlertDialog(
            onDismissRequest = { showReferralDialog = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReferralDialog = false }) {
                    Text(Localization.get("later", isTamil), color = Color.Gray)
                }
            },
            title = {
                 Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                     Text(Localization.get("refer_win", isTamil), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = CosmicAppTheme.colors.accent)
                     Text(Localization.get("refer_desc", isTamil), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                 }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Rules
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Surface(shape = CircleShape, color = CosmicAppTheme.colors.accent, modifier = Modifier.size(24.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("1", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if(isTamil) "உங்கள் Referral Code-ஐ நண்பர்களுக்கு பகிருங்கள். அவர்கள் இணையும் போது ₹188 பெறுவார்கள்!" else "Share your referral code with friends. They get ₹188 on signup!", fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Surface(shape = CircleShape, color = CosmicAppTheme.colors.accent, modifier = Modifier.size(24.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("2", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if(isTamil) "உங்கள் நண்பர் முதல் ரீசார்ஜ் செய்தவுடன் உங்களுக்கு ₹81 போனஸ் கிடைக்கும்!" else "Get ₹81 bonus when they make their first recharge!", fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // My Code Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CosmicAppTheme.colors.cardBg,
                        border = BorderStroke(1.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().clickable {
                            // Copy to clipboard
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Referral Code", referralCode ?: "")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code Copied!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = referralCode ?: "ASTRO111", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = CosmicAppTheme.colors.accent)
                            Text(Localization.get("copy", isTamil), color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            // Share via WhatsApp
                            val msg = if (isTamil)
                                "Astrohark செயலியில் இணையுங்கள்! நீங்கள் இணைய என் Referral Code: ${referralCode ?: ""} -ஐ பயன்படுத்தினால் ₹188 போனஸ் கிடைக்கும். முதல் ரீசார்ஜ் செய்ய மறந்துவிடாதீர்கள்! $shareLink"
                                else "Join Astrohark! Use my Referral Code: ${referralCode ?: ""} and get ₹188 bonus on signup. Don't forget to make your first recharge! $shareLink"
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(msg)}")
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(Localization.get("whatsapp_share", isTamil), fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    if (isNewUser) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Divider(color = Color.LightGray.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if(isTamil) "உங்களிடம் Referral Code உள்ளதா?" else "Do you have a Referral Code?", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = referralInput,
                                onValueChange = { referralInput = it },
                                placeholder = { Text(Localization.get("referral_code", isTamil), fontSize = 14.sp) },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (referralInput.isNotEmpty()) {
                                        onApplyReferral(referralInput)
                                        showReferralDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                                modifier = Modifier.height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(Localization.get("claim", isTamil))
                            }
                        }
                    }
                }
            },
            containerColor = CosmicAppTheme.colors.cardBg,
            shape = RoundedCornerShape(24.dp)
        )
    }


    fun checkBalanceAndProceed(action: () -> Unit) {
        if (!isGuest && walletBalance < 10) { // Skip check for guest (login handles it)
            showLowBalanceDialog = true
        } else {
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                onItemClick = { item ->
                    scope.launch { drawerState.close() }
                    onDrawerItemClick(item)
                    if (item == "logout") onLogoutClick()
                },
                onClose = { scope.launch { drawerState.close() } },
                session = userSession,
                isTamil = isTamil
            )
        }
    ) {
        Scaffold(
            containerColor = appBackgroundColor,
            topBar = {
                HomeTopBar(
                    balance = walletBalance,
                    superBalance = superWalletBalance,
                    onWalletClick = onWalletClick,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    isGuest = isGuest,
                    isTamil = isTamil,
                    onToggleLanguage = { isTamil = !isTamil },
                    onReferClick = { showReferralDialog = true },
                    userName = userSession?.name ?: if (isTamil) "அன்பர்" else "User",
                    shareLink = shareLink,
                    referralCode = referralCode,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it }
                )

            },
            floatingActionButton = {},
            bottomBar = {
                Column {
                    // STICKY FOOTER: Dual Yellow Buttons (shown on Home and Chat lists)
                    val showFooter = selectedTab == 0 || (selectedTab == 1 && activeServiceView == "chat")
                    if (showFooter) {
                        StickyFooterButtons(
                            isGuest = isGuest,
                            isTamil = isTamil,
                            onTabSelected = { tabIndex, serviceView ->
                                selectedTab = tabIndex
                                activeServiceView = serviceView
                                if (tabIndex == 1) {
                                    selectedFilter = when (serviceView) {
                                        "chat" -> "Chat"
                                        "call" -> "Call"
                                        "video" -> "Video"
                                        else -> "All"
                                    }
                                }
                            },
                            onLoginClick = { onBannerClick(com.astrohark.app.data.model.Banner(id = "", imageUrl = "")) }
                        )
                    }
                    HomeBottomBar(
                        selectedTab = selectedTab,
                        activeServiceView = activeServiceView,
                        isTamil = isTamil,
                        onTabSelected = { tabIndex, serviceView ->
                            selectedTab = tabIndex
                            activeServiceView = serviceView
                            if (tabIndex == 1) {
                                selectedFilter = when (serviceView) {
                                    "chat" -> "Chat"
                                    "call" -> "Call"
                                    "video" -> "Video"
                                    else -> "All"
                                }
                            }
                        }
                    )
                }
            }
    ) { padding ->
            // Profile Action Sheet for Live Astrologers
            if (selectedLiveAstro != null) {
                LiveAstroActionSheet(
                    astro = selectedLiveAstro!!,
                    isTamil = isTamil,
                    onChatClick = { onChatClick(it) },
                    onCallClick = { a, t -> onCallClick(a, t) },
                    onDismiss = { selectedLiveAstro = null }
                )
            }

            Box(modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().background(appBackgroundColor)) {
                // Content Layer
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (selectedTab) {
                        0 -> HomeTab(
                            walletBalance = walletBalance,
                            isTamil = isTamil,
                            filteredAstros = filteredAstros,
                            isLoading = isLoading,
                            banners = banners,
                            rituals = rituals,
                            services = services,
                            onBannerClick = onBannerClick,
                            onWalletClick = onWalletClick,
                            onChatClick = onChatClick,
                            onCallClick = onCallClick,
                            onRasiClick = onRasiClick,
                            showBanner = showBanner,
                            selectedFilter = selectedFilter,
                            onAstroClick = { selectedLiveAstro = it },
                            onViewAllClick = { selectedTab = 1; selectedFilter = "All" },
                            onAction = { action ->
                                if (action == "referral") {
                                    selectedTab = 4
                                } else if (action == "referral_share") {
                                    showReferralDialog = true
                                } else {

                                    if (action == "chat" || action == "call" || action == "video") {
                                        activeServiceView = action
                                        selectedFilter = action.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                                        selectedTab = 1
                                    } else {
                                        selectedFilter = "All"
                                        selectedTab = 1
                                    }
                                }
                            }

                        )
                        1 -> ConsultTab(filteredAstros, { astro -> checkBalanceAndProceed { onChatClick(astro) } }, { astro, type -> checkBalanceAndProceed { onCallClick(astro, type) } }, isTamil, searchQuery, { searchQuery = it }, selectedFilter, activeServiceView, onBack = { activeServiceView = null; selectedFilter = "All"; selectedTab = 0 })
                        2 -> RitualsTab(rituals, isTamil)
                        3 -> ProfileTab(walletBalance, isTamil, onWalletClick, onDrawerItemClick, onLogoutClick)
                        4 -> ReferralTab(referralCode, shareLink, isTamil, isNewUser, onApplyReferral)
                    }

                }
            }
        }
    }
}

@Composable
fun SupportAndPoliciesSection() {
    val context = LocalContext.current
    val baseUrl = "https://astrohark.com" // Update to your actual domain

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(CosmicAppTheme.colors.cardBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .border(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Policies & Support",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = CosmicAppTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PolicyLink("Return Policy", "$baseUrl/return-policy.html", context)
            PolicyLink("Shipping Policy", "$baseUrl/shipping-policy.html", context)
            PolicyLink("Refund Policy", "$baseUrl/refund-cancellation-policy.html", context)
            PolicyLink("Terms & Conditions", "$baseUrl/terms-condition.html", context)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Need Help? info@astrohark.com",
            style = MaterialTheme.typography.labelSmall,
            color = CosmicAppTheme.colors.textSecondary
        )
        Text(
            text = "© 2024 astrohark. All Rights Reserved.",
            style = MaterialTheme.typography.labelSmall,
            color = CosmicAppTheme.colors.textSecondary.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun TrustAndPolicySection(isTamil: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(CosmicAppTheme.colors.cardBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .border(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            TrustItem(
                icon = "🔐",
                title = "Privacy Policy",
                subtitle = if(isTamil) "பயனர் பாதுகாப்பு" else "User Privacy",
                modifier = Modifier.weight(1f)
            )

            Box(modifier = Modifier.width(1.dp).height(50.dp).background(Color.LightGray.copy(alpha = 0.3f)).align(Alignment.CenterVertically))

            TrustItem(
                icon = "💰",
                title = "Refund Policy",
                subtitle = if(isTamil) "பணம் திரும்ப" else "Refund Policy",
                modifier = Modifier.weight(1f)
            )

            Box(modifier = Modifier.width(1.dp).height(50.dp).background(Color.LightGray.copy(alpha = 0.3f)).align(Alignment.CenterVertically))

            TrustItem(
                icon = "🛡️",
                title = "Secure Pay",
                subtitle = if(isTamil) "பாதுகாப்பான முறை" else "Secure Payment",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TrustItem(icon: String, title: String, subtitle: String, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = CosmicAppTheme.colors.accent.copy(alpha = 0.1f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 20.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = CosmicAppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = CosmicAppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun LazyListScope.HomeTab(
    walletBalance: Double,
    isTamil: Boolean,
    filteredAstros: List<Astrologer>,
    isLoading: Boolean,
    banners: List<com.astrohark.app.data.model.Banner>,
    rituals: List<Ritual> = emptyList(),
    services: List<GridService> = emptyList(),
    onBannerClick: (com.astrohark.app.data.model.Banner) -> Unit,
    onWalletClick: () -> Unit,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    onRasiClick: (ComposeRasiItem) -> Unit,
    showBanner: Boolean = true,
    selectedFilter: String = "All",
    onAstroClick: (Astrologer) -> Unit,
    onViewAllClick: () -> Unit,
    onAction: (String) -> Unit
) {
    // 1. Services Section (Top Icons - Horoscope, Match, etc.)
    item {
        TopServicesSection(services, isTamil)
    }

    // 2. Unified Banner Slider (Referral Poster + Dynamic Banners)
    item {
        BannerSection(
            banners = banners,
            onBannerClick = onBannerClick,
            onReferClick = { onAction("referral") },
            onShareClick = { onAction("referral_share") }
        )
    }




    // 3. Quick Action Section (Chat, Call, Video) commented out
    /*
    item {
        QuickActionsSection(isTamil) { action ->
            onAction(action)
        }
    }
    */

    item {
        LiveAstrologersSection(filteredAstros, onAstroClick, onViewAllClick, isTamil)
    }

    item {
        Spacer(modifier = Modifier.height(8.dp))
        if (isLoading) AstrologerShimmerItem()
        else {
            filteredAstros.firstOrNull()?.let { astro ->
                AstrologerCard(astro, { onChatClick(it) }, { a, t -> onCallClick(a, t) }, 0, isTamil, selectedFilter)
            }
        }
    }

    item {
        ZodiacInsightsSection(isTamil, onRasiClick)
    }

    item {
        DailyRitualsSection(rituals, isTamil)
    }

    item {
        CustomerStoriesSection(isTamil)
    }

    item {
        FeedbackSection(isTamil)
    }

    item {
        TrustAndPolicySection(isTamil)
    }

    item {
        SupportAndPoliciesSection()
    }
}

@Composable
fun LiveAstrologersSection(
    astrologers: List<Astrologer>,
    onAstroClick: (Astrologer) -> Unit,
    onViewAllClick: () -> Unit,
    isTamil: Boolean
) {
    val liveAstros = astrologers.filter { it.isOnline }
    if (liveAstros.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isTamil) "நேரடி ஜோதிடர்கள்" else "Live Astrologers",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CosmicAppTheme.colors.textPrimary
            )
            Text(
                text = if (isTamil) "அனைத்தையும் பார்" else "View All",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CosmicAppTheme.colors.accent,
                modifier = Modifier.clickable { onViewAllClick() }
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(liveAstros) { astro ->
                LiveAstroCarouselItem(astro, onClick = { onAstroClick(astro) })
            }
        }
    }
}

@Composable
fun LiveAstroCarouselItem(astro: Astrologer, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(135.dp)
            .height(185.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = getImageUrl(astro.image),
                contentDescription = astro.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = painterResource(id = com.astrohark.app.R.drawable.ic_person_placeholder),
                placeholder = painterResource(id = com.astrohark.app.R.drawable.ic_person_placeholder)
            )

            // Dynamic Live Pulse Overlay (Optional but premium)
            if (astro.isOnline) {
                Surface(
                    color = Color.Red,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(8.dp).align(Alignment.TopEnd)
                ) {
                    Text(
                        "LIVE",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Name Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 300f
                        )
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = astro.name ?: "Astrologer",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveAstroActionSheet(
    astro: Astrologer,
    isTamil: Boolean,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Info Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = getImageUrl(astro.image),
                    contentDescription = astro.name,
                    modifier = Modifier
                        .size(65.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, CosmicAppTheme.colors.accent, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = astro.name ?: "Astrologer",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = Color.Black
                    )
                    Text(
                        text = "${astro.skills.firstOrNull() ?: "Vedic"} • ${astro.experience} ${if(isTamil) "வருடம்" else "Yrs"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Premium Yellow Action Buttons (Screenshot Style)
            LiveActionButton(
                text = if(isTamil) "ஜோதிடருடன் அரட்டை (Chat)" else "Chat with Astrologer",
                icon = Icons.Rounded.Chat,
                onClick = { onChatClick(astro); onDismiss() }
            )
            Spacer(modifier = Modifier.height(14.dp))
            LiveActionButton(
                text = if(isTamil) "ஜோதிடருடன் அழைக்கவும் (Call)" else "Call with Astrologer",
                icon = Icons.Rounded.Call,
                onClick = { onCallClick(astro, "call"); onDismiss() }
            )
            Spacer(modifier = Modifier.height(14.dp))
            LiveActionButton(
                text = if(isTamil) "ஜோதிடருடன் வீடியோ (Video)" else "Video with Astrologer",
                icon = Icons.Rounded.VideoCall,
                onClick = { onCallClick(astro, "video"); onDismiss() }
            )
        }
    }
}

@Composable
fun LiveActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    val premiumGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFE57F), // Light Gold
            Color(0xFFFFD700), // Pure Gold
            Color(0xFFFFB300)  // Deep Amber
        )
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(8.dp, RoundedCornerShape(29.dp), spotColor = Color(0xFFFFD700).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(29.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(premiumGradient),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun FeedbackSection(isTamil: Boolean) {
    var feedbackText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (showSuccessDialog) {
        SuccessDialog(
            isTamil = isTamil,
            onDismiss = { showSuccessDialog = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicAppTheme.colors.cardBg.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, CosmicAppTheme.colors.cardStroke.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isTamil) "கருத்துக்களை பகிரவும் (Share Feedback)" else "Share Feedback",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = CosmicAppTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = feedbackText,
                onValueChange = { feedbackText = it },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                placeholder = { Text(if (isTamil) "உங்கள் கருத்துக்களை இங்கே பதிவிடவும்..." else "Write your feedback here...", fontSize = 12.sp, color = Color.Gray) },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicAppTheme.colors.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CosmicAppTheme.colors.accent,
                    unfocusedBorderColor = CosmicAppTheme.colors.cardStroke,
                    cursorColor = CosmicAppTheme.colors.accent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (feedbackText.trim().isEmpty()) {
                        Toast.makeText(context, "Please enter feedback", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSending = true
                    com.astrohark.app.data.remote.SocketManager.sendFeedback(feedbackText) { success ->
                        scope.launch(Dispatchers.Main) {
                            isSending = false
                            if (success) {
                                feedbackText = ""
                                showSuccessDialog = true
                            } else {
                                Toast.makeText(context, "Failed to send feedback", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CosmicAppTheme.colors.accent),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(if (isTamil) "கருத்தை அனுப்பவும்" else "Send Feedback", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun SuccessDialog(isTamil: Boolean, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicAppTheme.colors.cardBg),
            border = BorderStroke(1.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(CosmicAppTheme.colors.accent.copy(alpha = 0.1f))
                        .border(2.dp, CosmicAppTheme.colors.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = CosmicAppTheme.colors.accent,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isTamil) "நன்றி!" else "Thank You!",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isTamil) "உங்கள் கருத்து வெற்றிகரமாக சமர்ப்பிக்கப்பட்டது." else "Your feedback has been submitted successfully.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CosmicAppTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicAppTheme.colors.accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isTamil) "சரி" else "Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}




fun LazyListScope.ConsultTab(
    astrologers: List<Astrologer>,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    isTamil: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: String = "All",
    activeServiceView: String? = null,
    onBack: () -> Unit = {}
) {
    if (activeServiceView != null) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when(activeServiceView) {
                        "chat" -> if(isTamil) "ஜோதிடருடன் அரட்டை" else "Chat with Astro"
                        "call" -> if(isTamil) "ஜோதிடருடன் அழைப்பு" else "Call with Astro"
                        "video" -> if(isTamil) "வீடியோ அழைப்பு" else "Video Call with Astro"
                        else -> if(isTamil) "ஜோதிடர்கள்" else "Astrologers"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
            }
        }
    }



    items(astrologers) { astro ->
        AstrologerCard(
            astro = astro,
            onChatClick = onChatClick,
            onCallClick = onCallClick,
            selectedTab = 1,
            isTamil = isTamil,
            selectedFilter = selectedFilter,
            activeServiceView = activeServiceView
        )
    }
}

fun LazyListScope.RitualsTab(rituals: List<Ritual>, isTamil: Boolean) {
    if (rituals.isEmpty()) {
        item {
            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                Text(if(isTamil) "சடங்குகள் எதுவும் இல்லை" else "No Rituals Available", color = Color.Gray)
            }
        }
        return
    }

    item {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(if(isTamil) "ஆன்மீக சடங்குகள்" else "Spiritual Rituals", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = CosmicAppTheme.colors.textPrimary)
            Spacer(modifier = Modifier.height(20.dp))
            rituals.forEach { ritual ->
                RitualCard(ritual, isTamil)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

fun LazyListScope.ProfileTab(
    balance: Double,
    isTamil: Boolean,
    onWalletClick: () -> Unit,
    onDrawerItemClick: (String) -> Unit,
    onLogoutClick: () -> Unit
) {
    item {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("My Account", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = CosmicAppTheme.colors.textPrimary)
            Spacer(modifier = Modifier.height(20.dp))
            WalletDashboard(balance, isTamil) { onWalletClick() }
            Spacer(modifier = Modifier.height(24.dp))
            ProfileItem("Personal Profile", Icons.Rounded.Person) { onDrawerItemClick("profile") }
            ProfileItem("Transaction History", Icons.Rounded.AccountBalanceWallet) { onWalletClick() }
            ProfileItem("Help & Support", Icons.Rounded.Help) { onDrawerItemClick("settings") }
            ProfileItem("Logout", Icons.Rounded.Logout) { onLogoutClick() }
        }
    }
}

fun LazyListScope.ReferralTab(
    referralCode: String?,
    shareLink: String,
    isTamil: Boolean,
    isNewUser: Boolean,
    onApplyReferral: (String) -> Unit
) {
    item {
        ReferralScreen(
            referralCode = referralCode,
            baseShareUrl = shareLink,
            isTamil = isTamil,
            isNewUser = isNewUser,
            onApplyReferral = onApplyReferral
        )
    }
}

@Composable
fun PolicyLink(label: String, url: String, context: android.content.Context) {
    Surface(
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF2196F3) // Premium Blue
                )
                Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp), tint = Color(0xFF2196F3))
            }
        }
    }
}

// --- 1. DRAWER ---
@Composable
fun AppDrawer(onItemClick: (String) -> Unit, onClose: () -> Unit, session: AuthResponse?, isTamil: Boolean = false) {
    val context = LocalContext.current
    val colors = CosmicAppTheme.colors
    ModalDrawerSheet(
        drawerContainerColor = colors.cardBg,
        drawerContentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.cardBg)
                .padding(24.dp)
        ) {
            // Close Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Close Drawer",
                        tint = Color.Red // Red Color (User Request)
                    )
                }
            }

            // Profile Section
            AsyncImage(
                model = getImageUrl(session?.image),
                contentDescription = "Profile",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, CosmicAppTheme.colors.accent.copy(alpha=0.5f), CircleShape),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.ic_person_placeholder),
                placeholder = painterResource(id = R.drawable.ic_person_placeholder)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(session?.name ?: "User Profile", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CosmicAppTheme.colors.textPrimary)
            Text(if(isTamil) "சுயவிவரத்தை மாற்ற" else "Edit Profile", style = MaterialTheme.typography.bodySmall, color = CosmicAppTheme.colors.textSecondary)

            Spacer(modifier = Modifier.height(8.dp))

        // Drawer Items
        val items = listOf("home", "profile", "wallet", "join_as_astrologer", "Terms & Conditions", "Privacy Policy", "settings", "logout")
        items.forEach { itemKey ->
            NavigationDrawerItem(
                label = {
                    Text(
                        text = if (itemKey.contains(" ")) itemKey else Localization.get(itemKey, isTamil),
                        color = if(itemKey == "logout") Color.Red else Color.DarkGray,
                        fontWeight = FontWeight.Bold
                    )
                },
                selected = false,
                onClick = {
                    when (itemKey) {
                        "Terms & Conditions" -> {
                            onClose()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://astrohark.com/terms-condition.html")))
                        }
                        "Privacy Policy" -> {
                            onClose()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://astrohark.com/privacy-policy.html")))
                        }
                        else -> onItemClick(itemKey)
                    }
                },
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Version 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
        )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// --- 2. HEADER ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    balance: Double,
    superBalance: Double,
    onWalletClick: () -> Unit,
    onMenuClick: () -> Unit,
    isGuest: Boolean = false,
    isTamil: Boolean = false,
    onToggleLanguage: () -> Unit = {},
    onReferClick: () -> Unit = {},
    userName: String = "User",
    shareLink: String = "",
    referralCode: String? = null,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        // Row 1: Avatar, Greeting, Wallet Pill, Translate, Support
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // User Info (Avatar + "Hi Name")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Avatar with Menu Overlay
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onMenuClick() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFF0F0F0), CircleShape)
                    ) {
                        Image(
                            painter = painterResource(id = com.astrohark.app.R.mipmap.ic_launcher_foreground),
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Text(
                    text = "Hi $userName",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
            }

            // Wallet Pill, Translate, Support Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Wallet Pill (Outlined with black/gray border, white bg, black text, plus icon inside black circle)
                Surface(
                    onClick = onWalletClick,
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color.Black),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "₹${balance.toInt()}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Plus inside black circle
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color.Black, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Translate Icon (A -> அ)
                IconButton(
                    onClick = onToggleLanguage,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = "Language",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Support circular button (yellow background with headset icon)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFFFFD600), CircleShape) // AstroTalk Yellow
                        .clickable {
                            context.startActivity(Intent(context, com.astrohark.app.ui.support.FeedbackSupportActivity::class.java))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SupportAgent,
                        contentDescription = "Support",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Row 2: Capsule Search Bar (directly below, matching search icon on right)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(if (isTamil) "தேடுக..." else "Search...", color = Color.Gray, fontSize = 14.sp) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            },
            shape = RoundedCornerShape(50), // Capsule shape
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFFCCCCCC),
                unfocusedBorderColor = Color(0xFFE0E0E0),
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )
    }
}

@Composable
fun WalletDashboard(balance: Double, isTamil: Boolean, onAddMoneyClick: () -> Unit) {
    val cardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E1E2C),
            Color(0xFF2D2D44)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = CosmicAppTheme.colors.accent.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFF0F0F0)), // Added border
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.background(cardGradient).padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = Localization.get("wallet_balance", false), // Forcing English as requested
                        style = MaterialTheme.typography.labelMedium,
                        color = CosmicAppTheme.colors.accent.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "₹${"%.2f".format(balance)}",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp
                        ),
                        color = Color.White
                    )
                }

                Button(
                    onClick = onAddMoneyClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicAppTheme.colors.accent),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Add Money",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}



@Composable
fun QuickActionsSection(isTamil: Boolean, onAction: (String) -> Unit) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 2.dp)) {
        Text(
            text = Localization.get("quick_actions", isTamil),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
            color = CosmicAppTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionItem("Chat", androidx.compose.material.icons.Icons.Rounded.Chat, Color(0xFF00BFA5), Modifier.weight(1f)) { onAction("chat") }
            QuickActionItem("Call", androidx.compose.material.icons.Icons.Rounded.Call, Color(0xFFE87A1E), Modifier.weight(1f)) { onAction("call") }
            QuickActionItem("Video", androidx.compose.material.icons.Icons.Rounded.VideoCall, Color(0xFFD32F2F), Modifier.weight(1f)) { onAction("video") }
        }
    }
}

@Composable
fun QuickActionItem(title: String, icon: ImageVector, iconColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(86.dp)
            .shadow(
                elevation = 4.dp, // Reduced elevation for cleaner look
                shape = RoundedCornerShape(20.dp),
                spotColor = iconColor.copy(alpha = 0.1f)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0F0F0)) // Consistent border
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val gradient = Brush.verticalGradient(
                colors = listOf(iconColor.copy(alpha = 0.25f), iconColor.copy(alpha = 0.05f))
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(gradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = CosmicAppTheme.colors.textPrimary.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}



@Composable
fun AstrologerCard(
    astro: Astrologer,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    selectedTab: Int,
    isTamil: Boolean = true,
    selectedFilter: String = "All",
    activeServiceView: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val statusColor = when {
        astro.isBusy -> Color(0xFFF44336) // Busy Red
        astro.isOnline -> Color(0xFF4CAF50) // Online Green
        else -> Color.Gray
    }

    val isPandit = astro.name.contains("Pandit", ignoreCase = true) ||
                  astro.skills.any { it.contains("Pandit", ignoreCase = true) }

    val badgeLabel = when {
        astro.rating >= 4.9 && astro.experience >= 10 -> "top_rated"
        astro.experience >= 8 -> "elite"
        astro.rating >= 4.7 && astro.experience >= 5 -> "must_try"
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .shadow(
                    elevation = if (isPandit) 10.dp else 4.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = if (isPandit) Color(0xFFFFD700).copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(
                width = 1.dp,
                color = if (isPandit) Color(0xFFFFD700).copy(alpha = 0.4f) else Color(0xFFF0F0F0)
            ),
            onClick = {
                val intent = Intent(context, com.astrohark.app.ui.profile.AstrologerProfileActivity::class.java).apply {
                    putExtra("astro_id", astro.userId)
                    putExtra("astro_name", astro.name)
                    putExtra("astro_exp", astro.experience.toString())
                    putExtra("astro_skills", astro.skills.joinToString(", "))
                    putExtra("astro_image", astro.image)
                    putExtra("astro_price", astro.price)
                    putExtra("is_chat_online", astro.isChatOnline)
                    putExtra("is_audio_online", astro.isAudioOnline)
                    putExtra("is_video_online", astro.isVideoOnline)
                }
                context.startActivity(intent)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT COLUMN: Circle avatar, rating, review/order count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(86.dp)
                ) {
                    Box(modifier = Modifier.size(72.dp)) {
                        AsyncImage(
                            model = getImageUrl(astro.image),
                            contentDescription = astro.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(
                                    if (isPandit) 2.dp else 1.dp,
                                    if (isPandit) Color(0xFFFFD700) else Color(0xFFF0F0F0),
                                    CircleShape
                                ),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = com.astrohark.app.R.drawable.ic_person_placeholder)
                        )

                        // Status indicator dot
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(12.dp)
                                .background(statusColor, CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Star rating layout
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (astro.rating > 0) astro.rating.toString() else "4.9",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black,
                            fontSize = 11.sp
                        )
                    }

                    // Review/order count
                    Text(
                        text = "${astro.orders} ${Localization.get("orders", isTamil)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // MIDDLE COLUMN: Name, verified, exp, skills, double price comparison
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = astro.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (astro.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Verified",
                                tint = Color(0xFF4CAF50), // Green verified checkmark
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (isPandit) {
                        Text(
                            text = "Pandit • पंडित",
                            color = Color(0xFFD32F2F),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Experience
                    Text(
                        text = "${Localization.get("exp", isTamil)} ${astro.experience} ${Localization.get("years", isTamil)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Skills list (joined by commas)
                    val skillsText = if (astro.skills.isNotEmpty()) astro.skills.joinToString(", ") else "Vedic"
                    Text(
                        text = skillsText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Dual price comparison
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "₹${(astro.price * 1.5).toInt()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                textDecoration = TextDecoration.LineThrough,
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "₹${astro.price.toInt()}/${Localization.get("min", isTamil)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            ),
                            color = Color(0xFFC62828)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // RIGHT COLUMN: Single outlined action button
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.width(90.dp)
                ) {
                    val service = when (activeServiceView) {
                        "chat" -> "chat"
                        "call" -> "call"
                        "video" -> "video"
                        else -> {
                            when {
                                astro.isChatOnline -> "chat"
                                astro.isAudioOnline -> "call"
                                astro.isVideoOnline -> "video"
                                else -> "chat"
                            }
                        }
                    }

                    when (service) {
                        "chat" -> {
                            AstrologerActionButton(
                                text = Localization.get("chat", isTamil),
                                icon = Icons.Rounded.Chat,
                                active = (astro.isChatOnline && !astro.isBusy),
                                borderColor = Color(0xFF2196F3), // Blue border
                                onClick = { onChatClick(astro) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "call" -> {
                            AstrologerActionButton(
                                text = Localization.get("call", isTamil),
                                icon = Icons.Rounded.Call,
                                active = (astro.isAudioOnline && !astro.isBusy),
                                borderColor = Color(0xFF4CAF50), // Green border
                                onClick = { onCallClick(astro, "call") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "video" -> {
                            AstrologerActionButton(
                                text = Localization.get("video", isTamil),
                                icon = Icons.Rounded.VideoCall,
                                active = (astro.isVideoOnline && !astro.isBusy),
                                borderColor = Color(0xFFD32F2F), // Keep Red border
                                onClick = { onCallClick(astro, "video") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Overlapping Badge Chip
        badgeLabel?.let { label ->
            Surface(
                color = Color(0xFFFFF1F1),
                shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                border = BorderStroke(1.dp, Color(0xFFFFD5D5)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 24.dp, y = 0.dp)
            ) {
                Text(
                    text = Localization.get(label, isTamil),
                    color = Color(0xFFD32F2F),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}




// --- 3. RASI ITEM (Fitted BG + Border) ---
@Composable
fun RasiItemView(item: ComposeRasiItem, isTamil: Boolean, onClick: (ComposeRasiItem) -> Unit) {
    val goldColor = Color(0xFFD4AF37)

    // Animation: Gentle Pulse
    val infiniteTransition = rememberInfiniteTransition(label = "RasiPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .padding(8.dp)
            .clickable { onClick(item) }
    ) {
        // Glassmorphism Card
        Surface(
            modifier = Modifier
                .size(85.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, goldColor.copy(alpha = 0.3f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    colorFilter = ColorFilter.tint(goldColor)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = Localization.get(item.name.lowercase(), isTamil),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = goldColor.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// --- 4. ASTROLOGER CARD (Green Border, Animation, Shadow) ---
// Retired in favor of unified card
@Composable
fun ZodiacInsightsSection(isTamil: Boolean, onRasiClick: (ComposeRasiItem) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    val darkNavy = Color(0xFF0A0E21)
    val goldColor = Color(0xFFD4AF37)

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(darkNavy, Color.Black)
                )
            )
            .border(1.dp, goldColor.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        // Premium Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Localization.get("zodiac_insights", isTamil),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = goldColor
            )
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = goldColor.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid with glass cards
        val allRasis = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val visibleRasis = if (isExpanded) allRasis else allRasis.take(9)

        Column {
            visibleRasis.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    row.forEach { rasiId ->
                        val rasiName = getRasiNameById(rasiId)
                        val rasiIcon = getRasiIconById(rasiId)
                        val item = ComposeRasiItem(rasiId, rasiName, rasiIcon, goldColor)
                        RasiItemView(item, isTamil, onRasiClick)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Expand Button with Glow
        TextButton(
            onClick = { isExpanded = !isExpanded },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isExpanded) Localization.get("show_less", isTamil) else Localization.get("expand_chart", isTamil),
                    color = goldColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = goldColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DailyRitualsSection(rituals: List<Ritual>, isTamil: Boolean) {
    if (rituals.isEmpty()) return

    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)) {
        Text(
            text = Localization.get("daily_rituals", isTamil),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = CosmicAppTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))

        rituals.forEachIndexed { index, ritual ->
            RitualCard(ritual, isTamil)
            if (index < rituals.size - 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun HomeBottomBar(
    selectedTab: Int,
    activeServiceView: String?,
    isTamil: Boolean,
    onTabSelected: (Int, String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(Color.White)
    ) {
        // Thin top divider line
        androidx.compose.material3.HorizontalDivider(
            color = Color(0xFFE5E5E5),
            thickness = 1.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isHomeSelected = selectedTab == 0
            BottomNavItem(
                label = if (isTamil) "முகப்பு" else "Home",
                icon = Icons.Rounded.Home,
                isSelected = isHomeSelected,
                modifier = Modifier.weight(1f)
            ) {
                onTabSelected(0, null)
            }

            val isChatSelected = selectedTab == 1 && activeServiceView == "chat"
            BottomNavItem(
                label = if (isTamil) "அரட்டை" else "Chat",
                icon = Icons.Rounded.Chat,
                isSelected = isChatSelected,
                modifier = Modifier.weight(1f)
            ) {
                onTabSelected(1, "chat")
            }

            val isVideoSelected = selectedTab == 1 && activeServiceView == "video"
            BottomNavItem(
                label = if (isTamil) "நேரலை" else "Live",
                icon = Icons.Rounded.VideoCall,
                isSelected = isVideoSelected,
                modifier = Modifier.weight(1f)
            ) {
                onTabSelected(1, "video")
            }

            val isCallSelected = selectedTab == 1 && activeServiceView == "call"
            BottomNavItem(
                label = if (isTamil) "அழைப்பு" else "Call",
                icon = Icons.Rounded.Call,
                isSelected = isCallSelected,
                modifier = Modifier.weight(1f)
            ) {
                onTabSelected(1, "call")
            }

            val isRemediesSelected = selectedTab == 2
            BottomNavItem(
                label = if (isTamil) "பரிகாரம்" else "Remedies",
                icon = Icons.Rounded.SelfImprovement,
                isSelected = isRemediesSelected,
                modifier = Modifier.weight(1f)
            ) {
                onTabSelected(2, null)
            }
        }
    }
}

@Composable
fun BottomNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) Color.Black else Color.Gray

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RitualCard(ritual: Ritual, isTamil: Boolean) {
    val context = LocalContext.current
    val title = if(isTamil && !ritual.titleTamil.isNullOrEmpty()) ritual.titleTamil else ritual.title
    val subtitle = if(isTamil && !ritual.subtitleTamil.isNullOrEmpty()) ritual.subtitleTamil else ritual.subtitle
    val description = if(isTamil && !ritual.descriptionTamil.isNullOrEmpty()) ritual.descriptionTamil else ritual.description

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(context, com.astrohark.app.ui.rituals.RitualDetailActivity::class.java).apply {
                    putExtra("title", title)
                    putExtra("subtitle", subtitle)
                    putExtra("description", description)
                    putExtra("imageUrl", ritual.imageUrl)
                    putExtra("price", ritual.price)
                    putExtra("isTamil", isTamil)
                }
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFF0F0F0))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = getImageUrl(ritual.imageUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.Black
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFFF5F5F5)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF8B4513),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF9F9F9)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = Color.Black, modifier = Modifier.weight(1f))
            Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}

@Composable
fun RasiGridSection(isTamil: Boolean, onClick: (ComposeRasiItem) -> Unit) {
    val rasiItems = listOf(
        ComposeRasiItem(1, "Aries", com.astrohark.app.R.drawable.ic_rasi_aries_premium, AriesRed),
        ComposeRasiItem(2, "Taurus", com.astrohark.app.R.drawable.ic_rasi_taurus_premium_copy, TaurusGreen),
        ComposeRasiItem(3, "Gemini", com.astrohark.app.R.drawable.ic_rasi_gemini_premium_copy, GeminiGreen),
        ComposeRasiItem(4, "Cancer", com.astrohark.app.R.drawable.ic_rasi_cancer_premium_copy, CancerBlue),
        ComposeRasiItem(5, "Leo", com.astrohark.app.R.drawable.ic_rasi_leo_premium, LeoGold),
        ComposeRasiItem(6, "Virgo", com.astrohark.app.R.drawable.ic_rasi_virgo_premium, VirgoOlive),
        ComposeRasiItem(7, "Libra", com.astrohark.app.R.drawable.ic_rasi_libra_premium_copy, LibraPink),
        ComposeRasiItem(8, "Scorpio", com.astrohark.app.R.drawable.ic_rasi_scorpio_premium, ScorpioMaroon),
        ComposeRasiItem(9, "Sagittarius", com.astrohark.app.R.drawable.ic_rasi_sagittarius_premium, SagPurple),
        ComposeRasiItem(10, "Capricorn", com.astrohark.app.R.drawable.ic_rasi_capricorn_premium_copy, CapTeal),
        ComposeRasiItem(11, "Aquarius", com.astrohark.app.R.drawable.ic_rasi_aquarius_premium, AquaBlue),
        ComposeRasiItem(12, "Pisces", com.astrohark.app.R.drawable.ic_rasi_pisces_premium_copy, PiscesIndigo)
    )

    // User Request: "12 rasi contain have one box that box bf use that bg" (Customer Style)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicAppTheme.colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha=0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)) {
            val rows = rasiItems.chunked(4)
            for (rowItems in rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (item in rowItems) {
                        RasiItemView(item, isTamil, onClick)
                    }
                }
            }
        }
    }
}

// Duplicate definitions removed


@Composable
fun InfoRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.DarkGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AstrologerActionButton(
    text: String,
    icon: ImageVector,
    active: Boolean,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (active) 1.0f else 0.4f
    val finalColor = if (active) borderColor else Color.Gray
    val containerColor = Color.White
    val contentColor = finalColor
    val borderStroke = androidx.compose.foundation.BorderStroke(1.dp, finalColor)

    Button(
        onClick = onClick,
        enabled = active,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = Color.Gray.copy(alpha = 0.6f)
        ),
        border = borderStroke,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier.height(32.dp).graphicsLayer(alpha = alpha)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
    }
}



@Composable
fun FilterBar(filters: List<String>, selectedFilter: String, onFilterSelected: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        items(filters) { filter ->
            val isSelected = filter == selectedFilter
            val containerColor = if (isSelected) Color(0xFF4CAF50) else Color.White
            val contentColor = if (isSelected) Color.White else Color.Black
            val borderColor = if (isSelected) Color.Transparent else Color.Gray.copy(alpha = 0.3f)

            Surface(
                onClick = { onFilterSelected(filter) },
                shape = RoundedCornerShape(50),
                color = containerColor,
                contentColor = contentColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                modifier = Modifier.height(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = filter,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun CircularActionButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        contentColor = Color.White,
        modifier = Modifier.size(40.dp),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

// 🌌 COSMIC ANIMATIONS

@Composable
fun StarField() {
    // 🌌 1. BACKGROUND STAR PARTICLE ANIMATION
    val stars = remember { List(40) { Triple(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat()) } }

    val infiniteTransition = rememberInfiniteTransition(label = "StarAnim")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "StarAlpha"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEachIndexed { index, (x, y, starSize) ->
            val phase = (index % 10) / 10f
            val baseAlpha = (animProgress + phase) % 1f
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx() * (starSize + 0.2f),
                center = androidx.compose.ui.geometry.Offset(x * size.width, y * size.height),
                alpha = baseAlpha * 0.4f // Low opacity
            )
        }
    }
}

@Composable
fun TopServicesSection(services: List<GridService>, isTamil: Boolean) {
    val context = LocalContext.current
    val listToUse = if (services.isEmpty()) {
        listOf(
            GridService("free_kundeli", "Free\nHoroscope", "இலவச ஜாதகம்", "kundeli", "FreeKundeli"),
            GridService("daily_horoscope", "Daily\nHoroscope", "தினசரி ராசிபலன்", "horoscope", "Horoscope"),
            GridService("marriage_matching", "Horoscope\nMatch", "திருமணப் பொருத்தம்", "matching", "MatchMaking"),
            GridService("academy", "Astro\nAcademy", "ஜோதிட அகாடமி", "academy", "Academy")
        )
    } else {
        services
    }

    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (listToUse.size > 4) 8.dp else 7.dp,
                end = if (listToUse.size > 4) 8.dp else 7.dp,
                top = 4.dp,
                bottom = 4.dp
            )
            .then(
                if (listToUse.size > 4) Modifier.horizontalScroll(scrollState)
                else Modifier
            ),
        horizontalArrangement = if (listToUse.size > 4) {
            Arrangement.spacedBy(6.dp)
        } else {
            Arrangement.SpaceBetween
        }
    ) {
        listToUse.forEach { service ->
            val displayName = if (isTamil && !service.titleTamil.isNullOrBlank()) {
                service.titleTamil
            } else {
                service.title
            }
            ServiceItem(displayName, service.icon) {
                val actionType = when (service.id) {
                    "free_kundeli" -> "kundali"
                    "marriage_matching" -> "match"
                    "daily_horoscope" -> "rasi"
                    "academy" -> "academy"
                    else -> {
                        val name = service.title
                        when {
                            name.contains("Kundeli", true) || name.contains("Horoscope", true) || name.contains("ஜாதகம்", true) -> {
                                 if (name.contains("Daily", true) || name.contains("தினசரி", true)) "rasi" else "kundali"
                            }
                            name.contains("Marriage", true) || name.contains("Matching", true) || name.contains("பொருத்தம்", true) -> "match"
                            name.contains("Astrology", true) || name.contains("ராசிபலன்", true) || name.contains("ஜோதிடம்", true) -> "rasi"
                            name.contains("Almanac", true) || name.contains("பஞ்சாங்கம்", true) || name.contains("Academy", true) -> "academy"
                            else -> ""
                        }
                    }
                }

                when(actionType) {
                    "kundali" -> {
                        val intent = Intent(context, com.astrohark.app.ui.horoscope.FreeHoroscopeActivity::class.java)
                        context.startActivity(intent)
                    }
                    "match" -> {
                        val intent = Intent(context, com.astrohark.app.ui.intake.IntakeActivity::class.java).apply {
                            putExtra("type", "match")
                            putExtra("isMatching", true)
                        }
                        context.startActivity(intent)
                    }
                    "rasi" -> {
                        val intent = Intent(context, com.astrohark.app.ui.rasipalan.RasipalanActivity::class.java)
                        context.startActivity(intent)
                    }
                    "academy" -> {
                        val intent = Intent(context, com.astrohark.app.ui.academy.AcademyActivity::class.java)
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceItem(name: String, icon: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(78.dp)
            .clickable { onClick() }
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFF0F0F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.size(64.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (icon.startsWith("http") || icon.contains("uploads/")) {
                    AsyncImage(
                        model = getImageUrl(icon),
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        contentScale = ContentScale.Fit,
                        error = painterResource(id = com.astrohark.app.R.drawable.ic_kundali_matching),
                        placeholder = painterResource(id = com.astrohark.app.R.drawable.ic_kundali_matching)
                    )
                } else {
                    val localIconRes = when (icon) {
                        "kundeli" -> com.astrohark.app.R.drawable.ic_kundali_matching
                        "horoscope" -> com.astrohark.app.R.drawable.ic_daily_horoscope_v2
                        "matching" -> com.astrohark.app.R.drawable.ic_match_v2
                        "academy" -> com.astrohark.app.R.drawable.ic_academy_v2
                        else -> com.astrohark.app.R.drawable.ic_kundali_matching
                    }
                    Image(
                        painter = painterResource(id = localIconRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 11.sp
            ),
            color = CosmicAppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CustomerStoriesSection(isTamil: Boolean) {
    val stories = listOf(
        Triple(if(isTamil) "அக்ஷய் சர்மா" else "Akshay Sharma", if(isTamil) "ஷார்ஜா, துபாய்" else "Sharjah, Dubai", if(isTamil) "ஆஷா மேமிடம் ஆலோசித்தேன்..." else "I talked to Asha ma'am on Anytime..."),
        Triple(if(isTamil) "பிரியா சிங்" else "Priya Singh", if(isTamil) "மும்பை, இந்தியா" else "Mumbai, India", if(isTamil) "எனது துல்லியமான கணிப்பு..." else "Very accurate prediction about my..."),
        Triple(if(isTamil) "ராகுல் வர்மா" else "Rahul Verma", if(isTamil) "டெல்லி, இந்தியா" else "Delhi, India", if(isTamil) "எனது திருமணத்தை தீர்க்க உதவியது..." else "Helped me resolve my marriage...")
    )

    Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(
            text = Localization.get("customer_stories", isTamil),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = CosmicAppTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            stories.forEach { (name, loc, review) ->
                CustomerStoryCard(name, loc, review)
            }
        }
    }
}

@Composable
fun CustomerStoryCard(name: String, loc: String, review: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0F0F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.width(260.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Image(
                painter = painterResource(id = com.astrohark.app.R.drawable.ic_person_placeholder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.weight(1f))
                }
                Text(text = loc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = review, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun StickyFooterButtons(
    isGuest: Boolean,
    isTamil: Boolean,
    onTabSelected: (Int, String?) -> Unit,
    onLoginClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Chat with Astrologer Button (Solid Yellow, Black text)
        Button(
            onClick = {
                if (isGuest) onLoginClick() else onTabSelected(1, "chat")
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600)), // AstroTalk Yellow
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Chat, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isTamil) "ஜோதிடருடன் அரட்டை" else "Chat with Astrologer",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
            }
        }

        // Call with Astrologer Button (Solid Yellow, Black text)
        Button(
            onClick = {
                if (isGuest) onLoginClick() else onTabSelected(1, "call")
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600)), // AstroTalk Yellow
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Call, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isTamil) "ஜோதிடருடன் அழைப்பு" else "Call with Astrologer",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
            }
        }
    }
}


@Composable
fun ConsultationHistoryCard(item: SessionHistoryItem) {
    val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
    val startTimeStr = if (item.startTime > 0) dateFormat.format(java.util.Date(item.startTime)) else "N/A"

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (item.type == "chat") Color(0xFF4A90E2).copy(alpha = 0.1f)
                            else Color(0xFFFF8C00).copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.type == "chat") androidx.compose.material.icons.Icons.Rounded.Chat else androidx.compose.material.icons.Icons.Rounded.Call,
                        contentDescription = null,
                        tint = if (item.type == "chat") Color(0xFF4A90E2) else Color(0xFFFF8C00),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.partnerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CosmicAppTheme.colors.textPrimary
                    )
                    Text(text = startTimeStr, fontSize = 11.sp, color = CosmicAppTheme.colors.textSecondary)
                }
                Text(
                    text = "₹${String.format("%.2f", item.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = if (item.isEarned) Color(0xFF4CAF50) else CosmicAppTheme.colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val totalSec = (item.duration / 1000).toLong()
                val mins = totalSec / 60
                val secs = totalSec % 60
                val duraText = if (mins > 0L) "${mins}m ${secs}s" else "${secs}s"

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Schedule, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Duration: $duraText", fontSize = 11.sp, color = Color.Gray)
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (item.isEarned) Color(0xFF4CAF50).copy(alpha = 0.1f) else CosmicAppTheme.colors.cardStroke,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (item.isEarned) "EARNED" else "PAID",
                        fontSize = 9.sp,
                        color = if (item.isEarned) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

data class SessionHistoryItem(
    val id: String,
    val partnerName: String,
    val type: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Int,
    val amount: Double,
    val isEarned: Boolean
)


fun getRasiNameById(id: Int): String {
    return when(id) {
        1 -> "Aries"
        2 -> "Taurus"
        3 -> "Gemini"
        4 -> "Cancer"
        5 -> "Leo"
        6 -> "Virgo"
        7 -> "Libra"
        8 -> "Scorpio"
        9 -> "Sagittarius"
        10 -> "Capricorn"
        11 -> "Aquarius"
        12 -> "Pisces"
        else -> ""
    }
}

fun getRasiIconById(id: Int): Int {
    return when(id) {
        1 -> com.astrohark.app.R.drawable.ic_rasi_aries_premium
        2 -> com.astrohark.app.R.drawable.ic_rasi_taurus_premium_copy
        3 -> com.astrohark.app.R.drawable.ic_rasi_gemini_premium_copy
        4 -> com.astrohark.app.R.drawable.ic_rasi_cancer_premium_copy
        5 -> com.astrohark.app.R.drawable.ic_rasi_leo_premium
        6 -> com.astrohark.app.R.drawable.ic_rasi_virgo_premium
        7 -> com.astrohark.app.R.drawable.ic_rasi_libra_premium_copy
        8 -> com.astrohark.app.R.drawable.ic_rasi_scorpio_premium
        9 -> com.astrohark.app.R.drawable.ic_rasi_sagittarius_premium
        10 -> com.astrohark.app.R.drawable.ic_rasi_capricorn_premium_copy
        11 -> com.astrohark.app.R.drawable.ic_rasi_aquarius_premium
        12 -> com.astrohark.app.R.drawable.ic_rasi_pisces_premium_copy
        else -> com.astrohark.app.R.mipmap.ic_launcher_foreground
    }
}

@Composable
fun ShimmerAnimation(
    modifier: Modifier = Modifier,
    content: @Composable (Brush) -> Unit
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.05f),
        ),
        start = Offset(10f, 10f),
        end = Offset(translateAnim, translateAnim)
    )
    content(brush)
}

@Composable
fun AstrologerShimmerItem() {
    ShimmerAnimation { brush ->
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(220.dp)
                .padding(8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(brush)
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(22.dp))
        )
    }
}
@Composable
fun ReferralScreen(
    referralCode: String?,
    baseShareUrl: String,
    isTamil: Boolean,
    isNewUser: Boolean,
    onApplyReferral: (String) -> Unit
) {
    val context = LocalContext.current
    var referralInput by remember { mutableStateOf("") }
    val shareLink = "$baseShareUrl&referrer=${referralCode ?: ""}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = Localization.get("refer_win", isTamil),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = CosmicAppTheme.colors.accent,
            textAlign = TextAlign.Center
        )
        Text(
            text = Localization.get("refer_desc", isTamil),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        // Referral Steps
        AstroCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ReferStepRow("1", if(isTamil) "உங்கள் Referral Code-ஐ நண்பர்களுக்கு பகிருங்கள். அவர்கள் இணையும் போது ₹188 பெறுவார்கள்!" else "Share your referral code with friends. They get ₹188 on signup!", isTamil)
                ReferStepRow("2", if(isTamil) "உங்கள் நண்பர் முதல் ரீசார்ஜ் செய்தவுடன் உங்களுக்கு ₹81 போனஸ் கிடைக்கும்!" else "Get ₹81 bonus when they make their first recharge!", isTamil)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Your Code Card
        AstroCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if(isTamil) "உங்கள் குறியீடு" else "YOUR CODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .border(1.dp, CosmicAppTheme.colors.accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .clickable {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Referral Code", referralCode ?: "")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code Copied!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = referralCode ?: "REF123",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = CosmicAppTheme.colors.accent
                    )
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = CosmicAppTheme.colors.accent, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                val msg = if (isTamil)
                    "Astrohark செயலியில் இணையுங்கள்! நீங்கள் இணைய என் Referral Code: ${referralCode ?: ""} -ஐ பயன்படுத்தினால் ₹188 போனஸ் கிடைக்கும். $shareLink"
                    else "Join Astrohark! Use my Referral Code: ${referralCode ?: ""} and get ₹188 bonus on signup. $shareLink"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(msg)}")
                }
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
            modifier = Modifier.fillMaxWidth().height(56.dp).shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
            Spacer(modifier = Modifier.width(12.dp))
            Text(Localization.get("whatsapp_share", isTamil), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }

        if (isNewUser) {
            Spacer(modifier = Modifier.height(32.dp))
            Divider(color = Color.Gray.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if(isTamil) "உங்களிடம் Referral Code உள்ளதா?" else "Do you have a Referral Code?",
                style = MaterialTheme.typography.titleSmall,
                color = CosmicAppTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = referralInput,
                    onValueChange = { referralInput = it },
                    placeholder = { Text("Enter Code", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CosmicAppTheme.colors.accent,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedPlaceholderColor = Color.Gray,
                        unfocusedPlaceholderColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (referralInput.isNotEmpty()) {
                            onApplyReferral(referralInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                    modifier = Modifier.height(54.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(Localization.get("claim", isTamil), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ReferStepRow(num: String, text: String, isTamil: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = CosmicAppTheme.colors.accent.copy(alpha = 0.1f),
            modifier = Modifier.size(28.dp).border(1.dp, CosmicAppTheme.colors.accent, CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(num, color = CosmicAppTheme.colors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = CosmicAppTheme.colors.textPrimary)
    }
}
