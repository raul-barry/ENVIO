package com.envio.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.envio.app.core
import com.envio.app.ui.components.Avatar
import com.envio.app.ui.theme.Dim
import com.envio.app.ui.theme.Space
import com.envio.app.ui.theme.Txt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        )
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) { content() }
    }
}

@Composable
private fun NavRow(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.s5, vertical = Space.s4)) {
        Text(title, style = Txt.base)
        Text(sub, style = Txt.sm, color = envioColors.textMuted)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.s5, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Txt.base)
            Text(sub, style = Txt.sm, color = envioColors.textMuted)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.s5),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = Txt.base, modifier = Modifier.padding(start = Space.s4).height(Dim.touchMin).wrapV())
    }
}

private fun Modifier.wrapV() = this.padding(vertical = Space.s4)

@Composable
fun SettingsHome(onBack: () -> Unit, onNav: (String) -> Unit) {
    val core = LocalContext.current.core
    val me = core.identity
    Page("Ajustes", onBack) {
        Row(
            Modifier.fillMaxWidth().clickable { onNav("settings/profile") }.padding(Space.s5),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(me.id, me.name, Dim.avatarLarge / 2, photoPath = me.photoPath)
            Column(Modifier.padding(start = Space.s5)) {
                Text(me.name, style = Txt.lg)
                Text(me.about.ifBlank { "Sin descripcion" }, style = Txt.sm, color = envioColors.textMuted)
            }
        }
        HorizontalDivider(color = envioColors.border)
        NavRow("Chats", "Tema, tamano de letra, tecla Enter") { onNav("settings/chats") }
        NavRow("Notificaciones", "Avisos de mensajes nuevos") { onNav("settings/notifications") }
        NavRow("Privacidad", "Confirmacion de lectura") { onNav("settings/privacy") }
        NavRow("Almacenamiento", "Espacio usado y limpieza") { onNav("settings/storage") }
        val ctx = LocalContext.current
        NavRow("Bateria", "Permitir que ENVIO siga conectado con la pantalla apagada") {
            try {
                ctx.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                )
            } catch (_: Exception) {
                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}

@Composable
fun ProfileSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val core = ctx.core
    val scope = rememberCoroutineScope()
    val me = core.identity
    var name by remember { mutableStateOf(me.name) }
    var about by remember { mutableStateOf(me.about) }
    var photo by remember { mutableStateOf(me.photoPath) }
    var saved by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch { core.setProfilePhoto(uri); photo = core.identity.photoPath }
    }
    Page("Perfil", onBack) {
        Column(Modifier.padding(Space.s5), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            Avatar(me.id, name, Dim.avatarLarge, Modifier.clickable { pick.launch("image/*") }, photoPath = photo)
            Button(onClick = { pick.launch("image/*") }) { Text("Cambiar foto") }
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(30); saved = false },
                label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = about, onValueChange = { about = it.take(120); saved = false },
                label = { Text("Info") }, modifier = Modifier.fillMaxWidth()
            )
            Text("Tu ID: ${me.id}", style = Txt.sm, color = envioColors.textMuted)
            Button(onClick = { core.saveProfile(name, about); saved = true }) { Text("Guardar") }
            if (saved) Text("Guardado.", style = Txt.sm, color = envioColors.success)
        }
    }
}

@Composable
fun ChatsSettings(onBack: () -> Unit) {
    val s = LocalContext.current.core.settings
    Page("Chats", onBack) {
        Text("Tema", style = Txt.xs, color = envioColors.accent, modifier = Modifier.padding(Space.s5))
        ChoiceRow("Segun el sistema", s.theme == "system") { s.updateTheme("system") }
        ChoiceRow("Claro", s.theme == "light") { s.updateTheme("light") }
        ChoiceRow("Oscuro", s.theme == "dark") { s.updateTheme("dark") }
        Text("Tamano de letra", style = Txt.xs, color = envioColors.accent, modifier = Modifier.padding(Space.s5))
        ChoiceRow("Pequena", s.fontSize == "small") { s.updateFontSize("small") }
        ChoiceRow("Mediana", s.fontSize == "medium") { s.updateFontSize("medium") }
        ChoiceRow("Grande", s.fontSize == "large") { s.updateFontSize("large") }
        Spacer(Modifier.height(Space.s3))
        SwitchRow("Enter envia", "La tecla Enter manda el mensaje", s.enterIsSend) { s.updateEnterIsSend(it) }
    }
}

@Composable
fun NotificationsSettings(onBack: () -> Unit) {
    val s = LocalContext.current.core.settings
    Page("Notificaciones", onBack) {
        SwitchRow("Avisos de mensajes", "Muestra una notificacion al recibir un mensaje", s.notifications) {
            s.updateNotifications(it)
        }
    }
}

@Composable
fun PrivacySettings(onBack: () -> Unit) {
    val s = LocalContext.current.core.settings
    Page("Privacidad", onBack) {
        SwitchRow(
            "Confirmaciones de lectura",
            "Si lo apagas, no avisas cuando lees y tampoco ves cuando te leen (palomitas azules).",
            s.readReceipts
        ) { s.updateReadReceipts(it) }
    }
}

@Composable
fun StorageSettings(onBack: () -> Unit) {
    val core = LocalContext.current.core
    var media by remember { mutableStateOf(core.mediaBytes()) }
    var temp by remember { mutableStateOf(core.tempBytes()) }
    Page("Almacenamiento", onBack) {
        Column(Modifier.padding(Space.s5), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            Text("Archivos recibidos y enviados: ${humanSize(media)}", style = Txt.base)
            Text("Archivos temporales: ${humanSize(temp)}", style = Txt.base)
            Button(onClick = { core.clearTemp(); temp = core.tempBytes() }) { Text("Limpiar temporales") }
        }
    }
}
