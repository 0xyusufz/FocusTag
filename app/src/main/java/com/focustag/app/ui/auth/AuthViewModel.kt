package com.focustag.app.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.repository.AuthRepository
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "AuthDebug"

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginMode: Boolean = true
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    val sessionStatus: StateFlow<SessionStatus> = repository.sessionStatus

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    private val _authSuccess = MutableSharedFlow<Unit>()
    val authSuccess = _authSuccess.asSharedFlow()

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(password: String) {
        _uiState.update { it.copy(confirmPassword = password, errorMessage = null) }
    }

    fun toggleAuthMode() {
        _uiState.update { 
            it.copy(
                isLoginMode = !it.isLoginMode,
                email = "",
                password = "",
                confirmPassword = "",
                errorMessage = null,
                isLoading = false
            ) 
        }
    }

    fun onLoginClicked() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.signIn(state.email, state.password)
            handleResult(result)
        }
    }

    fun onSignupClicked() {
        val state = _uiState.value
        
        if (state.email.isBlank() || state.password.isBlank() || state.confirmPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "All fields are required") }
            return
        }

        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.signUp(state.email, state.password)
            handleResult(result)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
        }
    }

    private suspend fun handleResult(result: Result<Unit>) {
        result.onSuccess {
            _uiState.update { it.copy(isLoading = false) }
            _authSuccess.emit(Unit)
        }.onFailure { error ->
            val errorCode = (error as? AuthRestException)?.errorCode?.name
            Log.e(TAG, "Auth failure: type=${error::class.java.simpleName}, code=$errorCode, message=${error.message}")

            val message = when (error) {
                is AuthRestException -> {
                    when (error.errorCode) {
                        AuthErrorCode.InvalidCredentials -> "Invalid email or password."
                        AuthErrorCode.UserNotFound -> "Invalid email or password."
                        AuthErrorCode.EmailNotConfirmed -> "Please confirm your email first."
                        AuthErrorCode.OverEmailSendRateLimit -> "Email sending limit reached. Please try again later."
                        AuthErrorCode.EmailExists -> "This email is already registered."
                        AuthErrorCode.WeakPassword -> "Password is too weak."
                        AuthErrorCode.EmailAddressInvalid -> "Invalid email format."
                        AuthErrorCode.Conflict -> "This email is already registered."
                        else -> "An unexpected error occurred."
                    }
                }
                is IOException -> "Network error. Please check your connection."
                else -> "An unexpected error occurred."
            }
            _uiState.update { it.copy(isLoading = false, errorMessage = message) }
        }
    }
}
