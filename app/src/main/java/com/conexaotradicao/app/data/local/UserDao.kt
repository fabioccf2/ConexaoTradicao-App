package com.conexaotradicao.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conexaotradicao.app.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE id = :userId")
    fun observe(userId: String): Flow<User?>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getById(userId: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: User)
}
