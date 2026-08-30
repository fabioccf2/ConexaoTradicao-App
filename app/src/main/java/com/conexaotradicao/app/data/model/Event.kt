package com.conexaotradicao.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Evento de carneamento cadastrado por um produtor (RF05).
 * Exibido na listagem (RF03) e na tela de detalhes (RF06).
 */
@Entity(tableName = "events")
data class Event(
    // Todos os campos têm valor padrão de propósito: necessário para o Firestore
    // desserializar documentos via reflection (construtor sem argumentos).
    @PrimaryKey val id: String = "",
    val producerId: String = "",
    val producerName: String = "",
    val producerRatingAverage: Double = 0.0,
    val animal: Animal = Animal.GADO,
    val dateMillis: Long = 0L,
    val city: String = "",
    val state: String = "",
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mainCutsSummary: String = "",
    val photoUrl: String? = null,
    // Só é exibida em detalhe após a confirmação de presença (RNF05 — privacidade)
    val exactLocationRevealed: Boolean = false,
    // RF10 — o produtor marcou esse evento como já realizado ("Finalizar Evento"): trava
    // reagendamento novo, libera avaliação (produtor avalia cada comprador, comprador avalia
    // o produtor) e mostra as fotos abaixo pra quem participou.
    val finalized: Boolean = false,
    // Fotos da carneação já realizada, tiradas/escolhidas pelo produtor ao finalizar.
    // Guardadas como JPEG comprimido + Base64 direto no Firestore (sem Storage — ver decisão
    // na Parte 3.16) — por isso o limite de poucas fotos por evento.
    val photoBase64List: List<String> = emptyList()
)
