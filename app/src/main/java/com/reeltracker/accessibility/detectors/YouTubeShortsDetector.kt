package com.reeltracker.accessibility.detectors

import com.reeltracker.accessibility.EventSnapshot

/**
 * Detects whether an [EventSnapshot] represents activity inside the YouTube Shorts full-screen player.
 *
 * Uses multi-signal confidence scoring as specified in Design §5.3.
 *
 * ## Key discrimination: Shorts player vs Shorts shelf
 *
 * The YouTube home feed contains a horizontal "Shorts shelf" with preview thumbnails.
 * That shelf must be excluded from counting — only the full-screen Shorts player qualifies.
 *
 * Rule: shelf-only IDs present AND no player IDs → [DetectionResult.NotInFeed].
 * Player IDs override shelf IDs.
 *
 * ## Known YouTube view IDs (as of v1; subject to app updates)
 *
 * **Primary IDs — Shorts full-screen player** (contribution: 0.50)
 * - `shorts_container`
 * - `reel_player_page`
 * - `shorts_shelf_item_container` with [NodeSnapshot.shortsNavTabSelected] context
 *
 * **Secondary IDs** (contribution: 0.25 when no primary)
 * - `shorts_recycler_view`
 *
 * **Shelf-only exclusion IDs** (veto when no player IDs)
 * - `shorts_shelf_header`
 * - `shorts_shelf_pivot`
 *
 * **Feed container IDs** (used for scroll-source validation)
 * - `shorts_container`, `reel_player_page`, `shorts_recycler_view`
 */
class YouTubeShortsDetector : FeedDetector {

    // ── View IDs ──────────────────────────────────────────────────────────────

    private val primaryIds = setOf(
        "shorts_container",
        "reel_player_page",
    )

    private val secondaryIds = setOf(
        "shorts_recycler_view",
    )

    /** Shelf IDs that indicate a home-feed Shorts shelf, NOT the full-screen player. */
    private val shelfOnlyIds = setOf(
        "shorts_shelf_header",
        "shorts_shelf_pivot",
    )

    /** Short IDs of known feed containers — used for scroll-source validation. */
    val feedContainerIds: Set<String> = primaryIds + secondaryIds

    companion object {
        /** [android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED] = 8 */
        internal const val TYPE_VIEW_SCROLLED = 0x00000008
    }

    // ── Detection ──────────────────────────────────────────────────────────────

    override fun detect(snapshot: EventSnapshot): DetectionResult {
        val ids = snapshot.nodeSnapshot.presentViewIds

        val hasPlayer = primaryIds.any { it in ids }
        val hasShelf = shelfOnlyIds.any { it in ids }

        // Shelf-only exclusion veto: shelf present, no player → NotInFeed
        if (hasShelf && !hasPlayer) {
            return DetectionResult.NotInFeed
        }

        val signals = mutableSetOf<DetectionSignal>()

        // Group A
        val hasSecondary = secondaryIds.any { it in ids }
        when {
            hasPlayer -> signals += DetectionSignal.PRIMARY_VIEW_ID
            hasSecondary -> signals += DetectionSignal.SECONDARY_VIEW_ID
        }

        // Group B — scroll from known container
        val scrollFromContainer = snapshot.eventType == TYPE_VIEW_SCROLLED &&
            snapshot.sourceViewId != null &&
            snapshot.sourceViewId in feedContainerIds
        if (scrollFromContainer) {
            signals += DetectionSignal.SCROLL_FROM_CONTAINER
            if (snapshot.scrollDeltaY != 0 &&
                snapshot.scrollDeltaY != EventSnapshot.SCROLL_DELTA_UNAVAILABLE
            ) {
                signals += DetectionSignal.NONZERO_SCROLL_DELTA
            }
        }

        // Group C — NAV_TAB_SHORTS (YouTube-specific)
        if (snapshot.nodeSnapshot.shortsNavTabSelected) {
            signals += DetectionSignal.NAV_TAB_SHORTS
        }

        val score = SignalGroup.computeScore(signals)
        val isTransition = DetectionSignal.SCROLL_FROM_CONTAINER in signals

        return if (score >= ConfidenceScore.PROVISIONAL_THRESHOLD) {
            DetectionResult.InFeed(confidence = score, presentSignals = signals, isTransition = isTransition)
        } else {
            DetectionResult.Unknown(confidence = score, presentSignals = signals)
        }
    }
}
