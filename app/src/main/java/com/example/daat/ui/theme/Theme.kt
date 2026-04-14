package com.example.daat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = OrangeApp,
    secondary = BrownApp,
    tertiary = BlueApp,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF252429),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = WhiteApp,
    onSurface = WhiteApp,
    primaryContainer = PointsOrange,
    onPrimaryContainer = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = OrangeApp,
    secondary = BrownApp,
    tertiary = BlueApp,
    background = BackgroundCream,
    surface = SurfaceIvory,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    primaryContainer = PointsOrange,
    onPrimaryContainer = Color.White
)

@Composable
fun DAATTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
