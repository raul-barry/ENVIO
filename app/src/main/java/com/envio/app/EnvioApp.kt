package com.envio.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class EnvioApp : Application() {
    lateinit var core: Core

    override fun onCreate() {
        super.onCreate()
        core = Core(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_SERVICE, "ENVIO activo", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_MESSAGES, "Mensajes", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    companion object {
        const val CH_SERVICE = "envio_service"
        const val CH_MESSAGES = "envio_messages"
    }
}

val Context.core: Core get() = (applicationContext as EnvioApp).core

object Notifier {
    fun message(ctx: Context, from: String, preview: String) {
        try {
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, EnvioApp.CH_MESSAGES)
                .setSmallIcon(R.drawable.ic_stat_envio)
                .setContentTitle(from)
                .setContentText(preview)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            NotificationManagerCompat.from(ctx).notify(from.hashCode(), n)
        } catch (_: SecurityException) {
            // sin permiso de notificaciones
        }
    }
}
