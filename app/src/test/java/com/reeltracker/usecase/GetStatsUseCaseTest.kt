package com.reeltracker.usecase

import com.reeltracker.data.repository.ViewEventRepository
import com.reeltracker.domain.model.DailyCount
import com.reeltracker.domain.model.DateUtils
import com.reeltracker.domain.model.Platform
import com.reeltracker.domain.usecase.GetStatsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GetStatsUseCaseTest {

    private val repository: ViewEventRepository = mockk()
    private val useCase = GetStatsUseCase(repository)

    // ── todayCountsFlow ──────────────────────────────────────────────────────

    @Test
    fun `todayCountsFlow returns flow from repository`() = runTest {
        val expected = listOf(
            DailyCount("2024-01-17", Platform.INSTAGRAM, 5),
            DailyCount("2024-01-17", Platform.YOUTUBE, 2),
        )
        coEvery { repository.dailyCountsFlow(any()) } returns flowOf(expected)

        val result = useCase.todayCountsFlow().first()

        assertEquals(expected, result)
    }

    @Test
    fun `todayCountsFlow with zero events returns empty list`() = runTest {
        coEvery { repository.dailyCountsFlow(any()) } returns flowOf(emptyList())

        val result = useCase.todayCountsFlow().first()

        assertTrue(result.isEmpty())
    }

    // ── weekCounts ──────────────────────────────────────────────────────────

    @Test
    fun `weekCounts queries correct ISO week range`() = runTest {
        val expected = listOf(
            DailyCount("2024-01-15", Platform.INSTAGRAM, 3),
            DailyCount("2024-01-16", Platform.INSTAGRAM, 7),
        )
        coEvery { repository.countsInRange("2024-01-15", "2024-01-21") } returns expected

        val result = useCase.weekCounts("2024-01-17") // Wednesday in that week

        assertEquals(expected, result)
    }

    @Test
    fun `weekCounts returns empty list when no events`() = runTest {
        coEvery { repository.countsInRange(any(), any()) } returns emptyList()

        val result = useCase.weekCounts("2024-01-17")

        assertTrue(result.isEmpty())
    }

    // ── monthCounts ──────────────────────────────────────────────────────────

    @Test
    fun `monthCounts queries correct month range`() = runTest {
        val expected = listOf(
            DailyCount("2024-02-14", Platform.YOUTUBE, 10),
        )
        coEvery { repository.countsInRange("2024-02-01", "2024-02-29") } returns expected

        val result = useCase.monthCounts("2024-02-14")

        assertEquals(expected, result)
    }

    // ── lastEventTimestamp ──────────────────────────────────────────────────

    @Test
    fun `lastEventTimestamp returns timestamp from repository`() = runTest {
        val ts = 1_700_000_000_000L
        coEvery { repository.lastEventTimestamp(Platform.INSTAGRAM) } returns ts

        val result = useCase.lastEventTimestamp(Platform.INSTAGRAM)

        assertEquals(ts, result)
    }

    @Test
    fun `lastEventTimestamp returns null when no events exist`() = runTest {
        coEvery { repository.lastEventTimestamp(Platform.YOUTUBE) } returns null

        val result = useCase.lastEventTimestamp(Platform.YOUTUBE)

        assertNull(result)
    }
}
