package com.envio.app.data

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.envio.app.crypto.Crypto
import java.security.PrivateKey
import java.security.SecureRandom

/**
 * Identidad local: no hace falta numero de telefono ni SIM.
 * Cada instalacion genera un ID aleatorio y un par de claves guardados cifrados en el telefono.
 */
class IdentityStore(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx,
        "envio_identity",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val isSetup: Boolean get() = prefs.contains("id")
    val id: String get() = prefs.getString("id", "") ?: ""
    val name: String get() = prefs.getString("name", "") ?: ""
    val publicKeyB64: String get() = prefs.getString("pub", "") ?: ""

    fun privateKey(): PrivateKey = Crypto.decodePrivate(prefs.getString("priv", "")!!)

    fun create(name: String) {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val newId = (1..10).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        val kp = Crypto.generateKeyPair()
        prefs.edit()
            .putString("id", newId)
            .putString("name", name.ifBlank { newId })
            .putString("pub", Crypto.encodePublic(kp.public))
            .putString("priv", Crypto.encodePrivate(kp.private))
            .apply()
    }

    /** Texto del codigo QR que otra persona escanea para agregarte. */
    fun qrText(): String = "envio://add?id=$id&n=${Uri.encode(name)}&k=$publicKeyB64"
}
