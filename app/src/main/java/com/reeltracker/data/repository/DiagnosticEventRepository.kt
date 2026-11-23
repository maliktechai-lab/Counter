package com.reeltracker.data.repository

import com.reeltracker.data.db.DiagnosticEventDao
import com.reeltracker.data.db.DiagnosticEventEntity

class DiagnosticEventRepository(private val dao: DiagnosticEventDao) {

    suspend fun insert(entity: DiagnosticEventEntity) {
        dao.insert(entity)
    }

    suspend fun recent(limit: Int = 50): List<DiagnosticEventEntity> =
        dao.recent(limit)

    /** Purge diagnostic events older than [beforeTimestampMs] to cap table size. */
    suspend fun purgeOlderThan(beforeTimestampMs: Long) {
        dao.deleteOlderThan(beforeTimestampMs)
    }
}
