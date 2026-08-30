package com.conexaotradicao.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conexaotradicao.app.data.model.Participation
import kotlinx.coroutines.flow.Flow

/** Histórico de participações do usuário (RF02) e reservas ativas (RF07). */
@Dao
interface ParticipationDao {

    @Query("SELECT * FROM participations WHERE userId = :userId ORDER BY scheduledAtMillis DESC")
    fun observeByUser(userId: String): Flow<List<Participation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(participation: Participation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(participations: List<Participation>)

    @Query("SELECT * FROM participations WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    suspend fun findByEventAndUser(eventId: String, userId: String): Participation?

    /** RF10 — todas as participações de um evento (todos os compradores), pro produtor
     * "Finalizar Evento" (marca todas como CONCLUIDO) e depois avaliar cada comprador. */
    @Query("SELECT * FROM participations WHERE eventId = :eventId")
    suspend fun getByEvent(eventId: String): List<Participation>

    /** RF07 — cancelar um agendamento. */
    @Delete
    suspend fun delete(participation: Participation)
}
