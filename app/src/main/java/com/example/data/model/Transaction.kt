package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String, // Owner of this transaction entry
    val type: String, // "SEND", "RECEIVE", "DEPOSIT", "WITHDRAW"
    val amount: Double,
    val recipientOrSender: String, // Receiver username, sender username, or bank/ATM name
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)
