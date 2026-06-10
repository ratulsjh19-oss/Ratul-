package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE username = :username ORDER BY timestamp DESC")
    fun getTransactionsForUser(username: String): Flow<List<Transaction>>

    @Query("DELETE FROM transactions WHERE username = :username")
    suspend fun clearTransactionsForUser(username: String)
}
