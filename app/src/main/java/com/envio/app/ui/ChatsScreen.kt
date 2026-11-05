package com.envio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.envio.app.Perms
import com.envio.app.core
import com.envio.app.data.Contact
import com.envio.app.data.Message
import com.envio.app.data.UnreadRow
import com.envio.app.service.EnvioService
import com.envio.app.ui.components.Avatar
import com.envio.app.ui.components.MessageTicks
import com.envio.app.ui.theme.Dim
import com.envio.app.ui.theme.Space
import com.envio.app.ui.theme.Txt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFmt = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

fun previewOf(m: Message?): String = when (m?.type) {
    null -> "Toca para escribir"
    "TEXT" -> m.text ?: ""
    "IMAGE" -> "Imagen"
    "AUDIO" -> "Audio"
    else -> "Archivo: ${m.fileName ?: ""}"
}

private fun stamp(ts: Long): String {
    val day = 86_400_000L
    return if (System.currentTimeMillis() - ts < day) timeFmt.format(Date(ts)) else dateFmt.format(Date(ts))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onStarred: () -> Unit,
    onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val core = ctx.core
    val c = envioColors

    LaunchedEffect(Unit) {
        if (core.identity.isSetup && Perms.allGranted(ctx)) EnvioService.start(ctx)
    }

    val contacts by remember { core.db.contactDao().observeAll() }.collectAsState(initial = emptyList<Contact>())
    val last by remember { core.db.messageDao().lastMessages() }.collectAsState(initial = emptyList<Message>())
    val unread by remember { core.db.messageDao().unreadCounts() }.collectAsState(initial = emptyList<UnreadRow>())
    val peers by core.nearby.connectedPeers.collectAsState()
    val lastMap = last.associateBy { it.contactId }
    val unreadMap = unread.associate { it.contactId to it.n }

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }

    val sorted = contacts
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedByDescending { lastMap[it.id]?.ts ?: 0L }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Buscar") }, singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else Text("ENVIO", style = Txt.lg, color = c.accent)
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(
                            if (searching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searching) "Cerrar busqueda" else "Buscar"
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Mas opciones")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Mensajes destacados") }, onClick = { menu = false; onStarred() })
                            DropdownMenuItem(text = { Text("Ajustes") }, onClick = { menu = false; onSettings() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = c.accent, contentColor = c.onAccent) {
                Icon(Icons.Default.Add, contentDescription = "Agregar contacto")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            StatusBanner()
            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(Space.s7), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank())
                            "Aun no tienes contactos.\nToca + para mostrar tu codigo QR o escanear el de otra persona."
                        else "Nadie coincide con \"$query\".",
                        style = Txt.base, color = c.textMuted
                    )
                }
            } else {
                LazyColumn {
                    items(sorted, key = { it.id }) { ct ->
                        val m = lastMap[ct.id]
                        val n = unreadMap[ct.id] ?: 0
                        Row(
                            Modifier.fillMaxWidth().height(Dim.chatRow)
                                .clickable { onOpen(ct.id) }
                                .padding(horizontal = Space.s5),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(ct.id, ct.name, Dim.avatarList, photoPath = ct.photoPath)
                            Column(Modifier.weight(1f).padding(horizontal = Space.s4)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(ct.name, style = Txt.base, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (ct.id in peers) Text("  cerca", style = Txt.meta, color = c.accent)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (m != null && m.outgoing) {
                                        MessageTicks(m.status)
                                        Box(Modifier.size(Space.s2))
                                    }
                                    Text(
                                        previewOf(m), style = Txt.sm, color = c.textMuted,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                                if (m != null) Text(stamp(m.ts), style = Txt.meta, color = if (n > 0) c.accent else c.textMuted)
                                if (n > 0) {
                                    Box(
                                        Modifier.background(c.accent, CircleShape).size(Space.s6),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(if (n > 99) "99+" else "$n", style = Txt.meta, color = c.onAccent)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
