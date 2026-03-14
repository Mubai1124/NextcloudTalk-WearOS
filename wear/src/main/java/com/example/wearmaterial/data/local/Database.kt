package com.example.wearmaterial.data.local

import androidx.room.*
import com.example.wearmaterial.models.Message
import com.example.wearmaterial.models.Room

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val token: String,
    val name: String,
    val displayName: String?,
    val type: Int,
    val lastMessage: String?,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val updatedAt: Long,
    val isFavorite: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Int,
    val conversationToken: String,
    val actorType: String?,
    val actorId: String?,
    val actorDisplayName: String?,
    val content: String,
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getAll(): List<ConversationEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>)
    
    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
    
    @Query("DELETE FROM conversations WHERE token = :token")
    suspend fun deleteByToken(token: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationToken = :token ORDER BY timestamp ASC")
    suspend fun getByConversation(token: String): List<MessageEntity>
    
    @Query("SELECT MAX(id) FROM messages WHERE conversationToken = :token")
    suspend fun getLastMessageId(token: String): Int?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
    
    @Query("DELETE FROM messages WHERE conversationToken = :token")
    suspend fun deleteByConversation(token: String)
}

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 2)
abstract class TalkDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}

// Extension functions for conversion
fun Room.toEntity() = ConversationEntity(
    token = token,
    name = name,
    displayName = displayName,
    type = type,
    lastMessage = lastMessage?.message,
    lastMessageTime = lastActivity,
    unreadCount = unreadMessages,
    updatedAt = lastActivity,
    isFavorite = isFavorite
)

fun ConversationEntity.toModel(): Room {
    val lastMsg = lastMessage?.let { msg ->
        Message(
            id = 0,
            token = "",
            actorType = null,
            actorId = null,
            actorDisplayName = null,
            timestamp = lastMessageTime,
            message = msg
        )
    }
    
    return Room(
        id = 0,
        token = token,
        name = name,
        displayName = displayName,
        type = type,
        lastMessage = lastMsg,
        unreadMessages = unreadCount,
        lastActivity = lastMessageTime,
        isFavorite = isFavorite
    )
}

fun Message.toEntity(conversationToken: String) = MessageEntity(
    id = id,
    conversationToken = conversationToken,
    actorType = actorType,
    actorId = actorId,
    actorDisplayName = actorDisplayName,
    content = message,
    timestamp = timestamp
)

fun MessageEntity.toModel() = Message(
    id = id,
    token = conversationToken,
    actorType = actorType,
    actorId = actorId,
    actorDisplayName = actorDisplayName,
    timestamp = timestamp,
    message = content
)
