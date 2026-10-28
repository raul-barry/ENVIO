package com.envio.app.crypto

import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado extremo a extremo: ECDH (P-256) entre las claves de los dos usuarios -> clave AES-256.
 * Texto: AES-GCM. Archivos: AES-GCM por bloques de 64 KB (no carga el archivo entero en memoria).
 */
object Crypto {
    private const val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    private const val CHUNK = 64 * 1024
    private val rnd = SecureRandom()

    fun b64(b: ByteArray): String = Base64.encodeToString(b, B64)
    fun unb64(s: String): ByteArray = Base64.decode(s, B64)

    fun generateKeyPair(): KeyPair {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"))
        return g.generateKeyPair()
    }

    fun encodePublic(k: PublicKey): String = b64(k.encoded)
    fun encodePrivate(k: PrivateKey): String = b64(k.encoded)
    fun decodePublic(s: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(s)))
    fun decodePrivate(s: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(unb64(s)))

    /** Clave compartida de 32 bytes entre mi clave privada y la pública del contacto. */
    fun sharedKey(priv: PrivateKey, pub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        val secret = ka.generateSecret()
        val md = MessageDigest.getInstance("SHA-256")
        md.update(secret)
        md.update("ENVIO-v1".toByteArray())
        return md.digest()
    }

    fun randomKey(): ByteArray = ByteArray(32).also { rnd.nextBytes(it) }

    fun encrypt(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
        require(data.size > 12 + 16) { "datos invalidos" }
        val iv = data.copyOfRange(0, 12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return c.doFinal(data, 12, data.size - 12)
    }

    private fun chunkCipher(mode: Int, key: ByteArray, prefix: ByteArray, idx: Int, last: Boolean): Cipher {
        val iv = ByteArray(12)
        System.arraycopy(prefix, 0, iv, 0, 8)
        iv[8] = (idx ushr 24).toByte()
        iv[9] = (idx ushr 16).toByte()
        iv[10] = (idx ushr 8).toByte()
        iv[11] = idx.toByte()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(byteArrayOf(if (last) 1 else 0))
        return c
    }

    private fun readChunk(input: InputStream): ByteArray {
        val buf = ByteArray(CHUNK)
        var n = 0
        while (n < CHUNK) {
            val r = input.read(buf, n, CHUNK - n)
            if (r < 0) break
            n += r
        }
        return if (n == CHUNK) buf else buf.copyOf(n)
    }

    /** Cifra [input] hacia el archivo [out] con la clave [key] (32 bytes). */
    fun encryptStream(input: InputStream, out: File, key: ByteArray) {
        val prefix = ByteArray(8).also { rnd.nextBytes(it) }
        FileOutputStream(out).use { fo ->
            val dos = DataOutputStream(BufferedOutputStream(fo))
            dos.write(prefix)
            var cur = readChunk(input)
            var idx = 0
            while (true) {
                val next = if (cur.size == CHUNK) readChunk(input) else ByteArray(0)
                val last = next.isEmpty()
                val ct = chunkCipher(Cipher.ENCRYPT_MODE, key, prefix, idx, last).doFinal(cur)
                dos.writeByte(if (last) 1 else 0)
                dos.writeInt(ct.size)
                dos.write(ct)
                if (last) break
                cur = next
                idx++
            }
            dos.flush()
        }
    }

    /** Descifra [input] hacia el archivo [out]. Lanza excepcion si fue alterado o truncado. */
    fun decryptStream(input: InputStream, out: File, key: ByteArray) {
        DataInputStream(BufferedInputStream(input)).use { dis ->
            val prefix = ByteArray(8)
            dis.readFully(prefix)
            FileOutputStream(out).use { fo ->
                var idx = 0
                while (true) {
                    val flag = dis.readByte().toInt()
                    val len = dis.readInt()
                    require(len in 16..(CHUNK + 16)) { "bloque invalido" }
                    val ct = ByteArray(len)
                    dis.readFully(ct)
                    val last = flag == 1
                    val plain = chunkCipher(Cipher.DECRYPT_MODE, key, prefix, idx, last).doFinal(ct)
                    fo.write(plain)
                    if (last) break
                    idx++
                }
            }
        }
    }
}
