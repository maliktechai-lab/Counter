package com.reeltracker.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.reeltracker.accessibility.detectors.DetectionResult
import com.reeltracker.accessibility.detectors.InstagramReelsDetector
import com.reeltracker.accessibility.detectors.YouTubeShortsDetector
import com.reeltracker.accessibility.statemachine.CountAction
import com.reeltracker.accessibility.statemachine.FeedStateMachine
import com.reeltracker.accessibility.statemachine.StateMachineEvent
import com.reeltracker.di.ServiceLocator
import com.reeltracker.domain.model.Platform
import com.reeltracker.domain.model.TransitionType
import com.reeltracker.domain.model.ViewEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Core accessibility event loop for ReelTracker.
 *
 * Receives [AccessibilityEvent]s from Instagram and YouTube (scoped by
 * accessibility_service_config.xml). For each event it:
 * 1. Takes a node tree snapshot via [NodeSnapshotBuilder] before the callback returns.
 * 2. Runs the appropriate [com.reeltracker.accessibility.detectors.FeedDetector].
 * 3. Maps the [DetectionResult] to a [StateMachineEvent].
 * 4. Feeds the event into the per-platform [FeedStateMachine].
 * 5. Executes the resulting [CountAction]s via use-cases on a background coroutine.
 *
 * Not thread-safe. All state mutations happen on the accessibility service thread;
 * IO is dispatched via the coroutine scope.
 */
class ReelTrackerAccessibilityService : AccessibilityService() {

    // ── Detectors ─────────────────────────────────────────────────────────────

    private val igDetector = InstagramReelsDetector()
    private val ytDetector = YouTubeShortsDetector()

    // ── Per-platform state machines ───────────────────────────────────────────

    private val stateMachines: Map<Platform, FeedStateMachine> = mapOf(
        Platform.INSTAGRAM to FeedStateMachine(Platform.INSTAGRAM),
        Platform.YOUTUBE   to FeedStateMachine(Platform.YOUTUBE),
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val foregroundTracker = ForegroundAppTracker()
    private val scrollDeltaTracker = ScrollDeltaTracker()

    /**
     * Package name that was in the foreground just before the most recent change.
     * Used to determine which platform got an AppLeft event.
     */
    private var previousForegroundPkg: String? = null

    // ── Coroutine scope (IO off the service main thread) ──────────────────────

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // ── AccessibilityService lifecycle ────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        stateMachines.values.forEach { sm ->
            sm.process(StateMachineEvent.ServiceResumed, System.currentTimeMillis())
        }
    }

    override fun onInterrupt() {
        val nowMs = System.currentTimeMillis()
        stateMachines.values.forEach { sm ->
            sm.process(StateMachineEvent.ServiceInterrupted, nowMs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        foregroundTracker.reset()
        scrollDeltaTracker.reset()
        serviceScope.cancel()
    }

    // ── Event processing ──────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // Only process events from supported platforms
        val platform = try {
            Platform.fromPackage(pkg)
        } catch (_: IllegalArgumentException) {
            return
        }

        val nowMs = event.eventTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        // Track foreground package changes — emit AppLeft / AppReturned to relevant machine
        val oldPkg = foregroundTracker.currentPackage()
        val packageChanged = foregroundTracker.update(pkg, nowMs)
        if (packageChanged) {
            handleForegroundChange(oldPkg = oldPkg, newPkg = pkg, nowMs = nowMs)
            previousForegroundPkg = oldPkg
        }

        // Snapshot the node tree inside the callback (nodes are recycled after return)
        val root = rootInActiveWindow?.let { RealNodeWrapper(it) }
        val nodeSnapshot = NodeSnapshotBuilder.build(root, pkg)

        // Extract the source view ID short name from the event's source node
        val sourceViewIdShort = event.source?.viewIdResourceName?.substringAfterLast(":id/")

        // Compute scroll delta (API 26–27 compat via ScrollDeltaTracker)
        val scrollDelta = scrollDeltaTracker.delta(sourceViewIdShort, event.scrollY)

        val snapshot = EventSnapshot(
            packageName  = pkg,
            eventType    = event.eventType,
            sourceViewId = sourceViewIdShort,
            scrollY      = event.scrollY,
            scrollMaxY   = -1, // AccessibilityEvent does not expose scrollMaxY via standard API
            scrollDeltaY = scrollDelta,
            eventAction  = event.action,
            timestampMs  = nowMs,
            nodeSnapshot = nodeSnapshot,
        )

        // Run detector
        val result = when (platform) {
            Platform.INSTAGRAM -> igDetector.detect(snapshot)
            Platform.YOUTUBE   -> ytDetector.detect(snapshot)
        }

        // Map detection result → state machine event
        val smEvent: StateMachineEvent = when (result) {
            is DetectionResult.InFeed -> {
                if (result.isTransition) StateMachineEvent.FeedScrolled(result.confidence)
                else                    StateMachineEvent.FeedEntered(result.confidence)
            }
            DetectionResult.NotInFeed -> StateMachineEvent.FeedLost
            is DetectionResult.Unknown -> StateMachineEvent.FeedLost
        }

        val machine = stateMachines[platform] ?: return
        val actions = machine.process(smEvent, nowMs)

        // Execute confirmed actions on IO dispatcher
        if (actions.isNotEmpty()) {
            val recordViewEvent = ServiceLocator.recordViewEventUseCase
            serviceScope.launch {
                for (action in actions) {
                    val viewEvent = when (action) {
                        is CountAction.RecordEntry -> ViewEvent(
                            platform       = action.platform,
                            timestampMs    = action.timestampMs,
                            transitionType = TransitionType.ENTRY,
                            confidence     = action.confidence,
                        )
                        is CountAction.RecordTransition -> ViewEvent(
                            platform       = action.platform,
                            timestampMs    = action.timestampMs,
                            transitionType = TransitionType.TRANSITION,
                            confidence     = action.confidence,
                        )
                    }
                    recordViewEvent(viewEvent)
                }
            }
        }
    }

    // ── Foreground change helpers ──────────────────────────────────────────────

    /**
     * Called when the foreground package changes from [oldPkg] to [newPkg].
     *
     * Fires [StateMachineEvent.AppLeft] on the platform that just left the foreground
     * and [StateMachineEvent.AppReturned] on the platform that returned, if applicable.
     */
    private fun handleForegroundChange(oldPkg: String?, newPkg: String, nowMs: Long) {
        // Platform that left the foreground
        if (oldPkg != null) {
            val leavingPlatform = platformForPkg(oldPkg)
            if (leavingPlatform != null) {
                stateMachines[leavingPlatform]?.process(StateMachineEvent.AppLeft, nowMs)
                scrollDeltaTracker.reset()
            }
        }

        // Platform that returned to the foreground (if the new pkg is a target)
        val returningPlatform = platformForPkg(newPkg)
        if (returningPlatform != null && oldPkg != null) {
            // Compute absence duration: time since the returning platform last left
            // foregroundTracker now shows newPkg; duration is measured from when it changed
            val absenceMs = foregroundTracker.foregroundDurationMs(nowMs)
            stateMachines[returningPlatform]?.process(StateMachineEvent.AppReturned(absenceMs), nowMs)
        }
    }

    private fun platformForPkg(pkg: String): Platform? =
        Platform.values().firstOrNull { it.packageName == pkg }
}
