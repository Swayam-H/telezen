package com.telezen.app.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.telezen.app.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF1F6B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE9E1),
    onPrimaryContainer = Color(0xFF06302A),
    secondary = Color(0xFF8A5A3C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E1D3),
    onSecondaryContainer = Color(0xFF34190A),
    tertiary = Color(0xFF4F5F8F),
    background = Color(0xFFF7F9F8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF7F9F8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDDE5E2),
    onSurfaceVariant = Color(0xFF414946),
    surfaceContainer = Color(0xFFEDF2F0),
    surfaceContainerHigh = Color(0xFFE6ECEA),
    surfaceContainerLow = Color(0xFFF1F5F3),
    outline = Color(0xFF717976),
    outlineVariant = Color(0xFFC1C9C5),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3C3),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1B5148),
    onPrimaryContainer = Color(0xFFCDE9E1),
    secondary = Color(0xFFE6BFA5),
    onSecondary = Color(0xFF4C2814),
    secondaryContainer = Color(0xFF653E27),
    onSecondaryContainer = Color(0xFFF5E1D3),
    tertiary = Color(0xFFB7C5F5),
    background = Color(0xFF101413),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBFC9C5),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerLow = Color(0xFF171D1B),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val AppTypography = Typography().let {
    it.copy(
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    )
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun TeleZenTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val activity = LocalContext.current as? ComponentActivity
    // Status/navigation bar icons must follow the in-app theme, not the system one.
    LaunchedEffect(dark, activity) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
