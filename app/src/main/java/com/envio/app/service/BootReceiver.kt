package com.envio.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.envio.app.Perms
import com.envio.app.core

/** Reinicia ENVIO al encender el telefono para seguir recibiendo sin abrir la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            if (context.core.identity.isSetup && Perms.allGranted(context)) EnvioService.start(context)
        } catch (_: Exception) {
        }
    }
}
