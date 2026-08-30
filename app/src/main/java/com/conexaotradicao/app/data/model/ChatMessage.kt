package com.conexaotradicao.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Mensagem trocada no chat direto entre comprador e produtor de um evento (RF08). */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    // Valores padrão exigidos pelo construtor sem argumentos que o Firestore usa via reflection.
    @PrimaryKey val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    // Nome de quem mandou, salvo junto da mensagem (não precisa buscar o perfil de cada
    // remetente pra mostrar "quem é quem" no chat, útil já que a conversa é única por
    // evento e pode ter mais de um comprador nela).
    val senderName: String = "",
    val text: String? = null,
    val photoUrl: String? = null,
    val audioUrl: String? = null,
    val timestampMillis: Long = 0L,
    val synced: Boolean = false
)
