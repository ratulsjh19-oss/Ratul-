package com.example.ui.screens

import android.widget.Toast
import com.example.ui.theme.DarkSecondary
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Notification
import com.example.data.model.Transaction
import com.example.data.model.User
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.BankingViewModel
import com.example.ui.viewmodel.OperationResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankingAppShell(
    viewModel: BankingViewModel,
    modifier: Modifier = Modifier
) {
    val currentUsername by viewModel.currentUsername.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val unreadNotificationsCount by viewModel.unreadNotificationsCount.collectAsState()

    var activeScreen by remember { mutableStateOf("auth") }

    LaunchedEffect(currentUsername) {
        activeScreen = if (currentUsername == null) "auth" else "dashboard"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentUsername != null && activeScreen != "auth") {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = activeScreen == "dashboard",
                        onClick = { activeScreen = "dashboard" },
                        icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                        modifier = Modifier.testTag("nav_dashboard")
                    )
                    NavigationBarItem(
                        selected = activeScreen == "notifications",
                        onClick = { activeScreen = "notifications" },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (unreadNotificationsCount > 0) {
                                        Badge { Text(unreadNotificationsCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Alerts")
                            }
                        },
                        label = { Text("Alerts") },
                        modifier = Modifier.testTag("nav_alerts")
                    )
                    NavigationBarItem(
                        selected = activeScreen == "profile",
                        onClick = { activeScreen = "profile" },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        modifier = Modifier.testTag("nav_profile")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeScreen) {
                "auth" -> AuthScreen(viewModel)
                "dashboard" -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToTransfer = { activeScreen = "send" },
                    onNavigateToReceive = { activeScreen = "receive" },
                    onNavigateToATM = { activeScreen = "deposit_withdraw" }
                )
                "send" -> SendMoneyScreen(
                    viewModel = viewModel,
                    onBack = { activeScreen = "dashboard" }
                )
                "receive" -> ReceiveMoneyScreen(
                    viewModel = viewModel,
                    onBack = { activeScreen = "dashboard" }
                )
                "deposit_withdraw" -> DepositWithdrawScreen(
                    viewModel = viewModel,
                    onBack = { activeScreen = "dashboard" }
                )
                "notifications" -> NotificationsScreen(viewModel)
                "profile" -> ProfileScreen(viewModel)
            }
        }
    }
}

// ---------------------------------------------------------
// REUSABLE HELPER AND DESIGN COMPONENTS
// ---------------------------------------------------------

@Composable
fun SectionHeader(title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        trailing?.invoke()
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// ---------------------------------------------------------
// 1. AUTH SCREEN (LOGIN & REGISTER)
// ---------------------------------------------------------

@Composable
fun AuthScreen(viewModel: BankingViewModel) {
    var isLoginMode by remember { mutableStateOf(true) }
    
    // Form States
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var fullNameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    
    var passwordVisible by remember { mutableStateOf(false) }

    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(authState) {
        if (authState is AuthState.Error) {
            Toast.makeText(context, (authState as AuthState.Error).message, Toast.LENGTH_LONG).show()
            viewModel.resetAuthState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                // Beautiful Glowing Shield Icon
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Shield Security Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(45.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "APEXBANK",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "A new tier of custom premium banking",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isLoginMode) "Customer Portal Secure Link" else "Register Apex Premium Account",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        if (!isLoginMode) {
                            OutlinedTextField(
                                value = fullNameInput,
                                onValueChange = { fullNameInput = it },
                                label = { Text("Full Name") },
                                leadingIcon = { Icon(Icons.Filled.Person, "Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_full_name"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Email Address") },
                                leadingIcon = { Icon(Icons.Filled.Mail, "Email") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_email"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("Phone Number") },
                                leadingIcon = { Icon(Icons.Filled.Phone, "Phone") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_phone"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Filled.Person, "User") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_username"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Security Password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, "Pass") },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = "Toggle text view"
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (isLoginMode) {
                                    viewModel.login(usernameInput, passwordInput)
                                } else {
                                    viewModel.register(
                                        usernameInput,
                                        fullNameInput,
                                        passwordInput,
                                        emailInput,
                                        phoneInput
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_submit"),
                            enabled = authState !is AuthState.Loading
                        ) {
                            if (authState is AuthState.Loading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = if (isLoginMode) "Authenticate Session" else "Generate Vault Keys",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isLoginMode) "New customer of Apex?" else "Already holding keys?",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    TextButton(
                        onClick = { isLoginMode = !isLoginMode },
                        modifier = Modifier.testTag("auth_toggle")
                    ) {
                        Text(
                            text = if (isLoginMode) "Register Vault" else "Log In",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ---------------------------------------------------------
// 2. DASHBOARD SCREEN
// ---------------------------------------------------------

@Composable
fun DashboardScreen(
    viewModel: BankingViewModel,
    onNavigateToTransfer: () -> Unit,
    onNavigateToReceive: () -> Unit,
    onNavigateToATM: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val otherUsers by viewModel.otherUsers.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var searchTransactionQuery by remember { mutableStateOf("") }
    var selectedTransferShortcutUser by remember { mutableStateOf<User?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item {
            // Dashboard Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Good day,",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = currentUser?.fullName ?: "Valued Customer",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                // Brand Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder(true)
                ) {
                    Text(
                        text = "APEX PRO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Obsidian Premium Credit Card Widget
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.586f)
                        .clickable {
                            currentUser?.accountNumber?.let { acct ->
                                clipboardManager.setText(AnnotatedString(acct))
                                Toast
                                    .makeText(
                                        context,
                                        "Account number copied to clipboard",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        }
                        .testTag("credit_card_card"),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF6750A4), // Deep Velvet Indigo/Purple
                                        Color(0xFF533F8A), // Saturated Purple
                                        Color(0xFF4F378B)  // Premium Shadow Dark Purple
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(1000f, 1000f)
                                )
                            )
                    ) {
                        // Card Aesthetic Pattern Lines drawing on Canvas
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color(0x0CD0BCFF),
                                radius = size.minDimension * 0.85f,
                                center = Offset(size.width * 0.9f, size.height * 0.2f)
                            )
                            drawCircle(
                                color = Color(0x14D0BCFF),
                                radius = size.minDimension * 0.5f,
                                center = Offset(size.width * 0.1f, size.height * 0.9f)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountBalanceWallet,
                                        contentDescription = "NFC Bank Emblem",
                                        tint = DarkSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Apex Minimalist Vault",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.QrCode,
                                    contentDescription = "NFC Signal",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "AVAILABLE BALANCE",
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "$%,.2f".format(currentUser?.balance ?: 0.0),
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 32.sp
                                    ),
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color.White,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "CARD MEMBER",
                                        fontSize = 8.sp,
                                        letterSpacing = 1.sp,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = currentUser?.fullName?.uppercase() ?: "VALUED MEMBER",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "ACCOUNT NUMBER",
                                        fontSize = 8.sp,
                                        letterSpacing = 1.sp,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = currentUser?.accountNumber ?: "XXXX-XXXX",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Functional Option Quick Grid
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Filled.Send,
                    label = "Transfer",
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    tintColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onNavigateToTransfer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_transfer")
                )
                QuickActionButton(
                    icon = Icons.Filled.QrCode,
                    label = "Receive",
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    tintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onNavigateToReceive,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_receive")
                )
                QuickActionButton(
                    icon = Icons.Filled.SystemUpdateAlt,
                    label = "ATM Banking",
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    tintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onNavigateToATM,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_atm")
                )
            }
        }

        // 1-Tap Quick Transfer seed picker list
        item {
            if (otherUsers.isNotEmpty()) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    SectionHeader(title = "Rapid Pay Shortcut")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(otherUsers) { otherUser ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        selectedTransferShortcutUser = otherUser
                                    }
                                    .width(75.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = otherUser.fullName.firstOrNull()?.uppercase() ?: "U",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = otherUser.fullName.split(" ").firstOrNull() ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Search & Filter Transactions History Bar
        item {
            SectionHeader(title = "Recent Transactions Ledger")
            OutlinedTextField(
                value = searchTransactionQuery,
                onValueChange = { searchTransactionQuery = it },
                placeholder = { Text("Search description, recipient or status...") },
                leadingIcon = { Icon(Icons.Filled.Search, "Query") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("transaction_search_input"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Transaction rows
        val filteredTransactions = transactions.filter {
            it.description.contains(searchTransactionQuery, ignoreCase = true) ||
            it.recipientOrSender.contains(searchTransactionQuery, ignoreCase = true) ||
            it.type.contains(searchTransactionQuery, ignoreCase = true)
        }

        if (filteredTransactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No recorded logs match this criteria",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(filteredTransactions) { trans ->
                TransactionListItem(trans)
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Direct Quick Pay dialog handler
    selectedTransferShortcutUser?.let { recipient ->
        QuickTransferDialog(
            recipient = recipient,
            onDismiss = { selectedTransferShortcutUser = null },
            onConfirmTransfer = { valAmount, description ->
                viewModel.sendMoney(recipient.username, valAmount, description)
                selectedTransferShortcutUser = null
                Toast.makeText(context, "Transfer parameters submitted to queue!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: Color,
    tintColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(95.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.2f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tintColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TransactionListItem(trans: Transaction) {
    val isIncoming = trans.type == "DEPOSIT" || trans.type == "RECEIVE"
    val accentColor = if (isIncoming) Color(0xFF2E7D32) else Color(0xFFB3261E)
    val indicatorIcon = if (isIncoming) Icons.Filled.Add else Icons.Filled.Remove
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable {
                val flowText = if (isIncoming) "Incoming via ${trans.type}: ${trans.recipientOrSender} amount $%,.2f memo: ${trans.description}".format(trans.amount)
                               else "Outgoing via ${trans.type} to ${trans.recipientOrSender} amount $%,.2f memo: ${trans.description}".format(trans.amount)
                clipboardManager.setText(AnnotatedString(flowText))
                Toast.makeText(context, "Copied transaction ledger detail!", Toast.LENGTH_SHORT).show()
            }
            .testTag("transaction_ledger_item"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon backdrop highlighting credit vs debit transaction mapping
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.12f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = indicatorIcon,
                    contentDescription = trans.type,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isIncoming) "From: ${trans.recipientOrSender}" else "To: ${trans.recipientOrSender}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = trans.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = formatTimestamp(trans.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isIncoming) "+" else "-") + "$%,.2f".format(trans.amount),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = accentColor
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = trans.type,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTransferDialog(
    recipient: User,
    onDismiss: () -> Unit,
    onConfirmTransfer: (String, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apex Rapid Pay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Close")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = recipient.fullName.firstOrNull()?.toString()?.uppercase() ?: "R",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(recipient.fullName, fontWeight = FontWeight.Bold)
                        Text(recipient.accountNumber, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Transfer Amount ($)") },
                    leadingIcon = { Icon(Icons.Filled.AttachMoney, "Value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_amount_input")
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Short Description") },
                    placeholder = { Text("E.g., Dinner, rent bill") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        onConfirmTransfer(amountText, noteText)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_submit_button")
                ) {
                    Text("Confirm Shield Transaction", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 3. SEND MONEY SCREEN
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMoneyScreen(
    viewModel: BankingViewModel,
    onBack: () -> Unit
) {
    var recipientAccountInput by remember { mutableStateOf("") }
    var transferAmountInput by remember { mutableStateOf("") }
    var transferMemoInput by remember { mutableStateOf("") }

    val otherUsers by viewModel.otherUsers.collectAsState()
    val operationResult by viewModel.operationResult.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(operationResult) {
        if (operationResult is OperationResult.Success) {
            Toast.makeText(context, (operationResult as OperationResult.Success).message, Toast.LENGTH_LONG).show()
            viewModel.clearOperationResult()
            onBack()
        } else if (operationResult is OperationResult.Error) {
            Toast.makeText(context, (operationResult as OperationResult.Error).message, Toast.LENGTH_LONG).show()
            viewModel.clearOperationResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Interbank Transfer") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("send_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Transfer assets instantaneously to any validated Apex Account or Username keys instantly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Transfer Ledger Parameters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = recipientAccountInput,
                            onValueChange = { recipientAccountInput = it },
                            label = { Text("Recipient username or APX key") },
                            placeholder = { Text("E.g., sarah or APX-1234-5678") },
                            leadingIcon = { Icon(Icons.Filled.Person, "Recipient") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_recipient_username"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = transferAmountInput,
                            onValueChange = { transferAmountInput = it },
                            label = { Text("Transfer Amount ($)") },
                            leadingIcon = { Icon(Icons.Filled.AttachMoney, "Currency") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_transfer_amount"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = transferMemoInput,
                            onValueChange = { transferMemoInput = it },
                            label = { Text("Payment Description Memo") },
                            placeholder = { Text("Reference text (Optional)") },
                            leadingIcon = { Icon(Icons.Filled.Edit, "Memo") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_transfer_memo"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                viewModel.sendMoney(
                                    recipientAccountInput,
                                    transferAmountInput,
                                    transferMemoInput
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("send_submit_button"),
                            enabled = operationResult !is OperationResult.Loading
                        ) {
                            if (operationResult is OperationResult.Loading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    "Disburse Vault Assets",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (otherUsers.isNotEmpty()) {
                item {
                    Text(
                        text = "Or choose registered interbank buddies:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                items(otherUsers) { user ->
                    val clipboardManager = LocalClipboardManager.current
                    val context = LocalContext.current
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                recipientAccountInput = user.username
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    user.fullName.firstOrNull()?.toString()?.uppercase() ?: "B",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(user.accountNumber, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(user.accountNumber))
                                    Toast.makeText(context, "Copied Buddy's APX Key: ${user.accountNumber}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy APX Key",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 4. RECEIVE MONEY SCREEN
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveMoneyScreen(
    viewModel: BankingViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Asset Billing QR") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("receive_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Show this secure payment code. Let others scan your Apex Key or transfer to your identity automatically.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            // High Fidelity Simulated QR Code Widget
            Card(
                modifier = Modifier
                    .size(270.dp)
                    .padding(12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Let's draw a professional high-contrast QR style matrix using Custom Canvas lines!
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 5f
                        val gap = 12f
                        
                        // Corner Anchors
                        drawRect(Color.Black, Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(60f, 60f), style = Stroke(strokeWidth))
                        drawRect(Color.Black, Offset(16f, 16f), size = androidx.compose.ui.geometry.Size(28f, 28f))
                        
                        drawRect(Color.Black, Offset(size.width - 60f, 0f), size = androidx.compose.ui.geometry.Size(60f, 60f), style = Stroke(strokeWidth))
                        drawRect(Color.Black, Offset(size.width - 44f, 16f), size = androidx.compose.ui.geometry.Size(28f, 28f))

                        drawRect(Color.Black, Offset(0f, size.height - 60f), size = androidx.compose.ui.geometry.Size(60f, 60f), style = Stroke(strokeWidth))
                        drawRect(Color.Black, Offset(16f, size.height - 44f), size = androidx.compose.ui.geometry.Size(28f, 28f))

                        // Random bits lines
                        drawLine(Color.Black, Offset(size.width * 0.4f, size.height * 0.3f), Offset(size.width * 0.6f, size.height * 0.3f), strokeWidth = 8f)
                        drawLine(Color.Black, Offset(size.width * 0.5f, size.height * 0.1f), Offset(size.width * 0.5f, size.height * 0.4f), strokeWidth = 8f)

                        drawLine(Color.Black, Offset(size.width * 0.2f, size.height * 0.6f), Offset(size.width * 0.6f, size.height * 0.6f), strokeWidth = 10f)
                        drawLine(Color.Black, Offset(size.width * 0.3f, size.height * 0.5f), Offset(size.width * 0.3f, size.height * 0.8f), strokeWidth = 8f)
                        
                        drawLine(Color.Black, Offset(size.width * 0.7f, size.height * 0.6f), Offset(size.width * 0.7f, size.height * 0.9f), strokeWidth = 12f)
                        drawLine(Color.Black, Offset(size.width * 0.6f, size.height * 0.8f), Offset(size.width * 0.9f, size.height * 0.8f), strokeWidth = 8f)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = currentUser?.accountNumber ?: "NO ACCOUNT LINKED",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            OutlinedButton(
                onClick = {
                    currentUser?.accountNumber?.let { acct ->
                        clipboardManager.setText(AnnotatedString(acct))
                        Toast.makeText(context, "Copied Account: $acct", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("receive_copy_clipboard"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, "Copy")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Apex Account Token")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // User Info details
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Full Profile Key: ${currentUser?.fullName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Vault Code Username: @${currentUser?.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 5. DEPOSIT & WITHDRAW SCREEN
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositWithdrawScreen(
    viewModel: BankingViewModel,
    onBack: () -> Unit
) {
    var isDepositMode by remember { mutableStateOf(true) }
    var operationAmountInput by remember { mutableStateOf("") }
    
    val operationResult by viewModel.operationResult.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(operationResult) {
        if (operationResult is OperationResult.Success) {
            Toast.makeText(context, (operationResult as OperationResult.Success).message, Toast.LENGTH_LONG).show()
            viewModel.clearOperationResult()
            onBack()
        } else if (operationResult is OperationResult.Error) {
            Toast.makeText(context, (operationResult as OperationResult.Error).message, Toast.LENGTH_LONG).show()
            viewModel.clearOperationResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apex ATM Terminal") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("atm_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tab Selector Row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    TabButton(
                        label = "Deposit Cash",
                        isActive = isDepositMode,
                        onClick = { isDepositMode = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("atm_tab_deposit")
                    )
                    TabButton(
                        label = "Withdraw Cash",
                        isActive = !isDepositMode,
                        onClick = { isDepositMode = false },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("atm_tab_withdraw")
                    )
                }
            }

            Text(
                text = if (isDepositMode) "Top up your available cash balance instantly from external cards or registered POS terminals instantly."
                else "Disburse physical cash currency securely from any registered partner ATM kiosk.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (isDepositMode) "Add Assets To Vault" else "Request Vault Cash Out",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = operationAmountInput,
                        onValueChange = { operationAmountInput = it },
                        label = { Text("Transaction Cash Amount ($)") },
                        leadingIcon = { Icon(Icons.Filled.AttachMoney, "ATM Cash") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("atm_amount_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (isDepositMode) {
                                viewModel.deposit(operationAmountInput)
                            } else {
                                viewModel.withdraw(operationAmountInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("atm_submit_button"),
                        enabled = operationResult !is OperationResult.Loading
                    ) {
                        if (operationResult is OperationResult.Loading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                if (isDepositMode) "Execute Secure Cash Deposit" else "Dispense ATM Cash Ledger",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------
// 6. ALERTS FEED SCREEN (NOTIFICATIONS)
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: BankingViewModel) {
    val notifications by viewModel.notifications.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Alerts Engine") },
                actions = {
                    if (notifications.any { !it.isRead }) {
                        TextButton(
                            onClick = {
                                viewModel.markAllNotificationsAsRead()
                                Toast.makeText(context, "All notifications read", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("alerts_read_all")
                        ) {
                            Text("Mark All Read")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsNone,
                        contentDescription = "Empty Alerts",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Your security inbox is empty",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Incoming logs of transfers, auth access and ATM disbursements appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(notifications) { notif ->
                    NotificationAlertItem(
                        notif = notif,
                        onMarkAsRead = {
                            viewModel.markNotificationAsRead(notif.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationAlertItem(
    notif: Notification,
    onMarkAsRead: () -> Unit
) {
    val cardBackground = if (notif.isRead) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
    
    val badgeMarker = if (!notif.isRead) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { if (!notif.isRead) onMarkAsRead() }
            .testTag("alert_ledger_item"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Brand logo highlighting unread status
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(badgeMarker, shape = CircleShape)
                    .align(Alignment.CenterVertically)
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notif.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!notif.isRead) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "NEW",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notif.message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatTimestamp(notif.timestamp),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ---------------------------------------------------------
// 7. PROFILE & SETTINGS SCREEN
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: BankingViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile Cryptographic Key") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Avatars Header block
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser?.fullName?.firstOrNull()?.toString()?.uppercase() ?: "U",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        currentUser?.fullName ?: "Apex Customer",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    val clipboardManager = LocalClipboardManager.current
                    val currentUsername = currentUser?.username ?: ""
                    Row(
                        modifier = Modifier
                            .clickable {
                                if (currentUsername.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(currentUsername))
                                    Toast.makeText(context, "Copied Username: @$currentUsername", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 12.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "@$currentUsername",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Username",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // High Fidelity Real-time Ledger Statistics Card
            item {
                val totalIn = transactions.filter { it.type == "DEPOSIT" || it.type == "RECEIVE" }.sumOf { it.amount }
                val totalOut = transactions.filter { it.type == "WITHDRAW" || it.type == "SEND" }.sumOf { it.amount }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Verified Asset Metrics (30-Day Flow)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CREDITED FLOW", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                Text("$%,.2f".format(totalIn), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("DEBITED FLOW", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                Text("$%,.2f".format(totalOut), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFB3261E))
                            }
                        }
                    }
                }
            }

            // Dark Mode and Settings List Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        // Secure Dark/Light Mode switch row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleTheme() }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                    contentDescription = "Theme",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Atmospheric Dark Scheme", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Optimize dark visual balance",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { viewModel.toggleTheme() },
                                modifier = Modifier.testTag("profile_dark_mode_switch")
                            )
                        }
                        
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Info 2: Email Configuration reference
                        ProfileInfoRow(
                            icon = Icons.Filled.Mail,
                            title = "Linked Secured Email",
                            value = currentUser?.email ?: "No email set"
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Info 3: Phone Number
                        ProfileInfoRow(
                            icon = Icons.Filled.Phone,
                            title = "Registered SMS Token",
                            value = currentUser?.phoneNumber ?: "No phone linked"
                        )
                    }
                }
            }

            // Action Log out buttons
            item {
                Button(
                    onClick = {
                        viewModel.logout()
                        Toast.makeText(context, "Secure session closed", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_logout_button")
                ) {
                    Icon(Icons.Filled.ExitToApp, "Exit")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Close Security Vault Portal", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (value.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(value))
                    Toast.makeText(context, "Copied $title!", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = "Copy",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
    }
}
