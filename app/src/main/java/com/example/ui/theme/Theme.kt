package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ExpiryColorPalette(
    val danger: Color,
    val dangerContainer: Color,
    val onDangerText: Color,
    val onDangerBadgeText: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningText: Color,
    val onWarningBadgeText: Color,
    val safe: Color,
    val safeContainer: Color,
    val onSafeText: Color,
    val onSafeBadgeText: Color,
    val medicine: Color,
    val medicineContainer: Color,
    val onMedicineText: Color,
    val isDark: Boolean
)

val LightExpiryColors = ExpiryColorPalette(
    danger = DangerRed,
    dangerContainer = DangerRedContainerLight,
    onDangerText = DangerRed,
    onDangerBadgeText = Color.White,
    warning = WarningAmber,
    warningContainer = WarningAmberContainerLight,
    onWarningText = WarningAmber,
    onWarningBadgeText = Color.White,
    safe = SafeGreen,
    safeContainer = SafeGreenContainerLight,
    onSafeText = SafeGreen,
    onSafeBadgeText = Color.White,
    medicine = MedicineIndigo,
    medicineContainer = MedicineIndigoContainerLight,
    onMedicineText = MedicineIndigo,
    isDark = false
)

val DarkExpiryColors = ExpiryColorPalette(
    danger = DangerRedDark, // #F87171 - soft coral red
    dangerContainer = DangerRedContainerDark.copy(alpha = 0.55f),
    onDangerText = DangerRedDark,
    onDangerBadgeText = Color(0xFF0F172A), // Dark text on bright badge for optimal readability
    warning = WarningAmberDark, // #FBBF24 - warm amber
    warningContainer = WarningAmberContainerDark.copy(alpha = 0.55f),
    onWarningText = WarningAmberDark,
    onWarningBadgeText = Color(0xFF1E293B), // Dark text on amber badge for optimal readability
    safe = SafeGreenDark, // #4ADE80 - mint green
    safeContainer = SafeGreenContainerDark.copy(alpha = 0.55f),
    onSafeText = SafeGreenDark,
    onSafeBadgeText = Color(0xFF064E3B),
    medicine = MedicineIndigoDark, // #A78BFA - pastel lavender
    medicineContainer = MedicineIndigoContainerDark.copy(alpha = 0.55f),
    onMedicineText = MedicineIndigoDark,
    isDark = true
)

val LocalExpiryColors = staticCompositionLocalOf { LightExpiryColors }

object ExpiryTheme {
    val colors: ExpiryColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalExpiryColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryContainer = EmeraldPrimaryContainerDark,
    onPrimaryContainer = EmeraldOnPrimaryContainerDark,
    secondary = SecondaryBlueDark,
    tertiary = WarningAmberDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    outlineVariant = OutlineVariantDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    error = DangerRedDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = EmeraldOnPrimaryLight,
    primaryContainer = EmeraldPrimaryContainerLight,
    onPrimaryContainer = EmeraldOnPrimaryContainerLight,
    secondary = SecondaryBlueLight,
    tertiary = WarningAmber,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    outlineVariant = Color(0xFFE2E8F0),
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    error = DangerRed
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val expiryPalette = if (isDark) DarkExpiryColors else LightExpiryColors

    CompositionLocalProvider(LocalExpiryColors provides expiryPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
