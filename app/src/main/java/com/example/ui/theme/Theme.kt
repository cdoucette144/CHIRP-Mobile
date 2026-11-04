package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AmberVfoPrimary,
    onPrimary = Color(0xFF140D00),
    primaryContainer = Color(0xFF3B2500),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = CyanRepeaterSecondary,
    onSecondary = Color(0xFF001F26),
    secondaryContainer = Color(0xFF003642),
    onSecondaryContainer = Color(0xFFB8EAFF),
    tertiary = RxSafeGreen,
    onTertiary = Color(0xFF00201D),
    tertiaryContainer = Color(0xFF003732),
    onTertiaryContainer = Color(0xFF75F8E8),
    error = TxHotCoral,
    onError = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = TacticalSlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedConsoleSurface,
    onSurfaceVariant = TextSecondary,
    outline = ConsoleBorder
)

private val LightColorScheme = lightColorScheme(
    primary = AmberLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF291800),
    secondary = CyanLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8EAFF),
    onSecondaryContainer = Color(0xFF001F26),
    tertiary = Color(0xFF006B60),
    onTertiary = Color.White,
    error = TxHotCoral,
    onError = Color.White,
    background = LightFieldBg,
    onBackground = Color(0xFF111827),
    surface = LightFieldSurface,
    onSurface = Color(0xFF111827),
    surfaceVariant = LightFieldCard,
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB)
)

@Composable
fun ChirpFieldTheme(
    darkTheme: Boolean = true,
    palette: String = "Amber / Cyan",
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val accents = when (palette) {
        "Teal / Blue" -> if (darkTheme) Color(0xFF61DEC6) to Color(0xFFAAC7FF) else Color(0xFF006B5B) to Color(0xFF355E9B)
        "Purple / Rose" -> if (darkTheme) Color(0xFFD3BCFF) to Color(0xFFF6B5CE) else Color(0xFF654894) to Color(0xFF893D5D)
        "Blue / Lime" -> if (darkTheme) Color(0xFFA6C8FF) to Color(0xFFC8DE84) else Color(0xFF315E9D) to Color(0xFF4B641B)
        else -> null
    }
    val colorScheme = accents?.let { (primary, secondary) ->
        base.copy(
            primary = primary,
            secondary = secondary,
            primaryContainer = primary.copy(alpha = 0.18f),
            onPrimaryContainer = base.onSurface,
            secondaryContainer = secondary.copy(alpha = 0.18f),
            onSecondaryContainer = base.onSurface
        )
    } ?: base

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
