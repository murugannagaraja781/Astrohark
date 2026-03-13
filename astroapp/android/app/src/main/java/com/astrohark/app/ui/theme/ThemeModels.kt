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

    // Brand Colors from Screenshot
    private val CocoaDark = Color(0xFF140F0A)   // Deepest Background
    private val CocoaSurface = Color(0xFF241A12) // Card/Surface
    private val AccentOrange = Color(0xFFFF7F00) // Primary Orange
    private val TextPrimary = Color(0xFFFFFFFF)  // White Text
    private val TextSecondary = Color(0xFFA58B74) // Muted Cream/Brown

    // Base Premium Template - Dark Cocoa Theme
    private val PremiumTemplate = ThemeColors(
        bgStart = CocoaDarkBg,
        bgCenter = CocoaDarkBg,
        bgEnd = CocoaDeepDark,
        headerStart = CocoaDarkBg,
        headerEnd = CocoaDarkBg,
        cardBg = CocoaSurface,
        cardStroke = CocoaSurface.copy(alpha = 0.5f),
        textPrimary = CocoaTextPrimary,
        textSecondary = CocoaTextSecondary,
        accent = CocoaAccent
    )

    // All themes use this premium template now
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
