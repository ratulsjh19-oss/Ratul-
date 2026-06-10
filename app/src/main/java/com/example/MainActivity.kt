package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.BankingAppShell
import com.example.ui.theme.BankingTheme
import com.example.ui.viewmodel.BankingViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: BankingViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val isDarkMode by viewModel.isDarkMode.collectAsState()
      BankingTheme(darkTheme = isDarkMode) {
        BankingAppShell(viewModel = viewModel)
      }
    }
  }
}
