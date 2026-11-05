package com.envio.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.Room
import com.envio.app.crypto.Crypto
import com.envio.app.data.AppSettings
import com.envio.app.data.Contact
import com.envio.app.data.ControlItem
import com.envio.app.data.EnvioDb
import com.envio.app.data.IdentityStore
import com.envio.app.data.MIGRATION_1_2
import com.envio.app.data.Message
import com.envio.app.net.Envelope
import com.envio.app.net.NearbyTransport
import com.envio.app.net.NetworkMonitor
import com.envio.app.net.OnlineTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Centro de la app. El usuario siempre toca "enviar" igual:
 * el mensaje se guarda como PENDIENTE y la cola lo manda por el mejor canal disponible:
 *   1) conexion directa (Bluetooth / Wi-Fi Direct) si el contacto esta cerca -> ENTREGADO
 *   2) internet si hay conexion -> ENVIADO, y pasa a ENTREGADO cuando el contacto confirma;
 *      mientras no confirme, se reintenta con espera creciente (hasta 7 dias)
 *   3) si ninguno esta disponible, espera y sale solo cuando aparezca uno.
 * Cuando el contacto abre el chat, devuelve un mensaje de control "leido" y pasa a LEIDO.
 */
class Core(val app: Application) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: EnvioDb = Room.databaseBuilder(app, EnvioDb::class.java, "envio.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    val identity = IdentityStore(app)
    val settings = AppSettings(app)
    val net = NetworkMonitor(app)
    val nearby = NearbyTransport(app, scope)
    val online = OnlineTransport(app, identity)
    val mediaDir = File(app.filesDir, "media").apply { mkdirs() }

    @Volatile
    var openChat: String? = null

    @Volatile
    private var knownIds: Set<String> = emptySet()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** id del mensaje o control -> [intentos, ultimo intento en ms]. Solo en memoria: al reiniciar se reintenta. */
    private val retryState = ConcurrentHashMap<String, LongArray>()

    /** ids de mensajes de control ya procesados, para poder confirmarlos aunque lleguen repetidos. */
    private val processedCtrl: MutableSet<String> = Collections.synchronizedSet(
        Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 500
        })
    )
    private var started = false

    init {
        nearby.isKnownContact = { it in knownIds }
        nearby.knownContacts = { knownIds }
        nearby.listener = object : NearbyTransport.Listener {
            override fun onPeerConnected(peerId: String) = flush(peerId)
            override fun onIncoming(env: Envelope, encFile: File?) {
                scope.launch {
                    try {
                        handleIncoming(env, encFile, "NEARBY")
                    } finally {
                        encFile?.delete()
                    }
                }
            }
        }
        online.onEnvelope = { env, f -> handleIncoming(env, f, "INTERNET") }
        online.ackBuilder = { env -> buildAck(env) }
        online.onAck = { env -> handleAck(env) }
        online.onConnected = { flush() }
        scope.launch {
            db.contactDao().observeAll().collect { list ->
                knownIds = list.map { it.id }.toSet()
                nearby.contactsChanged()
            }
        }
    }

    /** Lo llama el servicio en primer plano. */
    @Synchronized
    fun start() {
        if (!identity.isSetup) return
        nearby.start(identity.id)
        online.start(scope) { net.isOnline() }
        if (!started) {
            started = true
            net.start { onNetworkChange() }
            scope.launch { db.controlDao().purge(System.currentTimeMillis() - DAY_MS) }
        }
        onNetworkChange()
        flush()
    }

    private fun onNetworkChange() {
        online.reconnectNow()
        flush()
    }

    // ---------- Contactos ----------

    suspend fun addContactFromQr(text: String): Contact? {
        val uri = try { Uri.parse(text.trim()) } catch (e: Exception) { return null }
        if (uri.scheme != "envio" || uri.host != "add") return null
        val id = uri.getQueryParameter("id") ?: return null
        val name = (uri.getQueryParameter("n") ?: id).take(40)
        val key = uri.getQueryParameter("k") ?: return null
        if (!Regex("^[A-Z2-9]{10}$").matches(id) || id == identity.id) return null
        try { Crypto.decodePublic(key) } catch (e: Exception) { return null }
        val c = Contact(id, name, key, isNew = false)
        db.contactDao().upsert(c)
        nearby.contactsChanged()
        flush(id)
        return c
    }

    // ---------- Perfil propio ----------

    fun saveProfile(name: String, about: String) {
        identity.setProfile(name, about)
    }

    suspend fun setProfilePhoto(uri: Uri) = withContext(Dispatchers.IO) {
        val cr = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return@withContext
        val scale = minOf(1f, 512f / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else {
            bmp
        }
        val f = File(app.filesDir, "profile_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        identity.photoPath?.let { File(it).delete() }
        identity.setPhoto(f.absolutePath)
    }

    // ---------- Enviar ----------

    fun sendText(contactId: String, text: String, replyTo: Message? = null) {
        val t = text.trim().take(4000)
        if (t.isEmpty()) return
        scope.launch {
            db.messageDao().insert(
                Message(
                    id = UUID.randomUUID().toString(), contactId = contactId, outgoing = true,
                    type = "TEXT", text = t, fileName = null, mime = null, filePath = null,
                    size = 0, ts = System.currentTimeMillis(), status = "PENDING", channel = null,
                    replyToId = replyTo?.id, replyPreview = replyTo?.let { quotePreview(it) }
                )
            )
            flush(contactId)
        }
    }

    private fun quotePreview(m: Message): String = when (m.type) {
        "TEXT" -> (m.text ?: "").take(120)
        "IMAGE" -> "Imagen"
        "AUDIO" -> "Nota de voz"
        else -> m.fileName ?: "Archivo"
    }

    /** Envia cualquier archivo elegido por el usuario (imagen, audio, documento...). */
    suspend fun sendFile(contactId: String, uri: Uri, caption: String? = null) = withContext(Dispatchers.IO) {
        val cr = app.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        var name = "archivo"
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (ni >= 0) c.getString(ni)?.let { name = it }
            }
        }
        val id = UUID.randomUUID().toString()
        val dest = File(mediaDir, "${id}_${sanitize(name)}")
        val input = cr.openInputStream(uri) ?: return@withContext
        input.use { i -> dest.outputStream().use { i.copyTo(it) } }
        enqueueFile(contactId, id, dest, name, mime, caption)
    }

    /** Envia un archivo que ya esta en el telefono (por ejemplo una nota de voz grabada). */
    suspend fun sendLocalFile(contactId: String, file: File, name: String, mime: String) =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val dest = File(mediaDir, "${id}_${sanitize(name)}")
            file.copyTo(dest, overwrite = true)
            file.delete()
            enqueueFile(contactId, id, dest, name, mime)
        }

    private suspend fun enqueueFile(contactId: String, id: String, file: File, name: String, mime: String, caption: String? = null) {
        val type = when {
            mime.startsWith("image/") -> "IMAGE"
            mime.startsWith("audio/") -> "AUDIO"
            else -> "FILE"
        }
        db.messageDao().insert(
            Message(
                id = id, contactId = contactId, outgoing = true, type = type, text = caption?.trim()?.take(1000)?.ifBlank { null },
                fileName = name, mime = mime, filePath = file.absolutePath, size = file.length(),
                ts = System.currentTimeMillis(), status = "PENDING", channel = null
            )
        )
        flush(contactId)
    }

    // ---------- Acciones sobre mensajes ----------

    /** Borra todos los mensajes (y sus archivos) de un chat. Con [removeContact] tambien quita el contacto. */
    fun deleteChat(contactId: String, removeContact: Boolean) {
        scope.launch {
            db.messageDao().filePaths(contactId).forEach { runCatching { File(it).delete() } }
            db.messageDao().deleteChat(contactId)
            db.controlDao().deleteFor(contactId)
            if (removeContact) db.contactDao().delete(contactId)
            Notifier.cancel(app, contactId)
        }
    }

    fun setStarred(ids: List<String>, starred: Boolean) {
        scope.launch { ids.chunked(500).forEach { db.messageDao().setStarred(it, starred) } }
    }

    /** Elimina solo en este telefono. El otro lado conserva su copia. */
    fun deleteForMe(ids: List<String>) {
        scope.launch {
            ids.chunked(500).forEach { chunk ->
                db.messageDao().getByIds(chunk).forEach { m ->
                    val p = m.filePath
                    if (p != null && p.startsWith(mediaDir.absolutePath)) File(p).delete()
                }
                db.messageDao().deleteByIds(chunk)
            }
        }
    }

    /** Marca como leidos los mensajes recibidos y, si esta permitido, avisa al contacto. */
    suspend fun markRead(chatId: String) {
        val ids = db.messageDao().unreadIds(chatId)
        if (ids.isEmpty()) return
        db.messageDao().markChatRead(chatId, System.currentTimeMillis())
        db.contactDao().get(chatId)?.let { Notifier.cancel(app, it.name) }
        if (settings.readReceipts) {
            ids.chunked(200).forEach { chunk ->
                enqueueControl(chatId, "read", JSONObject().put("ids", JSONArray(chunk)))
            }
        }
    }

    private suspend fun enqueueControl(toId: String, op: String, payload: JSONObject) {
        db.controlDao().insert(
            ControlItem(
                id = UUID.randomUUID().toString(), toId = toId, op = op,
                payload = payload.toString(), ts = System.currentTimeMillis(), status = "PENDING"
            )
        )
        flush(toId)
    }

    // ---------- Almacenamiento ----------

    fun mediaBytes(): Long = mediaDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun tempBytes(): Long = app.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Borra archivos temporales con mas de una hora (no toca lo que se esta enviando ahora). */
    fun clearTemp(): Long {
        val limit = System.currentTimeMillis() - 3600_000L
        var freed = 0L
        app.cacheDir.walkTopDown().filter { it.isFile && it.lastModified() < limit }.forEach {
            val n = it.length()
            if (it.delete()) freed += n
        }
        return freed
    }

    // ---------- Cola de envio ----------

    fun flush(only: String? = null) {
        scope.launch {
            val since = System.currentTimeMillis() - MAX_RETRY_MS
            val ids = if (only != null) {
                listOf(only)
            } else {
                (db.messageDao().pendingContacts() + db.messageDao().unackedContacts(since) +
                    db.controlDao().pendingTargets() + db.controlDao().unackedTargets(since)).distinct()
            }
            ids.forEach { cid ->
                launch { locks.getOrPut(cid) { Mutex() }.withLock { sendPending(cid) } }
            }
        }
    }

    private suspend fun sendPending(cid: String) {
        val contact = db.contactDao().get(cid) ?: return
        sendControl(cid, contact)

        // 1) mensajes nuevos
        for (m in db.messageDao().pendingFor(cid)) {
            val viaNearby = settings.mode == "direct" && nearby.isConnected(cid)
            val viaNet = settings.mode == "internet" && net.isOnline() && online.isReady()
            if (!viaNearby && !viaNet) break
            val tooBigForNet = m.size > OnlineTransport.MAX_FILE_BYTES
            if (!viaNearby && tooBigForNet) continue // los archivos enormes esperan a estar cerca
            val built = try {
                build(m, contact)
            } catch (e: Exception) {
                db.messageDao().setStatus(m.id, "FAILED", null)
                continue
            }
            val (env, enc) = built
            var done = false
            try {
                if (viaNearby && nearby.send(cid, env, enc)) {
                    db.messageDao().markDeliveredNearby(m.id, System.currentTimeMillis())
                    done = true
                }
                if (!done && viaNet && !tooBigForNet && online.send(env, enc)) {
                    db.messageDao().markSentInternet(m.id)
                    retryState[m.id] = longArrayOf(1L, System.currentTimeMillis())
                    done = true
                }
            } finally {
                enc?.delete()
            }
            if (!done) break
        }

        // 2) enviados por internet que aun no confirmaron: reintentar
        retryUnacked(cid, contact)
    }

    private suspend fun retryUnacked(cid: String, contact: Contact) {
        if (!(settings.mode == "internet" && net.isOnline() && online.isReady())) return
        val now = System.currentTimeMillis()
        for (m in db.messageDao().unacked(cid, now - MAX_RETRY_MS)) {
            val st = retryState.getOrPut(m.id) { longArrayOf(0L, 0L) }
            if (now < st[1] + backoff(st[0].toInt(), m.size)) continue
            val built = try { build(m, contact) } catch (e: Exception) { continue }
            val (env, enc) = built
            try {
                online.send(env, enc)
            } finally {
                enc?.delete()
            }
            st[0] = st[0] + 1
            st[1] = System.currentTimeMillis()
        }
    }

    /** Mensajes de control: mismo camino que los mensajes, sin archivos. */
    private suspend fun sendControl(cid: String, contact: Contact) {
        for (c in db.controlDao().pendingFor(cid)) {
            val viaNearby = settings.mode == "direct" && nearby.isConnected(cid)
            val viaNet = settings.mode == "internet" && net.isOnline() && online.isReady()
            if (!viaNearby && !viaNet) return
            val env = try {
                buildControl(c, contact)
            } catch (e: Exception) {
                db.controlDao().setStatus(c.id, "FAILED")
                continue
            }
            if (viaNearby && nearby.send(cid, env, null)) {
                db.controlDao().setStatus(c.id, "DELIVERED")
                continue
            }
            if (viaNet && online.send(env, null)) {
                db.controlDao().setStatus(c.id, "SENT")
                retryState[c.id] = longArrayOf(1L, System.currentTimeMillis())
                continue
            }
            return
        }
        if (!(settings.mode == "internet" && net.isOnline() && online.isReady())) return
        val now = System.currentTimeMillis()
        for (c in db.controlDao().unackedFor(cid, now - MAX_RETRY_MS)) {
            val st = retryState.getOrPut(c.id) { longArrayOf(0L, 0L) }
            if (now < st[1] + backoff(st[0].toInt(), 0L)) continue
            val env = try { buildControl(c, contact) } catch (e: Exception) { continue }
            online.send(env, null)
            st[0] = st[0] + 1
            st[1] = System.currentTimeMillis()
        }
    }

    private fun backoff(attempts: Int, size: Long): Long {
        if (attempts <= 0) return 0L
        val base = if (size > 1_000_000L) 300_000L else 45_000L
        return minOf(base shl minOf(attempts - 1, 10), 6L * 3600_000L)
    }

    private fun build(m: Message, contact: Contact): Pair<Envelope, File?> {
        val shared = Crypto.sharedKey(identity.privateKey(), Crypto.decodePublic(contact.publicKey))
        var enc: File? = null
        var fileKey = ""
        if (m.filePath != null) {
            val key = Crypto.randomKey()
            val out = File.createTempFile("out_", ".enc", app.cacheDir)
            try {
                FileInputStream(File(m.filePath)).use { Crypto.encryptStream(it, out, key) }
            } catch (e: Exception) {
                out.delete(); throw e
            }
            enc = out
            fileKey = Crypto.b64(key)
        }
        val body = JSONObject()
            .put("text", m.text ?: "")
            .put("fileName", m.fileName ?: "")
            .put("mime", m.mime ?: "")
            .put("fileKey", fileKey)
            .put("replyTo", m.replyToId ?: "")
            .put("replyPreview", m.replyPreview ?: "")
        val ct = Crypto.encrypt(shared, body.toString().toByteArray(Charsets.UTF_8))
        val env = Envelope(
            id = m.id, from = identity.id, to = contact.id, ts = m.ts, type = m.type,
            ct = Crypto.b64(ct), spk = identity.publicKeyB64, sname = identity.name
        )
        return env to enc
    }

    private fun buildControl(c: ControlItem, contact: Contact): Envelope {
        val shared = Crypto.sharedKey(identity.privateKey(), Crypto.decodePublic(contact.publicKey))
        val body = JSONObject().put("op", c.op).put("payload", JSONObject(c.payload))
        val ct = Crypto.encrypt(shared, body.toString().toByteArray(Charsets.UTF_8))
        return Envelope(
            id = c.id, from = identity.id, to = contact.id, ts = c.ts, type = "CTRL",
            ct = Crypto.b64(ct), spk = identity.publicKeyB64, sname = identity.name
        )
    }

    // ---------- Confirmaciones de entrega (solo canal internet) ----------

    /** Confirmacion cifrada: solo quien tiene la clave compartida puede fabricarla. */
    suspend fun buildAck(env: Envelope): Envelope? {
        if (env.type == "CTRL") {
            if (!processedCtrl.contains(env.id)) return null
        } else if (db.messageDao().exists(env.id) == 0) {
            return null
        }
        val c = db.contactDao().get(env.from) ?: return null
        val shared = Crypto.sharedKey(identity.privateKey(), Crypto.decodePublic(c.publicKey))
        val ct = Crypto.encrypt(shared, "ack:${env.id}".toByteArray(Charsets.UTF_8))
        return Envelope(
            id = env.id, from = identity.id, to = env.from, ts = System.currentTimeMillis(),
            type = "ACK", ct = Crypto.b64(ct), spk = identity.publicKeyB64, sname = identity.name
        )
    }

    suspend fun handleAck(env: Envelope) {
        val c = db.contactDao().get(env.from) ?: return
        try {
            val shared = Crypto.sharedKey(identity.privateKey(), Crypto.decodePublic(c.publicKey))
            val plain = String(Crypto.decrypt(shared, Crypto.unb64(env.ct)), Charsets.UTF_8)
            if (plain != "ack:${env.id}") return
        } catch (e: Exception) {
            return
        }
        db.messageDao().markDelivered(env.id, env.from, System.currentTimeMillis())
        db.controlDao().markDelivered(env.id, env.from)
        retryState.remove(env.id)
    }

    // ---------- Recibir ----------

    /** True si el sobre ya no hace falta (procesado, duplicado o invalido). */
    suspend fun handleIncoming(env: Envelope, encFile: File?, channel: String): Boolean {
        if (!identity.isSetup || env.to != identity.id) return true
        if (!ID_REGEX.matches(env.id)) return true
        if (!Regex("^[A-Z2-9]{10}$").matches(env.from) || env.from == identity.id) return true
        val isCtrl = env.type == "CTRL"
        if (!isCtrl && db.messageDao().exists(env.id) > 0) return true

        var contact = db.contactDao().get(env.from)
        if (contact != null && contact.blocked) return true
        if (contact != null && contact.publicKey != env.spk) return true // clave distinta: se descarta
        if (isCtrl && contact == null) return true // solo los contactos pueden mandar control
        if (contact == null) {
            try { Crypto.decodePublic(env.spk) } catch (e: Exception) { return true }
            contact = Contact(env.from, env.sname.ifBlank { env.from }.take(40), env.spk, isNew = true)
        }

        val body: JSONObject
        try {
            val shared = Crypto.sharedKey(identity.privateKey(), Crypto.decodePublic(contact.publicKey))
            val plain = Crypto.decrypt(shared, Crypto.unb64(env.ct))
            body = JSONObject(String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            return true // no descifra: no es de esa persona
        }

        if (isCtrl) {
            handleControl(contact, body)
            processedCtrl.add(env.id)
            return true
        }

        var filePath: String? = null
        var fileName: String? = null
        var mime: String? = null
        var size = 0L
        var text: String? = null
        if (env.type == "TEXT") {
            text = body.optString("text")
        } else {
            if (encFile == null) return true
            fileName = body.optString("fileName").ifBlank { "archivo" }
            mime = body.optString("mime").ifBlank { "application/octet-stream" }
            val dest = File(mediaDir, "${env.id}_${sanitize(fileName)}")
            try {
                val key = Crypto.unb64(body.getString("fileKey"))
                encFile.inputStream().use { Crypto.decryptStream(it, dest, key) }
            } catch (e: Exception) {
                dest.delete()
                return true
            }
            filePath = dest.absolutePath
            size = dest.length()
            text = body.optString("text").takeIf { it.isNotBlank() }?.take(1000)
        }

        val replyToId = body.optString("replyTo").takeIf { it.isNotBlank() && ID_REGEX.matches(it) }
        val replyPreview = body.optString("replyPreview").takeIf { it.isNotBlank() }?.take(120)

        db.contactDao().upsert(contact)
        db.messageDao().insert(
            Message(
                id = env.id, contactId = env.from, outgoing = false, type = env.type, text = text,
                fileName = fileName, mime = mime, filePath = filePath, size = size,
                ts = minOf(env.ts, System.currentTimeMillis()).coerceAtLeast(1L),
                status = "RECEIVED", channel = channel,
                replyToId = replyToId, replyPreview = replyPreview
            )
        )
        if (openChat != env.from) {
            val preview = when (env.type) {
                "TEXT" -> text ?: ""
                "IMAGE" -> "Imagen"
                "AUDIO" -> "Audio"
                else -> fileName ?: "Archivo"
            }
            Notifier.message(app, contact.name, preview)
        }
        return true
    }

    private suspend fun handleControl(from: Contact, body: JSONObject) {
        when (body.optString("op")) {
            "read" -> {
                if (!settings.readReceipts) return
                val arr = body.optJSONObject("payload")?.optJSONArray("ids") ?: return
                val ids = (0 until minOf(arr.length(), 2000))
                    .map { arr.optString(it) }
                    .filter { ID_REGEX.matches(it) }
                val now = System.currentTimeMillis()
                ids.chunked(500).forEach { db.messageDao().markReadByRemote(it, from.id, now) }
            }
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)

    companion object {
        private const val MAX_RETRY_MS = 7L * 24 * 3600_000L
        private const val DAY_MS = 24L * 3600_000L
        private val ID_REGEX = Regex("^[0-9a-fA-F-]{36}$")
    }
}
