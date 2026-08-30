package com.conexaotradicao.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conexaotradicao.app.data.model.EventCardItem
import com.conexaotradicao.app.data.repository.EventRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Tela 2 — Próximos Eventos de Carneamento / Home (RF03, RF04). */
class HomeViewModel(
    private val eventRepository: EventRepository,
    private val currentUserId: String?
) : ViewModel() {

    private val cityFilter = MutableStateFlow("")
    private val productFilter = MutableStateFlow("")

    private val _events = MutableLiveData<List<EventCardItem>>(emptyList())
    val events: LiveData<List<EventCardItem>> = _events

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    init {
        viewModelScope.launch {
            val filteredEvents = combine(cityFilter, productFilter) { city, product -> city to product }
                .flatMapLatest { (city, product) -> eventRepository.observeEvents(city, product) }
            // RF07 — quais desses eventos o usuário logado já agendou, pra destacar o card
            // (ver EventAdapter). Sem usuário logado (não deveria acontecer aqui dentro do
            // app, mas por segurança), nenhum card fica destacado.
            val scheduledIds = currentUserId?.let { eventRepository.observeScheduledEventIds(it) }
                ?: flowOf(emptySet())
            combine(filteredEvents, scheduledIds) { events, ids ->
                events
                    // RF10 — evento já finalizado não é mais um "próximo evento de
                    // carneamento" (é o que a Home lista): some daqui de vez. Continua
                    // acessível pra quem participou (Perfil → Histórico, comprador) e pra
                    // quem criou (Perfil → Meus Eventos Finalizados, produtor).
                    .filter { !it.finalized }
                    .map { EventCardItem(it, isScheduled = ids.contains(it.id)) }
            }.collect { _events.value = it }
        }
        refresh()
    }

    fun onCityChanged(city: String) {
        cityFilter.value = city
    }

    fun onProductChanged(product: String) {
        productFilter.value = product
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            eventRepository.refresh()
            _isRefreshing.value = false
        }
    }
}
