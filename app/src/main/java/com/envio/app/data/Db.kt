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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String,
    val name: String,
    val publicKey: String,
    val isNew: Boolean = false
)

/**
 * type: TEXT | IMAGE | AUDIO | FILE
 * status (salientes): PENDING | SENT | FAILED   (entrantes: RECEIVED)
 * channel: NEARBY (directo, sin internet) | INTERNET
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
    val channel: String?
)

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

    @Query("UPDATE contacts SET isNew = 0 WHERE id = :id")
    suspend fun markSeen(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY ts ASC")
    fun observe(contactId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages m WHERE ts = (SELECT MAX(ts) FROM messages WHERE contactId = m.contactId)")
    fun lastMessages(): Flow<List<Message>>

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
}

@Database(entities = [Contact::class, Message::class], version = 1, exportSchema = false)
abstract class EnvioDb : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
}
