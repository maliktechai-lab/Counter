package com.reeltracker.accessibility.detectors

/**
 * Individual signals that contribute to the confidence score for a detection result.
 *
 * Signals are grouped (A, B, C) with per-group caps enforced by [SignalGroup].
 * The [ordinal] value of each signal is used in the diagnostic signal bitmask.
 */
enum class DetectionSignal {

    // ── Group A — View hierarchy identity (max 0.50) ────────────────────────

    /**
     * A known primary container view ID is present in [EventSnapshot.nodeSnapshot.presentViewIds].
     * Contributes 0.50. Correlated with [SECONDARY_VIEW_ID] — both present is still capped at 0.50.
     */
    PRIMARY_VIEW_ID,

    /**
     * A secondary view ID is present but no primary.
     * Contributes 0.25 (only when PRIMARY is absent).
     */
    SECONDARY_VIEW_ID,

    // ── Group B — Scroll gesture evidence (max 0.30) ────────────────────────

    /**
     * The event is [android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED]
     * and its source ID matches a known feed container.
     * Contributes 0.20 on its own.
     */
    SCROLL_FROM_CONTAINER,

    /**
     * [EventSnapshot.scrollDeltaY] is non-zero AND is not the
     * [EventSnapshot.SCROLL_DELTA_UNAVAILABLE] sentinel.
     *
     * Contributes +0.10 on top of [SCROLL_FROM_CONTAINER] (only valid when that
     * signal is also present on the same event). Does not contribute 0.10 alone
     * — SCROLL_FROM_CONTAINER must also be present to unlock the bonus.
     *
     * When [EventSnapshot.scrollDeltaY] == [EventSnapshot.SCROLL_DELTA_UNAVAILABLE]
     * (API < 28), this signal is NOT raised. The sentinel is treated as "not nonzero".
     */
    NONZERO_SCROLL_DELTA,

    // ── Group C — Contextual evidence (max 0.20) ────────────────────────────

    /**
     * The previous event type was [android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]
     * and the current event type is [android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED].
     * Contributes 0.10. Applies to both platforms.
     */
    EVENT_SEQUENCE,

    /**
     * YouTube-only. The bottom navigation bar contains a tab with text "Shorts" that
     * is currently selected ([NodeSnapshot.shortsNavTabSelected] == true).
     * The text value is NOT stored; only this boolean signal is retained.
     * Contributes 0.10.
     */
    NAV_TAB_SHORTS,
}
