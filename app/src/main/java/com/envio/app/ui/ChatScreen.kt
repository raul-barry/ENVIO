package com.envio.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.envio.app.core
import com.envio.app.data.Contact
import com.envio.app.data.Message
import com.envio.app.ui.components.Avatar
import com.envio.app.ui.components.ChatBubble
import com.envio.app.ui.components.DateChip
import com.envio.app.ui.components.ReplyQuote
import com.envio.app.ui.theme.Dim
import com.envio.app.ui.theme.Radius
import com.envio.app.ui.theme.Space
import com.envio.app.ui.theme.Txt
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dayFmt = SimpleDateFormat("d 'de' MMMM yyyy", Locale.getDefault())

private fun dayKey(ts: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(ts: Long): String {
    val today = dayKey(System.currentTimeMillis())
    val k = dayKey(ts)
    return when {
        k == today -> "Hoy"
        k == dayKey(System.currentTimeMillis() - 86_400_000L) -> "Ayer"
        else -> dayFmt.format(Date(ts))
    }
}

private fun copyText(ctx: Context, s: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ENVIO", s))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(contactId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val core = ctx.core
    val c = envioColors
    val scope = rememberCoroutineScope()

    val contact by remember(contactId) { core.db.contactDao().observe(contactId) }
        .collectAsState(initial = null as Contact?)
    val messages by remember(contactId) { core.db.messageDao().observe(contactId) }
        .collectAsState(initial = emptyList<Message>())
    val peers by core.nearby.connectedPeers.collectAsState()
    val onlineStatus by core.online.status.collectAsState()
    val onlineUp = onlineStatus.startsWith("Internet: conectado")

    var text by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val recorder = remember { VoiceRecorder(ctx) }
    val listState = rememberLazyListState()
    val size = when (core.settings.fontSize) { "small" -> 14.sp; "large" -> 19.sp; else -> 16.sp }
    val selecting = selected.isNotEmpty()

    // Chat abierto: marca como leido mientras la pantalla esta visible.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(contactId, owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                core.openChat = contactId
                scope.launch { core.db.contactDao().markSeen(contactId); core.markRead(contactId) }
            } else if (e == Lifecycle.Event.ON_PAUSE) {
                core.openChat = null
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            core.openChat = null
            Player.stop()
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
            if (core.openChat == contactId) core.markRead(contactId)
        }
    }
    BackHandler(enabled = selecting) { selected.clear() }

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

    fun send() {
        if (text.isBlank()) return
        core.sendText(contactId, text.trim(), replyTo)
        text = ""
        replyTo = null
    }

    val selMessages = messages.filter { it.id in selected }
    val allStarred = selMessages.isNotEmpty() && selMessages.all { it.starred }
    val direct = contactId in peers
    val subtitle = when {
        direct -> "cerca - conexion directa"
        onlineUp -> "por internet"
        else -> "sin conexion: se enviara al reconectar"
    }

    Scaffold(
        containerColor = c.chatBg,
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size}", style = Txt.lg) },
                    navigationIcon = {
                        IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar seleccion")
                        }
                    },
                    actions = {
                        if (selected.size == 1) {
                            IconButton(onClick = {
                                replyTo = selMessages.firstOrNull(); selected.clear()
                            }) { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Responder") }
                        }
                        IconButton(onClick = {
                            core.setStarred(selected.toList(), !allStarred); selected.clear()
                        }) {
                            Icon(
                                if (allStarred) Icons.Default.StarBorder else Icons.Default.Star,
                                contentDescription = if (allStarred) "Quitar de destacados" else "Destacar"
                            )
                        }
                        if (selMessages.all { it.type == "TEXT" }) {
                            IconButton(onClick = {
                                copyText(ctx, selMessages.joinToString("\n") { it.text ?: "" })
                                selected.clear()
                            }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copiar") }
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(contactId, contact?.name ?: "", Dim.avatarHeader, photoPath = contact?.photoPath)
                            Column(Modifier.padding(start = Space.s4)) {
                                Text(contact?.name ?: "", style = Txt.base, maxLines = 1)
                                Text(subtitle, style = Txt.meta, color = c.textMuted, maxLines = 1)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface)
                )
            }
        },
        bottomBar = {
            Column(Modifier.imePadding().background(c.chatBg)) {
                replyTo?.let { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.s3, vertical = Space.s2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReplyQuote(
                            author = if (r.outgoing) "Tu" else contact?.name,
                            preview = previewOf(r),
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { replyTo = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar respuesta")
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.s2, vertical = Space.s2),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = c.bg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(onClick = { attach = true }, modifier = Modifier.size(Dim.touchMin)) {
                                    Icon(Icons.Default.AttachFile, contentDescription = "Adjuntar")
                                }
                                DropdownMenu(expanded = attach, onDismissRequest = { attach = false }) {
                                    DropdownMenuItem(text = { Text("Imagen") }, onClick = { attach = false; pick.launch("image/*") })
                                    DropdownMenuItem(text = { Text("Audio") }, onClick = { attach = false; pick.launch("audio/*") })
                                    DropdownMenuItem(text = { Text("Cualquier archivo") }, onClick = { attach = false; pick.launch("*/*") })
                                }
                            }
                            if (recording) {
                                Text(
                                    "Grabando nota de voz...",
                                    color = c.danger,
                                    modifier = Modifier.weight(1f).padding(horizontal = Space.s3)
                                )
                            } else {
                                TextField(
                                    value = text,
                                    onValueChange = { text = it.take(4000) },
                                    placeholder = { Text("Mensaje") },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 5,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = c.bg,
                                        unfocusedContainerColor = c.bg,
                                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = if (core.settings.enterIsSend) ImeAction.Send else ImeAction.Default
                                    ),
                                    keyboardActions = KeyboardActions(onSend = { send() })
                                )
                            }
                        }
                    }
                    Box(
                        Modifier.padding(start = Space.s2).size(Dim.fab).background(c.accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (text.isBlank() || recording) {
                            IconButton(onClick = { toggleRecord() }) {
                                Icon(
                                    if (recording) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                                    contentDescription = if (recording) "Enviar nota de voz" else "Grabar nota de voz",
                                    tint = c.onAccent
                                )
                            }
                        } else {
                            IconButton(onClick = { send() }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = c.onAccent)
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!direct && !onlineUp) {
                Text(
                    "Sin conexion directa ni internet. Los mensajes quedan pendientes y salen solos.",
                    style = Txt.xs, color = c.onDangerContainer,
                    modifier = Modifier.fillMaxWidth().background(c.dangerContainer).padding(Space.s3)
                )
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                    val prev = messages.getOrNull(i - 1)
                    if (prev == null || dayKey(prev.ts) != dayKey(m.ts)) DateChip(dayLabel(m.ts))
                    val first = prev == null || prev.outgoing != m.outgoing || dayKey(prev.ts) != dayKey(m.ts)
                    ChatBubble(
                        m = m,
                        first = first,
                        selected = m.id in selected,
                        selecting = selecting,
                        textSize = size,
                        quoteAuthor = null,
                        onClick = { if (selecting) { if (m.id in selected) selected.remove(m.id) else selected.add(m.id) } },
                        onLongClick = { if (m.id in selected) selected.remove(m.id) else selected.add(m.id) },
                        onQuoteClick = { qid ->
                            val idx = messages.indexOfFirst { it.id == qid }
                            if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                        }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar ${selected.size} mensaje(s)?") },
            text = { Text("Se borran solo de este telefono. La otra persona los conserva.") },
            confirmButton = {
                TextButton(onClick = {
                    core.deleteForMe(selected.toList()); selected.clear(); confirmDelete = false
                }) { Text("Eliminar para mi") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}
