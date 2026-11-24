package com.reeltracker.accessibility.statemachine

import com.reeltracker.domain.model.Platform
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Regression tests: service interruption + rapid-swipe interactions between
 * [FeedStateMachine] and [TransitionConfirmationBuffer].
 *
 * These tests verify the two components work correctly together without an
 * Android process boundary — they are pure JVM tests.
 */
class ServiceInterruptionRegressionTest {

    private fun machine()  = FeedStateMachine(Platform.INSTAGRAM)
    private fun buffer()   = TransitionConfirmationBuffer()

    private fun entered(c: Float = 0.70f) = StateMachineEvent.FeedEntered(c)
    private fun scrolled(c: Float = 0.70f) = StateMachineEvent.FeedScrolled(c)

    // ── Rapid swipes: second RecordTransition auto-confirms first ─────────────

    @Test
    fun `rapid swipe pair T1 auto-confirmed by T2 T2 unconfirmed at session end`() {
        val m = machine(); val b = buffer()

        // Enter feed
        for (a in m.process(entered(), 0L))    b.submit(a, 0, 0L)

        // Swipe 1 at t=0
        for (a in m.process(scrolled(), 0L))   b.submit(a, 0, 0L)

        // Swipe 2 at t=250 — should auto-confirm T1
        val outcomes = mutableListOf<PendingOutcome>()
        for (a in m.process(scrolled(), 250L)) outcomes += b.submit(a, 400, 250L)

        val confirmed = outcomes.filterIsInstance<PendingOutcome.Confirmed>()
        assertEquals(1, confirmed.size, "T1 should be auto-confirmed by T2 arrival")
        val transition = confirmed[0].action as? CountAction.RecordTransition
        assertNotNull(transition)

        // Session ends — T2 expires unconfirmed
        outcomes += b.drain(700L)
        val unconfirmed = outcomes.filterIsInstance<PendingOutcome.Unconfirmed>()
        assertEquals(1, unconfirmed.size, "T2 should be unconfirmed (last in session)")
    }

    @Test
    fun `service interruption during pending transition cancels candidate`() {
        val m = machine(); val b = buffer()

        m.process(entered(), 0L).forEach { b.submit(it, 0, 0L) }
        m.process(scrolled(), 100L).forEach { b.submit(it, 0, 100L) }

        // Service interrupted — machine moves to SERVICE_INTERRUPTED, buffer cancels
        m.process(StateMachineEvent.ServiceInterrupted, 200L)
        val cancelled = b.cancel()

        assertNotNull(cancelled)
        assertTrue(cancelled is PendingOutcome.Unconfirmed)
        assertEquals(1, b.unconfirmedCount)

        // After ServiceResumed, state machine resets to IDLE
        m.process(StateMachineEvent.ServiceResumed, 300L)
        assertEquals(FeedState.IDLE, m.state)
    }

    @Test
    fun `service interrupt and resume then re-entry produces fresh RecordEntry`() {
        val m = machine(); val b = buffer()

        m.process(entered(), 0L).forEach  { b.submit(it, 0, 0L) }
        m.process(scrolled(), 100L).forEach { b.submit(it, 0, 100L) }
        b.cancel()  // simulate onInterrupt

        m.process(StateMachineEvent.ServiceInterrupted, 200L)
        m.process(StateMachineEvent.ServiceResumed, 300L)

        val reEntryActions = m.process(entered(), 400L)
        assertEquals(1, reEntryActions.size)
        assertTrue(reEntryActions[0] is CountAction.RecordEntry)

        val outcomes = reEntryActions.map { b.submit(it, 0, 400L) }.flatten()
        assertEquals(1, outcomes.filterIsInstance<PendingOutcome.Confirmed>().size)
    }

    @Test
    fun `app switch and return within 30s resumes without new entry`() {
        val m = machine(); val b = buffer()

        m.process(entered(), 0L).forEach { b.submit(it, 0, 0L) }
        m.process(scrolled(), 500L).forEach { b.submit(it, 0, 500L) }

        m.process(StateMachineEvent.AppLeft, 1000L)
        b.cancel() // pending candidate dropped when app left

        // Short absence
        m.process(StateMachineEvent.AppReturned(10_000L), 11_000L)
        assertEquals(FeedState.IN_FEED, m.state)

        // Scroll resumes — RecordTransition emitted, no new entry
        val actions = m.process(scrolled(), 11_500L)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is CountAction.RecordTransition)
    }

    @Test
    fun `app switch longer than 30s resets to IDLE and next entry starts new session`() {
        val m = machine(); val b = buffer()

        m.process(entered(), 0L).forEach { b.submit(it, 0, 0L) }
        m.process(scrolled(), 500L).forEach { b.submit(it, 0, 500L) }

        m.process(StateMachineEvent.AppLeft, 1000L)
        b.cancel()

        // Long absence
        m.process(StateMachineEvent.AppReturned(60_000L), 61_000L)
        assertEquals(FeedState.IDLE, m.state)

        val actions = m.process(entered(), 61_500L)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is CountAction.RecordEntry, "New session should start with RecordEntry")
    }
}
