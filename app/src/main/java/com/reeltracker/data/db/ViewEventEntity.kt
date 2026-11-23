package com.reeltracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.reeltracker.domain.model.Platform
import com.reeltracker.domain.model.TransitionType

/**
 * Room entity for a confirmed view event.
 *
 * Only events with corroborating video-advancement evidence reach this table.
 * No text, no content, no account information — only structural identifiers.
 */
@Entity(
    tableName = "view_events",
    indices = [
        Index(value = ["local_date"]),
        Index(value = ["platform", "local_date"]),
    ],
)
data class ViewEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Platform name as stored string (e.g. "INSTAGRAM", "YOUTUBE"). */
    val platform: String,

    /** UTC epoch milliseconds of the confirmed event. */
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,

    /** Local calendar date "YYYY-MM-DD" in the device time zone. */
    @ColumnInfo(name = "local_date")
    val localDate: String,

    /** Whether this record is a feed entry or a swipe transition. */
    @ColumnInfo(name = "transition_type")
    val transitionType: String,

    /** Detector confidence score [0.0, 1.0] at time of recording. Provisional heuristic. */
    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,
)

/** Aggregation result row from a GROUP-BY query. Not an entity. */
data class DailyCountRow(
    @ColumnInfo(name = "local_date") val localDate: String,
    val platform: String,
    val count: Int,
)
