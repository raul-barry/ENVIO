package com.envio.app.net

import android.content.Context
import android.net.Uri
import com.envio.app.data.IdentityStore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Canal con internet (estilo WhatsApp) usando Firebase:
 *  - Firestore: buzon de entrada de cada usuario  inbox/{userId}/messages/{msgId}
 *  - Storage: archivos ya cifrados               files/{userId}/{msgId}
 * Todo el contenido viaja cifrado de extremo a extremo; el servidor solo ve datos cifrados.
 * Si falta app/google-services.json, [available] es false y la app usa solo el canal directo.
 */
class OnlineTransport(private val ctx: Context, private val identity: IdentityStore) {

    var onEnvelope: (suspend (Envelope, File?) -> Boolean)? = null

    val available: Boolean get() = FirebaseApp.getApps(ctx).isNotEmpty()

    private var registeredFor: String? = null
    private var listening: ListenerRegistration? = null
    private val processing = ConcurrentHashMap.newKeySet<String>()

    suspend fun ensureRegistered(): Boolean {
        if (!available || !identity.isSetup) return false
        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) auth.signInAnonymously().await()
            val uid = auth.currentUser?.uid ?: return false
            if (registeredFor == identity.id) return true
            FirebaseFirestore.getInstance().collection("users").document(identity.id)
                .set(mapOf("ownerUid" to uid)).await()
            registeredFor = identity.id
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun send(env: Envelope, encFile: File?): Boolean {
        if (!ensureRegistered()) return false
        return try {
            var e = env.copy(fpid = 0L)
            if (encFile != null) {
                val path = "files/${env.to}/${env.id}"
                withTimeout(10 * 60_000L) {
                    FirebaseStorage.getInstance().reference.child(path)
                        .putFile(Uri.fromFile(encFile)).await()
                }
                e = e.copy(fref = path)
            }
            withTimeout(60_000L) {
                FirebaseFirestore.getInstance().collection("inbox").document(env.to)
                    .collection("messages").document(env.id).set(e.toMap()).await()
            }
            true
        } catch (ex: Exception) {
            false
        }
    }

    fun startListening(scope: CoroutineScope) {
        if (!available || !identity.isSetup || listening != null) return
        listening = FirebaseFirestore.getInstance().collection("inbox").document(identity.id)
            .collection("messages")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    listening?.remove(); listening = null
                    return@addSnapshotListener
                }
                snap?.documentChanges
                    ?.filter { it.type == DocumentChange.Type.ADDED }
                    ?.forEach { ch -> scope.launch { process(ch.document) } }
            }
    }

    private suspend fun process(doc: DocumentSnapshot) {
        if (!processing.add(doc.id)) return
        try {
            val env = Envelope.fromMap(doc.data) ?: return
            var f: File? = null
            if (env.fref.isNotEmpty()) {
                if (!env.fref.startsWith("files/${identity.id}/")) return
                f = File.createTempFile("in_", ".enc", ctx.cacheDir)
                try {
                    FirebaseStorage.getInstance().reference.child(env.fref).getFile(f).await()
                } catch (e: Exception) {
                    f.delete(); return
                }
            }
            val ok = onEnvelope?.invoke(env, f) ?: false
            f?.delete()
            if (ok) {
                try {
                    doc.reference.delete().await()
                    if (env.fref.isNotEmpty()) FirebaseStorage.getInstance().reference.child(env.fref).delete().await()
                } catch (_: Exception) {
                }
            }
        } finally {
            processing.remove(doc.id)
        }
    }
}
