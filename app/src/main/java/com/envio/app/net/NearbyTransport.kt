package com.envio.app.net

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Canal sin internet ni SIM. Usa Google Nearby Connections, que elige solo entre
 * Bluetooth, BLE y Wi-Fi Direct segun lo que tengan ambos telefonos.
 * Anuncia y busca todo el tiempo; se conecta automaticamente a los contactos conocidos,
 * sin preguntar al usuario, y entrega los mensajes pendientes.
 *
 * Android/Nearby pueden detener la busqueda o el anuncio por su cuenta pasado un rato.
 * Por eso hay un vigilante ([watchdog]) que reinicia ambos cada pocos minutos mientras falte
 * algun contacto por conectar, descarta conexiones muertas y reintenta las que se atoran.
 */
class NearbyTransport(private val ctx: Context, private val scope: CoroutineScope) {

    interface Listener {
        fun onPeerConnected(peerId: String)
        fun onIncoming(env: Envelope, encFile: File?)
    }

    var listener: Listener? = null
    var isKnownContact: (String) -> Boolean = { false }
    var knownContacts: () -> Set<String> = { emptySet() }

    val connectedPeers = MutableStateFlow<Set<String>>(emptySet())
    val status = MutableStateFlow("Iniciando...")

    private val client = Nearby.getConnectionsClient(ctx)
    private val serviceId = "com.envio.app.v1"
    private val strategy = Strategy.P2P_CLUSTER

    @Volatile private var myId = ""
    @Volatile private var advOk = false
    @Volatile private var discOk = false
    @Volatile private var err: String? = null
    @Volatile private var lastCycle = 0L
    @Volatile private var restarting = false

    private val endpointToPeer = ConcurrentHashMap<String, String>()
    private val peerToEndpoint = ConcurrentHashMap<String, String>()
    private val connecting = ConcurrentHashMap<String, Long>()
    private val discovered = ConcurrentHashMap<String, String>()

    private val sendWaiters = ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()
    private val pendingEnv = ConcurrentHashMap<Long, Envelope>()
    private val stagedFiles = ConcurrentHashMap<Long, File>()
    private val filePayloads = ConcurrentHashMap<Long, Payload>()

    fun isConnected(peerId: String): Boolean = peerToEndpoint.containsKey(peerId)

    private fun parsePeer(endpointName: String): String? {
        if (!endpointName.startsWith("ENVIO|")) return null
        val id = endpointName.removePrefix("ENVIO|")
        return if (Regex("^[A-Z2-9]{10}$").matches(id)) id else null
    }

    // ---------- Inicio / reinicio / vigilante ----------

    fun start(id: String) {
        myId = id
        startBoth()
    }

    private fun startBoth() {
        if (myId.isEmpty()) return
        lastCycle = System.currentTimeMillis()
        client.startAdvertising(
            "ENVIO|$myId", serviceId, connCb,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            advOk = true; err = null; refreshStatus()
        }.addOnFailureListener { e ->
            if ((e as? ApiException)?.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                advOk = true
            } else {
                advOk = false; err = e.message
            }
            refreshStatus()
        }
        client.startDiscovery(
            serviceId, discCb,
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            discOk = true; err = null; refreshStatus()
        }.addOnFailureListener { e ->
            if ((e as? ApiException)?.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                discOk = true
            } else {
                discOk = false; err = e.message
            }
            refreshStatus()
        }
    }

    /** Reinicia anuncio y busqueda. NO corta las conexiones que ya existen. */
    fun restart() {
        if (myId.isEmpty() || restarting) return
        restarting = true
        try {
            client.stopAdvertising()
            client.stopDiscovery()
        } catch (_: Exception) {
        }
        advOk = false; discOk = false
        discovered.clear(); connecting.clear()
        scope.launch {
            delay(800)
            restarting = false
            startBoth()
        }
    }

    /** Lo llama el servicio cada ~15 s. */
    fun watchdog() {
        if (myId.isEmpty() || restarting) return
        val now = System.currentTimeMillis()
        val known = knownContacts()
        val missing = known.isEmpty() || known.any { !peerToEndpoint.containsKey(it) }
        val failed = !advOk || !discOk
        if (failed && now - lastCycle > 15_000L) {
            restart(); return
        }
        if (missing && now - lastCycle > CYCLE_MS) {
            restart(); return
        }
        contactsChanged()
    }

    private fun refreshStatus() {
        val n = peerToEndpoint.size
        status.value = when {
            n > 0 -> "Cercanos: conexion directa con $n contacto(s)"
            advOk && discOk -> "Cercanos: buscando contactos..."
            err != null -> "Cercanos: inactivo ($err). Reintentando..."
            else -> "Cercanos: iniciando..."
        }
    }

    private fun publish() {
        connectedPeers.value = peerToEndpoint.keys.toSet()
        refreshStatus()
    }

    // ---------- Descubrimiento y conexion automatica ----------

    private val discCb = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val peer = parsePeer(info.endpointName) ?: return
            if (peer == myId) return
            discovered[endpointId] = peer
            maybeConnect(endpointId, peer)
        }

        override fun onEndpointLost(endpointId: String) {
            discovered.remove(endpointId)
        }
    }

    private fun maybeConnect(endpointId: String, peer: String) {
        if (!isKnownContact(peer) || peerToEndpoint.containsKey(peer)) return
        val now = System.currentTimeMillis()
        val prev = connecting[endpointId]
        if (prev != null && now - prev < 25_000L) return // ya hay un intento en curso
        connecting[endpointId] = now
        // Para que los dos telefonos no se llamen a la vez: el de ID menor llama primero.
        val wait = if (myId < peer) 0L else 4_000L
        scope.launch {
            if (wait > 0) delay(wait)
            if (peerToEndpoint.containsKey(peer) || !discovered.containsKey(endpointId)) {
                connecting.remove(endpointId); return@launch
            }
            client.requestConnection("ENVIO|$myId", endpointId, connCb)
                .addOnFailureListener { connecting.remove(endpointId) }
        }
    }

    /** Intenta conectar con todo lo ya descubierto (por ejemplo tras agregar un contacto). */
    fun contactsChanged() {
        discovered.forEach { (ep, peer) -> maybeConnect(ep, peer) }
    }

    fun retryDiscovered() = contactsChanged()

    private val connCb = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val peer = parsePeer(info.endpointName)
            if (peer == null || peer == myId) {
                client.rejectConnection(endpointId)
                return
            }
            endpointToPeer[endpointId] = peer
            client.acceptConnection(endpointId, payloadCb)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connecting.remove(endpointId)
            val peer = endpointToPeer[endpointId] ?: return
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val old = peerToEndpoint.put(peer, endpointId)
                if (old != null && old != endpointId) client.disconnectFromEndpoint(old)
                publish()
                listener?.onPeerConnected(peer)
            } else {
                endpointToPeer.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connecting.remove(endpointId)
            val peer = endpointToPeer.remove(endpointId)
            if (peer != null && peerToEndpoint[peer] == endpointId) peerToEndpoint.remove(peer)
            publish()
            lastCycle = 0L // el vigilante reinicia la busqueda en su proxima vuelta
        }
    }

    /** Conexion que dejo de responder: se cierra para que se vuelva a descubrir y reconectar. */
    private fun dropPeer(peerId: String) {
        val ep = peerToEndpoint.remove(peerId) ?: return
        endpointToPeer.remove(ep)
        connecting.remove(ep)
        try { client.disconnectFromEndpoint(ep) } catch (_: Exception) {}
        publish()
        lastCycle = 0L
    }

    // ---------- Recepcion ----------

    private val payloadCb = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val json = payload.asBytes()?.toString(Charsets.UTF_8) ?: return
                    val env = Envelope.fromJson(json) ?: return
                    if (env.fpid == 0L) {
                        listener?.onIncoming(env, null)
                    } else {
                        pendingEnv[env.fpid] = env
                        tryDeliver(env.fpid)
                    }
                }
                Payload.Type.FILE -> filePayloads[payload.id] = payload
                else -> {}
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val id = update.payloadId
            val st = update.status
            sendWaiters[id]?.let {
                if (st == PayloadTransferUpdate.Status.SUCCESS) it.complete(true)
                else if (st == PayloadTransferUpdate.Status.FAILURE || st == PayloadTransferUpdate.Status.CANCELED) it.complete(false)
            }
            val incoming = filePayloads[id] ?: return
            if (st == PayloadTransferUpdate.Status.SUCCESS) {
                scope.launch(Dispatchers.IO) {
                    stageFile(incoming)?.let { stagedFiles[id] = it }
                    filePayloads.remove(id)
                    tryDeliver(id)
                }
            } else if (st == PayloadTransferUpdate.Status.FAILURE || st == PayloadTransferUpdate.Status.CANCELED) {
                filePayloads.remove(id)
                pendingEnv.remove(id)
            }
        }
    }

    private fun stageFile(p: Payload): File? {
        val pf = p.asFile() ?: return null
        val out = File.createTempFile("in_", ".enc", ctx.cacheDir)
        return try {
            val uri = pf.asUri()
            if (uri != null) {
                ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                try { ctx.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            } else {
                pf.asJavaFile()?.let { it.copyTo(out, overwrite = true); it.delete() }
            }
            out
        } catch (e: Exception) {
            out.delete()
            null
        }
    }

    @Synchronized
    private fun tryDeliver(fpid: Long) {
        val env = pendingEnv[fpid] ?: return
        val f = stagedFiles[fpid] ?: return
        pendingEnv.remove(fpid)
        stagedFiles.remove(fpid)
        listener?.onIncoming(env, f)
    }

    // ---------- Envio ----------

    /** Envia el sobre (y el archivo cifrado si hay). True solo si el otro telefono lo recibio completo. */
    suspend fun send(peerId: String, env: Envelope, encFile: File?): Boolean {
        val ep = peerToEndpoint[peerId] ?: return false
        val ids = mutableListOf<Long>()
        val ok = try {
            var out = env
            var filePayload: Payload? = null
            if (encFile != null) {
                filePayload = Payload.fromFile(encFile)
                out = env.copy(fpid = filePayload.id)
            }
            val bytes = Payload.fromBytes(out.toJson().toByteArray(Charsets.UTF_8))
            val d1 = CompletableDeferred<Boolean>()
            sendWaiters[bytes.id] = d1; ids.add(bytes.id)
            var d2: CompletableDeferred<Boolean>? = null
            if (filePayload != null) {
                d2 = CompletableDeferred()
                sendWaiters[filePayload.id] = d2; ids.add(filePayload.id)
            }
            client.sendPayload(ep, bytes).await()
            if (filePayload != null) client.sendPayload(ep, filePayload).await()
            val ok1 = withTimeoutOrNull(30_000L) { d1.await() } == true
            val ok2 = if (d2 == null) true else withTimeoutOrNull(10 * 60_000L) { d2.await() } == true
            ok1 && ok2
        } catch (e: Exception) {
            false
        } finally {
            ids.forEach { sendWaiters.remove(it) }
        }
        if (!ok) dropPeer(peerId)
        return ok
    }

    companion object {
        /** Cada cuanto se reinicia la busqueda mientras falte algun contacto por conectar. */
        private const val CYCLE_MS = 120_000L
    }
}
