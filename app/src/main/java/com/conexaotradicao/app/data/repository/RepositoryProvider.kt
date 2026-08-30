package com.conexaotradicao.app.data.repository

import android.content.Context
import com.conexaotradicao.app.ConexaoTradicaoApp

/**
 * Fábrica simples dos repositórios, reaproveitando o AppDatabase único da Application.
 * Mantém o projeto sem um framework de injeção de dependência (Hilt/Koin) por enquanto —
 * pode ser evoluído depois sem afetar a UI, já que os Fragments só conhecem os repositórios.
 */
object RepositoryProvider {

    fun authRepository(context: Context): AuthRepository {
        val db = (context.applicationContext as ConexaoTradicaoApp).database
        return AuthRepository(userDao = db.userDao())
    }

    fun eventRepository(context: Context): EventRepository {
        val db = (context.applicationContext as ConexaoTradicaoApp).database
        return EventRepository(
            eventDao = db.eventDao(),
            participationDao = db.participationDao(),
            userDao = db.userDao(),
            ratingDao = db.ratingDao()
        )
    }

    fun userDao(context: Context) =
        (context.applicationContext as ConexaoTradicaoApp).database.userDao()

    fun chatRepository(context: Context): ChatRepository {
        val db = (context.applicationContext as ConexaoTradicaoApp).database
        return ChatRepository(chatMessageDao = db.chatMessageDao(), userDao = db.userDao())
    }

    fun profileRepository(context: Context): ProfileRepository {
        val db = (context.applicationContext as ConexaoTradicaoApp).database
        return ProfileRepository(
            userDao = db.userDao(),
            participationDao = db.participationDao(),
            ratingDao = db.ratingDao(),
            eventDao = db.eventDao()
        )
    }
}
