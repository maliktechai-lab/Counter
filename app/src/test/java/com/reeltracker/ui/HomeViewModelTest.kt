package com.reeltracker.ui

import com.reeltracker.domain.model.DailyCount
import com.reeltracker.domain.model.Platform
import com.reeltracker.domain.usecase.GetStatsUseCase
import com.reeltracker.ui.screens.home.HomeViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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
    fun `todayCounts emits empty map when no events`() = runTest(testDispatcher) {
        every { getStatsUseCase.todayCountsFlow(any()) } returns flowOf(emptyList())

        val vm = HomeViewModel(getStatsUseCase)
        advanceUntilIdle()

        assertEquals(emptyMap<Platform, Int>(), vm.todayCounts.value)
    }

    @Test
    fun `todayCounts maps DailyCount list to platform keyed map`() = runTest(testDispatcher) {
        val counts = listOf(
            DailyCount("2024-01-17", Platform.INSTAGRAM, 7),
            DailyCount("2024-01-17", Platform.YOUTUBE,   3),
        )
        every { getStatsUseCase.todayCountsFlow(any()) } returns flowOf(counts)

        val vm = HomeViewModel(getStatsUseCase)
        advanceUntilIdle()

        val result = vm.todayCounts.value
        assertEquals(7, result[Platform.INSTAGRAM])
        assertEquals(3, result[Platform.YOUTUBE])
    }

    @Test
    fun `todayCounts with only Instagram count has no YouTube entry`() = runTest(testDispatcher) {
        val counts = listOf(
            DailyCount("2024-01-17", Platform.INSTAGRAM, 5),
        )
        every { getStatsUseCase.todayCountsFlow(any()) } returns flowOf(counts)

        val vm = HomeViewModel(getStatsUseCase)
        advanceUntilIdle()

        val result = vm.todayCounts.value
        assertEquals(5, result[Platform.INSTAGRAM])
        assertEquals(null, result[Platform.YOUTUBE]) // absent = 0 on screen via ?: 0
    }
}
