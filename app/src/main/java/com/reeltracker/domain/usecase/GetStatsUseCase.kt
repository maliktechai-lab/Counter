package com.reeltracker.domain.usecase

import com.reeltracker.data.repository.ViewEventRepository
import com.reeltracker.domain.model.DailyCount
import com.reeltracker.domain.model.DateUtils
import com.reeltracker.domain.model.Platform
import kotlinx.coroutines.flow.Flow

/**
 * Retrieves confirmed video counts aggregated by date and platform.
 *
 * All counts are confirmed counts only. Unconfirmed transition candidates are
 * tracked separately via the diagnostic screen's unconfirmed-discard counter.
 */
class GetStatsUseCase(private val repository: ViewEventRepository) {

    /** Today's confirmed count per platform, as a live [Flow]. */
    fun todayCountsFlow(nowMs: Long = System.currentTimeMillis()): Flow<List<DailyCount>> =
        repository.dailyCountsFlow(DateUtils.today(nowMs))

    /** Confirmed counts for each day of the ISO week that contains [referenceDate]. */
    suspend fun weekCounts(referenceDate: String = DateUtils.today()): List<DailyCount> {
        val (start, end) = DateUtils.isoWeekRange(referenceDate)
        return repository.countsInRange(start, end)
    }

    /** Confirmed counts for each day of the calendar month that contains [referenceDate]. */
    suspend fun monthCounts(referenceDate: String = DateUtils.today()): List<DailyCount> {
        val (start, end) = DateUtils.monthRange(referenceDate)
        return repository.countsInRange(start, end)
    }

    /** Timestamp of the most recent confirmed event for [platform], or null. */
    suspend fun lastEventTimestamp(platform: Platform): Long? =
        repository.lastEventTimestamp(platform)
}
