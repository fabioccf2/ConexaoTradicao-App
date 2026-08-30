package com.conexaotradicao.app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.conexaotradicao.app.data.model.EventCardItem
import com.conexaotradicao.app.data.model.ParticipationStatus
import com.conexaotradicao.app.data.model.ParticipationWithCuts
import com.conexaotradicao.app.data.repository.ProfileRepository
import com.conexaotradicao.app.util.Resource
import kotlinx.coroutines.launch

/** Tela 5 — Perfil (RF02): dados do usuário, próximos eventos e histórico (RF10). */
class ProfileViewModel(
    private val userId: String?,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val user: LiveData<com.conexaotradicao.app.data.model.User?> =
        (userId?.let { profileRepository.observeUser(it) } ?: kotlinx.coroutines.flow.flowOf(null))
            .asLiveData()

    private val allParticipations: LiveData<List<ParticipationWithCuts>> =
        (userId?.let { profileRepository.observeParticipations(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .asLiveData()

    /** "Eventos que estou inscrito" — participações ainda agendadas (RF07). */
    val upcomingParticipations: LiveData<List<ParticipationWithCuts>> =
        allParticipations.map { list ->
            list.filter { it.participation.status == ParticipationStatus.AGENDADO }
        }

    /** Histórico de participações já concluídas, com avaliação (RF10). */
    val pastParticipations: LiveData<List<ParticipationWithCuts>> =
        allParticipations.map { list ->
            list.filter { it.participation.status != ParticipationStatus.AGENDADO }
        }

    private val myFinalizedEventsRaw: LiveData<List<com.conexaotradicao.app.data.model.Event>> =
        (userId?.let { profileRepository.observeMyFinalizedEvents(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .asLiveData()

    /** RF10 — eventos que o usuário criou (como produtor) e já finalizou — não aparecem mais
     * na Home (ver HomeViewModel), então é aqui que ele volta neles pra avaliar compradores
     * que faltaram ou rever as fotos. Reaproveita `EventCardItem`/`EventAdapter` da Home
     * (mesmo card, `isScheduled` sempre false aqui — não faz sentido pro próprio produtor). */
    val myFinalizedEvents: LiveData<List<EventCardItem>> =
        myFinalizedEventsRaw.map { events -> events.map { EventCardItem(it, isScheduled = false) } }

    private val _editProfileState = MutableLiveData<Resource<Unit>>()
    val editProfileState: LiveData<Resource<Unit>> = _editProfileState

    init {
        refresh()
    }

    /** RF10 — rebusca o próprio perfil no Firestore (nota em estrelas incluída) toda vez que
     * a tela de Perfil abre, pra não depender só do que foi salvo localmente no primeiro
     * login (ver `ProfileRepository.refreshUser`). */
    fun refresh() {
        val uid = userId ?: return
        viewModelScope.launch { profileRepository.refreshUser(uid) }
    }

    /** Editar Perfil — salva o novo nome no Room e no Firestore. */
    fun updateName(name: String) {
        val uid = userId
        if (uid == null) {
            _editProfileState.value = Resource.Error("Você precisa estar logado.")
            return
        }
        if (name.isBlank()) {
            _editProfileState.value = Resource.Error("O nome não pode ficar vazio.")
            return
        }
        _editProfileState.value = Resource.Loading
        viewModelScope.launch {
            val result = profileRepository.updateName(uid, name.trim())
            _editProfileState.value = result.fold(
                onSuccess = { Resource.Success(Unit) },
                onFailure = { Resource.Error(it.message ?: "Não foi possível salvar o nome.", it) }
            )
        }
    }
}
