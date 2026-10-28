package com.envio.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun EnvioTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF4DB6AC),
            onPrimary = Color(0xFF00201C),
            primaryContainer = Color(0xFF00504A),
            onPrimaryContainer = Color(0xFFB2DFDB)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF00695C),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFB2DFDB),
            onPrimaryContainer = Color(0xFF00201C)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
