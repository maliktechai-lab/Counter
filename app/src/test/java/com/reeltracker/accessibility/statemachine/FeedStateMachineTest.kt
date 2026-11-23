package com.reeltracker.accessibility.statemachine

import com.reeltracker.domain.model.Platform
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FeedStateMachineTest {

    private lateinit var machine: FeedStateMachine

    private fun entered(confidence: Float = 0.70f) = StateMachineEvent.FeedEntered(confidence)
    private fun scrolled(confidence: Float = 0.70f) = StateMachineEvent.FeedScrolled(confidence)
    private val lost = StateMachineEvent.FeedLost
    private val appLeft = StateMachineEvent.AppLeft
    private fun appReturned(absenceMs: Long) = StateMachineEvent.AppReturned(absenceMs)
    private val serviceInterrupted = StateMachineEvent.ServiceInterrupted
    private val serviceResumed = StateMachineEvent.ServiceResumed

    private val SHORT_ABSENCE = 10_000L  // < 30 s
    private val LONG_ABSENCE = 60_000L   // ≥ 30 s

    @BeforeEach
    fun setUp() {
        machine = FeedStateMachine(platform = Platform.INSTAGRAM)
    }

    // ── Initial state ──────────────────────────────────────────────────────

    @Test
    fun `initial state is IDLE`() = assertEquals(FeedState.IDLE, machine.state)

    // ── Normal session: IDLE → ENTERED → IN_FEED ──────────────────────────

    @Test
    fun `FeedEntered from IDLE emits RecordEntry and transitions to ENTERED`() {
        val actions = machine.process(entered(), 1000L)
        assertEquals(FeedState.ENTERED, machine.state)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is CountAction.RecordEntry)
    }

    @Test
    fun `FeedScrolled from ENTERED emits RecordTransition and transitions to IN_FEED`() {
        machine.process(entered(), 1000L)
        val actions = machine.process(scrolled(), 1500L)
        assertEquals(FeedState.IN_FEED, machine.state)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is CountAction.RecordTransition)
    }

    @Test
    fun `FeedScrolled from IN_FEED emits RecordTransition and stays IN_FEED`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        val actions = machine.process(scrolled(), 2000L)
        assertEquals(FeedState.IN_FEED, machine.state)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is CountAction.RecordTransition)
    }

    @Test
    fun `multiple scrolls each emit exactly one RecordTransition`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L) // → IN_FEED
        for (i in 2..5) {
            val actions = machine.process(scrolled(), (1500L + i * 500))
            assertEquals(1, actions.size)
            assertTrue(actions[0] is CountAction.RecordTransition)
        }
        assertEquals(FeedState.IN_FEED, machine.state)
    }

    // ── FeedEntered is idempotent in ENTERED/IN_FEED ──────────────────────

    @Test
    fun `FeedEntered from ENTERED returns empty (already entered)`() {
        machine.process(entered(), 1000L)
        val actions = machine.process(entered(), 1500L)
        assertTrue(actions.isEmpty())
        assertEquals(FeedState.ENTERED, machine.state)
    }

    @Test
    fun `FeedEntered from IN_FEED returns empty (already in feed)`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        val actions = machine.process(entered(), 2000L)
        assertTrue(actions.isEmpty())
        assertEquals(FeedState.IN_FEED, machine.state)
    }

    // ── FeedScrolled from IDLE (implicit entry) ───────────────────────────

    @Test
    fun `FeedScrolled from IDLE emits RecordEntry + RecordTransition and goes to IN_FEED`() {
        val actions = machine.process(scrolled(), 1000L)
        assertEquals(FeedState.IN_FEED, machine.state)
        assertEquals(2, actions.size)
        assertTrue(actions[0] is CountAction.RecordEntry)
        assertTrue(actions[1] is CountAction.RecordTransition)
    }

    // ── FeedLost ─────────────────────────────────────────────────────────

    @Test
    fun `FeedLost from IDLE returns empty`() {
        val actions = machine.process(lost, 1000L)
        assertTrue(actions.isEmpty())
        assertEquals(FeedState.IDLE, machine.state)
    }

    @Test
    fun `FeedLost from ENTERED transitions to IDLE, no actions`() {
        machine.process(entered(), 1000L)
        val actions = machine.process(lost, 1500L)
        assertTrue(actions.isEmpty())
        assertEquals(FeedState.IDLE, machine.state)
    }

    @Test
    fun `FeedLost from IN_FEED transitions to IDLE, no actions`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        val actions = machine.process(lost, 2000L)
        assertTrue(actions.isEmpty())
        assertEquals(FeedState.IDLE, machine.state)
    }

    // ── Re-entry after FeedLost ───────────────────────────────────────────

    @Test
    fun `re-entry after FeedLost produces new RecordEntry`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        machine.process(lost, 2000L)
        assertEquals(FeedState.IDLE, machine.state)

        val actions = machine.process(entered(), 3000L)
        assertEquals(FeedState.ENTERED, machine.state)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is CountAction.RecordEntry)
    }

    // ── App switch < 30 s (INTERRUPTED → resume) ──────────────────────────

    @Test
    fun `AppLeft from IN_FEED goes to INTERRUPTED`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        machine.process(appLeft, 2000L)
        assertEquals(FeedState.INTERRUPTED, machine.state)
    }

    @Test
    fun `AppReturned with short absence resumes to IN_FEED, no new entry`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        machine.process(appLeft, 2000L)
        val actions = machine.process(appReturned(SHORT_ABSENCE), 12_000L)
        assertTrue(actions.isEmpty(), "No actions expected on short-absence resume")
        assertEquals(FeedState.IN_FEED, machine.state)
    }

    @Test
    fun `after short-absence resume scrolling continues to emit RecordTransition`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        machine.process(appLeft, 2000L)
        machine.process(appReturned(SHORT_ABSENCE), 12_000L)
        val actions = machine.process(scrolled(), 13_000L)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is CountAction.RecordTransition)
    }

    // ── App switch ≥ 30 s (new session) ──────────────────────────────────

    @Test
    fun `AppReturned with long absence resets to IDLE`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        machine.process(appLeft, 2000L)
        machine.process(appReturned(LONG_ABSENCE), 62_000L)
        assertEquals(FeedState.IDLE, machine.state)
    }

    @Test
    fun `after long-absence return FeedEntered starts a new session with RecordEntry`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        machine.process(appLeft, 2000L)
        machine.process(appReturned(LONG_ABSENCE), 62_000L)
        val actions = machine.process(entered(), 63_000L)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is CountAction.RecordEntry)
    }

    // ── AppLeft from IDLE has no effect ──────────────────────────────────

    @Test
    fun `AppLeft from IDLE returns empty and state stays IDLE`() {
        val actions = machine.process(appLeft, 1000L)
        assertTrue(actions.isEmpty())
        assertEquals(FeedState.IDLE, machine.state)
    }

    // ── ServiceInterrupted path (independent from app-switch) ─────────────

    @Test
    fun `ServiceInterrupted from IDLE transitions to SERVICE_INTERRUPTED`() {
        machine.process(serviceInterrupted, 1000L)
        assertEquals(FeedState.SERVICE_INTERRUPTED, machine.state)
    }

    @Test
    fun `ServiceInterrupted from IN_FEED transitions to SERVICE_INTERRUPTED`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        machine.process(serviceInterrupted, 2000L)
        assertEquals(FeedState.SERVICE_INTERRUPTED, machine.state)
    }

    @Test
    fun `ServiceInterrupted does not interact with INTERRUPTED state`() {
        // App left → INTERRUPTED, then service interrupted → SERVICE_INTERRUPTED
        machine.process(entered(), 1000L)
        machine.process(appLeft, 1500L)
        assertEquals(FeedState.INTERRUPTED, machine.state)
        machine.process(serviceInterrupted, 2000L)
        assertEquals(FeedState.SERVICE_INTERRUPTED, machine.state)
    }

    @Test
    fun `ServiceResumed always resets to IDLE`() {
        machine.process(entered(), 1000L)
        machine.process(scrolled(), 1500L)
        machine.process(serviceInterrupted, 2000L)
        machine.process(serviceResumed, 3000L)
        assertEquals(FeedState.IDLE, machine.state)
    }

    @Test
    fun `ServiceResumed from SERVICE_INTERRUPTED then FeedEntered starts new session`() {
        machine.process(entered(), 1000L)
        machine.process(serviceInterrupted, 1500L)
        machine.process(serviceResumed, 2000L)
        val actions = machine.process(entered(), 2500L)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is CountAction.RecordEntry)
    }

    // ── INTERRUPTED resumed with FeedScrolled (implicit entry) ───────────

    @Test
    fun `FeedScrolled from INTERRUPTED emits RecordEntry + RecordTransition and goes to IN_FEED`() {
        machine.process(entered(), 1000L)
        machine.process(appLeft, 1500L)
        machine.process(appReturned(SHORT_ABSENCE), 11_500L) // resumes to IN_FEED
        // Now in IN_FEED — another AppLeft
        machine.process(appLeft, 12_000L)
        // But this time simulate coming back but then FeedScrolled fires before AppReturned
        machine.state.also { assertEquals(FeedState.INTERRUPTED, it) }
        // FeedScrolled from INTERRUPTED → implicit re-entry
        val actions = machine.process(scrolled(), 20_000L)
        assertEquals(2, actions.size)
        assertTrue(actions[0] is CountAction.RecordEntry)
        assertTrue(actions[1] is CountAction.RecordTransition)
        assertEquals(FeedState.IN_FEED, machine.state)
    }

    // ── Confidence is correctly carried ───────────────────────────────────

    @Test
    fun `confidence from FeedEntered is carried into RecordEntry`() {
        val actions = machine.process(entered(confidence = 0.80f), 1000L)
        val entry = actions[0] as CountAction.RecordEntry
        assertEquals(0.80f, entry.confidence, 0.001f)
    }

    @Test
    fun `confidence from FeedScrolled is carried into RecordTransition`() {
        machine.process(entered(confidence = 0.70f), 1000L)
        val actions = machine.process(scrolled(confidence = 0.75f), 1500L)
        val transition = actions[0] as CountAction.RecordTransition
        assertEquals(0.75f, transition.confidence, 0.001f)
    }

    // ── Platform is correctly set ──────────────────────────────────────────

    @Test
    fun `platform in CountAction matches machine platform`() {
        val actions = machine.process(entered(), 1000L)
        assertEquals(Platform.INSTAGRAM, (actions[0] as CountAction.RecordEntry).platform)
    }

    @Test
    fun `YouTube machine emits YouTube platform`() {
        val ytMachine = FeedStateMachine(Platform.YOUTUBE)
        val actions = ytMachine.process(entered(), 1000L)
        assertEquals(Platform.YOUTUBE, (actions[0] as CountAction.RecordEntry).platform)
    }

    // ── Independence of two machines ──────────────────────────────────────

    @Test
    fun `two platform machines are independent`() {
        val igMachine = FeedStateMachine(Platform.INSTAGRAM)
        val ytMachine = FeedStateMachine(Platform.YOUTUBE)

        igMachine.process(entered(), 1000L)
        igMachine.process(scrolled(), 1500L)

        // YouTube machine is still IDLE
        assertEquals(FeedState.IDLE, ytMachine.state)
        assertEquals(FeedState.IN_FEED, igMachine.state)
    }
}
