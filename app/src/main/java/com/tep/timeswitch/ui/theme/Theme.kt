package com.tep.timeswitch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 参考小程序主色 #1e86e0(视觉后续统一调整)
val BrandBlue = Color(0xFF1E86E0)
val BrandBlueLight = Color(0xFFE3F0FB)
val TextSecondary = Color(0xFF666666)
val TextHint = Color(0xFF959595)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = BrandBlue,
    secondary = Color(0xFF10C3A6),
    error = Color(0xFFF76260),
    background = Color(0xFFF6F6F6),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
)

private val DarkColors = darkColorScheme(primary = Color(0xFF7EB6F0))

@Composable
fun TEPTimeSwitchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
