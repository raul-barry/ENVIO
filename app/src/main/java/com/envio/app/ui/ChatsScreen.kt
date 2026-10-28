package com.envio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.envio.app.Perms
import com.envio.app.core
import com.envio.app.data.Contact
import com.envio.app.data.Message
import com.envio.app.service.EnvioService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

fun previewOf(m: Message?): String = when (m?.type) {
    null -> "Toca para escribir"
    "TEXT" -> m.text ?: ""
    "IMAGE" -> "Imagen"
    "AUDIO" -> "Audio"
    else -> "Archivo: ${m.fileName ?: ""}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val core = ctx.core

    LaunchedEffect(Unit) {
        if (core.identity.isSetup && Perms.allGranted(ctx)) EnvioService.start(ctx)
    }

    val contacts by remember { core.db.contactDao().observeAll() }.collectAsState(initial = emptyList<Contact>())
    val last by remember { core.db.messageDao().lastMessages() }.collectAsState(initial = emptyList<Message>())
    val peers by core.nearby.connectedPeers.collectAsState()
    val lastMap = last.associateBy { it.contactId }
    val sorted = contacts.sortedByDescending { lastMap[it.id]?.ts ?: 0L }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ENVIO") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Agregar contacto")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            StatusBanner()
            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Aun no tienes contactos.\nToca + para mostrar tu codigo QR o escanear el de otra persona.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn {
                    items(sorted, key = { it.id }) { c ->
                        val m = lastMap[c.id]
                        val near = c.id in peers
                        ListItem(
                            modifier = Modifier.clickable { onOpen(c.id) },
                            leadingContent = {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        c.name.take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            },
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (c.isNew) Text("  NUEVO", color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall)
                                    if (near) Text("  cerca", color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            supportingContent = {
                                Text(previewOf(m), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                if (m != null) Text(timeFmt.format(Date(m.ts)),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
