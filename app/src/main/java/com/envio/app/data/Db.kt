package com.envio.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String,
    val name: String,
    val publicKey: String,
    val isNew: Boolean = false,
    val about: String? = null,
    val photoPath: String? = null,
    val blocked: Boolean = false
)

/**
 * type: TEXT | IMAGE | AUDIO | FILE
 * status (salientes): PENDING | SENT (en el servidor, sin confirmar) | DELIVERED | READ | FAILED
 *                     (entrantes: RECEIVED)
 * channel: NEARBY (directo, sin internet) | INTERNET
 * readAt: entrante = cuando YO lo lei (0 = no leido); saliente = cuando el otro lo leyo.
 * contactId es el id del chat (hoy siempre un contacto).
 */
@Entity(tableName = "messages", indices = [Index("contactId")])
data class Message(
    @PrimaryKey val id: String,
    val contactId: String,
    val outgoing: Boolean,
    val type: String,
    val text: String?,
    val fileName: String?,
    val mime: String?,
    val filePath: String?,
    val size: Long,
    val ts: Long,
    val status: String,
    val channel: String?,
    val replyToId: String? = null,
    val replyPreview: String? = null,
    val starred: Boolean = false,
    val deliveredAt: Long = 0,
    val readAt: Long = 0
)

/** Mensajes de control (leido, ...). Misma politica de envio y reintento que los mensajes. */
@Entity(
    tableName = "control_queue",
    indices = [Index(value = ["status", "toId"], name = "idx_control_queue")]
)
data class ControlItem(
    @PrimaryKey val id: String,
    val toId: String,
    val op: String,
    val payload: String,
    val ts: Long,
    val status: String
)

data class UnreadRow(val contactId: String, val n: Int)

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observe(id: String): Flow<Contact?>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun get(id: String): Contact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: Contact)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE contacts SET isNew = 0 WHERE id = :id")
    suspend fun markSeen(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY ts ASC")
    fun observe(contactId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages m WHERE ts = (SELECT MAX(ts) FROM messages WHERE contactId = m.contactId)")
    fun lastMessages(): Flow<List<Message>>

    @Query("SELECT contactId, COUNT(*) AS n FROM messages WHERE outgoing = 0 AND readAt = 0 GROUP BY contactId")
    fun unreadCounts(): Flow<List<UnreadRow>>

    @Query("SELECT * FROM messages WHERE starred = 1 ORDER BY ts DESC")
    fun observeStarred(): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): Message?

    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Message>

    @Query("SELECT * FROM messages WHERE contactId = :contactId AND outgoing = 1 AND status = 'PENDING' ORDER BY ts ASC")
    suspend fun pendingFor(contactId: String): List<Message>

    @Query("SELECT DISTINCT contactId FROM messages WHERE outgoing = 1 AND status = 'PENDING'")
    suspend fun pendingContacts(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: Message): Long

    @Query("SELECT COUNT(*) FROM messages WHERE id = :id")
    suspend fun exists(id: String): Int

    @Query("UPDATE messages SET status = :status, channel = :channel WHERE id = :id")
    suspend fun setStatus(id: String, status: String, channel: String?)

    /** Salio por internet. Solo si seguia pendiente (no pisa un estado mas avanzado). */
    @Query("UPDATE messages SET status = 'SENT', channel = 'INTERNET' WHERE id = :id AND status = 'PENDING'")
    suspend fun markSentInternet(id: String)

    /** El otro telefono lo recibio por conexion directa. */
    @Query("UPDATE messages SET status = 'DELIVERED', channel = 'NEARBY', deliveredAt = :now WHERE id = :id AND status IN ('PENDING','SENT')")
    suspend fun markDeliveredNearby(id: String, now: Long)

    /** Confirmacion de entrega por internet. No pisa READ. */
    @Query("UPDATE messages SET status = 'DELIVERED', channel = 'INTERNET', deliveredAt = :now WHERE id = :id AND contactId = :contactId AND outgoing = 1 AND status IN ('PENDING','SENT')")
    suspend fun markDelivered(id: String, contactId: String, now: Long)

    /** Enviados por internet que todavia no confirmaron entrega (se reintentan). */
    @Query("SELECT * FROM messages WHERE contactId = :contactId AND outgoing = 1 AND status = 'SENT' AND channel = 'INTERNET' AND ts > :since ORDER BY ts ASC")
    suspend fun unacked(contactId: String, since: Long): List<Message>

    @Query("SELECT DISTINCT contactId FROM messages WHERE outgoing = 1 AND status = 'SENT' AND channel = 'INTERNET' AND ts > :since")
    suspend fun unackedContacts(since: Long): List<String>

    // ---- lectura ----

    @Query("SELECT id FROM messages WHERE contactId = :contactId AND outgoing = 0 AND readAt = 0 ORDER BY ts ASC")
    suspend fun unreadIds(contactId: String): List<String>

    @Query("UPDATE messages SET readAt = :now WHERE contactId = :contactId AND outgoing = 0 AND readAt = 0")
    suspend fun markChatRead(contactId: String, now: Long)

    /** El contacto confirmo que leyo estos mensajes mios. Solo mensajes de ese chat y salientes. */
    @Query("UPDATE messages SET status = 'READ', readAt = :now WHERE id IN (:ids) AND contactId = :contactId AND outgoing = 1")
    suspend fun markReadByRemote(ids: List<String>, contactId: String, now: Long)

    // ---- acciones sobre mensajes ----

    @Query("UPDATE messages SET starred = :starred WHERE id IN (:ids)")
    suspend fun setStarred(ids: List<String>, starred: Boolean)

    @Query("SELECT filePath FROM messages WHERE contactId = :contactId AND filePath IS NOT NULL")
    suspend fun filePaths(contactId: String): List<String>

    @Query("DELETE FROM messages WHERE contactId = :contactId")
    suspend fun deleteChat(contactId: String)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface ControlDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(c: ControlItem)

    @Query("SELECT * FROM control_queue WHERE toId = :toId AND status = 'PENDING' ORDER BY ts ASC")
    suspend fun pendingFor(toId: String): List<ControlItem>

    @Query("SELECT DISTINCT toId FROM control_queue WHERE status = 'PENDING'")
    suspend fun pendingTargets(): List<String>

    @Query("SELECT * FROM control_queue WHERE toId = :toId AND status = 'SENT' AND ts > :since ORDER BY ts ASC")
    suspend fun unackedFor(toId: String, since: Long): List<ControlItem>

    @Query("SELECT DISTINCT toId FROM control_queue WHERE status = 'SENT' AND ts > :since")
    suspend fun unackedTargets(since: Long): List<String>

    @Query("UPDATE control_queue SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("UPDATE control_queue SET status = 'DELIVERED' WHERE id = :id AND toId = :toId AND status IN ('PENDING','SENT')")
    suspend fun markDelivered(id: String, toId: String)

    @Query("DELETE FROM control_queue WHERE toId = :toId")
    suspend fun deleteFor(toId: String)

    @Query("DELETE FROM control_queue WHERE status IN ('DELIVERED','FAILED') AND ts < :before")
    suspend fun purge(before: Long)
}

@Database(entities = [Contact::class, Message::class, ControlItem::class], version = 2, exportSchema = false)
abstract class EnvioDb : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun controlDao(): ControlDao
}

/** v1 a v2 (hito 0): perfil de contacto, respuestas, destacados, leido y cola de control. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE contacts ADD COLUMN about TEXT")
        db.execSQL("ALTER TABLE contacts ADD COLUMN photoPath TEXT")
        db.execSQL("ALTER TABLE contacts ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN replyToId TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN replyPreview TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN deliveredAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN readAt INTEGER NOT NULL DEFAULT 0")
        // Lo que ya estaba recibido antes de actualizar cuenta como leido (si no, saldrian cientos de no leidos).
        db.execSQL("UPDATE messages SET readAt = ts WHERE outgoing = 0")
        // Los entregados antes de existir READ se quedan como estan.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS control_queue (" +
                "id TEXT NOT NULL, toId TEXT NOT NULL, op TEXT NOT NULL, payload TEXT NOT NULL, " +
                "ts INTEGER NOT NULL, status TEXT NOT NULL, PRIMARY KEY(id))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_control_queue ON control_queue (status, toId)")
    }
}
