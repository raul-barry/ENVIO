package com.envio.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.envio.app.core
import com.envio.app.ui.theme.DarkPalette
import com.envio.app.ui.theme.EnvioPalette
import com.envio.app.ui.theme.LightPalette

val LocalEnvioPalette = staticCompositionLocalOf { LightPalette }

/** Colores propios de ENVIO que Material 3 no tiene: burbujas, fondo del chat, palomitas. */
val envioColors: EnvioPalette
    @Composable @ReadOnlyComposable get() = LocalEnvioPalette.current

@Composable
fun EnvioTheme(content: @Composable () -> Unit) {
    val system = isSystemInDarkTheme()
    val dark = when (LocalContext.current.core.settings.theme) {
        "dark" -> true
        "light" -> false
        else -> system
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            primaryContainer = p.bubbleOut, onPrimaryContainer = p.text,
            background = p.bg, onBackground = p.text,
            surface = p.bg, onSurface = p.text,
            surfaceVariant = p.surface, onSurfaceVariant = p.textMuted,
            outline = p.borderInput, outlineVariant = p.border,
            error = p.danger, errorContainer = p.dangerContainer, onErrorContainer = p.onDangerContainer
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            primaryContainer = p.bubbleOut, onPrimaryContainer = p.text,
            background = p.bg, onBackground = p.text,
            surface = p.bg, onSurface = p.text,
            surfaceVariant = p.surface, onSurfaceVariant = p.textMuted,
            outline = p.borderInput, outlineVariant = p.border,
            error = p.danger, errorContainer = p.dangerContainer, onErrorContainer = p.onDangerContainer
        )
    }
    CompositionLocalProvider(LocalEnvioPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
