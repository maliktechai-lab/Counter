package com.reeltracker.domain.model

/**
 * Live classification of what the monitoring engine currently observes for a given platform.
 *
 * Transitions are driven by detection output and state machine state only.
 * [UnknownScreen] is never counted.
 */
sealed class DetectionStatus {
    /** Service active; not currently in a supported feed for this platform. */
    object Monitoring : DetectionStatus()

    /** Confidence ≥ threshold; user is confirmed in a Reels/Shorts feed. */
    data class SupportedFeed(val confidence: Float) : DetectionStatus()

    /** An exclusion ID is present; this is a known non-feed screen (home feed, search, inbox). */
    object UnsupportedScreen : DetectionStatus()

    /** Signals are absent or contradictory; classification is not possible. Not counted. */
    data class UnknownScreen(val confidence: Float) : DetectionStatus()

    /** onInterrupt() was called; events may be missed until the service resumes. */
    object ServiceInterrupted : DetectionStatus()

    /**
     * Service is running and the target app is confirmed in the foreground,
     * but no confirmed event has been recorded in > 2 hours of that foreground activity.
     * Does not assert detection failure — only reports the absence of recent counts.
     */
    data class StaleDetection(val lastEventMs: Long) : DetectionStatus()
}
