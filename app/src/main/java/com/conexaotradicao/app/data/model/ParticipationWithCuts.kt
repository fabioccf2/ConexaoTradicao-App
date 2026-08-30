package com.conexaotradicao.app.data.model

/**
 * Participação (RF07) já com o nome dos cortes selecionados resolvido, pra exibir em
 * "Meus Próximos Eventos"/Histórico do Perfil (RF02) sem cada item da lista precisar
 * consultar o banco sozinho. Não é entidade do Room — só uma combinação pra UI.
 */
data class ParticipationWithCuts(
    val participation: Participation,
    val cutNames: List<String>
)
