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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focustag.app.data.model.FocusSessionState
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.repository.SupabaseAuthRepository
import com.focustag.app.data.repository.SupabaseProfileRepository
import com.focustag.app.data.repository.AppInventoryRepository
import com.focustag.app.data.repository.AppPolicyRepository
import com.focustag.app.data.repository.EnforcementRepository
import com.focustag.app.data.repository.FocusRepository
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.data.supabase.SupabaseModule
import com.focustag.app.data.worker.SyncScheduler
import com.focustag.app.domain.AccessibilityEnforcementStrategy
import com.focustag.app.domain.EnforcementCoordinator
import com.focustag.app.domain.EnforcementCoordinatorHub
import com.focustag.app.ui.apps.AppSelectionScreen
import com.focustag.app.ui.apps.AppSelectionViewModel
import com.focustag.app.ui.auth.AuthViewModel
import com.focustag.app.ui.auth.HomeScreen
import com.focustag.app.ui.auth.LoginScreen
import com.focustag.app.ui.auth.SignupScreen
import com.focustag.app.ui.focus.FocusViewModel
import com.focustag.app.ui.profile.ProfileScreen
import com.focustag.app.ui.profile.ProfileViewModel
import com.focustag.app.ui.theme.FocusTagTheme
import com.focustag.app.util.NfcController
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var nfcController: NfcController
    private var activeFocusViewModel: FocusViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcController = NfcController(this)
        SessionHistoryRepository.initCollector(applicationContext)
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

                    val appSelectionViewModel: AppSelectionViewModel? = if (sessionStatus is SessionStatus.Authenticated) {
                        val userId = (sessionStatus as SessionStatus.Authenticated).session.user?.id ?: ""
                        viewModel(
                            key = userId, // Scoped to user
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return AppSelectionViewModel(
                                        AppInventoryRepository(this@MainActivity),
                                        AppPolicyRepository(this@MainActivity, userId)
                                    ) as T
                                }
                            }
                        )
                    } else null

                    val focusViewModel: FocusViewModel? = if (sessionStatus is SessionStatus.Authenticated) {
                        val userId = (sessionStatus as SessionStatus.Authenticated).session.user?.id ?: ""
                        viewModel(
                            key = "focus_$userId", // Scoped to user
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    val coordinator = EnforcementCoordinatorHub.getCoordinator(this@MainActivity, userId)
                                    return FocusViewModel(
                                        context = this@MainActivity.applicationContext,
                                        focusRepository = FocusRepository(this@MainActivity, userId),
                                        enforcementCoordinator = coordinator
                                    ) as T
                                }
                            }
                        )
                    } else null
                    
                    activeFocusViewModel = focusViewModel

                    val focusSessionState by focusViewModel?.focusState?.collectAsState(initial = FocusSessionState()) ?: remember { mutableStateOf(FocusSessionState()) }
                    val isFocusActive = focusSessionState.focusState == FocusState.FOCUS_ACTIVE

                    LaunchedEffect(sessionStatus) {
                        Log.d("MainActivity", "Auth status changed: $sessionStatus")
                        if (sessionStatus is SessionStatus.Authenticated) {
                            val session = (sessionStatus as SessionStatus.Authenticated).session
                            val userId = session.user?.id ?: ""
                            val email = session.user?.email ?: ""
                            Log.d("MainActivity", "Authenticated: userId=$userId, email=$email")
                            profileViewModel.loadProfile(userId, email)
                            focusViewModel?.refreshEnforcementStatus()
                            focusViewModel?.refreshAccessibilityCapability()
                            focusViewModel?.refreshNfcCapability()
                            SyncScheduler.scheduleSync(this@MainActivity, userId)
                        } else {
                            currentScreen = "home"
                        }
                    }

                    DisposableEffect(Unit) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                focusViewModel?.refreshAccessibilityCapability()
                                focusViewModel?.refreshNfcCapability()
                                val userId = (sessionStatus as? SessionStatus.Authenticated)?.session?.user?.id
                                userId?.let { SyncScheduler.scheduleSync(this@MainActivity, it) }
                            }
                        }
                        lifecycle.addObserver(observer)
                        onDispose {
                            lifecycle.removeObserver(observer)
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
                                        isFocusActive = isFocusActive,
                                        onBack = { currentScreen = "home" }
                                    )
                                    "apps" -> {
                                        appSelectionViewModel?.let {
                                            AppSelectionScreen(
                                                viewModel = it,
                                                isFocusActive = isFocusActive,
                                                onBack = { currentScreen = "home" }
                                            )
                                        }
                                    }
                                    else -> {
                                        focusViewModel?.let { focusVM ->
                                            HomeScreen(
                                                authViewModel = authViewModel,
                                                focusViewModel = focusVM,
                                                onNavigateToProfile = { currentScreen = "profile" },
                                                onNavigateToApps = { currentScreen = "apps" }
                                            )
                                        }
                                    }
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

    override fun onResume() {
        super.onResume()
        nfcController.enableReaderMode { tagId ->
            lifecycleScope.launch(Dispatchers.Main) {
                activeFocusViewModel?.onTagEvent(tagId)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcController.disableReaderMode()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        SupabaseModule.client.handleDeeplinks(intent)
    }
}
