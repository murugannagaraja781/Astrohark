package com.astrohark.app.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppTheme(val title: String) {
    CosmicPurple("Cosmic Purple"),
    MidnightIndigo("Midnight Indigo"),
    RoyalBlue("Royal Blue Mystic"),
    EmeraldNight("Emerald Night"),
    CharcoalGold("Charcoal Gold"),
    DeepAmethyst("Deep Amethyst"),
    SunsetGlow("Sunset Glow"),
    OceanBreeze("Ocean Breeze"),
    ForestMystic("Forest Mystic"),
    RubyPassion("Ruby Passion")
}

data class ThemeColors(
    val bgStart: Color,
    val bgCenter: Color,
    val bgEnd: Color,
    val headerStart: Color,
    val headerEnd: Color,
    val cardBg: Color,
    val cardStroke: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color
)

object ThemePalette {

    // Brand Colors from Logo
    private val BrandYellow = Color(0xFFF5C518)  // Logo Yellow
    private val BrandOrange = Color(0xFFE87A1E)  // Logo Orange
    private val BrandOrangeDark = Color(0xFFD4700B) // Darker Orange variant

    // Base Premium Template - Using Logo Colors
    private val PremiumTemplate = ThemeColors(
        bgStart = Color(0xFFFFFFFF),      // Pure White Background
        bgCenter = Color(0xFFFFFFFF),
        bgEnd = Color(0xFFFFFFFF),
        headerStart = BrandOrange,         // Orange from logo
        headerEnd = BrandOrangeDark,       // Darker orange
        cardBg = Color(0xFFFFFFFF),
        cardStroke = BrandOrange,          // Orange Borders
        textPrimary = Color(0xFF1C1F26),
        textSecondary = Color(0xFF6B7280),
        accent = BrandYellow               // Yellow Accent from logo
    )

    // All themes use logo-based premium template
    val CosmicPurple = PremiumTemplate
    val MidnightIndigo = PremiumTemplate
    val RoyalBlue = PremiumTemplate
    val EmeraldNight = PremiumTemplate
    val CharcoalGold = PremiumTemplate
    val DeepAmethyst = PremiumTemplate
    val SunsetGlow = PremiumTemplate
    val OceanBreeze = PremiumTemplate
    val ForestMystic = PremiumTemplate
    val RubyPassion = PremiumTemplate

    // Helper to get colors by enum
    fun getColors(theme: AppTheme): ThemeColors = PremiumTemplate
}
