package com.conexaotradicao.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Reserva de um comprador em um evento — "Agendar Carneação" (RF07).
 * Alimenta o histórico de participações do perfil (RF02) e a avaliação pós-evento (RF10).
 */
@Entity(tableName = "participations")
data class Participation(
    // Valores padrão exigidos pelo construtor sem argumentos que o Firestore usa via reflection.
    @PrimaryKey val id: String = "",
    val eventId: String = "",
    val userId: String = "",
    val selectedCutIds: List<String> = emptyList(),
    val status: ParticipationStatus = ParticipationStatus.AGENDADO,
    val scheduledAtMillis: Long = 0L,
    // RF10 — o comprador já avaliou o produtor por essa participação?
    val alreadyRated: Boolean = false,
    // RF10 — o produtor já avaliou esse comprador por essa participação?
    val producerRated: Boolean = false
)
