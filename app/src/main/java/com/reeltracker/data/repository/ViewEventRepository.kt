package com.reeltracker.data.repository

import com.reeltracker.data.db.DailyCountRow
import com.reeltracker.data.db.ViewEventDao
import com.reeltracker.data.db.ViewEventEntity
import com.reeltracker.domain.model.DailyCount
import com.reeltracker.domain.model.DateUtils
import com.reeltracker.domain.model.Platform
import com.reeltracker.domain.model.TransitionType
import com.reeltracker.domain.model.ViewEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ViewEventRepository(private val dao: ViewEventDao) {

    suspend fun insert(event: ViewEvent) {
        dao.insert(event.toEntity())
    }

    suspend fun dailyCounts(date: String): List<DailyCount> =
        dao.dailyCounts(date).map { it.toDomain() }

    suspend fun countsInRange(startDate: String, endDate: String): List<DailyCount> =
        dao.countsInRange(startDate, endDate).map { it.toDomain() }

    fun dailyCountsFlow(date: String): Flow<List<DailyCount>> =
        dao.dailyCountsFlow(date).map { rows -> rows.map { it.toDomain() } }

    suspend fun lastEventTimestamp(platform: Platform): Long? =
        dao.lastEventTimestamp(platform.name)

    // ── Mappers ──────────────────────────────────────────────────────────────

    private fun ViewEvent.toEntity() = ViewEventEntity(
        platform = platform.name,
        timestampMs = timestampMs,
        localDate = DateUtils.toLocalDate(timestampMs),
        transitionType = transitionType.name,
        confidenceScore = confidence,
    )

    private fun DailyCountRow.toDomain() = DailyCount(
        date = localDate,
        platform = Platform.fromString(platform),
        count = count,
    )
}
