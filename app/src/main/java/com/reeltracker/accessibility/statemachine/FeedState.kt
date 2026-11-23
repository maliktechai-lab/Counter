package com.reeltracker.accessibility.statemachine

/**
 * Per-platform session state for the [FeedStateMachine].
 *
 * Six states modelling the five lifecycle situations from FR-16:
 *
 * 1. [IDLE]              — not in a feed; waiting for entry.
 * 2. [ENTERED]           — just entered the feed; RecordEntry has been emitted.
 * 3. [IN_FEED]           — actively scrolling; RecordTransition emitted on each scroll.
 * 4. [INTERRUPTED]       — app switched away (< 30 s timeout); can resume without new ENTRY.
 * 5. [SERVICE_INTERRUPTED] — accessibility service received onInterrupt(); events may be missed.
 * 6. [FEED_LOST]         — transient; feed detection went to NotInFeed/Unknown mid-session.
 *   This is an internal transition state that leads back to IDLE.
 */
enum class FeedState {
    IDLE,
    ENTERED,
    IN_FEED,
    INTERRUPTED,
    SERVICE_INTERRUPTED,
}
