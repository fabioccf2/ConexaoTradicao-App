package com.conexaotradicao.app.data.model

/**
 * Participação (comprador agendado num evento) já com o nome do comprador resolvido —
 * usado na lista que o produtor vê pra avaliar cada comprador depois de finalizar o evento
 * (RF10). Não é entidade do Room, só uma combinação pra UI (mesmo padrão de
 * [ParticipationWithCuts] e [EventCardItem]).
 */
data class ParticipationWithUser(
    val participation: Participation,
    val buyerName: String
)
