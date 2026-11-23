package com.reeltracker.accessibility.detectors

import com.reeltracker.accessibility.EventSnapshot

/**
 * Computes a confidence score from a set of detected signals.
 *
 * Rules (see Design §5.3):
 *
 * **Group A — View hierarchy identity (max 0.50)**
 * - [DetectionSignal.PRIMARY_VIEW_ID] alone → 0.50
 * - [DetectionSignal.SECONDARY_VIEW_ID] alone → 0.25
 * - Both present → 0.50 (capped, not 0.75)
 *
 * **Group B — Scroll gesture evidence (max 0.30)**
 * - [DetectionSignal.SCROLL_FROM_CONTAINER] alone → 0.20
 * - [DetectionSignal.NONZERO_SCROLL_DELTA] alone → 0.10 (only combined with SCROLL_FROM_CONTAINER)
 * - Both present on the same event → 0.30
 * - Note: [DetectionSignal.NONZERO_SCROLL_DELTA] contributes 0.0 when
 *   [EventSnapshot.SCROLL_DELTA_UNAVAILABLE] is in effect — callers must not raise this signal
 *   when the delta is the sentinel value.
 *
 * **Group C — Contextual evidence (max 0.20)**
 * - [DetectionSignal.EVENT_SEQUENCE] → 0.10
 * - [DetectionSignal.NAV_TAB_SHORTS] → 0.10
 * - Both → 0.20
 *
 * **Exclusion veto:** if any exclusion signal was raised by the caller (represented by
 * passing `hasExclusion = true`), the total score is forced to 0.0 regardless of other signals.
 */
object SignalGroup {

    fun computeScore(presentSignals: Set<DetectionSignal>): Float =
        computeScoreWithExclusion(presentSignals, hasExclusion = false)

    fun computeScoreWithExclusion(presentSignals: Set<DetectionSignal>, hasExclusion: Boolean): Float {
        if (hasExclusion) return 0.0f

        // Group A
        val groupA: Float = when {
            DetectionSignal.PRIMARY_VIEW_ID in presentSignals -> 0.50f
            DetectionSignal.SECONDARY_VIEW_ID in presentSignals -> 0.25f
            else -> 0.0f
        }

        // Group B
        val scrollFromContainer = DetectionSignal.SCROLL_FROM_CONTAINER in presentSignals
        val nonzeroDelta = DetectionSignal.NONZERO_SCROLL_DELTA in presentSignals
        val groupB: Float = when {
            scrollFromContainer && nonzeroDelta -> 0.30f
            scrollFromContainer -> 0.20f
            nonzeroDelta -> 0.10f   // nonzero delta alone (rare, but allowed as per table)
            else -> 0.0f
        }

        // Group C
        val eventSeq = if (DetectionSignal.EVENT_SEQUENCE in presentSignals) 0.10f else 0.0f
        val navTab = if (DetectionSignal.NAV_TAB_SHORTS in presentSignals) 0.10f else 0.0f
        val groupC = eventSeq + navTab  // max 0.20

        return groupA + groupB + groupC
    }
}
