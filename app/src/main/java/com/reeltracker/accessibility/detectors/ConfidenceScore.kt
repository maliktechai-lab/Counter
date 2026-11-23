package com.reeltracker.accessibility.detectors

/**
 * Confidence score thresholds for the detection pipeline.
 *
 * The threshold values are **provisional heuristics** subject to real-device calibration
 * (TC-07, TC-15). They are intentionally named "provisional" to prevent them from being
 * treated as calibrated probabilities.
 */
object ConfidenceScore {

    /**
     * Minimum confidence score required to classify a screen as [DetectionResult.InFeed].
     *
     * This value (0.60) requires that at least one gesture-based signal (Group B) accompanies
     * any view-hierarchy signal (Group A). Primary view ID alone = 0.50 (below threshold).
     * Primary + scroll from container = 0.70 (above threshold).
     *
     * This threshold is provisional. It should be calibrated against real-device ground-truth
     * data as part of the acceptance test protocol described in docs/acceptance-test-plan.md.
     */
    const val PROVISIONAL_THRESHOLD = 0.60f
}
