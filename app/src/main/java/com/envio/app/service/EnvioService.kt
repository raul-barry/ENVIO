package com.envio.app.service

import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.envio.app.EnvioApp
import com.envio.app.MainActivity
import com.envio.app.R
import com.envio.app.core
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano: mantiene ENVIO anunciandose y buscando contactos cercanos
 * y reintenta la cola de envio, incluso con la app cerrada.
 */
class EnvioService : Service() {

    private var loop: Job? = null
    private var receiverRegistered = false

    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // Se encendio/apago Bluetooth o Wi-Fi: reiniciar busqueda cercana
            core.nearby.restart()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, EnvioApp.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_envio)
            .setContentTitle("ENVIO activo")
            .setContentText("Listo para enviar y recibir, con o sin internet")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(1, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return
        }

        core.start()

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(this, radioReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true

        loop = core.scope.launch {
            var tick = 0
            while (isActive) {
                delay(20_000L)
                tick++
                core.flush()
                core.nearby.retryDiscovered()
                // cada ~10 min sin conexiones: reiniciar la busqueda por si Android la detuvo
                if (tick % 30 == 0 && core.nearby.connectedPeers.value.isEmpty()) core.nearby.restart()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        loop?.cancel()
        if (receiverRegistered) {
            try { unregisterReceiver(radioReceiver) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, EnvioService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
