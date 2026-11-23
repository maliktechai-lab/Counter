package com.reeltracker.accessibility.statemachine

import com.reeltracker.domain.model.Platform

/**
 * Per-platform session state machine for the ReelTracker detection pipeline.
 *
 * Models 5 lifecycle situations (FR-16) across the following states:
 * - [FeedState.IDLE]                — not in feed
 * - [FeedState.ENTERED]             — entered feed, entry recorded
 * - [FeedState.IN_FEED]             — scrolling through feed
 * - [FeedState.INTERRUPTED]         — app switched away temporarily (< 30 s = resume; ≥ 30 s = new session)
 * - [FeedState.SERVICE_INTERRUPTED] — accessibility service interrupted
 *
 * **App-switch vs Service-interrupt independence (FR-16):**
 * - [StateMachineEvent.AppLeft] / [StateMachineEvent.AppReturned] manage the INTERRUPTED state.
 * - [StateMachineEvent.ServiceInterrupted] / [StateMachineEvent.ServiceResumed] manage SERVICE_INTERRUPTED.
 * - These two paths do NOT interact. Receiving ServiceInterrupted does not clear an INTERRUPTED state.
 *
 * Not thread-safe. Must be accessed only from the accessibility service main thread.
 */
class FeedStateMachine(
    val platform: Platform,
    private val resumeWindowMs: Long = RESUME_WINDOW_MS,
) {
    var state: FeedState = FeedState.IDLE
        private set

    /** Timestamp when the app left the foreground (for INTERRUPTED timeout calculation). */
    private var appLeftAtMs: Long = 0L

    /**
     * Process an input event and return the list of [CountAction]s to execute.
     *
     * Returns an empty list when no counting action is required.
     */
    fun process(event: StateMachineEvent, nowMs: Long = System.currentTimeMillis()): List<CountAction> {
        return when (event) {
            is StateMachineEvent.FeedEntered -> onFeedEntered(nowMs, event.confidence)
            is StateMachineEvent.FeedScrolled -> onFeedScrolled(event.confidence, nowMs)
            is StateMachineEvent.FeedLost -> onFeedLost()
            is StateMachineEvent.AppLeft -> onAppLeft(nowMs)
            is StateMachineEvent.AppReturned -> onAppReturned(event.absenceMs, nowMs)
            is StateMachineEvent.ServiceInterrupted -> onServiceInterrupted()
            is StateMachineEvent.ServiceResumed -> onServiceResumed()
        }
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    private fun onFeedEntered(nowMs: Long, confidence: Float): List<CountAction> = when (state) {
        FeedState.IDLE -> {
            // Fresh entry — record first video
            state = FeedState.ENTERED
            listOf(CountAction.RecordEntry(platform, nowMs, confidence))
        }
        FeedState.ENTERED, FeedState.IN_FEED -> {
            // Already in feed; non-scroll entry signal is redundant
            emptyList()
        }
        FeedState.INTERRUPTED, FeedState.SERVICE_INTERRUPTED -> {
            // Resumed into feed — treat as new entry
            state = FeedState.ENTERED
            listOf(CountAction.RecordEntry(platform, nowMs, confidence))
        }
    }

    private fun onFeedScrolled(confidence: Float, nowMs: Long): List<CountAction> {
        return when (state) {
            FeedState.IDLE -> {
                // Scroll before explicit entry — treat as implicit entry + transition
                state = FeedState.IN_FEED
                listOf(
                    CountAction.RecordEntry(platform, nowMs, confidence),
                    CountAction.RecordTransition(platform, nowMs, confidence),
                )
            }
            FeedState.ENTERED -> {
                state = FeedState.IN_FEED
                listOf(CountAction.RecordTransition(platform, nowMs, confidence))
            }
            FeedState.IN_FEED -> {
                listOf(CountAction.RecordTransition(platform, nowMs, confidence))
            }
            FeedState.INTERRUPTED, FeedState.SERVICE_INTERRUPTED -> {
                // Resumed with a scroll — implicit re-entry + transition
                state = FeedState.IN_FEED
                listOf(
                    CountAction.RecordEntry(platform, nowMs, confidence),
                    CountAction.RecordTransition(platform, nowMs, confidence),
                )
            }
        }
    }

    private fun onFeedLost(): List<CountAction> {
        return when (state) {
            FeedState.IDLE -> emptyList()
            FeedState.ENTERED, FeedState.IN_FEED,
            FeedState.INTERRUPTED, FeedState.SERVICE_INTERRUPTED -> {
                state = FeedState.IDLE
                emptyList()
            }
        }
    }

    private fun onAppLeft(nowMs: Long): List<CountAction> {
        return when (state) {
            FeedState.IDLE -> emptyList()
            FeedState.ENTERED, FeedState.IN_FEED -> {
                appLeftAtMs = nowMs
                state = FeedState.INTERRUPTED
                emptyList()
            }
            FeedState.INTERRUPTED -> {
                // Update the leave time (second AppLeft while already interrupted)
                appLeftAtMs = nowMs
                emptyList()
            }
            FeedState.SERVICE_INTERRUPTED -> emptyList()
        }
    }

    private fun onAppReturned(absenceMs: Long, nowMs: Long): List<CountAction> {
        return when (state) {
            FeedState.INTERRUPTED -> {
                if (absenceMs < resumeWindowMs) {
                    // Short absence — resume without new entry
                    state = FeedState.IN_FEED
                } else {
                    // Long absence — start new session
                    state = FeedState.IDLE
                }
                emptyList()
            }
            FeedState.SERVICE_INTERRUPTED -> {
                state = FeedState.IDLE
                emptyList()
            }
            FeedState.IDLE, FeedState.ENTERED, FeedState.IN_FEED -> emptyList()
        }
    }

    private fun onServiceInterrupted(): List<CountAction> {
        return when (state) {
            FeedState.IDLE -> {
                state = FeedState.SERVICE_INTERRUPTED
                emptyList()
            }
            FeedState.ENTERED, FeedState.IN_FEED -> {
                state = FeedState.SERVICE_INTERRUPTED
                emptyList()
            }
            FeedState.INTERRUPTED -> {
                // Service interrupted while app was also away; keep SERVICE_INTERRUPTED
                state = FeedState.SERVICE_INTERRUPTED
                emptyList()
            }
            FeedState.SERVICE_INTERRUPTED -> emptyList()
        }
    }

    private fun onServiceResumed(): List<CountAction> {
        state = FeedState.IDLE
        return emptyList()
    }

    companion object {
        /** App-switch resume window: absences shorter than this can resume an existing session. */
        const val RESUME_WINDOW_MS = 30_000L
    }
}
