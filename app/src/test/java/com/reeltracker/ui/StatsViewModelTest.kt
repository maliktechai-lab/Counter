package com.reeltracker.ui

import com.reeltracker.domain.model.DailyCount
import com.reeltracker.domain.model.Platform
import com.reeltracker.domain.usecase.GetStatsUseCase
import com.reeltracker.ui.screens.stats.StatsPeriod
import com.reeltracker.ui.screens.stats.StatsViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val getStatsUseCase: GetStatsUseCase = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads weekly counts`() = runTest(testDispatcher) {
        val weeklyCounts = listOf(
            DailyCount("2024-01-15", Platform.INSTAGRAM, 3),
        )
        coEvery { getStatsUseCase.weekCounts(any()) }  returns weeklyCounts
        coEvery { getStatsUseCase.monthCounts(any()) } returns emptyList()

        val vm = StatsViewModel(getStatsUseCase)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(StatsPeriod.WEEK, state.period)
        assertFalse(state.isLoading)
        assertEquals(weeklyCounts, state.counts)
    }

    @Test
    fun `selectPeriod MONTH loads monthly counts`() = runTest(testDispatcher) {
        val weeklyCounts  = listOf(DailyCount("2024-01-15", Platform.INSTAGRAM, 2))
        val monthlyCounts = listOf(
            DailyCount("2024-01-01", Platform.YOUTUBE, 10),
            DailyCount("2024-01-15", Platform.INSTAGRAM, 2),
        )
        coEvery { getStatsUseCase.weekCounts(any()) }  returns weeklyCounts
        coEvery { getStatsUseCase.monthCounts(any()) } returns monthlyCounts

        val vm = StatsViewModel(getStatsUseCase)
        advanceUntilIdle() // let init coroutine complete

        vm.selectPeriod(StatsPeriod.MONTH)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(StatsPeriod.MONTH, state.period)
        assertFalse(state.isLoading)
        assertEquals(monthlyCounts, state.counts)
    }

    @Test
    fun `selectPeriod WEEK reloads weekly counts`() = runTest(testDispatcher) {
        val weeklyCounts = listOf(DailyCount("2024-01-17", Platform.YOUTUBE, 5))
        coEvery { getStatsUseCase.weekCounts(any()) }  returns weeklyCounts
        coEvery { getStatsUseCase.monthCounts(any()) } returns emptyList()

        val vm = StatsViewModel(getStatsUseCase)
        advanceUntilIdle()

        vm.selectPeriod(StatsPeriod.MONTH)
        advanceUntilIdle()
        vm.selectPeriod(StatsPeriod.WEEK)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(StatsPeriod.WEEK, state.period)
        assertEquals(weeklyCounts, state.counts)
    }

    @Test
    fun `empty counts result in empty list not loading`() = runTest(testDispatcher) {
        coEvery { getStatsUseCase.weekCounts(any()) }  returns emptyList()
        coEvery { getStatsUseCase.monthCounts(any()) } returns emptyList()

        val vm = StatsViewModel(getStatsUseCase)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(emptyList<DailyCount>(), state.counts)
    }
}
