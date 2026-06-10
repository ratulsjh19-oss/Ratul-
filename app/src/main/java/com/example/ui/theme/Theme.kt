package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Clean Minimalism Design Palette
val DarkPrimary = Color(0xFFD0BCFF)       // Soft Lavender
val DarkSecondary = Color(0xFFCCC2DC)     // Pale Gold/Sage Lavender
val DarkTertiary = Color(0xFFEFB8C8)      // Rose Mint
val DarkBackground = Color(0xFF141218)    // Ultra-dark Obsidian
val DarkSurface = Color(0xFF1D1B22)       // Minimalist Dark Card
val DarkOnPrimary = Color(0xFF381E72)
val DarkOnBackground = Color(0xFFE6E1E5)
val DarkOnSurface = Color(0xFFE6E1E5)

val LightPrimary = Color(0xFF6750A4)      // Deep Velvet Indigo/Purple
val LightSecondary = Color(0xFF625B71)    // Slate Lavender
val LightTertiary = Color(0xFF7D5260)     // Mauve Accent
val LightBackground = Color(0xFFFEF7FF)   // Light Minimalist Cream Background
val LightSurface = Color(0xFFF7F2FA)      // Very Soft Lavender Card
val LightOnPrimary = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF1D1B20)
val LightOnSurface = Color(0xFF1D1B20)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkOnPrimary,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightOnPrimary,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B)
)

@Composable
fun BankingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // We override dynamicColors to preserve the highly customized, themed premium look of the bank app
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
