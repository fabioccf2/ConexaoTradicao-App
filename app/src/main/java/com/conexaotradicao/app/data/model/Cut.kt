package com.conexaotradicao.app.data.model

import androidx.room.Entity

/** Corte/subproduto disponível em um evento, com preço por kg (RF05, RF06). */
@Entity(tableName = "cuts", primaryKeys = ["id", "eventId"])
data class Cut(
    // Valores padrão exigidos pelo construtor sem argumentos que o Firestore usa via reflection.
    val id: String = "",
    val eventId: String = "",
    val name: String = "",
    val pricePerKg: Double = 0.0
)
