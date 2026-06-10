package com.example.data.database

import androidx.room.*
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    fun getUserByUsernameFlow(username: String): Flow<User?>

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT * FROM users WHERE username != :excludeUsername")
    fun getAllOtherUsersFlow(excludeUsername: String): Flow<List<User>>
}
