package com.envio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.envio.app.ui.envioColors
import java.io.File

/** Foto si hay, o la inicial sobre uno de 8 colores elegido por el ID (siempre el mismo para la misma persona). */
@Composable
fun Avatar(
    id: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    photoPath: String? = null
) {
    val c = envioColors
    val colors = listOf(c.avatar1, c.avatar2, c.avatar3, c.avatar4, c.avatar5, c.avatar6, c.avatar7, c.avatar8)
    val bg = colors[(id.hashCode() and 0x7fffffff) % colors.size]
    val hasPhoto = remember(photoPath) { photoPath != null && File(photoPath).exists() }
    Box(
        modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (hasPhoto && photoPath != null) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                color = c.avatarText,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}
