package com.reeltracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiagnosticEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DiagnosticEventEntity): Long

    /** Most recent N diagnostic events, newest first. */
    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp_ms DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<DiagnosticEventEntity>

    /** Delete diagnostic events older than [beforeTimestampMs]. Used to cap table size. */
    @Query("DELETE FROM diagnostic_events WHERE timestamp_ms < :beforeTimestampMs")
    suspend fun deleteOlderThan(beforeTimestampMs: Long): Int
}
