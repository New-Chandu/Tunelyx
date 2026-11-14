package com.chandyou.tunelyx.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class TunelyxCustomColors(
    val bgBase: Color,
    val bgSurface: Color,
    val bgSurfaceElevated: Color,
    val bgCardStack: Color,
    val bgGlass: Color,
    val borderSubtle: Color,
    val borderFocus: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentGreen: Color,
    val accentGreenGlow: Color,
    val onAccent: Color,
    val dislikeRed: Color,
    val dislikeRedGlow: Color,
    val bookmarkPurple: Color,
    val bookmarkPurpleGlow: Color,
    val skipAmber: Color,
    val skipAmberGlow: Color,
    val isDark: Boolean
) {
    val bgApp: Color get() = bgBase
    val cardBg: Color get() = bgSurface
    val accentRed: Color get() = dislikeRed
}

val LocalTunelyxColors = compositionLocalOf {
    TunelyxCustomColors(
        bgBase = AmoledBlack,
        bgSurface = SurfaceDark,
        bgSurfaceElevated = SurfaceElevatedDark,
        bgCardStack = CardStackDark,
        bgGlass = GlassDark,
        borderSubtle = BorderSubtleDark,
        borderFocus = BorderFocusDark,
        textPrimary = TextPrimaryDark,
        textSecondary = TextSecondaryDark,
        textTertiary = TextTertiaryDark,
        accentGreen = SpotifyGreen,
        accentGreenGlow = SpotifyGreenGlow,
        onAccent = Color.Black,
        dislikeRed = DislikeRed,
        dislikeRedGlow = DislikeRedGlow,
        bookmarkPurple = BookmarkPurple,
        bookmarkPurpleGlow = BookmarkPurpleGlow,
        skipAmber = SkipAmber,
        skipAmberGlow = SkipAmberGlow,
        isDark = true
    )
}

object TunelyxTheme {
    val colors: TunelyxCustomColors
        @Composable
        get() = LocalTunelyxColors.current
}

@Composable
fun TunelyxTheme(
    darkTheme: Boolean = true,
    accentPreset: AccentColorPreset = AccentColorPreset.GREEN,
    content: @Composable () -> Unit
) {
    val resolvedAccent = if (!darkTheme && accentPreset == AccentColorPreset.WHITE) {
        Color(0xFF18181B)
    } else {
        accentPreset.color
    }
    val resolvedAccentGlow = if (!darkTheme && accentPreset == AccentColorPreset.WHITE) {
        Color(0x2818181B)
    } else {
        accentPreset.glow
    }
    val resolvedOnAccent = if (!darkTheme && accentPreset == AccentColorPreset.WHITE) {
        Color.White
    } else {
        Color.Black
    }

    val customColors = if (darkTheme) {
        TunelyxCustomColors(
            bgBase = AmoledBlack,
            bgSurface = SurfaceDark,
            bgSurfaceElevated = SurfaceElevatedDark,
            bgCardStack = CardStackDark,
            bgGlass = GlassDark,
            borderSubtle = BorderSubtleDark,
            borderFocus = BorderFocusDark,
            textPrimary = TextPrimaryDark,
            textSecondary = TextSecondaryDark,
            textTertiary = TextTertiaryDark,
            accentGreen = resolvedAccent,
            accentGreenGlow = resolvedAccentGlow,
            onAccent = resolvedOnAccent,
            dislikeRed = DislikeRed,
            dislikeRedGlow = DislikeRedGlow,
            bookmarkPurple = BookmarkPurple,
            bookmarkPurpleGlow = BookmarkPurpleGlow,
            skipAmber = SkipAmber,
            skipAmberGlow = SkipAmberGlow,
            isDark = true
        )
    } else {
        TunelyxCustomColors(
            bgBase = BackgroundLight,
            bgSurface = SurfaceLight,
            bgSurfaceElevated = SurfaceElevatedLight,
            bgCardStack = CardStackLight,
            bgGlass = GlassLight,
            borderSubtle = BorderSubtleLight,
            borderFocus = BorderFocusLight,
            textPrimary = TextPrimaryLight,
            textSecondary = TextSecondaryLight,
            textTertiary = TextTertiaryLight,
            accentGreen = resolvedAccent,
            accentGreenGlow = resolvedAccentGlow,
            onAccent = resolvedOnAccent,
            dislikeRed = Color(0xFFE11D48),
            dislikeRedGlow = Color(0x40E11D48),
            bookmarkPurple = Color(0xFF9333EA),
            bookmarkPurpleGlow = Color(0x409333EA),
            skipAmber = Color(0xFFD97706),
            skipAmberGlow = Color(0x40D97706),
            isDark = false
        )
    }

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = customColors.accentGreen,
            background = customColors.bgBase,
            surface = customColors.bgSurface,
            onPrimary = customColors.onAccent,
            onBackground = customColors.textPrimary,
            onSurface = customColors.textPrimary
        )
    } else {
        lightColorScheme(
            primary = customColors.accentGreen,
            background = customColors.bgBase,
            surface = customColors.bgSurface,
            onPrimary = customColors.onAccent,
            onBackground = customColors.textPrimary,
            onSurface = customColors.textPrimary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalTunelyxColors provides customColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}
