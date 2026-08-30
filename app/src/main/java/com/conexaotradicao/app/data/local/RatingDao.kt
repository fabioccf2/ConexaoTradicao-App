package com.conexaotradicao.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conexaotradicao.app.data.model.Rating

@Dao
interface RatingDao {

    @Query("SELECT * FROM ratings WHERE targetUserId = :userId ORDER BY createdAtMillis DESC")
    suspend fun getForUser(userId: String): List<Rating>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rating: Rating)
}
