package com.conexaotradicao.app.data.repository

import android.content.Context
import android.util.Log
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
    companion object {
        // RF11 — tag temporária de depuração pro bug de notificação de chat não aparecendo
        // (usuário relatou "não tá tendo notificação entre mensagens"). Filtrar por essa tag
        // no Logcat revela cada etapa: se o listener foi criado, se a mensagem chegou, se foi
        // ignorada de propósito (é minha própria mensagem / conversa já aberta na tela) ou se
        // a notificação foi de fato disparada mas não apareceu (aí o problema é permissão/
        // Não Perturbe do sistema, não o código).
        private const val TAG_RF11_DEBUG = "RF11_DEBUG"
    }

    // Dispatchers.Main de propósito: start()/stop() só são chamados a partir do ciclo de
    // vida da Activity (thread principal), então mantendo tudo na Main evitamos condição de
    // corrida na lista de registrations sem precisar de sincronização manual.
    private var job = Job()
    private var scope = CoroutineScope(Dispatchers.Main.immediate + job)
    private val registrations = mutableListOf<ListenerRegistration>()
    private val sessionStartMillis = System.currentTimeMillis()

    fun start() {
        Log.i(TAG_RF11_DEBUG, "start() chamado — uid=$currentUserId, sessionStartMillis=$sessionStartMillis")
        stop()
        job = Job()
        scope = CoroutineScope(Dispatchers.Main.immediate + job)

        scope.launch {
            val chats = runCatching { findMyChats() }
                .onFailure { e -> Log.e(TAG_RF11_DEBUG, "findMyChats() lançou exceção", e) }
                .getOrDefault(emptyMap())
            Log.i(TAG_RF11_DEBUG, "findMyChats() achou ${chats.size} conversa(s): $chats")
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
        Log.i(TAG_RF11_DEBUG, "attachListener(eventId=$eventId, label=$label)")
        val registration = firestore.collection(Constants.COLLECTION_CHATS)
            .document(eventId)
            .collection("messages")
            .whereGreaterThan("timestampMillis", sessionStartMillis)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Antes esse erro era descartado silenciosamente (segundo parâmetro do
                    // listener ignorado como "_") — se o Firestore recusar o listener por
                    // qualquer motivo (regra de segurança, índice faltando etc.), o app
                    // simplesmente nunca soube, e nunca ia notificar nada dessa conversa.
                    Log.e(TAG_RF11_DEBUG, "listener de $eventId falhou", error)
                    return@addSnapshotListener
                }
                val changes = snapshot?.documentChanges.orEmpty()
                Log.i(TAG_RF11_DEBUG, "listener de $eventId disparou — ${changes.size} mudança(s)")
                changes.forEach { change ->
                    if (change.type != DocumentChange.Type.ADDED) {
                        Log.i(TAG_RF11_DEBUG, "  ignorado: tipo=${change.type} (não é mensagem nova)")
                        return@forEach
                    }
                    val senderId = change.document.getString("senderId")
                    val text = change.document.getString("text")
                    if (text == null) {
                        Log.w(TAG_RF11_DEBUG, "  ignorado: documento ${change.document.id} sem campo 'text'")
                        return@forEach
                    }
                    if (senderId == currentUserId) {
                        Log.i(TAG_RF11_DEBUG, "  ignorado: mensagem é minha própria (senderId=$senderId)")
                        return@forEach
                    }
                    if (ChatScreenTracker.openConversationId == eventId) {
                        Log.i(TAG_RF11_DEBUG, "  ignorado: conversa $eventId já está aberta na tela")
                        return@forEach
                    }

                    Log.i(TAG_RF11_DEBUG, "  disparando notificação: label=$label texto=\"$text\"")
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
