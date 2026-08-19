package com.focustag.app.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ProfileDebug"

data class ProfileUiState(
    val userId: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "student",
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val errorMessage: String? = null
)

class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    fun loadProfile(userId: String, email: String) {
        viewModelScope.launch {
            Log.d(TAG, "Loading profile for userId: $userId")
            _uiState.update { it.copy(isLoading = true, errorMessage = null, userId = userId, email = email) }
            val result = repository.getProfile(userId)
            result.onSuccess { profile ->
                if (profile != null) {
                    Log.d(TAG, "Profile loaded successfully: $profile")
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            name = profile.name ?: "",
                            role = profile.role
                        ) 
                    }
                } else {
                    Log.w(TAG, "Profile row not found for userId: $userId")
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            errorMessage = "Profile not found. Please complete signup." 
                        ) 
                    }
                }
            }.onFailure { error ->
                val errorDetails = when (error) {
                    is io.github.jan.supabase.exceptions.RestException -> "code=${error.error}, description=${error.description}"
                    else -> "message=${error.message}"
                }
                Log.e(TAG, "Failed to load profile: type=${error::class.java.simpleName}, $errorDetails", error)
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = "Failed to load profile" 
                    ) 
                }
            }
        }
    }

    fun onNameChanged(newName: String) {
        _uiState.update { it.copy(name = newName, errorMessage = null) }
    }

    fun toggleEditing() {
        _uiState.update { it.copy(isEditing = !it.isEditing, errorMessage = null) }
    }

    fun saveProfile() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.updateName(state.userId, state.name)
            result.onSuccess {
                Log.d(TAG, "Profile updated successfully")
                _uiState.update { it.copy(isLoading = false, isEditing = false) }
            }.onFailure { error ->
                val errorDetails = when (error) {
                    is io.github.jan.supabase.exceptions.RestException -> "code=${error.error}, description=${error.description}"
                    else -> "message=${error.message}"
                }
                Log.e(TAG, "Failed to update profile: type=${error::class.java.simpleName}, $errorDetails", error)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to update profile") }
            }
        }
    }
}
