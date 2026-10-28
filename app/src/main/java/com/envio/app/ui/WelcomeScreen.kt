package com.envio.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.envio.app.Perms
import com.envio.app.core
import com.envio.app.service.EnvioService

@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val core = ctx.core
    val alreadySetup = remember { core.identity.isSetup }
    var name by remember { mutableStateOf(if (alreadySetup) core.identity.name else "") }
    var denied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (!core.identity.isSetup) core.identity.create(name.trim())
        if (Perms.allGranted(ctx)) {
            EnvioService.start(ctx)
            onDone()
        } else {
            denied = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("ENVIO", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Envia mensajes, fotos, audios y archivos con internet o sin internet, " +
                "sin numero de telefono ni SIM.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Para poder enviar y recibir directo entre telefonos cercanos, ENVIO necesita " +
                "permiso de Bluetooth / dispositivos cercanos, Wi-Fi y notificaciones. " +
                "Se piden una sola vez, ahora.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        if (!alreadySetup) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Tu nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
        Button(
            enabled = alreadySetup || name.isNotBlank(),
            onClick = { launcher.launch(Perms.all().toTypedArray()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Permitir y comenzar") }

        if (denied) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Faltan permisos. Sin ellos ENVIO solo podra enviar por internet.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                )
            }) { Text("Abrir ajustes de la app") }
            TextButton(onClick = onDone) { Text("Continuar de todas formas") }
        }
    }
}
