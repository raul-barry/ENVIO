package com.envio.app.net

import org.json.JSONObject

/**
 * Sobre que viaja por cualquier canal (Bluetooth/Wi-Fi Direct o internet).
 * El contenido real (texto, nombre de archivo, clave del archivo) va cifrado dentro de [ct].
 *  - spk / sname: clave publica y nombre del remitente (para contactos nuevos).
 *  - fpid: id del payload del archivo cifrado (solo canal directo).
 *  - fref: ruta del archivo cifrado en el almacenamiento (solo canal internet).
 */
data class Envelope(
    val id: String,
    val from: String,
    val to: String,
    val ts: Long,
    val type: String,
    val ct: String,
    val spk: String,
    val sname: String,
    val fpid: Long = 0L,
    val fref: String = ""
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id); put("from", from); put("to", to); put("ts", ts)
        put("type", type); put("ct", ct); put("spk", spk); put("sname", sname)
        put("fpid", fpid); put("fref", fref)
    }.toString()

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id, "from" to from, "to" to to, "ts" to ts,
        "type" to type, "ct" to ct, "spk" to spk, "sname" to sname, "fref" to fref
    )

    companion object {
        fun fromJson(s: String): Envelope? = try {
            val o = JSONObject(s)
            Envelope(
                o.getString("id"), o.getString("from"), o.getString("to"), o.optLong("ts"),
                o.getString("type"), o.getString("ct"), o.getString("spk"), o.optString("sname"),
                o.optLong("fpid", 0L), o.optString("fref", "")
            )
        } catch (e: Exception) {
            null
        }

        fun fromMap(m: Map<String, Any?>?): Envelope? {
            if (m == null) return null
            return try {
                Envelope(
                    m["id"] as String, m["from"] as String, m["to"] as String,
                    (m["ts"] as? Number)?.toLong() ?: 0L,
                    m["type"] as String, m["ct"] as String, m["spk"] as String,
                    (m["sname"] as? String) ?: "", 0L, (m["fref"] as? String) ?: ""
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
