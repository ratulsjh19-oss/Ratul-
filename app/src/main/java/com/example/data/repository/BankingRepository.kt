package com.example.data.repository

import com.example.data.database.UserDao
import com.example.data.database.TransactionDao
import com.example.data.database.NotificationDao
import com.example.data.model.User
import com.example.data.model.Transaction
import com.example.data.model.Notification
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import kotlin.random.Random

class BankingRepository(
    private val userDao: UserDao,
    private val transactionDao: TransactionDao,
    private val notificationDao: NotificationDao
) {

    // Help secure database by storing a SHA-256 hash instead of a plain string
    private fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            password // Fallback, though SHA-256 is guaranteed to exist on Android
        }
    }

    suspend fun registerUser(
        username: String,
        fullName: String,
        rawPassword: String,
        email: String,
        phoneNumber: String
    ): Result<User> {
        val trimmedUsername = username.trim().lowercase()
        if (trimmedUsername.isEmpty() || fullName.trim().isEmpty() || rawPassword.isEmpty()) {
            return Result.failure(IllegalArgumentException("Fields cannot be empty"))
        }

        val existingUser = userDao.getUserByUsername(trimmedUsername)
        if (existingUser != null) {
            return Result.failure(IllegalArgumentException("Username already exists"))
        }

        // Generate custom Account Number mimicking Material M3 finance specs
        val part1 = Random.nextInt(1000, 9999)
        val part2 = Random.nextInt(1000, 9999)
        val accountNumber = "APX-$part1-$part2"

        val newUser = User(
            username = trimmedUsername,
            fullName = fullName.trim(),
            passwordHash = hashPassword(rawPassword),
            email = email.trim(),
            phoneNumber = phoneNumber.trim(),
            balance = 1000.0, // Promo welcome credit
            accountNumber = accountNumber
        )

        userDao.insertUser(newUser)

        // Seed welcome notification
        notificationDao.insertNotification(
            Notification(
                username = trimmedUsername,
                title = "Welcome to ApexBank! 🎉",
                message = "Thank you for joining ApexBank, $fullName. We have credited your account with a $1,000.00 welcome bonus! Start exploring your dashboard.",
                timestamp = System.currentTimeMillis()
            )
        )

        // Seed a deposit transaction for the welcome gift
        transactionDao.insertTransaction(
            Transaction(
                username = trimmedUsername,
                type = "DEPOSIT",
                amount = 1000.0,
                recipientOrSender = "Apex Rewards Core",
                description = "Apex Welcome Promo Benefit",
                timestamp = System.currentTimeMillis()
            )
        )

        return Result.success(newUser)
    }

    suspend fun loginUser(username: String, rawPassword: String): Result<User> {
        val trimmedUsername = username.trim().lowercase()
        val user = userDao.getUserByUsername(trimmedUsername) ?: return Result.failure(
            IllegalArgumentException("User does not exist")
        )

        val inputHash = hashPassword(rawPassword)
        if (user.passwordHash != inputHash) {
            return Result.failure(IllegalArgumentException("Incorrect secure password"))
        }

        // Generate success session access log alert
        notificationDao.insertNotification(
            Notification(
                username = trimmedUsername,
                title = "New Sign-in Alert 🔒",
                message = "A security session was opened for account ${user.accountNumber} on June 10, 2026. If this wasn't you, lock your credentials.",
                timestamp = System.currentTimeMillis()
            )
        )

        return Result.success(user)
    }

    fun getUserFlow(username: String): Flow<User?> {
        return userDao.getUserByUsernameFlow(username.trim().lowercase())
    }

    fun getOtherUsers(excludeUsername: String): Flow<List<User>> {
        return userDao.getAllOtherUsersFlow(excludeUsername.trim().lowercase())
    }

    fun getTransactions(username: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsForUser(username.trim().lowercase())
    }

    fun getNotifications(username: String): Flow<List<Notification>> {
        return notificationDao.getNotificationsForUser(username.trim().lowercase())
    }

    fun getUnreadNotificationsCount(username: String): Flow<Int> {
        return notificationDao.getUnreadCount(username.trim().lowercase())
    }

    suspend fun deposit(username: String, amount: Double, source: String = "External ATM"): Result<Unit> {
        if (amount <= 0.0) return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
        val trimmedUsername = username.trim().lowercase()
        val user = userDao.getUserByUsername(trimmedUsername) ?: return Result.failure(
            IllegalArgumentException("User not found")
        )

        val updatedUser = user.copy(balance = user.balance + amount)
        userDao.updateUser(updatedUser)

        val formattedAmount = "$%,.2f".format(amount)
        transactionDao.insertTransaction(
            Transaction(
                username = trimmedUsername,
                type = "DEPOSIT",
                amount = amount,
                recipientOrSender = source,
                description = "Self Cash Deposit via $source"
            )
        )

        notificationDao.insertNotification(
            Notification(
                username = trimmedUsername,
                title = "Deposit Successful 📈",
                message = "An amount of $formattedAmount has been deposited successfully. Your new balance is $%,.2f.".format(updatedUser.balance)
            )
        )

        return Result.success(Unit)
    }

    suspend fun withdraw(username: String, amount: Double, destination: String = "ATM Terminal"): Result<Unit> {
        if (amount <= 0.0) return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
        val trimmedUsername = username.trim().lowercase()
        val user = userDao.getUserByUsername(trimmedUsername) ?: return Result.failure(
            IllegalArgumentException("User not found")
        )

        if (user.balance < amount) {
            return Result.failure(IllegalArgumentException("Insufficient funds available"))
        }

        val updatedUser = user.copy(balance = user.balance - amount)
        userDao.updateUser(updatedUser)

        val formattedAmount = "$%,.2f".format(amount)
        transactionDao.insertTransaction(
            Transaction(
                username = trimmedUsername,
                type = "WITHDRAW",
                amount = amount,
                recipientOrSender = destination,
                description = "ATM Cash Withdrawal"
            )
        )

        notificationDao.insertNotification(
            Notification(
                username = trimmedUsername,
                title = "Cash Disbursed 📉",
                message = "You withdrew $formattedAmount from $destination. Your new balance is $%,.2f.".format(updatedUser.balance)
            )
        )

        return Result.success(Unit)
    }

    suspend fun sendMoney(
        senderUsername: String,
        recipientQuery: String, // Can be username or account number
        amount: Double,
        description: String
    ): Result<Unit> {
        if (amount <= 0.0) return Result.failure(IllegalArgumentException("Transfer amount must exceed $0.00"))
        
        val trimmedSender = senderUsername.trim().lowercase()
        val targetQuery = recipientQuery.trim()

        if (trimmedSender == targetQuery.lowercase()) {
            return Result.failure(IllegalArgumentException("Cannot send money to your own account"))
        }

        val sender = userDao.getUserByUsername(trimmedSender) ?: return Result.failure(
            IllegalArgumentException("Sender credentials invalid")
        )

        if (sender.balance < amount) {
            return Result.failure(IllegalArgumentException("Insufficient funds available for transfer"))
        }

        // Find recipient: check matching username, then matching account number!
        val allUsers = userDao.getAllUsers()
        val recipient = allUsers.firstOrNull {
            it.username.equals(targetQuery, ignoreCase = true) || 
            it.accountNumber.equals(targetQuery, ignoreCase = true)
        } ?: return Result.failure(IllegalArgumentException("Recipient account or username not recognized"))

        val trimmedRecipient = recipient.username

        // Transactions:
        // Sender deduction
        val updatedSender = sender.copy(balance = sender.balance - amount)
        userDao.updateUser(updatedSender)

        // Recipient collection
        val updatedRecipient = recipient.copy(balance = recipient.balance + amount)
        userDao.updateUser(updatedRecipient)

        val finalDesc = if (description.trim().isEmpty()) "Direct Wallet Transfer" else description.trim()
        val formattedAmount = "$%,.2f".format(amount)

        // Post sender transaction log
        transactionDao.insertTransaction(
            Transaction(
                username = trimmedSender,
                type = "SEND",
                amount = amount,
                recipientOrSender = recipient.fullName,
                description = finalDesc
            )
        )

        // Post recipient transaction log
        transactionDao.insertTransaction(
            Transaction(
                username = trimmedRecipient,
                type = "RECEIVE",
                amount = amount,
                recipientOrSender = sender.fullName,
                description = finalDesc
            )
        )

        // Notify sender
        notificationDao.insertNotification(
            Notification(
                username = trimmedSender,
                title = "Payment Transferred 📤",
                message = "Sent $formattedAmount to ${recipient.fullName} (${recipient.accountNumber}). Your new balance is $%,.2f.".format(updatedSender.balance)
            )
        )

        // Notify recipient
        notificationDao.insertNotification(
            Notification(
                username = trimmedRecipient,
                title = "Payment Received 📥",
                message = "Received $formattedAmount from ${sender.fullName}. Your new balance is $%,.2f.".format(updatedRecipient.balance)
            )
        )

        return Result.success(Unit)
    }

    suspend fun markNotificationRead(id: Int) {
        notificationDao.markAsRead(id)
    }

    suspend fun markAllNotificationsRead(username: String) {
        notificationDao.markAllAsRead(username.trim().lowercase())
    }

    // Prefill some sample users to make transferring money incredibly fun and real immediately!
    suspend fun seedSampleUsersIfDatabaseEmpty() {
        val users = userDao.getAllUsers()
        if (users.isEmpty()) {
            registerUser(
                username = "sarah",
                fullName = "Sarah Jenkins",
                rawPassword = "password",
                email = "sarah@apexbank.com",
                phoneNumber = "+1 (555) 0192"
            )
            registerUser(
                username = "marcus",
                fullName = "Marcus Aurelius",
                rawPassword = "password",
                email = "marcus@apexbank.com",
                phoneNumber = "+1 (555) 7281"
            )
            registerUser(
                username = "elena",
                fullName = "Elena Rostova",
                rawPassword = "password",
                email = "elena@apexbank.com",
                phoneNumber = "+1 (555) 3042"
            )
        }
    }
}
