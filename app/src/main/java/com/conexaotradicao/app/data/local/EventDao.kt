package com.conexaotradicao.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conexaotradicao.app.data.model.Cut
import com.conexaotradicao.app.data.model.Event
import kotlinx.coroutines.flow.Flow

/** Acesso local (cache offline — RNF02) aos eventos e seus cortes/preços. */
@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY dateMillis ASC")
    fun observeAll(): Flow<List<Event>>

    @Query(
        "SELECT * FROM events WHERE city LIKE '%' || :city || '%' " +
            "AND mainCutsSummary LIKE '%' || :product || '%' ORDER BY dateMillis ASC"
    )
    fun observeFiltered(city: String, product: String): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getById(eventId: String): Event?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<Event>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: Event)

    @Query("SELECT * FROM cuts WHERE eventId = :eventId")
    fun observeCuts(eventId: String): Flow<List<Cut>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCuts(cuts: List<Cut>)

    /** Resolve os nomes dos cortes escolhidos numa participação (RF02/RF07), pra mostrar
     * "Picanha, Costela" na lista de "Meus Próximos Eventos"/Histórico do Perfil. */
    @Query("SELECT * FROM cuts WHERE eventId = :eventId AND id IN (:cutIds)")
    suspend fun getCutsByIds(eventId: String, cutIds: List<String>): List<Cut>

    /** RF10 — todos os eventos de um produtor, pra atualizar o `producerRatingAverage` de
     * cada um assim que ele recebe uma avaliação nova (o card/detalhe de qualquer evento
     * dele reflete a nota mais atual, não só o evento avaliado). */
    @Query("SELECT * FROM events WHERE producerId = :producerId")
    suspend fun getByProducer(producerId: String): List<Event>

    /** RF10 — eventos que esse produtor já finalizou (deixaram de aparecer na Home — ver
     * HomeViewModel), pra ele conseguir voltar neles no Perfil e continuar avaliando
     * compradores que ainda faltavam ou rever as fotos. */
    @Query("SELECT * FROM events WHERE producerId = :producerId AND finalized = 1 ORDER BY dateMillis DESC")
    fun observeFinalizedByProducer(producerId: String): Flow<List<Event>>

    /** Excluir Evento (produtor): remove o evento e seus cortes/preços do cache local. */
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteById(eventId: String)

    @Query("DELETE FROM cuts WHERE eventId = :eventId")
    suspend fun deleteCutsByEvent(eventId: String)
}
