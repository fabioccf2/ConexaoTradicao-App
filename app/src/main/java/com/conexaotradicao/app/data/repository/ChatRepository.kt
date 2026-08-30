package com.conexaotradicao.app.data.repository

import com.conexaotradicao.app.data.local.ChatMessageDao
import com.conexaotradicao.app.data.local.UserDao
import com.conexaotradicao.app.data.model.ChatMessage
import com.conexaotradicao.app.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Chat direto comprador/produtor (RF08). Escreve sempre em Room primeiro (mensagem aparece
 * na hora, mesmo sem sinal — RNF02) e sincroniza com o Firestore, que devolve as mensagens
 * em tempo real via listener para os dois lados da conversa.
 */
class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        chatMessageDao.observe(conversationId)

    suspend fun send(conversationId: String, senderId: String, text: String) {
        // Como a conversa é única por evento (pode ter mais de um comprador nela), salvamos
        // o nome de quem mandou junto da mensagem pra dar pra identificar "quem é quem" na
        // tela, tipo um grupo. Se o nome não estiver salvo localmente (ex.: conta que só fez
        // login nesse aparelho, ver bug documentado em ProfileRepository.updateName),
        // cai num rótulo genérico em vez de deixar em branco.
        val senderName = userDao.getById(senderId)?.name?.takeIf { it.isNotBlank() } ?: "Usuário"
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            senderName = senderName,
            text = text,
            timestampMillis = System.currentTimeMillis(),
            synced = false
        )
        chatMessageDao.upsert(message)
        runCatching {
            firestore.collection(Constants.COLLECTION_CHATS)
                .document(conversationId)
                .collection("messages")
                .document(message.id)
                .set(message)
            chatMessageDao.markSynced(message.id)
        }
    }

    /** Listener em tempo real do Firestore -> regrava as mensagens novas em Room. */
    fun listenRemote(conversationId: String): Flow<Unit> = callbackFlow {
        val registration = firestore.collection(Constants.COLLECTION_CHATS)
            .document(conversationId)
            .collection("messages")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.toObjects(ChatMessage::class.java)?.forEach { remoteMessage ->
                    trySend(Unit)
                    launch { chatMessageDao.upsert(remoteMessage.copy(synced = true)) }
                }
            }
        awaitClose { registration.remove() }
    }
}
