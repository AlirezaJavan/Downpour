package io.github.alirezajavan.downpour.sample.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF3D5AFE)
private val IndigoDark = Color(0xFF8C9EFF)
private val Teal = Color(0xFF00BFA5)
private val TealDark = Color(0xFF64FFDA)
private val Amber = Color(0xFFFFAB40)
private val AmberDark = Color(0xFFFFD180)

private val LightColors =
    lightColorScheme(
        primary = Indigo,
        onPrimary = Color.White,
        secondary = Teal,
        onSecondary = Color.Black,
        tertiary = Amber,
        onTertiary = Color.Black,
    )

private val DarkColors =
    darkColorScheme(
        primary = IndigoDark,
        onPrimary = Color.Black,
        secondary = TealDark,
        onSecondary = Color.Black,
        tertiary = AmberDark,
        onTertiary = Color.Black,
    )

@Composable
fun DownpourSampleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
