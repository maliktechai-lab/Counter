package com.reeltracker.accessibility.detectors

/**
 * Result returned by a [FeedDetector] for a single [com.reeltracker.accessibility.EventSnapshot].
 *
 * The three leaf states mirror [com.reeltracker.domain.model.DetectionStatus]:
 * - [InFeed] → SUPPORTED_FEED
 * - [NotInFeed] → UNSUPPORTED_SCREEN (exclusion veto applied)
 * - [Unknown] → UNKNOWN_SCREEN (insufficient signals)
 */
sealed class DetectionResult {

    /**
     * Confidence score is at or above [ConfidenceScore.PROVISIONAL_THRESHOLD].
     *
     * @param confidence Score in [0.0, 1.0].
     * @param presentSignals The signals that produced this score.
     * @param isTransition True when [DetectionSignal.SCROLL_FROM_CONTAINER] is in [presentSignals],
     *   indicating a feed-scroll event that could represent a video transition.
     */
    data class InFeed(
        val confidence: Float,
        val presentSignals: Set<DetectionSignal>,
        val isTransition: Boolean,
    ) : DetectionResult()

    /**
     * An exclusion view ID is present. This is a known non-feed screen (home feed, search, etc.).
     * Score is forced to 0.0.
     */
    object NotInFeed : DetectionResult()

    /**
     * Signals are absent, contradictory, or below the threshold.
     * Never produces a count increment.
     *
     * @param confidence Score in [0.0, 1.0) — below threshold (may be 0.0).
     */
    data class Unknown(
        val confidence: Float,
        val presentSignals: Set<DetectionSignal>,
    ) : DetectionResult()
}
