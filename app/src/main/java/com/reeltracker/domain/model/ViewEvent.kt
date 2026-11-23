package com.reeltracker.domain.model

/**
 * A confirmed view event ready for persistence.
 * Only events with corroborating evidence of video advancement reach this type.
 */
data class ViewEvent(
    val platform: Platform,
    val timestampMs: Long,
    val transitionType: TransitionType,
    /** Confidence score [0.0, 1.0] at the time of detection. Provisional heuristic — see TC-07. */
    val confidence: Float,
)
