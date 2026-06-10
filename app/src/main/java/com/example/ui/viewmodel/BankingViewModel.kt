package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Notification
import com.example.data.model.Transaction
import com.example.data.model.User
import com.example.data.repository.BankingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Success(val user: User) : AuthState
    data class Error(val message: String) : AuthState
}

sealed interface OperationResult {
    object Idle : OperationResult
    object Loading : OperationResult
    data class Success(val message: String) : OperationResult
    data class Error(val message: String) : OperationResult
}

class BankingViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = BankingRepository(
        userDao = database.userDao(),
        transactionDao = database.transactionDao(),
        notificationDao = database.notificationDao()
    )

    // Current logged-in user
    private val _currentUsername = MutableStateFlow<String?>(null)
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    // Light / Dark Theme State - reactive inside ViewModel to satisfy toggle requirement
    private val _isDarkMode = MutableStateFlow(true) // Default to modern premium dark look
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Reactive Flow for current user details
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUser: StateFlow<User?> = _currentUsername
        .flatMapLatest { username ->
            if (username == null) flowOf(null)
            else repository.getUserFlow(username)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // List of OTHER users (for easy Send Money transfer list)
    @OptIn(ExperimentalCoroutinesApi::class)
    val otherUsers: StateFlow<List<User>> = _currentUsername
        .flatMapLatest { username ->
            if (username == null) flowOf(emptyList())
            else repository.getOtherUsers(username)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Transactions Flow
    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> = _currentUsername
        .flatMapLatest { username ->
            if (username == null) flowOf(emptyList())
            else repository.getTransactions(username)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Notifications Flow
    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications: StateFlow<List<Notification>> = _currentUsername
        .flatMapLatest { username ->
            if (username == null) flowOf(emptyList())
            else repository.getNotifications(username)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Unread Notifications Count Flow
    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadNotificationsCount: StateFlow<Int> = _currentUsername
        .flatMapLatest { username ->
            if (username == null) flowOf(0)
            else repository.getUnreadNotificationsCount(username)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // UI Operation Results
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _operationResult = MutableStateFlow<OperationResult>(OperationResult.Idle)
    val operationResult: StateFlow<OperationResult> = _operationResult.asStateFlow()

    init {
        viewModelScope.launch {
            // Pre-create friendly demo accounts on initial install!
            repository.seedSampleUsersIfDatabaseEmpty()
        }
    }

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun login(username: String, pwh: String) {
        if (username.isBlank() || pwh.isBlank()) {
            _authState.value = AuthState.Error("Username and password cannot be empty")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            repository.loginUser(username, pwh)
                .onSuccess { user ->
                    _currentUsername.value = user.username
                    _authState.value = AuthState.Success(user)
                    _operationResult.value = OperationResult.Idle
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Authentication failed")
                }
        }
    }

    fun register(username: String, fullName: String, pwh: String, email: String, phone: String) {
        if (username.isBlank() || fullName.isBlank() || pwh.isBlank() || email.isBlank() || phone.isBlank()) {
            _authState.value = AuthState.Error("All fields are required")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            repository.registerUser(username, fullName, pwh, email, phone)
                .onSuccess { user ->
                    _currentUsername.value = user.username
                    _authState.value = AuthState.Success(user)
                    _operationResult.value = OperationResult.Idle
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Registration failed")
                }
        }
    }

    suspend fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    fun logout() {
        _currentUsername.value = null
        _authState.value = AuthState.Idle
        _operationResult.value = OperationResult.Idle
    }

    fun deposit(amountString: String) {
        val amount = amountString.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            _operationResult.value = OperationResult.Error("Enter a valid deposit amount")
            return
        }
        _operationResult.value = OperationResult.Loading
        val username = _currentUsername.value ?: return

        viewModelScope.launch {
            repository.deposit(username, amount)
                .onSuccess {
                    _operationResult.value = OperationResult.Success("Deposited $%,.2f successfully".format(amount))
                }
                .onFailure { error ->
                    _operationResult.value = OperationResult.Error(error.message ?: "Deposit failed")
                }
        }
    }

    fun withdraw(amountString: String) {
        val amount = amountString.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            _operationResult.value = OperationResult.Error("Enter a valid withdrawal amount")
            return
        }
        _operationResult.value = OperationResult.Loading
        val username = _currentUsername.value ?: return

        viewModelScope.launch {
            repository.withdraw(username, amount)
                .onSuccess {
                    _operationResult.value = OperationResult.Success("Withdrew $%,.2f successfully".format(amount))
                }
                .onFailure { error ->
                    _operationResult.value = OperationResult.Error(error.message ?: "Withdrawal failed")
                }
        }
    }

    fun sendMoney(recipient: String, amountString: String, description: String) {
        val amount = amountString.toDoubleOrNull() ?: 0.0
        if (recipient.isBlank()) {
            _operationResult.value = OperationResult.Error("Recipient username or account number is required")
            return
        }
        if (amount <= 0.0) {
            _operationResult.value = OperationResult.Error("Enter a valid transfer amount")
            return
        }
        _operationResult.value = OperationResult.Loading
        val username = _currentUsername.value ?: return

        viewModelScope.launch {
            repository.sendMoney(username, recipient, amount, description)
                .onSuccess {
                    _operationResult.value = OperationResult.Success("Transferred $%,.2f to $recipient successfully".format(amount))
                }
                .onFailure { error ->
                    _operationResult.value = OperationResult.Error(error.message ?: "Transfer failed")
                }
        }
    }

    fun clearOperationResult() {
        _operationResult.value = OperationResult.Idle
    }

    fun markNotificationAsRead(id: Int) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        val username = _currentUsername.value ?: return
        viewModelScope.launch {
            repository.markAllNotificationsRead(username)
        }
    }
}
