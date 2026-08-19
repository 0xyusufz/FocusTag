package com.focustag.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focustag.app.data.repository.SupabaseAuthRepository
import com.focustag.app.data.repository.SupabaseProfileRepository
import com.focustag.app.data.supabase.SupabaseModule
import com.focustag.app.ui.auth.AuthViewModel
import com.focustag.app.ui.auth.HomeScreen
import com.focustag.app.ui.auth.LoginScreen
import com.focustag.app.ui.auth.SignupScreen
import com.focustag.app.ui.profile.ProfileScreen
import com.focustag.app.ui.profile.ProfileViewModel
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

                    val profileViewModel: ProfileViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ProfileViewModel(SupabaseProfileRepository()) as T
                            }
                        }
                    )
                    
                    val sessionStatus by authViewModel.sessionStatus.collectAsState()
                    val uiState by authViewModel.uiState.collectAsState()
                    var currentScreen by remember { mutableStateOf("home") }

                    LaunchedEffect(sessionStatus) {
                        Log.d("MainActivity", "Auth status changed: $sessionStatus")
                        if (sessionStatus is SessionStatus.Authenticated) {
                            val session = (sessionStatus as SessionStatus.Authenticated).session
                            val userId = session.user?.id ?: ""
                            val email = session.user?.email ?: ""
                            Log.d("MainActivity", "Authenticated: userId=$userId, email=$email")
                            profileViewModel.loadProfile(userId, email)
                        } else {
                            currentScreen = "home"
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (sessionStatus) {
                            is SessionStatus.Authenticated -> {
                                when (currentScreen) {
                                    "profile" -> ProfileScreen(
                                        viewModel = profileViewModel,
                                        onBack = { currentScreen = "home" }
                                    )
                                    else -> HomeScreen(
                                        viewModel = authViewModel,
                                        onNavigateToProfile = { currentScreen = "profile" }
                                    )
                                }
                            }
                            else -> {
                                if (uiState.isLoginMode) {
                                    LoginScreen(viewModel = authViewModel)
                                } else {
                                    SignupScreen(viewModel = authViewModel)
                                }
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
