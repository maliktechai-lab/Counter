package com.reeltracker.accessibility.statemachine

import com.reeltracker.accessibility.EventSnapshot

/**
 * Layer 2 confirmation buffer for transition candidates.
 *
 * A [CountAction.RecordTransition] produced by [FeedStateMachine] is held as PENDING
 * for [confirmationWindowMs]. It is confirmed and counted only when qualifying
 * corroborating evidence of video advancement arrives within that window.
 *
 * [CountAction.RecordEntry] bypasses the buffer entirely — feed entry is confirmed
 * by window-level signals that do not require scroll corroboration.
 *
 * ## Corroboration rules
 *
 * Corroboration is accepted only when **all** of:
 * - A candidate is pending.
 * - The corroborating event's timestamp is at least [GESTURE_SEPARATION_MIN_MS] after
 *   the candidate's submission timestamp (prevents same-gesture self-corroboration).
 * - The event meets one of:
 *   1. `TYPE_VIEW_SCROLLED` from a known feed container.
 *   2. `TYPE_WINDOW_CONTENT_CHANGED` from a known feed container AND
 *      `abs(scrollY − candidate.submissionScrollY) ≥ SCROLL_ADVANCE_MIN_PX`.
 *
 * Additionally, arriving of a new [CountAction.RecordTransition] in [submit] auto-confirms
 * the pending candidate (the second independent gesture is itself the strongest evidence).
 *
 * ## Buffer capacity
 *
 * One pending slot per buffer instance (one instance per platform). When a second
 * RecordTransition arrives while one is pending, the pending one is confirmed by the
 * new arrival, and the new one takes the slot.
 *
 * ## Known limitations
 *
 * The last swipe in any session will always be unconfirmed unless a qualifying
 * corroborating event arrives afterwards (Design §8.7).
 *
 * Not thread-safe. Must be accessed from the accessibility service main thread.
 */
class TransitionConfirmationBuffer(
    private val confirmationWindowMs: Long = DEFAULT_CONFIRMATION_WINDOW_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    companion object {
        /** Candidate expires if no corroboration arrives within this window. */
        const val DEFAULT_CONFIRMATION_WINDOW_MS = 400L

        /**
         * Minimum time gap between a candidate's submission and a corroborating event.
         * Events arriving sooner are within the same gesture window and cannot corroborate.
         */
        const val GESTURE_SEPARATION_MIN_MS = 150L

        /**
         * Minimum scroll position change required for TYPE_WINDOW_CONTENT_CHANGED
         * corroboration. Provisional — see TC-15.
         */
        const val SCROLL_ADVANCE_MIN_PX = 50

        private const val TYPE_VIEW_SCROLLED            = 0x00000008
        private const val TYPE_WINDOW_CONTENT_CHANGED   = 0x00000800
    }

    private data class PendingCandidate(
        val action: CountAction.RecordTransition,
        val submittedAtMs: Long,
        /** scrollY at time of submission, used for WINDOW_CONTENT_CHANGED advance check. */
        val submissionScrollY: Int,
        var corroborated: Boolean = false,
    )

    private var pending: PendingCandidate? = null

    private var _unconfirmedCount: Int = 0

    /** Session-cumulative count of candidates discarded as UNCONFIRMED. */
    val unconfirmedCount: Int get() = _unconfirmedCount

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Submit a [CountAction] from the state machine.
     *
     * - [CountAction.RecordEntry] bypasses the buffer → immediate [PendingOutcome.Confirmed].
     * - [CountAction.RecordTransition] enters the pending slot. If a candidate is already
     *   pending, it is auto-confirmed by this new submission before the new one enters.
     */
    fun submit(action: CountAction, scrollY: Int = -1, nowMs: Long = clock()): List<PendingOutcome> {
        return when (action) {
            is CountAction.RecordEntry -> {
                listOf(PendingOutcome.Confirmed(action))
            }
            is CountAction.RecordTransition -> {
                val outcomes = mutableListOf<PendingOutcome>()

                // Auto-confirm any existing pending candidate: the second independent gesture
                // arriving is itself the strongest evidence of video advancement.
                pending?.let { current ->
                    outcomes += PendingOutcome.Confirmed(current.action)
                }

                // New candidate enters the pending slot.
                pending = PendingCandidate(
                    action            = action,
                    submittedAtMs     = nowMs,
                    submissionScrollY = scrollY,
                    corroborated      = false,
                )

                outcomes
            }
        }
    }

    /**
     * Attempt to corroborate the pending candidate using an external event.
     *
     * Corroboration is accepted only when ALL of:
     * (a) A pending candidate exists.
     * (b) `eventTimestampMs - candidate.submittedAtMs ≥ GESTURE_SEPARATION_MIN_MS`.
     * (c) The event qualifies:
     *     — `TYPE_VIEW_SCROLLED` from a known feed container, OR
     *     — `TYPE_WINDOW_CONTENT_CHANGED` from a feed container AND
     *       `|scrollY − submissionScrollY| ≥ SCROLL_ADVANCE_MIN_PX`.
     *
     * Sets `candidate.corroborated = true` when accepted. Does not immediately confirm;
     * confirmation happens at [drain] or on the next [submit(RecordTransition)].
     */
    fun tryCorroborate(
        eventType: Int,
        sourceViewId: String?,
        scrollY: Int,
        eventTimestampMs: Long,
        isFeedContainer: Boolean,
    ) {
        val candidate = pending ?: return

        // (b) Gesture-separation guard
        val gapMs = eventTimestampMs - candidate.submittedAtMs
        if (gapMs < GESTURE_SEPARATION_MIN_MS) return

        // (c) Qualification check
        val qualifies = when (eventType) {
            TYPE_VIEW_SCROLLED -> isFeedContainer
            TYPE_WINDOW_CONTENT_CHANGED -> isFeedContainer &&
                scrollY >= 0 &&
                kotlin.math.abs(scrollY - candidate.submissionScrollY) >= SCROLL_ADVANCE_MIN_PX
            else -> false
        }

        if (qualifies) {
            candidate.corroborated = true
        }
    }

    /**
     * Drain expired candidates.
     *
     * For each pending candidate whose [confirmationWindowMs] has elapsed:
     * - `corroborated == true`  → [PendingOutcome.Confirmed].
     * - `corroborated == false` → [PendingOutcome.Unconfirmed]; [unconfirmedCount] incremented.
     *
     * Call on every incoming event to ensure timely expiry.
     */
    fun drain(nowMs: Long = clock()): List<PendingOutcome> {
        val candidate = pending ?: return emptyList()
        if (nowMs - candidate.submittedAtMs < confirmationWindowMs) return emptyList()

        pending = null
        return if (candidate.corroborated) {
            listOf(PendingOutcome.Confirmed(candidate.action))
        } else {
            _unconfirmedCount++
            listOf(PendingOutcome.Unconfirmed(candidate.action))
        }
    }

    /**
     * Discard the pending candidate without counting.
     * Called when [com.reeltracker.accessibility.statemachine.StateMachineEvent.FeedLost] fires.
     *
     * @return [PendingOutcome.Unconfirmed] if a candidate was pending, else null.
     */
    fun cancel(): PendingOutcome? {
        val candidate = pending ?: return null
        pending = null
        _unconfirmedCount++
        return PendingOutcome.Unconfirmed(candidate.action)
    }

    /** Reset for use in tests or service restart. */
    fun reset() {
        pending = null
        _unconfirmedCount = 0
    }
}
