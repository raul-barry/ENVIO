package com.envio.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.envio.app.core
import com.envio.app.data.Contact
import com.envio.app.data.Message
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val msgTimeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(contactId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val core = ctx.core
    val scope = rememberCoroutineScope()

    val contact by remember(contactId) { core.db.contactDao().observe(contactId) }
        .collectAsState(initial = null as Contact?)
    val messages by remember(contactId) { core.db.messageDao().observe(contactId) }
        .collectAsState(initial = emptyList<Message>())
    val peers by core.nearby.connectedPeers.collectAsState()

    var text by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(ctx) }
    val listState = rememberLazyListState()

    DisposableEffect(contactId) {
        core.openChat = contactId
        scope.launch { core.db.contactDao().markSeen(contactId) }
        onDispose {
            core.openChat = null
            Player.stop()
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { core.sendFile(contactId, uri) }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) recording = recorder.start()
    }

    fun toggleRecord() {
        if (!recording) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) recording = recorder.start() else micPerm.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            recording = false
            recorder.stop()?.let { f ->
                scope.launch { core.sendLocalFile(contactId, f, "nota_de_voz.m4a", "audio/mp4") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(contact?.name ?: "")
                        Text(
                            if (contactId in peers) "Cerca - conexion directa" else "Sin conexion directa",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("Adjuntar") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Imagen") }, onClick = { menu = false; pick.launch("image/*") })
                            DropdownMenuItem(text = { Text("Audio") }, onClick = { menu = false; pick.launch("audio/*") })
                            DropdownMenuItem(text = { Text("Cualquier archivo") }, onClick = { menu = false; pick.launch("*/*") })
                        }
                    }
                    if (recording) {
                        Text(
                            "Grabando nota de voz...",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                    } else {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(4000) },
                            placeholder = { Text("Mensaje") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4
                        )
                    }
                    if (text.isBlank() || recording) {
                        TextButton(onClick = { toggleRecord() }) { Text(if (recording) "Enviar" else "Voz") }
                    } else {
                        IconButton(onClick = {
                            core.sendText(contactId, text)
                            text = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                        }
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(pad).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(messages, key = { it.id }) { m -> Bubble(m) }
        }
    }
}

@Composable
private fun Bubble(m: Message) {
    val ctx = LocalContext.current
    val out = m.outgoing
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (out) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (out) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(8.dp)) {
                when (m.type) {
                    "IMAGE" -> {
                        if (m.filePath != null) {
                            AsyncImage(
                                model = File(m.filePath),
                                contentDescription = m.fileName,
                                modifier = Modifier
                                    .sizeIn(maxWidth = 240.dp, maxHeight = 300.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { Media.open(ctx, m.filePath, m.mime) }
                            )
                        }
                    }
                    "AUDIO" -> {
                        val playing = Player.current == m.filePath
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { m.filePath?.let { Player.toggle(it) } }) {
                                Text(if (playing) "Detener" else "Reproducir")
                            }
                            Text(humanSize(m.size), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    "FILE" -> Text(
                        "${m.fileName ?: "archivo"} (${humanSize(m.size)})",
                        modifier = Modifier.clickable { Media.open(ctx, m.filePath, m.mime) }
                    )
                    else -> Text(m.text ?: "")
                }
                val footer = buildString {
                    append(msgTimeFmt.format(Date(m.ts)))
                    if (out) {
                        append("  ")
                        append(
                            when {
                                m.status == "PENDING" -> "Pendiente"
                                m.status == "FAILED" -> "Error"
                                m.channel == "NEARBY" -> "Enviado directo"
                                else -> "Enviado por internet"
                            }
                        )
                    } else if (m.channel == "NEARBY") {
                        append("  Directo")
                    }
                }
                Text(
                    footer,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
