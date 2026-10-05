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
    onDangerText = DangerRedTextLight,
    onDangerBadgeText = Color.White,
    warning = WarningAmber,
    warningContainer = WarningAmberContainerLight,
    onWarningText = WarningAmberTextLight,
    onWarningBadgeText = Color.White,
    safe = SafeGreen,
    safeContainer = SafeGreenContainerLight,
    onSafeText = SafeGreenTextLight,
    onSafeBadgeText = Color.White,
    medicine = MedicineIndigo,
    medicineContainer = MedicineIndigoContainerLight,
    onMedicineText = MedicineIndigoTextLight,
    isDark = false
)

val DarkExpiryColors = ExpiryColorPalette(
    danger = DangerRedDark,
    dangerContainer = DangerRedContainerDark.copy(alpha = 0.6f),
    onDangerText = DangerRedTextDark,
    onDangerBadgeText = Color(0xFF0F172A),
    warning = WarningAmberDark,
    warningContainer = WarningAmberContainerDark.copy(alpha = 0.6f),
    onWarningText = WarningAmberTextDark,
    onWarningBadgeText = Color(0xFF1E293B),
    safe = SafeGreenDark,
    safeContainer = SafeGreenContainerDark.copy(alpha = 0.6f),
    onSafeText = SafeGreenTextDark,
    onSafeBadgeText = Color(0xFF042F2E),
    medicine = MedicineIndigoDark,
    medicineContainer = MedicineIndigoContainerDark.copy(alpha = 0.6f),
    onMedicineText = MedicineIndigoTextDark,
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
    primary = BeepMintPrimaryDark,
    onPrimary = BeepMintOnPrimaryDark,
    primaryContainer = BeepMintPrimaryContainerDark,
    onPrimaryContainer = BeepMintOnPrimaryContainerDark,
    secondary = BeepSecondaryDark,
    tertiary = WarningAmberDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    outline = OutlineVariantDark,
    outlineVariant = OutlineVariantDark.copy(alpha = 0.6f),
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    error = DangerRedDark
)

private val LightColorScheme = lightColorScheme(
    primary = BeepMintPrimaryLight,
    onPrimary = BeepMintOnPrimaryLight,
    primaryContainer = BeepMintPrimaryContainerLight,
    onPrimaryContainer = BeepMintOnPrimaryContainerLight,
    secondary = BeepSecondaryLight,
    tertiary = WarningAmber,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight.copy(alpha = 0.7f),
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
