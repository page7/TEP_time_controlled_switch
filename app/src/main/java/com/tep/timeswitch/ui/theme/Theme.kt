package com.tep.timeswitch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌色 #10B981(App Logo 绿;2026-11-20 起替代原小程序蓝 #1e86e0)
val BrandGreen = Color(0xFF10B981)
val BrandGreenLight = Color(0xFFD1FAE5)
val TextSecondary = Color(0xFF666666)
val TextHint = Color(0xFF959595)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenLight,
    onPrimaryContainer = BrandGreen,
    secondary = Color(0xFF10C3A6),
    error = Color(0xFFF76260),
    background = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
)

private val DarkColors = darkColorScheme(primary = Color(0xFF6EE7B7))

@Composable
fun TEPTimeSwitchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
