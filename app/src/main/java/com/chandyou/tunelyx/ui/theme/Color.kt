package com.chandyou.tunelyx.ui.theme

import androidx.compose.ui.graphics.Color

// Spotify & Brand Colors
val SpotifyGreen = Color(0xFF1ED760)
val SpotifyGreenGlow = Color(0x591ED760)
val SpotifyGreenDark = Color(0xFF14A047)

// Action Dock Colors
val DislikeRed = Color(0xFFF43F5E)
val DislikeRedGlow = Color(0x59F43F5E)

val BookmarkPurple = Color(0xFFA855F7)
val BookmarkPurpleGlow = Color(0x59A855F7)

val SkipAmber = Color(0xFFFBBF24)
val SkipAmberGlow = Color(0x59FBBF24)

// AMOLED Pure Black Dark Theme
val AmoledBlack = Color(0xFF000000)
val SurfaceDark = Color(0xFF0A0A0C)
val SurfaceElevatedDark = Color(0xFF141418)
val CardStackDark = Color(0xFF18181E)
val GlassDark = Color(0xE014141A)
val BorderSubtleDark = Color(0x1FFFFFFF)
val BorderFocusDark = Color(0x38FFFFFF)
val TextPrimaryDark = Color(0xFFF5F6F8)
val TextSecondaryDark = Color(0xFF9EA3AE)
val TextTertiaryDark = Color(0xFF656B78)

// Pristine High-Contrast Light Theme
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceElevatedLight = Color(0xFFFFFFFF)
val CardStackLight = Color(0xFFF1F5F9)
val GlassLight = Color(0xF2FFFFFF)
val BorderSubtleLight = Color(0x1F000000)
val BorderFocusLight = Color(0x40000000)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF334155)
val TextTertiaryLight = Color(0xFF64748B)

// Accent Presets
enum class AccentColorPreset(val displayName: String, val color: Color, val glow: Color) {
    WHITE("Pure White", Color(0xFFFFFFFF), Color(0x40FFFFFF)),
    GREEN("Spotify Green", Color(0xFF1ED760), Color(0x591ED760)),
    PURPLE("Electric Purple", Color(0xFFA855F7), Color(0x59A855F7)),
    PINK("Neon Pink", Color(0xFFEC4899), Color(0x59EC4899)),
    ORANGE("Sunset Orange", Color(0xFFF97316), Color(0x59F97316)),
    CYAN("Cyber Cyan", Color(0xFF06B6D4), Color(0x5906B6D4))
}
