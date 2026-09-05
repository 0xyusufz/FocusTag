package com.focustag.app.ui.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.model.RosterStudent
import com.focustag.app.data.model.TeacherClass
import com.focustag.app.data.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeacherUiState(
    val classes: List<TeacherClass> = emptyList(),
    val selectedClass: TeacherClass? = null,
    val roster: List<RosterStudent> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class TeacherViewModel(private val repository: TeacherRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherUiState())
    val uiState = _uiState.asStateFlow()

    fun loadClasses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getMyClasses()
                .onSuccess { classes ->
                    _uiState.update { it.copy(isLoading = false, classes = classes) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load classes") }
                }
        }
    }

    fun selectClass(teacherClass: TeacherClass) {
        _uiState.update { it.copy(selectedClass = teacherClass, roster = emptyList()) }
        loadRoster(teacherClass.id)
    }

    fun loadRoster(classId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getClassRoster(classId)
                .onSuccess { roster ->
                    _uiState.update { it.copy(isLoading = false, roster = roster) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load roster") }
                }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedClass = null, roster = emptyList()) }
    }
}
