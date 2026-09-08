package com.focustag.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.data.repository.SupabaseHistoryRepository
import com.focustag.app.data.repository.NfcRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistorySessionItem(
    val record: FocusSessionRecord,
    val durationMillis: Long?,
    val interceptionCount: Int,
    val tagDisplayName: String? = null
)

data class HistoryUiState(
    val sessions: List<HistorySessionItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val tagMap: Map<String, String> = emptyMap()
)

class HistoryViewModel(
    private val userId: String,
    private val localRepo: SessionHistoryRepository,
    private val remoteRepo: SupabaseHistoryRepository,
    private val nfcRepository: NfcRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        localRepo.sessions,
        localRepo.events,
        _isLoading,
        _errorMessage
    ) { sessions, events, loading, error ->
        val tagMap = nfcRepository.getCache()?.tagDisplayNames ?: emptyMap()
        val items = sessions.map { session ->
            val sessionEvents = events.filter { it.sessionId == session.sessionId }
            val duration = if (session.endAt != null) {
                session.endAt - session.startAt
            } else if (session.status == com.focustag.app.data.model.SessionStatus.IN_PROGRESS) {
                // For IN_PROGRESS, duration is since start
                System.currentTimeMillis() - session.startAt
            } else {
                null
            }
            val displayName = when (session.tagId) {
                "simulated_tag_01" -> "Simulated Tag"
                null -> null
                else -> tagMap[session.tagId] ?: session.tagId
            }
            HistorySessionItem(session, duration, sessionEvents.size, tagDisplayName = displayName)
        }
        HistoryUiState(items, loading, error, tagMap = tagMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val sessionsResult = remoteRepo.fetchSessions(userId)
                if (sessionsResult.isSuccess) {
                    localRepo.mergeCloudSessions(sessionsResult.getOrThrow())
                } else {
                    _errorMessage.value = "Failed to fetch cloud sessions: ${sessionsResult.exceptionOrNull()?.message}"
                }

                val eventsResult = remoteRepo.fetchEvents(userId)
                if (eventsResult.isSuccess) {
                    localRepo.mergeCloudEvents(eventsResult.getOrThrow())
                } else {
                    _errorMessage.value = "Failed to fetch cloud events: ${eventsResult.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Unexpected error during refresh: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
