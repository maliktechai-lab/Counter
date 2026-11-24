package com.reeltracker.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.reeltracker.accessibility.detectors.DetectionResult
import com.reeltracker.accessibility.detectors.InstagramReelsDetector
import com.reeltracker.accessibility.detectors.YouTubeShortsDetector
import com.reeltracker.accessibility.statemachine.CountAction
import com.reeltracker.accessibility.statemachine.EventSignatureDeduplicator
import com.reeltracker.accessibility.statemachine.FeedStateMachine
import com.reeltracker.accessibility.statemachine.PendingOutcome
import com.reeltracker.accessibility.statemachine.StateMachineEvent
import com.reeltracker.accessibility.statemachine.TransitionConfirmationBuffer
import com.reeltracker.di.ServiceLocator
import com.reeltracker.domain.model.Platform
import com.reeltracker.domain.model.TransitionType
import com.reeltracker.domain.model.ViewEvent
import com.reeltracker.service.KeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Core accessibility event loop for ReelTracker.
 *
 * Pipeline per event:
 * 1. Synchronously extract an immutable [EventSnapshot] before the callback returns
 *    (node references are pooled and recycled by the framework).
 * 2. [ForegroundAppTracker.update] on TYPE_WINDOW_STATE_CHANGED; fire AppLeft/AppReturned.
 * 3. [EventSignatureDeduplicator.shouldPass] — if false, drain buffer and return.
 * 4. Run [com.reeltracker.accessibility.detectors.FeedDetector].
 * 5. Convert [DetectionResult] → [StateMachineEvent] → [FeedStateMachine.process].
 * 6. [TransitionConfirmationBuffer.tryCorroborate] *before* submit.
 * 7. [TransitionConfirmationBuffer.submit] for each [CountAction].
 * 8. [TransitionConfirmationBuffer.drain].
 * 9. For each [PendingOutcome.Confirmed]: persist via [com.reeltracker.domain.usecase.RecordViewEventUseCase].
 *
 * Not thread-safe. All state mutations happen on the accessibility service main thread;
 * IO is dispatched via [serviceScope].
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

    // ── Layer 1: deduplicator ──────────────────────────────────────────────────

    private val deduplicator = EventSignatureDeduplicator()

    // ── Layer 2: per-platform confirmation buffers ─────────────────────────────

    private val confirmBuffers: Map<Platform, TransitionConfirmationBuffer> = mapOf(
        Platform.INSTAGRAM to TransitionConfirmationBuffer(),
        Platform.YOUTUBE   to TransitionConfirmationBuffer(),
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val foregroundTracker  = ForegroundAppTracker()
    private val scrollDeltaTracker = ScrollDeltaTracker()

    // ── Coroutine scope ───────────────────────────────────────────────────────

    private val serviceJob   = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // ── AccessibilityService lifecycle ────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        val nowMs = System.currentTimeMillis()
        stateMachines.values.forEach { it.process(StateMachineEvent.ServiceResumed, nowMs) }
        deduplicator.reset()
        scrollDeltaTracker.reset()
        foregroundTracker.reset()
        confirmBuffers.values.forEach { it.reset() }
        KeepAliveService.start(this)
    }

    /**
     * onInterrupt fires when the system temporarily suspends events (e.g. phone call,
     * screen off on some devices). It is INDEPENDENT of app-switch (AppLeft/AppReturned).
     */
    override fun onInterrupt() {
        val nowMs = System.currentTimeMillis()
        stateMachines.values.forEach { it.process(StateMachineEvent.ServiceInterrupted, nowMs) }
        // Cancel all pending candidates — we may miss corroborating events.
        confirmBuffers.values.forEach { it.cancel() }
    }

    override fun onDestroy() {
        super.onDestroy()
        foregroundTracker.reset()
        scrollDeltaTracker.reset()
        serviceScope.cancel()
        KeepAliveService.stop(this)
    }

    // ── Event processing ──────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // ── Synchronous framework-object reads (must finish before callback returns) ──
        val pkg       = event.packageName?.toString() ?: return
        val eType     = event.eventType
        val sourceRes = event.source?.viewIdResourceName   // read from pooled object now
        val sY        = event.scrollY                      // available API 26+
        val action    = event.action
        val ts        = event.eventTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        // API 28+ provides scrollDeltaY directly; use sentinel below API 28
        val rawDelta: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            event.scrollDeltaY
        } else {
            EventSnapshot.SCROLL_DELTA_UNAVAILABLE
        }

        val root       = rootInActiveWindow?.let { RealNodeWrapper(it) }
        val nodeSnap   = NodeSnapshotBuilder.build(root, pkg)

        // ── All framework object reads complete ───────────────────────────────

        val sourceViewId = sourceRes?.substringAfterLast(":id/")

        // For API 26/27 compute delta from ScrollDeltaTracker using scrollY;
        // on API 28+ use the directly available value.
        val scrollDelta = if (sY >= 0 && rawDelta == EventSnapshot.SCROLL_DELTA_UNAVAILABLE) {
            scrollDeltaTracker.delta(sourceViewId, sY)
        } else {
            rawDelta
        }

        val snapshot = EventSnapshot(
            packageName  = pkg,
            eventType    = eType,
            sourceViewId = sourceViewId,
            scrollY      = sY,
            scrollMaxY   = -1,
            scrollDeltaY = scrollDelta,
            eventAction  = action,
            timestampMs  = ts,
            nodeSnapshot = nodeSnap,
        )

        processSnapshot(snapshot)
    }

    // ── Post-snapshot pipeline ────────────────────────────────────────────────

    private fun processSnapshot(snapshot: EventSnapshot) {
        val pkg = snapshot.packageName

        // Step 2: foreground tracking on window state changes
        if (snapshot.eventType == TYPE_WINDOW_STATE_CHANGED) {
            val oldPkg = foregroundTracker.currentPackage()
            val changed = foregroundTracker.update(pkg, snapshot.timestampMs)
            if (changed) {
                handleForegroundChange(oldPkg, pkg, snapshot.timestampMs)
                scrollDeltaTracker.reset()
                deduplicator.reset()
            }
        }

        // Gate: only process events from supported platforms
        val platform = platformForPkg(pkg) ?: return
        val machine  = stateMachines[platform]  ?: return
        val buffer   = confirmBuffers[platform] ?: return

        // Step 3: Layer 1 deduplication
        if (!deduplicator.shouldPass(platform, snapshot)) {
            buffer.drain(snapshot.timestampMs)
            return
        }

        // Step 4: detection
        val result = when (platform) {
            Platform.INSTAGRAM -> igDetector.detect(snapshot)
            Platform.YOUTUBE   -> ytDetector.detect(snapshot)
        }

        // Step 5: state machine
        val smEvent: StateMachineEvent = when (result) {
            is DetectionResult.InFeed -> {
                if (result.isTransition) StateMachineEvent.FeedScrolled(result.confidence)
                else                    StateMachineEvent.FeedEntered(result.confidence)
            }
            DetectionResult.NotInFeed -> StateMachineEvent.FeedLost
            is DetectionResult.Unknown -> StateMachineEvent.FeedLost
        }

        val actions = machine.process(smEvent, snapshot.timestampMs)

        // Cancel buffer if feed was lost
        if (smEvent == StateMachineEvent.FeedLost) {
            buffer.cancel()
        }

        // Step 6: tryCorroborate BEFORE submit
        val isFeedContainer = isFeedContainer(platform, snapshot.sourceViewId)
        buffer.tryCorroborate(
            eventType        = snapshot.eventType,
            sourceViewId     = snapshot.sourceViewId,
            scrollY          = snapshot.scrollY,
            eventTimestampMs = snapshot.timestampMs,
            isFeedContainer  = isFeedContainer,
        )

        // Steps 7+8: submit each action, then drain
        val outcomes = mutableListOf<PendingOutcome>()
        for (a in actions) {
            outcomes += buffer.submit(a, snapshot.scrollY, snapshot.timestampMs)
        }
        outcomes += buffer.drain(snapshot.timestampMs)

        // Step 9: persist confirmed events
        val confirmed = outcomes.filterIsInstance<PendingOutcome.Confirmed>()
        if (confirmed.isNotEmpty()) {
            val recordViewEvent = ServiceLocator.recordViewEventUseCase
            serviceScope.launch {
                for (outcome in confirmed) {
                    val viewEvent = when (val ca = outcome.action) {
                        is CountAction.RecordEntry -> ViewEvent(
                            platform       = ca.platform,
                            timestampMs    = ca.timestampMs,
                            transitionType = TransitionType.ENTRY,
                            confidence     = ca.confidence,
                        )
                        is CountAction.RecordTransition -> ViewEvent(
                            platform       = ca.platform,
                            timestampMs    = ca.timestampMs,
                            transitionType = TransitionType.TRANSITION,
                            confidence     = ca.confidence,
                        )
                    }
                    recordViewEvent(viewEvent)
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun handleForegroundChange(oldPkg: String?, newPkg: String, nowMs: Long) {
        if (oldPkg != null) {
            platformForPkg(oldPkg)?.let { leavingPlatform ->
                stateMachines[leavingPlatform]?.process(StateMachineEvent.AppLeft, nowMs)
                confirmBuffers[leavingPlatform]?.cancel()
            }
        }
        if (oldPkg != null) {
            platformForPkg(newPkg)?.let { returningPlatform ->
                val absenceMs = foregroundTracker.foregroundDurationMs(nowMs)
                stateMachines[returningPlatform]?.process(
                    StateMachineEvent.AppReturned(absenceMs), nowMs
                )
            }
        }
    }

    private fun platformForPkg(pkg: String): Platform? =
        Platform.values().firstOrNull { it.packageName == pkg }

    private fun isFeedContainer(platform: Platform, sourceViewId: String?): Boolean {
        if (sourceViewId == null) return false
        return when (platform) {
            Platform.INSTAGRAM -> sourceViewId in igDetector.feedContainerIds
            Platform.YOUTUBE   -> sourceViewId in ytDetector.feedContainerIds
        }
    }

    companion object {
        private const val TYPE_WINDOW_STATE_CHANGED = 0x00000020
    }
}
