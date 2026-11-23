package com.reeltracker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reeltracker.di.ServiceLocator
import com.reeltracker.domain.model.DailyCount
import com.reeltracker.domain.model.Platform
import com.reeltracker.domain.usecase.GetStatsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Home screen.
 *
 * Exposes:
 * - [todayCounts] — a [StateFlow] of today's confirmed count per platform,
 *   derived from [GetStatsUseCase.todayCountsFlow]. Reactive — updates whenever
 *   a new event is inserted.
 */
class HomeViewModel(
    private val getStatsUseCase: GetStatsUseCase = ServiceLocator.getStatsUseCase,
) : ViewModel() {

    /** Today's confirmed view counts, keyed by [Platform]. Empty map = no views yet. */
    val todayCounts: StateFlow<Map<Platform, Int>> = getStatsUseCase
        .todayCountsFlow()
        .map { counts -> counts.associate { it.platform to it.count } }
        .stateIn(
            scope             = viewModelScope,
            started           = SharingStarted.Eagerly,
            initialValue      = emptyMap(),
        )

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel() as T
        }
    }
}
