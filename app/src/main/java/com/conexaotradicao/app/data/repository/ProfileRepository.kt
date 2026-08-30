package com.conexaotradicao.app.data.repository

import com.conexaotradicao.app.data.local.EventDao
import com.conexaotradicao.app.data.local.ParticipationDao
import com.conexaotradicao.app.data.local.RatingDao
import com.conexaotradicao.app.data.local.UserDao
import com.conexaotradicao.app.data.model.Event
import com.conexaotradicao.app.data.model.ParticipationWithCuts
import com.conexaotradicao.app.data.model.Rating
import com.conexaotradicao.app.data.model.User
import com.conexaotradicao.app.util.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Perfil do usuário, histórico de participações (RF02) e avaliações pós-evento (RF10). */
class ProfileRepository(
    private val userDao: UserDao,
    private val participationDao: ParticipationDao,
    private val ratingDao: RatingDao,
    private val eventDao: EventDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun observeUser(userId: String): Flow<User?> = userDao.observe(userId)

    /**
     * RF02/RF10 — busca o perfil do usuário LOGADO direto no Firestore e atualiza o cache
     * local (Room). Sem isso, a tela de Perfil só mostra o que foi salvo localmente no
     * primeiro login/cadastro (ver `AuthRepository.syncCurrentUserToLocal`, que só preenche
     * uma vez e nunca mais mexe se já existir registro local) — então uma nota em estrelas
     * dada por OUTRO usuário, em OUTRO aparelho, nunca aparecia no próprio perfil de quem foi
     * avaliado, mesmo já estando gravada certinho no Firestore (confirmado por log: a
     * gravação funciona, só a tela de quem foi avaliado nunca ia buscar de novo). Chamado
     * toda vez que a tela de Perfil abre; best-effort (offline simplesmente mantém o que já
     * tinha local, sem travar nem mostrar erro).
     */
    suspend fun refreshUser(userId: String) {
        runCatching {
            val user = firestore.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .get()
                .await()
                .toObject(User::class.java)
            if (user != null) userDao.upsert(user)
        }
    }

    /** Participações do usuário (RF02/RF07) já com o nome dos cortes selecionados resolvido
     * — a lista mostra "Picanha, Costela" em vez de só data/status, e o `eventId` de cada
     * uma dá pra abrir o evento correspondente ao tocar no item. */
    fun observeParticipations(userId: String): Flow<List<ParticipationWithCuts>> =
        participationDao.observeByUser(userId).map { list ->
            list.map { participation ->
                val cutNames = if (participation.selectedCutIds.isEmpty()) {
                    emptyList()
                } else {
                    eventDao.getCutsByIds(participation.eventId, participation.selectedCutIds)
                        .map { it.name }
                }
                ParticipationWithCuts(participation, cutNames)
            }
        }

    suspend fun rate(rating: Rating) = ratingDao.insert(rating)

    /** RF10 — eventos que esse usuário criou (como produtor) e já finalizou. Some da Home
     * assim que finalizado (ver HomeViewModel), então é por aqui — "Meus Eventos
     * Finalizados" no Perfil — que o produtor consegue voltar num evento já realizado, seja
     * pra continuar avaliando compradores ou só rever as fotos. Pra quem nunca criou um
     * evento (conta só compradora), essa lista vem sempre vazia — a seção fica escondida. */
    fun observeMyFinalizedEvents(userId: String): Flow<List<Event>> =
        eventDao.observeFinalizedByProducer(userId)

    /** Editar Perfil (RF02) — por enquanto só o nome; grava no Room e sincroniza no Firestore. */
    suspend fun updateName(userId: String, name: String): Result<Unit> = runCatching {
        val current = userDao.getById(userId) ?: throw IllegalStateException("Usuário não encontrado.")
        val updated = current.copy(name = name)
        userDao.upsert(updated)
        firestore.collection(Constants.COLLECTION_USERS)
            .document(userId)
            .update("name", name)
            .await()
    }
}
