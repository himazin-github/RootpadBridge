package com.rootpad.bridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DDC84),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF00532C),
    onPrimaryContainer = Color(0xFF9BF6C0),
    secondary = Color(0xFF8FCF9F),
    onSecondary = Color(0xFF00391D),
    secondaryContainer = Color(0xFF1C5136),
    onSecondaryContainer = Color(0xFFAFEBBF),
    tertiary = Color(0xFF8DC9E8),
    onTertiary = Color(0xFF003548),
    tertiaryContainer = Color(0xFF1C4D62),
    onTertiaryContainer = Color(0xFFC5E9FF),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C2D26),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE1E3E5),
    surface = Color(0xFF141920),
    onSurface = Color(0xFFE1E3E5),
    surfaceVariant = Color(0xFF1C232C),
    onSurfaceVariant = Color(0xFFBEC8CF),
    outline = Color(0xFF3A444E),
    surfaceContainerHigh = Color(0xFF1A212A),
    surfaceContainer = Color(0xFF161C24)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006D3B),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF40654D),
    background = Color(0xFFF6FBF4),
    surface = Color(0xFFFBFDF8),
    onBackground = Color(0xFF191C1A),
    onSurface = Color(0xFF191C1A)
)

/**
 * ダークテーマを基本としたテーマ。システム設定がライトでも既定はダーク。
 */
@Composable
fun RootPadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    forceDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme || forceDark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
