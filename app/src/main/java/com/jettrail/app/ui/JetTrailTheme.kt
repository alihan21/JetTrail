package com.jettrail.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object JetColors {
    val Ink = Color(0xFF080B10)
    val Panel = Color(0xFF111721)
    val PanelRaised = Color(0xFF18212D)
    val Cyan = Color(0xFF6FE7DE)
    val Blue = Color(0xFF76A9FF)
    val Amber = Color(0xFFFFB84D)
    val Red = Color(0xFFFF665E)
    val Text = Color(0xFFF4F7FA)
    val Muted = Color(0xFF95A2B5)
}

private val DarkScheme = darkColorScheme(
    primary = JetColors.Cyan,
    onPrimary = Color(0xFF003733),
    secondary = JetColors.Blue,
    tertiary = JetColors.Amber,
    background = JetColors.Ink,
    onBackground = JetColors.Text,
    surface = JetColors.Panel,
    onSurface = JetColors.Text,
    surfaceVariant = JetColors.PanelRaised,
    onSurfaceVariant = JetColors.Muted,
    error = JetColors.Red
)

private val RedAmberScheme = darkColorScheme(
    primary = Color(0xFFFF9F43),
    onPrimary = Color(0xFF321400),
    secondary = Color(0xFFFF675D),
    tertiary = Color(0xFFFFC36A),
    background = Color(0xFF090403),
    onBackground = Color(0xFFFFD4B5),
    surface = Color(0xFF160A08),
    onSurface = Color(0xFFFFD4B5),
    surfaceVariant = Color(0xFF28110D),
    onSurfaceVariant = Color(0xFFC99272),
    error = Color(0xFFFF665E)
)

private val JetTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 25.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp)
)

@Composable
fun JetTrailTheme(theme: CabinTheme = CabinTheme.DARK, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (theme == CabinTheme.RED_AMBER) RedAmberScheme else DarkScheme,
        typography = JetTypography,
        content = content
    )
}
