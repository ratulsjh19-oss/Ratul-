package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val username: String,
    val fullName: String,
    val passwordHash: String,
    val email: String,
    val phoneNumber: String,
    val balance: Double = 1000.0, // Default welcome balance of $1000
    val accountNumber: String = "" // Generated e.g., "APEX-5829-1048"
)
