package com.conexaotradicao.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Usuário do app — pode ser produtor rural ou comprador (RF01, RF02).
 * Espelha o documento "users/{uid}" no Firestore e é cacheado localmente via Room
 * para permitir uso offline-first (RNF02).
 */
@Entity(tableName = "users")
data class User(
    // Todos os campos têm valor padrão de propósito: o SDK do Firestore precisa de um
    // construtor sem argumentos para desserializar documentos em objetos Kotlin.
    @PrimaryKey val id: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val role: UserRole = UserRole.COMPRADOR,
    val ratingAverage: Double = 0.0,
    val ratingCount: Int = 0
)
