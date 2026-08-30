package com.conexaotradicao.app.ui.createevent

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conexaotradicao.app.data.local.UserDao
import com.conexaotradicao.app.data.model.Animal
import com.conexaotradicao.app.data.model.Cut
import com.conexaotradicao.app.data.model.Event
import com.conexaotradicao.app.data.repository.EventRepository
import com.conexaotradicao.app.util.Resource
import kotlinx.coroutines.launch
import java.util.UUID

/** Tela de cadastro de evento pelo produtor (RF05). */
class CreateEventViewModel(
    private val currentUserId: String?,
    private val eventRepository: EventRepository,
    private val userDao: UserDao
) : ViewModel() {

    private val _state = MutableLiveData<Resource<Unit>>()
    val state: LiveData<Resource<Unit>> = _state

    fun publish(
        animal: Animal,
        dateMillis: Long,
        city: String,
        state: String,
        address: String,
        cuts: List<Pair<String, Double>>,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        if (currentUserId == null) {
            _state.value = Resource.Error("Você precisa estar logado para cadastrar um evento.")
            return
        }
        if (city.isBlank() || state.isBlank() || dateMillis == 0L) {
            _state.value = Resource.Error("Preencha data, cidade e estado.")
            return
        }
        val validCuts = cuts.filter { it.first.isNotBlank() && it.second > 0.0 }
        if (validCuts.isEmpty()) {
            _state.value = Resource.Error("Adicione ao menos um corte com preço.")
            return
        }

        _state.value = Resource.Loading
        viewModelScope.launch {
            val user = userDao.getById(currentUserId)
            val eventId = UUID.randomUUID().toString()

            val event = Event(
                id = eventId,
                producerId = currentUserId,
                producerName = user?.name ?: "Produtor",
                producerRatingAverage = user?.ratingAverage ?: 0.0,
                animal = animal,
                dateMillis = dateMillis,
                city = city,
                state = state,
                address = address.ifBlank { null },
                latitude = latitude,
                longitude = longitude,
                exactLocationRevealed = latitude != null && longitude != null,
                mainCutsSummary = validCuts.joinToString(", ") { it.first }
            )
            val cutEntities = validCuts.map { (name, price) ->
                Cut(id = UUID.randomUUID().toString(), eventId = eventId, name = name, pricePerKg = price)
            }

            val result = eventRepository.createEvent(event, cutEntities)
            _state.value = result.fold(
                onSuccess = { Resource.Success(Unit) },
                onFailure = { Resource.Error(it.message ?: "Não foi possível publicar o evento.", it) }
            )
        }
    }
}
