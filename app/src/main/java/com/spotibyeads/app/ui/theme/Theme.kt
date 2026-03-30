package com.spotibyeads.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.White,
    secondary = SpotifyGreenDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariant,
    background = SpotifyBlack,
    onBackground = OnSurfaceLight,
    error = ErrorRed,
)

@Composable
fun SpotiByeAdsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
