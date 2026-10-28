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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Canal sin internet ni SIM. Usa Google Nearby Connections, que elige solo entre
 * Bluetooth, BLE y Wi-Fi Direct segun lo que tengan ambos telefonos.
 * Siempre anuncia y busca; se conecta automaticamente a los contactos conocidos,
 * sin preguntar al usuario, y entrega los mensajes pendientes.
 */
class NearbyTransport(private val ctx: Context, private val scope: CoroutineScope) {

    interface Listener {
        fun onPeerConnected(peerId: String)
        fun onIncoming(env: Envelope, encFile: File?)
    }

    var listener: Listener? = null
    var isKnownContact: (String) -> Boolean = { false }

    val connectedPeers = MutableStateFlow<Set<String>>(emptySet())
    val status = MutableStateFlow("Iniciando...")

    private val client = Nearby.getConnectionsClient(ctx)
    private val serviceId = "com.envio.app.v1"
    private val strategy = Strategy.P2P_CLUSTER

    private var myId = ""
    private var advOk = false
    private var discOk = false
    private var err: String? = null

    private val endpointToPeer = ConcurrentHashMap<String, String>()
    private val peerToEndpoint = ConcurrentHashMap<String, String>()
    private val connecting = ConcurrentHashMap.newKeySet<String>()
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

    // ---------- Inicio / reinicio ----------

    fun start(id: String) {
        myId = id
        client.startAdvertising(
            "ENVIO|$id", serviceId, connCb,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            advOk = true; refreshStatus()
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
            discOk = true; refreshStatus()
        }.addOnFailureListener { e ->
            if ((e as? ApiException)?.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                discOk = true
            } else {
                discOk = false; err = e.message
            }
            refreshStatus()
        }
    }

    /** Reinicia anuncio y busqueda (por ejemplo al encender Bluetooth/Wi-Fi). Mantiene las conexiones. */
    fun restart() {
        if (myId.isEmpty()) return
        try {
            client.stopAdvertising()
            client.stopDiscovery()
        } catch (_: Exception) {
        }
        advOk = false; discOk = false; err = null
        discovered.clear(); connecting.clear()
        start(myId)
    }

    private fun refreshStatus() {
        val n = peerToEndpoint.size
        status.value = when {
            advOk && discOk && n > 0 -> "Conexion directa con $n contacto(s) cercano(s)"
            advOk && discOk -> "Buscando contactos cercanos..."
            err != null -> "Busqueda cercana inactiva: $err"
            else -> "Iniciando..."
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
            discovered[endpointId] = peer
            maybeConnect(endpointId, peer)
        }

        override fun onEndpointLost(endpointId: String) {
            discovered.remove(endpointId)
        }
    }

    private fun maybeConnect(endpointId: String, peer: String) {
        if (!isKnownContact(peer)) return
        if (peerToEndpoint.containsKey(peer)) return
        if (!connecting.add(endpointId)) return
        client.requestConnection("ENVIO|$myId", endpointId, connCb)
            .addOnFailureListener { connecting.remove(endpointId) }
    }

    /** Llamar cuando cambia la lista de contactos o periodicamente para reintentar. */
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
            val peer = endpointToPeer.remove(endpointId)
            if (peer != null && peerToEndpoint[peer] == endpointId) peerToEndpoint.remove(peer)
            publish()
        }
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
        return try {
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
    }
}
