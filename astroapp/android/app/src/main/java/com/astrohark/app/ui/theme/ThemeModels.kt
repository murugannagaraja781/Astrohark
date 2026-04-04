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
    val bgStartDark: Color,  // Added Dark Background Support
    val bgCenterDark: Color,
    val bgEndDark: Color,
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
    private val DarkCoffee = Color(0xFF5D4037)  // Dark Coffee for Borders

    // Premium Dark Theme Palette (Cocoa/Midnight based)
    private val DarkBg = Color(0xFF140F0A)       // Deep Dark Cocoa
    private val DarkSurface = Color(0xFF1C140E)  // Dark Cocoa Surface
    private val DarkAccent = Color(0xFFFF7F00)   // Brand Orange
    private val DarkTextPrimary = Color(0xFFF5F2F0) // Off White
    private val DarkTextSecondary = Color(0xFFA58B74) // Muted Cocoa

    // Base Premium Template
    private val PremiumTemplate = ThemeColors(
        bgStart = LightBg,
        bgCenter = LightBg,
        bgEnd = LightBg,
        bgStartDark = DarkBg,
        bgCenterDark = DarkBg,
        bgEndDark = DarkBg,
        headerStart = LightSurface,
        headerEnd = LightSurface,
        cardBg = LightSurface,
        cardStroke = DarkCoffee,
        textPrimary = LightTextPrimary,
        textSecondary = LightTextSecondary,
        accent = LightAccent
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
