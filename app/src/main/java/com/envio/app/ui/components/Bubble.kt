package com.envio.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.envio.app.data.Message
import com.envio.app.ui.Media
import com.envio.app.ui.Player
import com.envio.app.ui.envioColors
import com.envio.app.ui.humanSize
import com.envio.app.ui.theme.Dim
import com.envio.app.ui.theme.Radius
import com.envio.app.ui.theme.Space
import com.envio.app.ui.theme.Txt
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

/**
 * Burbuja de mensaje. [first] = primer mensaje de una racha del mismo autor: lleva la "cola"
 * (una esquina con radio chico) para que se vea quien habla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    m: Message,
    first: Boolean,
    selected: Boolean,
    selecting: Boolean,
    textSize: TextUnit,
    quoteAuthor: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuoteClick: (String) -> Unit
) {
    val c = envioColors
    val ctx = LocalContext.current
    val out = m.outgoing
    val shape = RoundedCornerShape(
        topStart = if (!out && first) Dim.bubbleTail else Dim.bubbleRadius,
        topEnd = if (out && first) Dim.bubbleTail else Dim.bubbleRadius,
        bottomEnd = Dim.bubbleRadius,
        bottomStart = Dim.bubbleRadius
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(if (selected) c.accent.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        val maxBubble = maxWidth * Dim.bubbleMaxPercent
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = Space.s3, end = Space.s3,
                    top = if (first) Space.s2 else Space.s1, bottom = Space.s1
                ),
            horizontalArrangement = if (out) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                color = if (out) c.bubbleOut else c.bubbleIn,
                shape = shape,
                modifier = Modifier.widthIn(max = maxBubble)
            ) {
                Column(Modifier.padding(horizontal = Space.s3, vertical = Space.s2)) {
                    val qid = m.replyToId
                    if (qid != null && !m.replyPreview.isNullOrBlank()) {
                        ReplyQuote(
                            author = quoteAuthor,
                            preview = m.replyPreview,
                            onClick = { if (!selecting) onQuoteClick(qid) }
                        )
                        Spacer(Modifier.height(Space.s2))
                    }
                    when (m.type) {
                        "IMAGE" -> if (m.filePath != null) {
                            AsyncImage(
                                model = File(m.filePath),
                                contentDescription = m.fileName ?: "Imagen",
                                modifier = Modifier
                                    .sizeIn(maxWidth = 240.dp, maxHeight = 300.dp)
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .clickable(enabled = !selecting) { Media.open(ctx, m.filePath, m.mime) }
                            )
                        }
                        "AUDIO" -> {
                            val playing = Player.current == m.filePath
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    enabled = !selecting,
                                    onClick = { m.filePath?.let { Player.toggle(it) } }
                                ) { Text(if (playing) "Detener" else "Reproducir") }
                                Text(humanSize(m.size), style = Txt.meta, color = c.textMuted)
                            }
                        }
                        "FILE" -> Text(
                            "${m.fileName ?: "archivo"} (${humanSize(m.size)})",
                            style = Txt.base.copy(fontSize = textSize),
                            color = c.text,
                            modifier = Modifier.clickable(enabled = !selecting) {
                                Media.open(ctx, m.filePath, m.mime)
                            }
                        )
                        else -> Text(
                            m.text ?: "",
                            style = Txt.base.copy(fontSize = textSize, lineHeight = textSize * 1.35f),
                            color = c.text
                        )
                    }
                    if (m.type != "TEXT" && !m.text.isNullOrBlank()) {
                        Text(
                            m.text,
                            style = Txt.base.copy(fontSize = textSize),
                            color = c.text,
                            modifier = Modifier.padding(top = Space.s2)
                        )
                    }
                    Row(
                        Modifier.align(Alignment.End).padding(top = Space.s1),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (m.starred) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Destacado",
                                tint = c.textMuted,
                                modifier = Modifier.height(12.dp).width(12.dp)
                            )
                            Spacer(Modifier.width(Space.s2))
                        }
                        Text(timeFmt.format(Date(m.ts)), style = Txt.meta, color = c.textMuted)
                        if (out) {
                            Spacer(Modifier.width(Space.s2))
                            MessageTicks(m.status)
                        }
                    }
                }
            }
        }
    }
}

/** Cita de un mensaje al que se responde: barra de color a la izquierda, autor y vista previa. */
@Composable
fun ReplyQuote(author: String?, preview: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = envioColors
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(Radius.sm))
            .background(c.accent.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(c.accent))
        Column(Modifier.padding(horizontal = Space.s3, vertical = Space.s2)) {
            if (author != null) {
                Text(author, style = Txt.xs.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
            }
            Text(
                preview,
                style = Txt.sm,
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Pastilla con el dia ("Hoy", "Ayer", fecha) entre grupos de mensajes. */
@Composable
fun DateChip(label: String) {
    val c = envioColors
    Box(Modifier.fillMaxWidth().padding(vertical = Space.s3), contentAlignment = Alignment.Center) {
        Surface(color = c.bg.copy(alpha = 0.9f), shape = RoundedCornerShape(Radius.pill)) {
            Text(
                label,
                style = Txt.xs,
                color = c.textMuted,
                modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
                fontStyle = FontStyle.Normal
            )
        }
    }
}
