package com.astrohark.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.astrohark.app.R
import com.astrohark.app.data.local.TokenManager
import com.astrohark.app.data.model.Astrologer
import com.astrohark.app.data.remote.SocketManager

import com.astrohark.app.ui.wallet.WalletActivity
import com.astrohark.app.utils.showErrorAlert
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import com.astrohark.app.data.model.Banner
import com.astrohark.app.data.api.ApiClient
import com.astrohark.app.data.model.BannerResponse


import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit


import com.astrohark.app.ui.dashboard.RasiDetailDialog

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HomeActivity"
        private const val SERVER_URL = "https://astrohark.com"
    }

    private lateinit var tokenManager: TokenManager

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // State Holders
    private val _walletBalance = MutableStateFlow(0.0)
    private val _superWalletBalance = MutableStateFlow(0.0)
    private val _horoscope = MutableStateFlow("Loading Horoscope...")
    private val _astrologers = MutableStateFlow<List<Astrologer>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _referralCode = MutableStateFlow<String?>(null)
    private val _isNewUser = MutableStateFlow(false)
    private val _banners = MutableStateFlow<List<Banner>>(emptyList())


    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Legacy ThemeManager removed.

        tokenManager = TokenManager(this)

        setContent {
            // Retrieve Page Overrides
            val context = androidx.compose.ui.platform.LocalContext.current
            val pageName = "HomeActivity"

            // Default Colors (if not set, returns 0/Transparent/Default)
            val customBg = com.astrohark.app.utils.PageThemeManager.getPageColor(context, pageName, com.astrohark.app.utils.PageThemeManager.ATTR_BG, 0)
            val customCard = com.astrohark.app.utils.PageThemeManager.getPageColor(context, pageName, com.astrohark.app.utils.PageThemeManager.ATTR_CARD, 0)
            val customFont = com.astrohark.app.utils.PageThemeManager.getPageColor(context, pageName, com.astrohark.app.utils.PageThemeManager.ATTR_FONT, 0)
            val customBtn = com.astrohark.app.utils.PageThemeManager.getPageColor(context, pageName, com.astrohark.app.utils.PageThemeManager.ATTR_BUTTON, 0)

            // Dynamic Cosmic Theme
            com.astrohark.app.ui.theme.CosmicAppTheme {
                val balance by _walletBalance.collectAsState()
                val superBalance by _superWalletBalance.collectAsState()
                val horoscope by _horoscope.collectAsState()
                val astrologers by _astrologers.collectAsState()
                val isLoading by _isLoading.collectAsState()
                val referralCode by _referralCode.collectAsState()
                val isNewUser by _isNewUser.collectAsState()
                val banners by _banners.collectAsState()

                var selectedRasiItem by remember { mutableStateOf<ComposeRasiItem?>(null) }


                // Dialog removed in favor of Activity navigation


                HomeScreen(
                    walletBalance = balance,
                    superWalletBalance = superBalance,
                    horoscope = horoscope,
                    astrologers = astrologers,
                    isLoading = isLoading,
                    banners = banners,
                    onBannerClick = { banner ->
                        if (banner.offerPercentage > 0.0) {
                            val intent = Intent(this, com.astrohark.app.ui.wallet.SuperWalletActivity::class.java).apply {
                                putExtra("bannerTitle", banner.title)
                                putExtra("offerPercentage", banner.offerPercentage)
                            }
                            startActivity(intent)
                        } else {
                            // Mirror web behavior: scroll to astrologer list
                            // We can trigger a refresh or just notify the UI to scroll
                            // For simplicity, let's assume HomeScreen handles state
                            // or we can show a Toast for now if it's already on Home
                            lifecycleScope.launch {
                                // This is a placeholder for actual scroll-to logic if we had a scroll state here
                                // For now, we'll let HomeScreen handle the click if we want to scroll
                            }
                            Toast.makeText(this, "Check out our top astrologers!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onChatClick = { astro ->
                        val intent = Intent(this, com.astrohark.app.ui.intake.IntakeActivity::class.java).apply {
                            putExtra("partnerId", astro.userId)
                            putExtra("partnerName", astro.name)
                            putExtra("partnerImage", astro.image)
                            putExtra("type", "chat")
                        }
                        startActivity(intent)
                    },
                    onCallClick = { astro, type ->
                        val intent = Intent(this, com.astrohark.app.ui.intake.IntakeActivity::class.java).apply {
                            putExtra("partnerId", astro.userId)
                            putExtra("partnerName", astro.name)
                            putExtra("partnerImage", astro.image)
                            putExtra("type", type)
                        }
                        startActivity(intent)
                    },
                    onRasiClick = { item ->
                        // Launch RasipalanActivity with filtering extras
                        val intent = Intent(this, com.astrohark.app.ui.rasipalan.RasipalanActivity::class.java).apply {
                            putExtra("signId", item.id)
                            putExtra("signName", item.name)
                        }
                        startActivity(intent)
                    },
                    onLogoutClick = {
                        tokenManager.clearSession()
                        val intent = Intent(this, com.astrohark.app.ui.auth.LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    },
                    onDrawerItemClick = { item ->
                        when(item) {
                            "logout" -> {
                                tokenManager.clearSession()
                                val intent = Intent(this, com.astrohark.app.ui.auth.LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            "settings" -> {
                                startActivity(Intent(this, com.astrohark.app.ui.settings.SettingsActivity::class.java))
                            }
                            "profile" -> {
                                startActivity(Intent(this, com.astrohark.app.ui.profile.UserProfileActivity::class.java))
                            }
                            "wallet" -> {
                                startActivity(Intent(this, com.astrohark.app.ui.wallet.WalletActivity::class.java))
                            }
                            "join_as_astrologer" -> {
                                startActivity(Intent(this, com.astrohark.app.ui.auth.AstrologerRegistrationActivity::class.java))
                            }
                            else -> {
                                // Handle Navigation
                            }
                        }
                    },
                    onServiceClick = { serviceName ->
                        handleServiceClick(serviceName)
                    },
                    onWalletClick = {
                        startActivity(Intent(this, com.astrohark.app.ui.wallet.WalletActivity::class.java))
                    },
                    referralCode = referralCode,
                    isNewUser = isNewUser,
                    onApplyReferral = { code -> applyReferralCode(code) }
                )

            }
        }

        // Logout & Socket Logic kept same/adapted
        // Note: Logout button is not yet in HomeScreen (User didn't explicitly ask for it, but should probably be in Drawer or Profile?)
        // The original code had a logout button in XML. I will assume it's okay to omit for this "Screen" demo, or I can add it to TopBar later.

        // Load data
        loadWalletBalance()
        loadDailyHoroscope()
        loadAstrologers()

        // Automatic Referral Check
        checkAndApplyAutomaticReferral()

        // Setup Socket for real-time updates
        setupSocket()
    }

    // Composable State (Must be hoisted or handled via callback to Compose)
    // Since this is an Activity hosting Compose content, the easiest way is to push state to the Compose root.
    // However, we are declaring `showRasiDialog` inside the Activity class which is not Composable.
    // We should move `showRasiDialog` logic into the `setContent`.

    /* Removed legacy showRasiDialog function */

    private fun loadWalletBalance() {
        val session = tokenManager.getUserSession()
        _walletBalance.value = session?.walletBalance ?: 0.0
        _superWalletBalance.value = session?.superWalletBalance ?: 0.0
        _referralCode.value = session?.referralCode
        _isNewUser.value = session?.isNewUser ?: false
    }


    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = com.astrohark.app.data.api.ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    tokenManager.saveUserSession(user)
                    _walletBalance.value = user.walletBalance ?: 0.0
                    _superWalletBalance.value = user.superWalletBalance ?: 0.0
                    _referralCode.value = user.referralCode
                    _isNewUser.value = user.isNewUser ?: false
                }

            } catch (e: Exception) {
                Log.e(TAG, "Balance refresh failed", e)
            }
        }
    }

    private fun loadDailyHoroscope() {
        lifecycleScope.launch {
            try {
                _horoscope.value = fetchHoroscope()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading horoscope", e)
                _horoscope.value = "Good progress will occur today as Chandrashtama has passed."
            }
        }
    }

    private suspend fun fetchHoroscope(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$SERVER_URL/api/daily-horoscope")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                json.optString("content", "Today is a good day!")
            } else {
                "Today is a good day!"
            }
        }
    }

    private fun loadAstrologers() {
        _isLoading.value = true
        lifecycleScope.launch {
            try {
                val list = fetchAstrologers()
                _astrologers.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Error loading astrologers", e)
                // showErrorAlert("Failed to load astrologers") // Toast
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchAstrologers(): List<Astrologer> = withContext(Dispatchers.IO) {
        val socket = SocketManager.getSocket()
        val result = mutableListOf<Astrologer>()

        // Fallback or Initial Load via HTTP
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/astrology/astrologers")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val arr = json.optJSONArray("astrologers") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        result.add(parseAstrologer(arr.getJSONObject(i)))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP fallback failed", e)
        }
        result.sortWith(
            compareByDescending<Astrologer> {
                it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
            }.thenByDescending { it.experience }
        )
        result
    }

    private fun parseAstrologer(json: JSONObject): Astrologer {
        val skillsArr = json.optJSONArray("skills")
        val skills = mutableListOf<String>()
        if (skillsArr != null) {
            for (i in 0 until skillsArr.length()) {
                skills.add(skillsArr.getString(i))
            }
        }

        return Astrologer(
            userId = json.optString("userId", ""),
            name = json.optString("name", "Astrologer"),
            phone = json.optString("phone", ""),
            skills = skills,
            price = json.optInt("price", 15),
            isOnline = json.optBoolean("isOnline", false),
            isChatOnline = json.optBoolean("isChatOnline", false),
            isAudioOnline = json.optBoolean("isAudioOnline", false),
            isVideoOnline = json.optBoolean("isVideoOnline", false),
            image = json.optString("image", ""),
            experience = json.optInt("experience", 0),
            isVerified = json.optBoolean("isVerified", false),
            walletBalance = json.optDouble("walletBalance", 0.0),
            isBusy = json.optBoolean("isBusy", false)
        )
    }

    private fun setupSocket() {
        SocketManager.init()
        val socket = SocketManager.getSocket()
        val session = tokenManager.getUserSession()
        if (session != null) {
            SocketManager.registerUser(session.userId ?: "")
        }

        socket?.on("astro-list") { args ->
            val data = args[0] as? JSONObject ?: return@on
            val arr = data.optJSONArray("list") ?: JSONArray()
            val list = mutableListOf<Astrologer>()
            for (i in 0 until arr.length()) {
                list.add(parseAstrologer(arr.getJSONObject(i)))
            }
            // Sort: Online first (Any online status), then Experience
            val sortedList = list.sortedWith(
                compareByDescending<Astrologer> {
                    it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
                }.thenByDescending { it.experience }
            )
            lifecycleScope.launch(Dispatchers.Main) {
                _astrologers.value = sortedList
                _isLoading.value = false
            }
        }

        socket?.on("astrologer-update") { args ->
            val data = args[0] as? JSONArray ?: return@on
            val list = mutableListOf<Astrologer>()
            for (i in 0 until data.length()) {
                list.add(parseAstrologer(data.getJSONObject(i)))
            }
            val sortedList = list.sortedWith(
                compareByDescending<Astrologer> {
                    it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
                }.thenByDescending { it.experience }
            )
            lifecycleScope.launch(Dispatchers.Main) {
                _astrologers.value = sortedList
            }
        }

        socket?.on("astro-status-change") { args ->
            // Update individual status in list
            val data = args[0] as? JSONObject ?: return@on
            val userId = data.optString("userId")

            // Check for specific service fields or fallback to master online
            val service = data.optString("service") // "chat", "call", "video"
            val isEnabled = data.optBoolean("isEnabled", false)
            val isMasterOnline = data.optBoolean("isOnline", false)

            val currentList = _astrologers.value.toMutableList()
            val index = currentList.indexOfFirst { it.userId == userId }
            if (index != -1) {
                val astro = currentList[index]
                val updatedAstro = if (service.isNotEmpty()) {
                    when (service) {
                        "chat" -> astro.copy(isChatOnline = isEnabled)
                        "call", "audio" -> astro.copy(isAudioOnline = isEnabled)
                        "video" -> astro.copy(isVideoOnline = isEnabled)
                        else -> astro
                    }.copy(
                        // Re-evaluate master online status
                        isOnline = isEnabled || (if(service=="chat") false else astro.isChatOnline) ||
                                              (if(service=="call") false else astro.isAudioOnline) ||
                                              (if(service=="video") false else astro.isVideoOnline)
                    )
                } else {
                    astro.copy(isOnline = isMasterOnline)
                }

                currentList[index] = updatedAstro
                _astrologers.value = currentList
            }
        }

        socket?.on("wallet-update") { args ->
            val data = args[0] as? JSONObject ?: return@on
            val balance = data.optDouble("balance", 0.0)
            val superBalance = data.optDouble("superBalance", 0.0)

            lifecycleScope.launch(Dispatchers.Main) {
                _walletBalance.value = balance
                _superWalletBalance.value = superBalance
            }
            tokenManager.updateWalletBalance(balance)
            tokenManager.updateSuperWalletBalance(superBalance)
        }

        socket?.on("banners-updated") {
            Log.d(TAG, "[Socket] Banners updated, refreshing...")
            fetchBanners()
        }

        socket?.emit("get-astrologers")
    }

    private fun startChat(astro: Astrologer) {
        initiateSession(astro.userId, "chat", astro.name, astro.image)
    }

    private fun startCall(astro: Astrologer, type: String) {
        initiateSession(astro.userId, type, astro.name, astro.image)
    }

    private fun initiateSession(astrologerId: String, type: String, astroName: String, astroImage: String) {
        if (_walletBalance.value <= 0.0) {
            Toast.makeText(this, "Insufficient Main Balance. Please recharge to start.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, com.astrohark.app.ui.wallet.WalletActivity::class.java)
            startActivity(intent)
            return
        }
        val intent = Intent(this, com.astrohark.app.ui.intake.IntakeActivity::class.java).apply {
            putExtra("partnerId", astrologerId)
            putExtra("partnerName", astroName)
            putExtra("partnerImage", astroImage)
            putExtra("type", type)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadWalletBalance()
        refreshWalletBalance()
        fetchBanners()
        // Ensure astrologer list is fresh when returning to the screen
        SocketManager.getSocket()?.emit("get-astrologers")
    }

    private fun fetchBanners() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.api.getBanners()
                if (response.isSuccessful && response.body()?.ok == true) {
                    _banners.value = response.body()?.banners ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching banners: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // SocketManager.disconnect() - kept same
    }

    private fun handleServiceClick(serviceName: String) {
        when (serviceName.replace("\n", " ")) {
            "Free  horoscope" -> {
                val intent = Intent(this, com.astrohark.app.ui.horoscope.FreeHoroscopeActivity::class.java)
                startActivity(intent)
            }
            "Horoscope Match" -> {
                val intent = Intent(this, com.astrohark.app.ui.intake.IntakeActivity::class.java).apply {
                    putExtra("type", "match")
                    putExtra("isMatching", true)
                }
                startActivity(intent)
            }
            "Daily Horoscope" -> {
                val intent = Intent(this, com.astrohark.app.ui.rasipalan.RasipalanActivity::class.java)
                startActivity(intent)
            }
            "Free  Star Services" -> {
                Toast.makeText(this, "Free Star Services - Coming Soon!", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "$serviceName clicked", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyReferralCode(code: String) {
        val session = tokenManager.getUserSession() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestBody = JSONObject().apply {
                    put("userId", session.userId)
                    put("referralCode", code)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$SERVER_URL/api/referral/apply")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful && json.optBoolean("ok")) {
                            Toast.makeText(this@HomeActivity, json.optString("message", "Success!"), Toast.LENGTH_LONG).show()
                            tokenManager.clearPendingReferral() // Clear it after success
                            refreshWalletBalance()
                            val updated = session.copy(isNewUser = false)
                            tokenManager.saveUserSession(updated)
                            _isNewUser.value = false
                        } else {
                            // If auto-applying fails, we don't spam toasts unless it was manual
                            // But for manual click, it will still trigger this.
                            val msg = json.optString("message", "Invalid Code")
                            if (code != tokenManager.getPendingReferral()) {
                                Toast.makeText(this@HomeActivity, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying referral", e)
            }
        }
    }

    private fun checkAndApplyAutomaticReferral() {
        val pendingCode = tokenManager.getPendingReferral()
        val session = tokenManager.getUserSession()
        
        if (pendingCode != null && session?.isNewUser == true) {
            Log.d(TAG, "Auto-applying referral code: $pendingCode")
            applyReferralCode(pendingCode)
        }
    }
}


