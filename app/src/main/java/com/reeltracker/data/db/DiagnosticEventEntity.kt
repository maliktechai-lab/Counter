package com.reeltracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a diagnostic event record.
 *
 * Contains only structural metadata — no text, no content from monitored apps.
 * Used for the Diagnostic screen and health monitoring.
 */
@Entity(
    tableName = "diagnostic_events",
    indices = [Index(value = ["timestamp_ms"])],
)
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,

    /** Platform string (e.g. "INSTAGRAM"). */
    val platform: String,

    /** Integer event type from AccessibilityEvent (e.g. TYPE_VIEW_SCROLLED = 8). */
    @ColumnInfo(name = "event_type")
    val eventType: Int,

    /** Name of the detection state at time of recording (e.g. "UNKNOWN_SCREEN"). */
    @ColumnInfo(name = "screen_state")
    val screenState: String,

    /** Confidence score [0.0, 1.0]. */
    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    /**
     * Bitmask of [com.reeltracker.accessibility.detectors.DetectionSignal] values
     * present at time of detection.
     */
    @ColumnInfo(name = "signal_bitmask")
    val signalBitmask: Int,

    /**
     * Session-cumulative count of unconfirmed-and-discarded transition candidates
     * at the time this record was written.
     */
    @ColumnInfo(name = "unconfirmed_discards")
    val unconfirmedDiscards: Int,
)
