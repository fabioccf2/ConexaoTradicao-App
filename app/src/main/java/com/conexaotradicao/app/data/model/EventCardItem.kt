package com.conexaotradicao.app.data.model

/**
 * Evento (RF03) já com a informação de "o usuário logado já agendou presença aqui?" (RF07)
 * resolvida, pra destacar visualmente o card na Home quando já tiver carneação marcada.
 * Não é entidade do Room — só uma combinação pra UI.
 */
data class EventCardItem(
    val event: Event,
    val isScheduled: Boolean
)
