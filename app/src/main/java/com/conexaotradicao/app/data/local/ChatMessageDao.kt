package com.conexaotradicao.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conexaotradicao.app.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/** Mensagens de chat em cache local — permite ler conversas antigas offline (RNF02). */
@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestampMillis ASC")
    fun observe(conversationId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE synced = 0")
    suspend fun getPendingSync(): List<ChatMessage>

    @Query("UPDATE chat_messages SET synced = 1 WHERE id = :messageId")
    suspend fun markSynced(messageId: String)
}
