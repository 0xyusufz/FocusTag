package com.focustag.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.data.repository.NfcRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val localRepo: SessionHistoryRepository,
    private val nfcRepository: NfcRepository
) : ViewModel() {

    // Periodic ticker to update "Active Session" duration every second
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    val uiState: StateFlow<DashboardState> = combine(
        localRepo.sessions,
        localRepo.events,
        ticker
    ) { sessions, events, now ->
        val tagMap = nfcRepository.getCache()?.tagDisplayNames ?: emptyMap()
        DashboardStatsCalculator.calculate(sessions, events, now, tagMap = tagMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState(isLoading = true))
}
