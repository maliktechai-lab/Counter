package com.reeltracker.accessibility.statemachine

/**
 * Input events that drive the [FeedStateMachine].
 *
 * Events are translated from [com.reeltracker.accessibility.detectors.DetectionResult]
 * and from lifecycle signals in the accessibility service.
 */
sealed class StateMachineEvent {

    // ── Detection-derived events ─────────────────────────────────────────────

    /**
     * Confidence ≥ threshold and [DetectionResult.InFeed.isTransition] == false.
     * @param confidence Detector confidence score at time of event.
     */
    data class FeedEntered(val confidence: Float) : StateMachineEvent()

    /**
     * Confidence ≥ threshold and [DetectionResult.InFeed.isTransition] == true.
     * @param confidence Detector confidence score at time of event.
     */
    data class FeedScrolled(val confidence: Float) : StateMachineEvent()

    /**
     * An exclusion ID was detected (NotInFeed) or detection fell to Unknown.
     * The feed has been lost.
     */
    object FeedLost : StateMachineEvent()

    // ── Lifecycle events ──────────────────────────────────────────────────────

    /**
     * The monitored app moved to the background. Fired by [ForegroundAppTracker]
     * when the foreground package changes away from the target app.
     */
    object AppLeft : StateMachineEvent()

    /**
     * The monitored app returned to the foreground. Fired by [ForegroundAppTracker]
     * when the foreground package changes back to the target app.
     *
     * @param absenceMs  How long the app was absent (ms). Used to decide between
     *   resuming the interrupted session (< 30 s) and starting a new one (≥ 30 s).
     */
    data class AppReturned(val absenceMs: Long) : StateMachineEvent()

    /**
     * The accessibility service received [onInterrupt()].
     * Events may be missed until the service resumes.
     * This is SEPARATE from [AppLeft] / [AppReturned].
     */
    object ServiceInterrupted : StateMachineEvent()

    /**
     * The accessibility service has resumed (onServiceConnected or first event after interrupt).
     * Returns the state machine to IDLE so the next detection can start fresh.
     */
    object ServiceResumed : StateMachineEvent()
}
