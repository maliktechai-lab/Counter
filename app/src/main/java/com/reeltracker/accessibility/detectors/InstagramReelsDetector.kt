package com.reeltracker.accessibility.detectors

import com.reeltracker.accessibility.EventSnapshot

/**
 * Detects whether an [EventSnapshot] represents activity inside the Instagram Reels feed.
 *
 * Uses multi-signal confidence scoring as specified in Design §5.3.
 * Stateless — the previous event type for EVENT_SEQUENCE is tracked by the caller
 * and passed in [snapshot] metadata (not needed for Instagram detection, see note below).
 *
 * ## Known Instagram view IDs (as of v1; subject to app updates)
 *
 * **Primary containers** (contribution: 0.50)
 * - `reel_recycler_view` — the main vertical Reels pager
 * - `clips_recycler_view` — alternative Reels container name
 *
 * **Secondary IDs** (contribution: 0.25 when no primary)
 * - `reels_tray_recycler_view` — a Reels tray on the home feed (not a full-screen player)
 *
 * **Exclusion IDs** (veto → score 0.0, NotInFeed)
 * - `feed_recycler` — main home feed, not Reels
 * - `clips_tray` — Reels preview tray, not full player
 *
 * Note: EVENT_SEQUENCE signal is detected by the service layer and reflected in
 * [snapshot.eventType]. The detector evaluates it in the context of the prior event type
 * which the SERVICE tracks and passes via a boolean flag on the snapshot. For V1
 * simplicity, EVENT_SEQUENCE is not explicitly tracked here — the service may supply it
 * via snapshot metadata in future revisions. Currently not wired (contributes 0.0 from
 * this detector; Group C is populated by the service layer when constructing DetectionResult).
 *
 * @see YouTubeShortsDetector for the YouTube-specific counterpart.
 */
class InstagramReelsDetector : FeedDetector {

    // ── View IDs ──────────────────────────────────────────────────────────────

    private val primaryIds = setOf(
        "reel_recycler_view",
        "clips_recycler_view",
    )

    private val secondaryIds = setOf(
        "reels_tray_recycler_view",
    )

    private val exclusionIds = setOf(
        "feed_recycler",
        "clips_tray",
    )

    companion object {
        /** [android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED] = 8 */
        internal const val TYPE_VIEW_SCROLLED = 0x00000008
    }

    /** Short IDs of known feed containers — used for scroll-source validation. */
    val feedContainerIds: Set<String> = primaryIds + secondaryIds

    // ── Detection ──────────────────────────────────────────────────────────────

    override fun detect(snapshot: EventSnapshot): DetectionResult {
        val ids = snapshot.nodeSnapshot.presentViewIds

        // Exclusion veto first
        if (exclusionIds.any { it in ids }) {
            return DetectionResult.NotInFeed
        }

        val signals = mutableSetOf<DetectionSignal>()

        // Group A
        val hasPrimary = primaryIds.any { it in ids }
        val hasSecondary = secondaryIds.any { it in ids }
        when {
            hasPrimary -> signals += DetectionSignal.PRIMARY_VIEW_ID
            hasSecondary -> signals += DetectionSignal.SECONDARY_VIEW_ID
        }

        // Group B — scroll from known container
        val scrollFromContainer = snapshot.eventType == TYPE_VIEW_SCROLLED &&
            snapshot.sourceViewId != null &&
            snapshot.sourceViewId in feedContainerIds
        if (scrollFromContainer) {
            signals += DetectionSignal.SCROLL_FROM_CONTAINER
            // Only raise NONZERO_SCROLL_DELTA when delta is a real non-zero value
            if (snapshot.scrollDeltaY != 0 &&
                snapshot.scrollDeltaY != EventSnapshot.SCROLL_DELTA_UNAVAILABLE
            ) {
                signals += DetectionSignal.NONZERO_SCROLL_DELTA
            }
        }

        // Group C — EVENT_SEQUENCE not tracked here (service layer responsibility in V1)

        val score = SignalGroup.computeScore(signals)
        val isTransition = DetectionSignal.SCROLL_FROM_CONTAINER in signals

        return if (score >= ConfidenceScore.PROVISIONAL_THRESHOLD) {
            DetectionResult.InFeed(confidence = score, presentSignals = signals, isTransition = isTransition)
        } else {
            DetectionResult.Unknown(confidence = score, presentSignals = signals)
        }
    }
}
