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

    // Premium Light Theme Palette
    private val LightBg = Color(0xFFF8F9FA)      // Soft Gray/White
    private val LightSurface = Color(0xFFFFFFFF) // Pure White
    private val LightAccent = Color(0xFFB8860B)  // Dark Goldenrod / Premium Gold
    private val LightTextPrimary = Color(0xFF1A1A1A) // Near Black
    private val LightTextSecondary = Color(0xFF64748B) // Slate Gray
    private val LightBorder = Color(0xFFE2E8F0) // Soft Border

    // Base Premium Template - Light Theme
    private val PremiumLightTemplate = ThemeColors(
        bgStart = LightBg,
        bgCenter = LightBg,
        bgEnd = LightBg,
        headerStart = LightSurface,
        headerEnd = LightSurface,
        cardBg = LightSurface,
        cardStroke = LightBorder,
        textPrimary = LightTextPrimary,
        textSecondary = LightTextSecondary,
        accent = LightAccent
    )

    // All themes use this premium template now
    val CosmicPurple = PremiumLightTemplate
    val MidnightIndigo = PremiumLightTemplate
    val RoyalBlue = PremiumLightTemplate
    val EmeraldNight = PremiumLightTemplate
    val CharcoalGold = PremiumLightTemplate
    val DeepAmethyst = PremiumLightTemplate
    val SunsetGlow = PremiumLightTemplate
    val OceanBreeze = PremiumLightTemplate
    val ForestMystic = PremiumLightTemplate
    val RubyPassion = PremiumLightTemplate

    // Helper to get colors by enum
    fun getColors(theme: AppTheme): ThemeColors = PremiumTemplate
}
