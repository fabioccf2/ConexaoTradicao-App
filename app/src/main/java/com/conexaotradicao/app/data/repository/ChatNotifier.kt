package com.conexaotradicao.app.data.repository

import android.content.Context
import com.conexaotradicao.app.data.model.Event
import com.conexaotradicao.app.ui.chat.ChatScreenTracker
import com.conexaotradicao.app.util.Constants
import com.conexaotradicao.app.util.NotificationHelper
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * RF11 — "push" local de mensagens novas do chat (RF08). Escuta o Firestore em tempo real
 * para todas as conversas relevantes ao usuário (eventos que ele criou como produtor +
 * eventos em que ele confirmou presença como comprador, RF07) e dispara uma notificação do
 * sistema quando chega mensagem de outra pessoa numa conversa que não é a que está aberta
 * na tela agora. Ver [NotificationHelper] pra entender por que isso é local e não FCM real.
 */
class ChatNotifier(
    private val context: Context,
    private val currentUserId: String,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    // Dispatchers.Main de propósito: start()/stop() só são chamados a partir do ciclo de
    // vida da Activity (thread principal), então mantendo tudo na Main evitamos condição de
    // corrida na lista de registrations sem precisar de sincronização manual.
    private var job = Job()
    private var scope = CoroutineScope(Dispatchers.Main.immediate + job)
    private val registrations = mutableListOf<ListenerRegistration>()
    private val sessionStartMillis = System.currentTimeMillis()

    fun start() {
        stop()
        job = Job()
        scope = CoroutineScope(Dispatchers.Main.immediate + job)

        scope.launch {
            val chats = runCatching { findMyChats() }.getOrDefault(emptyMap())
            chats.forEach { (eventId, label) -> attachListener(eventId, label) }
        }
    }

    fun stop() {
        registrations.forEach { it.remove() }
        registrations.clear()
        job.cancel()
    }

    /** eventId -> rótulo (cidade/estado) do evento, pra descobrir quais conversas acompanhar. */
    private suspend fun findMyChats(): Map<String, String> {
        val chats = mutableMapOf<String, String>()

        val myEvents = firestore.collection(Constants.COLLECTION_EVENTS)
            .whereEqualTo("producerId", currentUserId)
            .get()
            .await()
            .toObjects(Event::class.java)
        myEvents.forEach { chats[it.id] = "${it.city}/${it.state}" }

        val myParticipations = firestore.collection(Constants.COLLECTION_PARTICIPATIONS)
            .whereEqualTo("userId", currentUserId)
            .get()
            .await()
        val participationEventIds = myParticipations.documents
            .mapNotNull { it.getString("eventId") }
            .filterNot { chats.containsKey(it) }
            .distinct()

        participationEventIds.forEach { eventId ->
            val event = firestore.collection(Constants.COLLECTION_EVENTS)
                .document(eventId)
                .get()
                .await()
                .toObject(Event::class.java)
            if (event != null) chats[eventId] = "${event.city}/${event.state}"
        }

        return chats
    }

    private fun attachListener(eventId: String, label: String) {
        val registration = firestore.collection(Constants.COLLECTION_CHATS)
            .document(eventId)
            .collection("messages")
            .whereGreaterThan("timestampMillis", sessionStartMillis)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type != DocumentChange.Type.ADDED) return@forEach
                    val senderId = change.document.getString("senderId")
                    val text = change.document.getString("text") ?: return@forEach
                    if (senderId == currentUserId) return@forEach
                    if (ChatScreenTracker.openConversationId == eventId) return@forEach

                    NotificationHelper.showNotification(
                        context = context,
                        notificationId = eventId.hashCode(),
                        title = label,
                        body = text
                    )
                }
            }
        registrations.add(registration)
    }
}
