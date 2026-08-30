package com.conexaotradicao.app.data.repository

import android.util.Log
import com.conexaotradicao.app.data.local.EventDao
import com.conexaotradicao.app.data.local.ParticipationDao
import com.conexaotradicao.app.data.local.RatingDao
import com.conexaotradicao.app.data.local.UserDao
import com.conexaotradicao.app.data.model.Cut
import com.conexaotradicao.app.data.model.Event
import com.conexaotradicao.app.data.model.Participation
import com.conexaotradicao.app.data.model.ParticipationStatus
import com.conexaotradicao.app.data.model.ParticipationWithUser
import com.conexaotradicao.app.data.model.Rating
import com.conexaotradicao.app.data.model.User
import com.conexaotradicao.app.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Listagem/detalhe de eventos (RF03–RF06), estratégia offline-first (RNF02):
 * a Home sempre observa o Room; [refresh] busca no Firestore e regrava o cache local
 * quando há conexão, sem bloquear a UI.
 */
class EventRepository(
    private val eventDao: EventDao,
    private val participationDao: ParticipationDao,
    private val userDao: UserDao,
    private val ratingDao: RatingDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        // RF10 — tag temporária de depuração pro bug da nota que não persiste no perfil
        // (ver Parte 3.16, "Quarto ajuste"). Filtrar por essa tag no Logcat do Android
        // Studio (aba "Logcat", campo de busca) revela o motivo real de uma busca/gravação
        // no Firestore ter falhado, já que essas operações são best-effort (não travam a
        // tela) e por isso não aparecem como erro nenhum pro usuário.
        private const val TAG_RF10_DEBUG = "RF10_DEBUG"
    }

    fun observeEvents(city: String = "", product: String = ""): Flow<List<Event>> =
        if (city.isBlank() && product.isBlank()) {
            eventDao.observeAll()
        } else {
            eventDao.observeFiltered(city, product)
        }

    fun observeCuts(eventId: String): Flow<List<Cut>> = eventDao.observeCuts(eventId)

    suspend fun getEvent(eventId: String): Event? = eventDao.getById(eventId)

    /** RNF05 — localização exata só é revelada a quem já confirmou presença (RF07) no evento. */
    suspend fun hasScheduledParticipation(eventId: String, userId: String): Boolean =
        participationDao.findByEventAndUser(eventId, userId) != null

    /** A participação (com os cortes escolhidos) do usuário logado nesse evento, se já
     * tiver agendado — usado pra pré-marcar os checkboxes de corte quando ele reabre a
     * tela (RF06/RF07), em vez de mostrar tudo desmarcado como se nunca tivesse escolhido. */
    suspend fun getParticipation(eventId: String, userId: String): Participation? =
        participationDao.findByEventAndUser(eventId, userId)

    /** Ids dos eventos em que o usuário logado já tem uma participação AGENDADO (RF07) —
     * usado pra destacar visualmente o card na Home quando já marcou presença. */
    fun observeScheduledEventIds(userId: String): Flow<Set<String>> =
        participationDao.observeByUser(userId).map { list ->
            list.filter { it.status == ParticipationStatus.AGENDADO }
                .map { it.eventId }
                .toSet()
        }

    /** RF05 — cadastro de evento pelo produtor: grava no Firestore e já reflete no cache local. */
    suspend fun createEvent(event: Event, cuts: List<Cut>): Result<Unit> = runCatching {
        firestore.collection(Constants.COLLECTION_EVENTS).document(event.id).set(event).await()

        val cutsCollection = firestore.collection(Constants.COLLECTION_EVENTS)
            .document(event.id)
            .collection(Constants.COLLECTION_CUTS)
        for (cut in cuts) {
            cutsCollection.document(cut.id).set(cut).await()
        }

        eventDao.upsert(event)
        eventDao.upsertCuts(cuts)
    }

    /**
     * RF07 — botão "Agendar Carneação": grava a participação (Firestore + Room), pra aparecer
     * imediatamente em "Meus Próximos Eventos" no Perfil (RF02), mesmo offline.
     */
    suspend fun scheduleParticipation(
        eventId: String,
        userId: String,
        selectedCutIds: List<String>,
        eventDateMillis: Long
    ): Result<Unit> = runCatching {
        val participation = Participation(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            userId = userId,
            selectedCutIds = selectedCutIds,
            status = ParticipationStatus.AGENDADO,
            scheduledAtMillis = eventDateMillis
        )
        participationDao.upsert(participation)
        runCatching {
            firestore.collection(Constants.COLLECTION_PARTICIPATIONS)
                .document(participation.id)
                .set(participation)
                .await()
        }
    }

    /**
     * RF07 — cancela o agendamento do usuário nesse evento (Room + Firestore). Sem efeito
     * se ele nunca tinha agendado (`participation` fica nulo e a função simplesmente não
     * faz nada).
     */
    suspend fun cancelParticipation(eventId: String, userId: String): Result<Unit> = runCatching {
        val participation = participationDao.findByEventAndUser(eventId, userId) ?: return@runCatching
        participationDao.delete(participation)
        runCatching {
            firestore.collection(Constants.COLLECTION_PARTICIPATIONS)
                .document(participation.id)
                .delete()
                .await()
        }
    }

    /**
     * RF10 — botão "Finalizar Evento" (só o produtor dono do evento vê): marca o evento como
     * realizado, guarda as fotos que ele escolheu (já comprimidas/Base64 — ver decisão na
     * Parte 3.16) e passa todas as participações AGENDADO desse evento pra CONCLUIDO, o que
     * libera a avaliação nos dois sentidos (produtor avalia cada comprador, comprador avalia
     * o produtor).
     */
    suspend fun finalizeEvent(eventId: String, photoBase64List: List<String>): Result<Unit> = runCatching {
        val event = eventDao.getById(eventId) ?: throw IllegalStateException("Evento não encontrado.")
        val updatedEvent = event.copy(finalized = true, photoBase64List = photoBase64List)
        eventDao.upsert(updatedEvent)
        // Best-effort (mesmo padrão do resto do repositório): se o Firestore falhar aqui
        // (sem internet, por exemplo), não pode abortar a função inteira e deixar as
        // participações abaixo sem serem concluídas — o evento já está marcado localmente
        // e ressincroniza na próxima vez que `refresh()` rodar com internet.
        runCatching {
            firestore.collection(Constants.COLLECTION_EVENTS).document(eventId).set(updatedEvent).await()
        }

        val participations = fetchParticipationsForEvent(eventId)
            .filter { it.status == ParticipationStatus.AGENDADO }
        for (participation in participations) {
            val updated = participation.copy(status = ParticipationStatus.CONCLUIDO)
            participationDao.upsert(updated)
            runCatching {
                firestore.collection(Constants.COLLECTION_PARTICIPATIONS)
                    .document(updated.id)
                    .set(updated)
                    .await()
            }
        }
    }

    /** RF10 — lista de compradores de um evento (participações não canceladas), com o nome
     * já resolvido, pro produtor avaliar cada um depois de finalizar o evento. */
    suspend fun getParticipantsForEvent(eventId: String): List<ParticipationWithUser> =
        fetchParticipationsForEvent(eventId)
            .filter { it.status != ParticipationStatus.CANCELADO }
            .map { participation -> ParticipationWithUser(participation, resolveUserName(participation.userId)) }

    /**
     * RF10 — busca as participações de um evento **direto no Firestore**, não só no cache
     * local do Room. Esse é o bug que fazia "Finalizar Evento" e "Avaliar Compradores"
     * parecerem funcionar (sem erro nenhum) mas não encontrarem ninguém: o único sync que
     * existia (`syncParticipationsToLocal`) só traz pro Room as participações do PRÓPRIO
     * usuário logado (`whereEqualTo("userId", ...)`) — o produtor nunca tinha, no banco local
     * dele, as participações de OUTRAS pessoas (os compradores) nos eventos que ele mesmo
     * criou. Cai pro cache local só como último recurso (sem internet) — nesse caso pode não
     * encontrar comprador nenhum ainda, mas não trava a função inteira.
     */
    private suspend fun fetchParticipationsForEvent(eventId: String): List<Participation> =
        runCatching {
            firestore.collection(Constants.COLLECTION_PARTICIPATIONS)
                .whereEqualTo("eventId", eventId)
                .get()
                .await()
                .toObjects(Participation::class.java)
        }.getOrElse { participationDao.getByEvent(eventId) }

    /** Nome de um usuário pra mostrar na lista de avaliação (RF10) — mesmo motivo do
     * [fetchParticipationsForEvent]: o produtor normalmente não tem o `User` de outra pessoa
     * no cache local (só sincroniza o próprio perfil), então busca no Firestore quando não
     * encontra local, e já aproveita pra guardar no Room (não precisa buscar de novo depois). */
    private suspend fun resolveUserName(userId: String): String =
        fetchUser(userId)?.name ?: "Comprador"

    /** Busca um `User` local (Room) e, se não achar (caso clássico: é o usuário de OUTRA
     * pessoa, não o dono do celular), busca direto no Firestore e guarda local pra próxima
     * vez. Usado tanto pra pegar o nome de exibição quanto pra atualizar a média de estrelas
     * de alguém que não é o usuário logado neste aparelho (ver [updateUserRatingAggregate]). */
    private suspend fun fetchUser(userId: String): User? {
        userDao.getById(userId)?.let { return it }
        return runCatching {
            val snapshot = firestore.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .get()
                .await()
            if (!snapshot.exists()) {
                Log.w(TAG_RF10_DEBUG, "fetchUser($userId): documento não existe em users/ no Firestore")
            }
            val user = snapshot.toObject(User::class.java)
            if (user != null) userDao.upsert(user)
            user
        }.onFailure { e ->
            Log.e(TAG_RF10_DEBUG, "fetchUser($userId) lançou exceção ao buscar no Firestore", e)
        }.getOrNull()
    }

    /** Participação de um usuário específico num evento — primeiro local (Room), e se não
     * achar (caso do produtor procurando a participação de um COMPRADOR no aparelho dele,
     * que nunca foi sincronizada localmente), busca direto no Firestore. Mesma causa raiz
     * documentada em [fetchParticipationsForEvent], só que filtrando por um usuário só. */
    private suspend fun fetchParticipationForUser(eventId: String, userId: String): Participation? =
        participationDao.findByEventAndUser(eventId, userId) ?: runCatching {
            firestore.collection(Constants.COLLECTION_PARTICIPATIONS)
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .toObjects(Participation::class.java)
                .firstOrNull()
        }.onFailure { e ->
            Log.e(TAG_RF10_DEBUG, "fetchParticipationForUser(evento=$eventId, usuario=$userId) lançou exceção", e)
        }.getOrNull()

    /** RF10 — comprador avalia o produtor (estrelas) depois do evento finalizado: grava a
     * avaliação, atualiza a média/contagem do produtor (`User` + todos os eventos dele, pra
     * refletir em qualquer card/detalhe) e marca essa participação como já avaliada. */
    suspend fun rateProducer(
        eventId: String,
        raterUserId: String,
        producerId: String,
        stars: Int
    ): Result<Unit> = runCatching {
        saveRating(eventId, raterUserId, producerId, stars)
        val newAverage = updateUserRatingAggregate(producerId, stars)

        val producerEvents = eventDao.getByProducer(producerId)
        val updatedEvents = producerEvents.map { it.copy(producerRatingAverage = newAverage) }
        eventDao.upsertAll(updatedEvents)
        for (updatedEvent in updatedEvents) {
            runCatching {
                firestore.collection(Constants.COLLECTION_EVENTS)
                    .document(updatedEvent.id)
                    .update("producerRatingAverage", newAverage)
                    .await()
            }
        }

        val participation = fetchParticipationForUser(eventId, raterUserId)
        if (participation != null) {
            val updated = participation.copy(alreadyRated = true)
            participationDao.upsert(updated)
            runCatching {
                firestore.collection(Constants.COLLECTION_PARTICIPATIONS)
                    .document(updated.id)
                    .update("alreadyRated", true)
                    .await()
            }
        }
    }

    /** RF10 — produtor avalia um comprador (estrelas) depois do evento finalizado: grava a
     * avaliação, atualiza a média/contagem do comprador (`User`) e marca essa participação
     * como já avaliada pelo produtor. */
    suspend fun rateClient(
        eventId: String,
        raterUserId: String,
        clientUserId: String,
        stars: Int
    ): Result<Unit> = runCatching {
        saveRating(eventId, raterUserId, clientUserId, stars)
        updateUserRatingAggregate(clientUserId, stars)

        val participation = fetchParticipationForUser(eventId, clientUserId)
        if (participation != null) {
            val updated = participation.copy(producerRated = true)
            participationDao.upsert(updated)
            runCatching {
                firestore.collection(Constants.COLLECTION_PARTICIPATIONS)
                    .document(updated.id)
                    .update("producerRated", true)
                    .await()
            }
        }
    }

    /** Grava o documento de avaliação em si (Room + Firestore) — comum aos dois sentidos. */
    private suspend fun saveRating(eventId: String, raterUserId: String, targetUserId: String, stars: Int) {
        val rating = Rating(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            raterUserId = raterUserId,
            targetUserId = targetUserId,
            stars = stars,
            createdAtMillis = System.currentTimeMillis()
        )
        ratingDao.insert(rating)
        runCatching {
            firestore.collection(Constants.COLLECTION_RATINGS).document(rating.id).set(rating).await()
        }
    }

    /** Recalcula a média/contagem de estrelas de um usuário (produtor ou comprador — o campo
     * é o mesmo `User.ratingAverage`/`ratingCount` pros dois papéis) somando a nota nova à
     * média anterior, e grava o resultado no Room + Firestore. Devolve a nova média. */
    private suspend fun updateUserRatingAggregate(targetUserId: String, stars: Int): Double {
        val user = fetchUser(targetUserId)
        if (user == null) {
            Log.e(TAG_RF10_DEBUG, "updateUserRatingAggregate($targetUserId): fetchUser voltou null — a média/contagem NÃO foi gravada em lugar nenhum (nem Room nem Firestore).")
        }
        val currentAverage = user?.ratingAverage ?: 0.0
        val currentCount = user?.ratingCount ?: 0
        val newCount = currentCount + 1
        val newAverage = ((currentAverage * currentCount) + stars) / newCount

        if (user != null) {
            val updatedUser = user.copy(ratingAverage = newAverage, ratingCount = newCount)
            userDao.upsert(updatedUser)
            runCatching {
                firestore.collection(Constants.COLLECTION_USERS)
                    .document(targetUserId)
                    .update(mapOf("ratingAverage" to newAverage, "ratingCount" to newCount))
                    .await()
            }.onFailure { e ->
                Log.e(TAG_RF10_DEBUG, "updateUserRatingAggregate($targetUserId): achou o usuário mas a gravação no Firestore falhou", e)
            }.onSuccess {
                Log.i(TAG_RF10_DEBUG, "updateUserRatingAggregate($targetUserId): gravou média=$newAverage contagem=$newCount no Firestore com sucesso")
            }
        }
        return newAverage
    }

    /**
     * Sincroniza do Firestore pro Room as participações do usuário logado — mesmo bug de
     * fundo já corrigido pro perfil (ver [AuthRepository.syncCurrentUserToLocal]): igual o
     * perfil, a participação é gravada localmente na hora de agendar (RF07), mas nada
     * buscava de volta do Firestore caso o banco local fosse recriado (troca de schema do
     * Room, reinstalação, banco vazio numa sessão do Firebase já persistida etc.) — nesse
     * caso a agenda em Firestore continua correta, só o cache local é que fica sem nada, e
     * "Meus Próximos Eventos"/"Histórico" aparecem vazios mesmo com carneação agendada de
     * verdade. Chamado em toda abertura do app (MainActivity.onStart), best-effort (sem
     * internet, silenciosamente não atualiza e mantém o que já tinha local).
     */
    suspend fun syncParticipationsToLocal(userId: String) {
        runCatching {
            val snapshot = firestore.collection(Constants.COLLECTION_PARTICIPATIONS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            participationDao.upsertAll(snapshot.toObjects(Participation::class.java))
        }
    }

    suspend fun refresh(): Result<Unit> = runCatching {
        val snapshot = firestore.collection(Constants.COLLECTION_EVENTS).get().await()
        val events = snapshot.toObjects(Event::class.java)
        eventDao.upsertAll(events)

        for (event in events) {
            val cutsSnapshot = firestore.collection(Constants.COLLECTION_EVENTS)
                .document(event.id)
                .collection(Constants.COLLECTION_CUTS)
                .get()
                .await()
            eventDao.upsertCuts(cutsSnapshot.toObjects(Cut::class.java))
        }
    }
}
