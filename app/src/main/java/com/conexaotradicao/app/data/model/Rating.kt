package com.conexaotradicao.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Avaliação em estrelas feita após a participação em um evento (RF10). */
@Entity(tableName = "ratings")
data class Rating(
    // Valores padrão exigidos pelo construtor sem argumentos que o Firestore usa via reflection.
    @PrimaryKey val id: String = "",
    val eventId: String = "",
    val raterUserId: String = "",
    val targetUserId: String = "",
    val stars: Int = 0,
    val comment: String? = null,
    val createdAtMillis: Long = 0L
)
