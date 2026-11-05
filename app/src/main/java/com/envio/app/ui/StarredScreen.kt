package com.envio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.envio.app.core
import com.envio.app.data.Contact
import com.envio.app.data.Message
import com.envio.app.ui.theme.Space
import com.envio.app.ui.theme.Txt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val core = LocalContext.current.core
    val c = envioColors
    val msgs by remember { core.db.messageDao().observeStarred() }.collectAsState(initial = emptyList<Message>())
    val contacts by remember { core.db.contactDao().observeAll() }.collectAsState(initial = emptyList<Contact>())
    val names = contacts.associate { it.id to it.name }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Mensajes destacados") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        )
    }) { pad ->
        if (msgs.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(Space.s7), contentAlignment = Alignment.Center) {
                Text(
                    "Aun no destacaste nada.\nManten pulsado un mensaje y toca la estrella.",
                    style = Txt.base, color = c.textMuted
                )
            }
        } else {
            LazyColumn(Modifier.padding(pad)) {
                items(msgs, key = { it.id }) { m ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onOpen(m.contactId) }.padding(Space.s5)
                    ) {
                        Text(
                            (if (m.outgoing) "Tu" else names[m.contactId] ?: "?") + " - " + fmt.format(Date(m.ts)),
                            style = Txt.xs, color = c.accent
                        )
                        Text(previewOf(m), style = Txt.base, color = c.text)
                    }
                    HorizontalDivider(color = c.border)
                }
            }
        }
    }
}
