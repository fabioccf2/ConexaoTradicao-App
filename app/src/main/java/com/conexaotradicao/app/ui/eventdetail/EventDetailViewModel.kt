package com.conexaotradicao.app.ui.eventdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.conexaotradicao.app.data.model.Cut
import com.conexaotradicao.app.data.model.Event
import com.conexaotradicao.app.data.model.Participation
import com.conexaotradicao.app.data.model.ParticipationWithUser
import com.conexaotradicao.app.data.repository.EventRepository
import com.conexaotradicao.app.util.Resource
import kotlinx.coroutines.launch

/** Tela 3 — Detalhes do Evento (RF05, RF06, RF07, RF09). */
class EventDetailViewModel(
    private val eventId: String,
    private val currentUserId: String?,
    private val eventRepository: EventRepository
) : ViewModel() {

    val cuts: LiveData<List<Cut>> = eventRepository.observeCuts(eventId).asLiveData()

    private val _event = MutableLiveData<Event?>()
    val event: LiveData<Event?> = _event

    private val _scheduleState = MutableLiveData<Resource<Unit>>()
    val scheduleState: LiveData<Resource<Unit>> = _scheduleState

    private val _cancelState = MutableLiveData<Resource<Unit>>()
    val cancelState: LiveData<Resource<Unit>> = _cancelState

    // RF10
    private val _finalizeState = MutableLiveData<Resource<Unit>>()
    val finalizeState: LiveData<Resource<Unit>> = _finalizeState

    private val _rateState = MutableLiveData<Resource<Unit>>()
    val rateState: LiveData<Resource<Unit>> = _rateState

    /** A participação do usuário logado nesse evento (se comprador e já tiver agendado) —
     * usado tanto pra saber se já foi avaliada (botão "Avaliar Produtor") quanto o status
     * (AGENDADO/CONCLUIDO/CANCELADO). */
    private val _myParticipation = MutableLiveData<Participation?>()
    val myParticipation: LiveData<Participation?> = _myParticipation

    /** RF10 — lista de compradores do evento, só relevante (e só carregada de fato) pro
     * produtor dono do evento, pra ele avaliar cada um depois de finalizar. */
    private val _participants = MutableLiveData<List<ParticipationWithUser>>(emptyList())
    val participants: LiveData<List<ParticipationWithUser>> = _participants

    /**
     * RNF05 — localização exata só aparece pra quem já confirmou presença neste evento.
     * Reaproveitado também pra saber se o botão "Agendar Carneação" deve virar
     * "Carneação Agendada ✓" com um botão de cancelar (RF07) — as duas coisas dependem
     * exatamente da mesma pergunta: "esse usuário já tem uma participação agendada aqui?".
     */
    private val _locationUnlocked = MutableLiveData(false)
    val locationUnlocked: LiveData<Boolean> = _locationUnlocked

    // Working set mutável usado internamente (schedule() lê isso na hora de agendar);
    // _selectedCutIds espelha o mesmo conteúdo como LiveData pra CutAdapter conseguir
    // pré-marcar os checkboxes reativamente (ver refreshSelectedCuts() e toggleCut()).
    private val selectedCutIdsSet = mutableSetOf<String>()

    private val _selectedCutIds = MutableLiveData<Set<String>>(emptySet())
    /** RF06/RF07 — cortes marcados agora (seleção manual) ou já escolhidos numa participação
     * existente (pré-carregados ao abrir a tela) — o que estiver aqui é o que aparece
     * marcado nos checkboxes, inclusive depois de rolar a lista (CutAdapter). */
    val selectedCutIds: LiveData<Set<String>> = _selectedCutIds

    init {
        viewModelScope.launch { _event.value = eventRepository.getEvent(eventId) }
        refreshLocationUnlocked()
        refreshMyParticipation()
        refreshParticipants()
    }

    private fun refreshLocationUnlocked() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            _locationUnlocked.value = eventRepository.hasScheduledParticipation(eventId, userId)
        }
    }

    /** Busca a participação já existente do usuário (se houver), pré-marca os cortes que
     * ele escolheu da última vez (em vez de abrir a tela sempre em branco) e guarda a
     * participação inteira (RF10 — pra saber se já avaliou o produtor). */
    private fun refreshMyParticipation() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            val participation = eventRepository.getParticipation(eventId, userId)
            _myParticipation.value = participation
            selectedCutIdsSet.clear()
            if (participation != null) selectedCutIdsSet.addAll(participation.selectedCutIds)
            _selectedCutIds.value = selectedCutIdsSet.toSet()
        }
    }

    /** RF10 — lista de compradores do evento, pro produtor avaliar cada um. Chamado sempre
     * (mesmo pro comprador, que simplesmente não vai mostrar essa lista na tela) porque é
     * uma leitura local, offline-first, sem custo perceptível. */
    private fun refreshParticipants() {
        viewModelScope.launch {
            _participants.value = eventRepository.getParticipantsForEvent(eventId)
        }
    }

    fun toggleCut(cutId: String, checked: Boolean) {
        if (checked) selectedCutIdsSet.add(cutId) else selectedCutIdsSet.remove(cutId)
        _selectedCutIds.value = selectedCutIdsSet.toSet()
    }

    /** RF10 — botão "Finalizar Evento" (só o produtor dono do evento vê): marca o evento como
     * realizado com as fotos escolhidas e conclui todas as participações agendadas. */
    fun finalizeEvent(photoBase64List: List<String>) {
        _finalizeState.value = Resource.Loading
        viewModelScope.launch {
            val result = eventRepository.finalizeEvent(eventId, photoBase64List)
            _finalizeState.value = result.fold(
                onSuccess = { Resource.Success(Unit) },
                onFailure = { Resource.Error(it.message ?: "Não foi possível finalizar o evento.", it) }
            )
            if (result.isSuccess) {
                _event.value = eventRepository.getEvent(eventId)
                refreshParticipants()
            }
        }
    }

    /** RF10 — comprador avalia o produtor, depois do evento finalizado. */
    fun rateProducer(stars: Int) {
        val userId = currentUserId
        val producerId = event.value?.producerId
        if (userId == null || producerId == null) {
            _rateState.value = Resource.Error("Você precisa estar logado para avaliar.")
            return
        }
        _rateState.value = Resource.Loading
        viewModelScope.launch {
            val result = eventRepository.rateProducer(eventId, userId, producerId, stars)
            _rateState.value = result.fold(
                onSuccess = { Resource.Success(Unit) },
                onFailure = { Resource.Error(it.message ?: "Não foi possível enviar a avaliação.", it) }
            )
            if (result.isSuccess) refreshMyParticipation()
        }
    }

    /** RF10 — produtor avalia um comprador específico, depois do evento finalizado. */
    fun rateClient(clientUserId: String, stars: Int) {
        val producerId = currentUserId
        if (producerId == null) {
            _rateState.value = Resource.Error("Você precisa estar logado para avaliar.")
            return
        }
        _rateState.value = Resource.Loading
        viewModelScope.launch {
            val result = eventRepository.rateClient(eventId, producerId, clientUserId, stars)
            _rateState.value = result.fold(
                onSuccess = { Resource.Success(Unit) },
                onFailure = { Resource.Error(it.message ?: "Não foi possível enviar a avaliação.", it) }
            )
            if (result.isSuccess) refreshParticipants()
        }
    }

    /** RF07 — botão "Agendar Carneação": reserva os cortes selecionados e grava a participação. */
    fun schedule() {
        val userId = currentUserId
        if (userId == null) {
            _scheduleState.value = Resource.Error("Você precisa estar logado para agendar.")
            return
        }
        // RF10 — o botão já some da tela nesse caso (ver EventDetailFragment), essa é só uma
        // segunda trava contra corrida (produtor finalizando bem na hora que alguém tenta
        // agendar): não faz sentido reservar presença numa carneação que já aconteceu.
        if (event.value?.finalized == true) {
            _scheduleState.value = Resource.Error("Esse evento já foi finalizado pelo produtor.")
            return
        }
        _scheduleState.value = Resource.Loading
        viewModelScope.launch {
            val eventDate = event.value?.dateMillis ?: System.currentTimeMillis()
            val result = eventRepository.scheduleParticipation(
                eventId = eventId,
                userId = userId,
                selectedCutIds = selectedCutIdsSet.toList(),
                eventDateMillis = eventDate
            )
            _scheduleState.value = result.fold(
                onSuccess = { Resource.Success(Unit) },
                onFailure = { Resource.Error(it.message ?: "Não foi possível agendar.", it) }
            )
            if (result.isSuccess) {
                refreshLocationUnlocked()
                refreshMyParticipation()
            }
        }
    }

    /** RF07 — botão "Cancelar Agendamento": desfaz a participação já confirmada. */
    fun cancel() {
        val userId = currentUserId
        if (userId == null) {
            _cancelState.value = Resource.Error("Você precisa estar logado para cancelar.")
            return
        }
        _cancelState.value = Resource.Loading
        viewModelScope.launch {
            val result = eventRepository.cancelParticipation(eventId, userId)
            _cancelState.value = result.fold(
                onSuccess = { Resource.Success(Unit) },
                onFailure = { Resource.Error(it.message ?: "Não foi possível cancelar.", it) }
            )
            if (result.isSuccess) {
                refreshLocationUnlocked()
                // Cancelou o agendamento — a participação não existe mais, então isso também
                // limpa os checkboxes e o `myParticipation` (RF10).
                refreshMyParticipation()
            }
        }
    }
}
