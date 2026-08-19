package com.focustag.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focustag.app.data.repository.SupabaseAuthRepository
import com.focustag.app.data.supabase.SupabaseModule
import com.focustag.app.ui.auth.AuthViewModel
import com.focustag.app.ui.auth.SignupScreen
import com.focustag.app.ui.theme.FocusTagTheme
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseModule.client.handleDeeplinks(intent)
        enableEdgeToEdge()
        setContent {
            FocusTagTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val authViewModel: AuthViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return AuthViewModel(SupabaseAuthRepository()) as T
                            }
                        }
                    )
                    
                    val sessionStatus by authViewModel.sessionStatus.collectAsState()
                    
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (sessionStatus) {
                            is SessionStatus.Authenticated -> {
                                Text(text = "Email verified / Session active")
                            }
                            else -> {
                                SignupScreen(viewModel = authViewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        SupabaseModule.client.handleDeeplinks(intent)
    }
}
