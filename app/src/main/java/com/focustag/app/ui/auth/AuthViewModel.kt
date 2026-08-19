package com.focustag.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.repository.AuthRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignupState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    val sessionStatus: StateFlow<SessionStatus> = repository.sessionStatus

    private val _uiState = MutableStateFlow(SignupState())
    val uiState = _uiState.asStateFlow()

    private val _signupSuccess = MutableSharedFlow<Unit>()
    val signupSuccess = _signupSuccess.asSharedFlow()

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(password: String) {
        _uiState.update { it.copy(confirmPassword = password, errorMessage = null) }
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
            
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                _signupSuccess.emit(Unit)
            }.onFailure { error ->
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = error.localizedMessage ?: "An error occurred during signup"
                    ) 
                }
            }
        }
    }
}
