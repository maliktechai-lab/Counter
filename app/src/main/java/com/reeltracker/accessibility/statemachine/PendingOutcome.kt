package com.reeltracker.accessibility.statemachine

/**
 * Result returned by [TransitionConfirmationBuffer.submit],
 * [TransitionConfirmationBuffer.drain], and [TransitionConfirmationBuffer.cancel].
 *
 * - [Confirmed]   — the action has sufficient corroborating evidence; persist it.
 * - [Unconfirmed] — the confirmation window expired without qualifying corroboration,
 *   or the feed was lost before the window closed; discard and do not count.
 */
sealed class PendingOutcome {
    /** The action is confirmed and should be persisted as a view event. */
    data class Confirmed(val action: CountAction) : PendingOutcome()

    /** The action was discarded — insufficient evidence of video advancement. */
    data class Unconfirmed(val droppedAction: CountAction) : PendingOutcome()
}
