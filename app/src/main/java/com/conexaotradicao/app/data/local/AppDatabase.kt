package com.conexaotradicao.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.conexaotradicao.app.data.model.ChatMessage
import com.conexaotradicao.app.data.model.Cut
import com.conexaotradicao.app.data.model.Event
import com.conexaotradicao.app.data.model.Participation
import com.conexaotradicao.app.data.model.Rating
import com.conexaotradicao.app.data.model.User

/**
 * Banco local (SQLite via Room) — é a base da estratégia offline-first (RNF02):
 * a UI sempre lê daqui primeiro; os repositórios sincronizam com o Firestore em segundo plano
 * e regravam o resultado aqui quando a conexão está disponível.
 */
@Database(
    entities = [User::class, Event::class, Cut::class, Participation::class, ChatMessage::class, Rating::class],
    // v2: adicionado ChatMessage.senderName (chat mostra quem mandou cada mensagem).
    // v3: adicionado Event.finalized/photoBase64List e Participation.producerRated (RF10 —
    // "Finalizar Evento" + avaliação nos dois sentidos, produtor↔comprador).
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun participationDao(): ParticipationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun ratingDao(): RatingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "conexao_tradicao.db"
                )
                    // Projeto em desenvolvimento, sem migrations escritas ainda: se o schema
                    // mudar (como agora, v1 -> v2), recria o banco local em vez de crashar.
                    // Tudo aqui é cache offline-first do Firestore, então não perde dado de
                    // verdade — só precisa reabrir o app com internet pra ressincronizar.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
