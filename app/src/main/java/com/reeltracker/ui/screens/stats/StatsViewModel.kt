package com.reeltracker.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reeltracker.di.ServiceLocator
import com.reeltracker.domain.model.DailyCount
import com.reeltracker.domain.model.DateUtils
import com.reeltracker.domain.usecase.GetStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which period the Stats screen is showing. */
enum class StatsPeriod { WEEK, MONTH }

/**
 * UI state for the Stats screen.
 *
 * @param period The active tab selection.
 * @param counts Confirmed daily counts for the selected period.
 * @param isLoading True while the initial query is running.
 */
data class StatsUiState(
    val period: StatsPeriod       = StatsPeriod.WEEK,
    val counts: List<DailyCount>  = emptyList(),
    val isLoading: Boolean        = true,
)

/**
 * ViewModel for the Stats screen.
 *
 * Exposes a [StateFlow<StatsUiState>] that is updated when the user switches
 * between WEEK and MONTH views.
 */
class StatsViewModel(
    private val getStatsUseCase: GetStatsUseCase = ServiceLocator.getStatsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadCounts(StatsPeriod.WEEK)
    }

    fun selectPeriod(period: StatsPeriod) {
        _uiState.value = _uiState.value.copy(period = period, isLoading = true)
        loadCounts(period)
    }

    private fun loadCounts(period: StatsPeriod) {
        viewModelScope.launch {
            val today  = DateUtils.today()
            val counts = when (period) {
                StatsPeriod.WEEK  -> getStatsUseCase.weekCounts(today)
                StatsPeriod.MONTH -> getStatsUseCase.monthCounts(today)
            }
            _uiState.value = StatsUiState(
                period    = period,
                counts    = counts,
                isLoading = false,
            )
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StatsViewModel() as T
        }
    }
}
