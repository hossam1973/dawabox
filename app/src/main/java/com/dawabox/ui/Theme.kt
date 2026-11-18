package com.dawabox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Teal = Color(0xFF0F7A68)
val OkGreen = Color(0xFF2F8F4E)
val WarnAmber = Color(0xFFB7791F)
val BadRed = Color(0xFFC2453A)

private val Light = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFFDFF1EC), onPrimaryContainer = Color(0xFF0B3E35),
    secondary = Color(0xFF3B6F8F), tertiary = Color(0xFFB7791F),
    background = Color(0xFFF3F6F5), surface = Color.White,
    onBackground = Color(0xFF14302B), onSurface = Color(0xFF14302B),
    surfaceVariant = Color(0xFFE6EEEB), onSurfaceVariant = Color(0xFF5B726D),
    error = BadRed,
)
private val Dark = darkColorScheme(
    primary = Color(0xFF3CC2A7), onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF163A33), onPrimaryContainer = Color(0xFFBFEDE2),
    secondary = Color(0xFF8FC3E3), tertiary = Color(0xFFE2A84B),
    background = Color(0xFF0D1715), surface = Color(0xFF14211E),
    onBackground = Color(0xFFE3EEEB), onSurface = Color(0xFFE3EEEB),
    surfaceVariant = Color(0xFF24352F), onSurfaceVariant = Color(0xFF93AAA4),
    error = Color(0xFFEC7A6E),
)

/** Larger than default type so older patients can read it comfortably. */
private val BigType = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun DawaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = BigType, content = content)
}
