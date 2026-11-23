package com.reeltracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ViewEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ViewEventEntity): Long

    /** Daily confirmed count per platform for a single date. */
    @Query(
        """
        SELECT local_date, platform, COUNT(*) AS count
        FROM view_events
        WHERE local_date = :date
        GROUP BY platform
        """
    )
    suspend fun dailyCounts(date: String): List<DailyCountRow>

    /** Daily confirmed counts for all platforms across a date range (inclusive). */
    @Query(
        """
        SELECT local_date, platform, COUNT(*) AS count
        FROM view_events
        WHERE local_date >= :startDate AND local_date <= :endDate
        GROUP BY local_date, platform
        ORDER BY local_date
        """
    )
    suspend fun countsInRange(startDate: String, endDate: String): List<DailyCountRow>

    /** Reactive total confirmed count for today, by platform. Used by HomeScreen. */
    @Query(
        """
        SELECT local_date, platform, COUNT(*) AS count
        FROM view_events
        WHERE local_date = :date
        GROUP BY platform
        """
    )
    fun dailyCountsFlow(date: String): Flow<List<DailyCountRow>>

    /** Timestamp of the most recent confirmed event for a given platform, or null if none. */
    @Query(
        """
        SELECT MAX(timestamp_ms) FROM view_events WHERE platform = :platform
        """
    )
    suspend fun lastEventTimestamp(platform: String): Long?

    /** Delete all events older than [beforeDate] (exclusive). Used for data retention. */
    @Query("DELETE FROM view_events WHERE local_date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String): Int
}
