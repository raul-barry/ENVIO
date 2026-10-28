package com.envio.app.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.envio.app.Perms
import com.envio.app.Radios
import com.envio.app.core
import com.envio.app.service.EnvioService
import kotlinx.coroutines.delay

/**
 * Avisa cuando falta un permiso o esta apagado Bluetooth / Wi-Fi / Ubicacion.
 * Android no deja que una app los encienda en silencio: se activan con un toque.
 */
@Composable
fun StatusBanner() {
    val ctx = LocalContext.current
    val core = ctx.core
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            tick++
        }
    }
    val permsOk = remember(tick) { Perms.allGranted(ctx) }
    val bt = remember(tick) { Radios.bluetoothOn(ctx) }
    val wifi = remember(tick) { Radios.wifiOn(ctx) }
    val loc = remember(tick) { !Radios.needsLocation() || Radios.locationOn(ctx) }
    val status by core.nearby.status.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (Perms.allGranted(ctx)) EnvioService.start(ctx)
        tick++
    }
    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { tick++ }

    Column {
        if (!permsOk) {
            Banner("Faltan permisos para enviar sin internet.", "Dar permisos") {
                permLauncher.launch(Perms.all().toTypedArray())
            }
        } else {
            if (!bt) Banner("Bluetooth apagado: no podras enviar ni recibir directo.", "Activar") {
                btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            if (!wifi) Banner("Wi-Fi apagado: se necesita para archivos grandes sin internet.", "Activar") {
                Radios.openWifi(ctx)
            }
            if (!loc) Banner("Ubicacion apagada (Android la exige para buscar cerca).", "Activar") {
                ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}
