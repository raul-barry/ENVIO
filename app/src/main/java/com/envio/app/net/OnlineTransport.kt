package com.envio.app.net

import android.content.Context
import com.envio.app.data.IdentityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.BitSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object RelayConfig {
    /**
     * Servidores MQTT publicos y gratuitos (sin cuenta). ENVIO escucha en TODOS y publica en el
     * primero que este conectado, asi que si uno se cae el otro sigue funcionando.
     * Para usar un servidor propio, cambia esta lista (por ejemplo "ssl://mi-servidor.com:8883").
     * Todo viaja cifrado de extremo a extremo; el servidor solo ve datos cifrados.
     */
    val brokers = listOf(
        "ssl://broker.emqx.io:8883",
        "ssl://broker.hivemq.com:8883"
    )
    const val TOPIC_PREFIX = "envio/v1/in/"
}

/**
 * Canal con internet SIN cuentas ni configuracion, por un relevo MQTT.
 *
 * - Cada usuario escucha su buzon  envio/v1/in/<su ID>  (sesion persistente, QoS 1).
 * - Textos: un sobre cifrado. Archivos: el archivo ya cifrado se parte en bloques de 32 KB.
 * - El servidor publico NO garantiza guardar mensajes, asi que la entrega se confirma de
 *   extremo a extremo: el receptor devuelve un ACK cifrado y el emisor reintenta (con espera
 *   creciente) hasta recibirlo. El receptor ignora los duplicados.
 *
 * Tipos de trama (primer byte): 'E' sobre (JSON), 'C' bloque de archivo, 'A' confirmacion (JSON).
 */
class OnlineTransport(private val ctx: Context, private val identity: IdentityStore) {

    var onEnvelope: (suspend (Envelope, File?) -> Boolean)? = null
    var onAck: (suspend (Envelope) -> Unit)? = null
    var ackBuilder: (suspend (Envelope) -> Envelope?)? = null
    var onConnected: (() -> Unit)? = null

    val status = MutableStateFlow("Internet: iniciando...")

    private val rnd = SecureRandom()
    private val dir = File(ctx.cacheDir, "relay").apply { mkdirs() }
    private val links = RelayConfig.brokers.map { Link(it) }

    private lateinit var scope: CoroutineScope
    private var netOnline: () -> Boolean = { false }
    private var started = false
    @Volatile private var stuckSince = 0L

    fun isReady(): Boolean = links.any { it.isConnected() }

    // ---------- Ciclo de vida ----------

    @Synchronized
    fun start(scope: CoroutineScope, netOnline: () -> Boolean) {
        if (started) return
        started = true
        this.scope = scope
        this.netOnline = netOnline
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try { tick() } catch (_: Exception) {}
                delay(15_000L)
            }
        }
    }

    /** Llamar cuando vuelve la red o cuando se quiere comprobar la conexion ya. */
    fun reconnectNow() {
        if (!started) return
        scope.launch(Dispatchers.IO) { try { tick() } catch (_: Exception) {} }
    }

    private fun tick() {
        cleanup()
        if (!identity.isSetup) return
        if (!netOnline()) {
            stuckSince = 0L
            refreshStatus()
            return
        }
        if (isReady()) stuckSince = 0L
        else if (stuckSince == 0L) stuckSince = System.currentTimeMillis()
        links.forEach { it.connectIfNeeded() }
        refreshStatus()
    }

    private fun refreshStatus() {
        val n = links.count { it.isConnected() }
        status.value = when {
            n > 0 -> "Internet: conectado ($n de ${links.size} servidores)"
            !netOnline() -> "Internet: sin conexion"
            stuckSince != 0L && System.currentTimeMillis() - stuckSince > 45_000L ->
                "Internet: no se pudo llegar al servidor. Reintentando..."
            else -> "Internet: conectando..."
        }
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        for ((k, a) in asms.entries.toList()) {
            if (now - a.created > DAY_MS) {
                asms.remove(k); a.file.delete()
            }
        }
        if (envs.size > 200) envs.clear()
        if (doneIds.size > 500) {
            doneIds.entries.sortedBy { it.value }.take(100).forEach { doneIds.remove(it.key) }
        }
        dir.listFiles()?.forEach { f ->
            if (now - f.lastModified() > DAY_MS && asms.values.none { it.file == f }) f.delete()
        }
    }

    // ---------- Una conexion a un servidor ----------

    private inner class Link(val uri: String) : MqttCallback {
        @Volatile var client: MqttAsyncClient? = null
        private val busy = AtomicBoolean(false)
        @Volatile private var since = 0L

        fun isConnected(): Boolean = try { client?.isConnected == true } catch (e: Exception) { false }

        fun connectIfNeeded() {
            if (isConnected()) return
            val now = System.currentTimeMillis()
            if (!busy.compareAndSet(false, true)) {
                if (now - since < 40_000L) return // ya esta intentando
            }
            since = now
            try {
                client?.let { old ->
                    try { old.disconnectForcibly(0, 0) } catch (_: Exception) {}
                    try { old.close() } catch (_: Exception) {}
                }
                val c = MqttAsyncClient(uri, "envio-${identity.id}", MemoryPersistence())
                c.setCallback(this)
                client = c
                val o = MqttConnectOptions().apply {
                    isCleanSession = false          // el servidor guarda los mensajes mientras no estoy
                    isAutomaticReconnect = false    // reconecta el vigilante de arriba
                    keepAliveInterval = 60
                    connectionTimeout = 20
                    maxInflight = 32
                }
                c.connect(o, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        busy.set(false)
                        try { c.subscribe(RelayConfig.TOPIC_PREFIX + identity.id, 1) } catch (_: Exception) {}
                        refreshStatus()
                        onConnected?.invoke()
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        busy.set(false)
                        refreshStatus()
                    }
                })
            } catch (e: Exception) {
                busy.set(false)
            }
        }

        fun publish(topic: String, payload: ByteArray): IMqttDeliveryToken {
            val m = MqttMessage(payload)
            m.qos = 1
            m.isRetained = false
            return client!!.publish(topic, m)
        }

        override fun connectionLost(cause: Throwable?) {
            refreshStatus()
            if (started) scope.launch(Dispatchers.IO) {
                delay(3_000L)
                try { tick() } catch (_: Exception) {}
            }
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            val p = message?.payload ?: return
            scope.launch(Dispatchers.IO) {
                try { handleFrame(p) } catch (_: Exception) {}
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    }

    private fun frame(type: Char, body: ByteArray): ByteArray {
        val out = ByteArray(body.size + 1)
        out[0] = type.code.toByte()
        System.arraycopy(body, 0, out, 1, body.size)
        return out
    }

    // ---------- Enviar ----------

    /** True si el servidor acepto TODO (sobre y bloques). La entrega real se confirma con el ACK. */
    suspend fun send(env: Envelope, encFile: File?): Boolean = withContext(Dispatchers.IO) {
        if (!identity.isSetup) return@withContext false
        val attempt = if (encFile != null) rnd.nextInt() else 0
        val out = env.copy(fpid = 0L, fref = if (encFile != null) Integer.toHexString(attempt) else "")
        val topic = RelayConfig.TOPIC_PREFIX + env.to
        for (link in links) {
            if (!link.isConnected()) continue
            try {
                if (encFile != null) sendChunks(link, topic, env.id, attempt, encFile)
                link.publish(topic, frame('E', out.toJson().toByteArray(Charsets.UTF_8)))
                    .waitForCompletion(20_000L)
                return@withContext true
            } catch (e: Exception) {
                continue
            }
        }
        false
    }

    private fun sendChunks(link: Link, topic: String, id: String, attempt: Int, f: File) {
        val total = ((f.length() + CHUNK - 1) / CHUNK).toInt().coerceAtLeast(1)
        require(total <= MAX_CHUNKS) { "archivo demasiado grande" }
        val uuid = UUID.fromString(id)
        val tokens = ArrayList<IMqttDeliveryToken>()
        FileInputStream(f).use { fi ->
            val buf = ByteArray(CHUNK)
            for (idx in 0 until total) {
                var n = 0
                while (n < CHUNK) {
                    val r = fi.read(buf, n, CHUNK - n)
                    if (r < 0) break
                    n += r
                }
                val bb = ByteBuffer.allocate(1 + 16 + 4 + 4 + 4 + n)
                bb.put('C'.code.toByte())
                bb.putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits)
                bb.putInt(attempt).putInt(idx).putInt(total)
                bb.put(buf, 0, n)
                tokens.add(link.publish(topic, bb.array()))
                if (tokens.size >= 8) {
                    tokens.forEach { it.waitForCompletion(30_000L) }
                    tokens.clear()
                }
            }
        }
        tokens.forEach { it.waitForCompletion(30_000L) }
    }

    private suspend fun sendAck(env: Envelope) {
        val ack = try { ackBuilder?.invoke(env) } catch (e: Exception) { null } ?: return
        val data = frame('A', ack.toJson().toByteArray(Charsets.UTF_8))
        val topic = RelayConfig.TOPIC_PREFIX + env.from
        for (l in links) {
            if (!l.isConnected()) continue
            try {
                l.publish(topic, data).waitForCompletion(20_000L)
                return
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Recibir ----------

    private class Asm(val total: Int, val file: File) {
        val got = BitSet(total)
        val created = System.currentTimeMillis()
    }

    private val asms = ConcurrentHashMap<String, Asm>()
    private val envs = ConcurrentHashMap<String, Envelope>()
    private val doneIds = ConcurrentHashMap<String, Long>()
    private val ioMutex = Mutex()

    private suspend fun handleFrame(p: ByteArray) {
        if (p.size < 2 || !identity.isSetup) return
        when (p[0].toInt().toChar()) {
            'E' -> Envelope.fromJson(String(p, 1, p.size - 1, Charsets.UTF_8))?.let { onEnvelopeFrame(it) }
            'C' -> onChunk(p)
            'A' -> Envelope.fromJson(String(p, 1, p.size - 1, Charsets.UTF_8))?.let {
                if (it.to == identity.id && it.type == "ACK") onAck?.invoke(it)
            }
        }
    }

    private suspend fun onEnvelopeFrame(env: Envelope) {
        if (env.to != identity.id || env.type == "ACK") return
        val id = env.id.lowercase()
        if (doneIds.containsKey(id)) {
            sendAck(env); return
        }
        if (env.fref.isEmpty()) {
            finish(env, null); return
        }
        val attempt = env.fref.toLongOrNull(16)?.toInt() ?: return
        val key = "$id:$attempt"
        envs[key] = env
        tryComplete(key)
    }

    private suspend fun onChunk(p: ByteArray) {
        if (p.size < 1 + 16 + 12 + 1) return
        val bb = ByteBuffer.wrap(p, 1, p.size - 1)
        val id = UUID(bb.long, bb.long).toString().lowercase()
        val attempt = bb.int
        val idx = bb.int
        val total = bb.int
        val n = bb.remaining()
        if (total !in 1..MAX_CHUNKS || idx !in 0 until total || n < 1 || n > CHUNK) return
        if (doneIds.containsKey(id)) return
        val key = "$id:$attempt"
        if (!asms.containsKey(key) && asms.size >= 12) return
        val asm = asms.computeIfAbsent(key) { Asm(total, File(dir, "${id}_$attempt.part")) }
        if (asm.total != total) return
        val data = ByteArray(n)
        bb.get(data)
        ioMutex.withLock {
            RandomAccessFile(asm.file, "rw").use {
                it.seek(idx.toLong() * CHUNK)
                it.write(data)
            }
            asm.got.set(idx)
        }
        tryComplete(key)
    }

    private suspend fun tryComplete(key: String) {
        val asm = asms[key] ?: return
        val env = envs[key] ?: return
        val full = ioMutex.withLock { asm.got.cardinality() == asm.total }
        if (!full) return
        if (!envs.remove(key, env)) return // otro hilo ya lo completo
        asms.remove(key)
        finish(env, asm.file)
        val prefix = env.id.lowercase() + ":"
        asms.keys.filter { it.startsWith(prefix) }.forEach { asms.remove(it)?.file?.delete() }
        envs.keys.filter { it.startsWith(prefix) }.forEach { envs.remove(it) }
    }

    private suspend fun finish(env: Envelope, file: File?) {
        val ok = try { onEnvelope?.invoke(env, file) ?: false } catch (e: Exception) { false }
        file?.delete()
        if (ok) {
            doneIds[env.id.lowercase()] = System.currentTimeMillis()
            sendAck(env)
        }
    }

    companion object {
        const val CHUNK = 32 * 1024
        /** Mas grande que esto solo sale por conexion directa (para no abusar del servidor publico). */
        const val MAX_FILE_BYTES = 25L * 1024 * 1024
        private const val MAX_CHUNKS = 1024
        private const val DAY_MS = 24L * 3600_000L
    }
}
