package com.conexaotradicao.app.data.model

/** Tipo de animal de um evento de carneamento. */
enum class Animal {
    GADO,
    PORCO
}

enum class UserRole {
    PRODUTOR,
    COMPRADOR
}

/** Situação da participação do comprador em um evento (RF07). */
enum class ParticipationStatus {
    AGENDADO,
    CONCLUIDO,
    CANCELADO
}
