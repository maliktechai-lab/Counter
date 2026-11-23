package com.reeltracker.accessibility.statemachine

import com.reeltracker.domain.model.Platform
import com.reeltracker.domain.model.TransitionType

/**
 * Actions emitted by [FeedStateMachine.process] that instruct the service layer
 * to record a view event.
 *
 * Actions flow to the [TransitionConfirmationBuffer]:
 * - [RecordEntry] bypasses the buffer (feed entry is confirmed by window-level signals).
 * - [RecordTransition] enters the buffer as PENDING and requires corroboration.
 */
sealed class CountAction {

    /**
     * Record the first video of a new feed session.
     * Emitted exactly once when transitioning IDLE/INTERRUPTED → ENTERED.
     */
    data class RecordEntry(
        val platform: Platform,
        val timestampMs: Long,
        val confidence: Float,
    ) : CountAction()

    /**
     * Record a potential swipe-to-next-video transition.
     * Emitted each time [StateMachineEvent.FeedScrolled] fires in ENTERED or IN_FEED.
     * Must be confirmed by the [TransitionConfirmationBuffer] before being persisted.
     */
    data class RecordTransition(
        val platform: Platform,
        val timestampMs: Long,
        val confidence: Float,
    ) : CountAction()
}
