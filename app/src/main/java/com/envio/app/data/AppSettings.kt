package com.envio.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ajustes de la app. Cada uno es un estado de Compose: al cambiarlo la pantalla se actualiza sola. */
class AppSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("envio_settings", Context.MODE_PRIVATE)

    /** system | light | dark */
    var theme by mutableStateOf(p.getString("theme", "system") ?: "system")
        private set

    /** small | medium | large */
    var fontSize by mutableStateOf(p.getString("fontSize", "medium") ?: "medium")
        private set

    var enterIsSend by mutableStateOf(p.getBoolean("enterIsSend", false))
        private set

    var notifications by mutableStateOf(p.getBoolean("notifications", true))
        private set

    /** Si esta apagado ni envio ni veo confirmaciones de lectura. */
    var readReceipts by mutableStateOf(p.getBoolean("readReceipts", true))
        private set

    /** internet | direct: por que canal se envia. */
    var mode by mutableStateOf(p.getString("mode", "internet") ?: "internet")
        private set

    fun updateMode(v: String) { mode = v; p.edit().putString("mode", v).apply() }

    fun updateTheme(v: String) { theme = v; p.edit().putString("theme", v).apply() }
    fun updateFontSize(v: String) { fontSize = v; p.edit().putString("fontSize", v).apply() }
    fun updateEnterIsSend(v: Boolean) { enterIsSend = v; p.edit().putBoolean("enterIsSend", v).apply() }
    fun updateNotifications(v: Boolean) { notifications = v; p.edit().putBoolean("notifications", v).apply() }
    fun updateReadReceipts(v: Boolean) { readReceipts = v; p.edit().putBoolean("readReceipts", v).apply() }
}
