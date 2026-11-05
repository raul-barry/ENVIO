package com.envio.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.envio.app.ui.theme.Dim
import com.envio.app.ui.theme.Radius
import com.envio.app.ui.theme.Space
import com.envio.app.ui.theme.Txt

private fun meta(ctx: Context, uri: Uri): Triple<String, Long, String> {
    var name = "archivo"
    var size = 0L
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val z = c.getColumnIndex(OpenableColumns.SIZE)
            if (n >= 0) c.getString(n)?.let { name = it }
            if (z >= 0 && !c.isNull(z)) size = c.getLong(z)
        }
    }
    return Triple(name, size, ctx.contentResolver.getType(uri) ?: "")
}

/** Vista previa antes de enviar: imagenes a pantalla completa con pie de foto propio; otros archivos en tarjeta. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SendPreview(
    uris: List<Uri>,
    toName: String,
    onCancel: () -> Unit,
    onSend: (List<Pair<Uri, String>>) -> Unit
) {
    val ctx = LocalContext.current
    val c = envioColors
    val captions = remember(uris) { mutableStateMapOf<Int, String>() }
    val pager = rememberPagerState { uris.size }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize().padding(bottom = 120.dp, top = 56.dp)) { i ->
                val (name, size, mime) = remember(uris[i]) { meta(ctx, uris[i]) }
                if (mime.startsWith("image/")) {
                    AsyncImage(
                        model = uris[i], contentDescription = name,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                (name.substringAfterLast('.', "").uppercase().ifEmpty { "ARCHIVO" }),
                                style = Txt.xl, color = Color.White
                            )
                            Text(name, style = Txt.base, color = Color.White, modifier = Modifier.padding(Space.s4))
                            Text(humanSize(size), style = Txt.sm, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            IconButton(onClick = onCancel, modifier = Modifier.padding(Space.s3).align(Alignment.TopStart)) {
                Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color.White)
            }
            if (uris.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${uris.size}", style = Txt.sm, color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = Space.s5)
                )
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().padding(Space.s3)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = captions[pager.currentPage] ?: "",
                        onValueChange = { captions[pager.currentPage] = it.take(1000) },
                        placeholder = { Text("Agrega un pie de foto") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radius.pill),
                        maxLines = 3,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Box(
                        Modifier.padding(start = Space.s3).size(Dim.fab).background(c.accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            onSend(uris.mapIndexed { i, u -> u to (captions[i] ?: "") })
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = c.onAccent)
                        }
                    }
                }
                Text("Para $toName", style = Txt.xs, color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = Space.s2, start = Space.s4))
            }
        }
    }
}
