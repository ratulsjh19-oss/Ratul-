package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Notification
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: Notification)

    @Query("SELECT * FROM notifications WHERE username = :username ORDER BY timestamp DESC")
    fun getNotificationsForUser(username: String): Flow<List<Notification>>

    @Query("SELECT COUNT(*) FROM notifications WHERE username = :username AND isRead = 0")
    fun getUnreadCount(username: String): Flow<Int>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Int)

    @Query("UPDATE notifications SET isRead = 1 WHERE username = :username")
    suspend fun markAllAsRead(username: String)
}
