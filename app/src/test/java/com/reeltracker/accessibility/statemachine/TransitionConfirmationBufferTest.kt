package com.reeltracker.accessibility.statemachine

import com.reeltracker.domain.model.Platform
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [TransitionConfirmationBuffer].
 *
 * Key design contracts verified:
 * - RecordEntry bypasses the buffer (always Confirmed immediately).
 * - RecordTransition is PENDING until corroborated or expired.
 * - No event within GESTURE_SEPARATION_MIN_MS can corroborate a candidate.
 * - TYPE_WINDOW_CONTENT_CHANGED requires a feed container source AND ≥50 px scroll advance.
 * - Two rapid genuine RecordTransitions: T1 auto-confirmed by T2 arrival; T2 pending.
 * - Expired-without-corroboration → Unconfirmed; unconfirmedCount incremented.
 * - cancel() → Unconfirmed; unconfirmedCount incremented.
 */
class TransitionConfirmationBufferTest {

    private lateinit var buffer: TransitionConfirmationBuffer
    private var now = 0L

    private val TYPE_VIEW_SCROLLED          = 0x00000008
    private val TYPE_WINDOW_CONTENT_CHANGED = 0x00000800
    private val TYPE_WINDOW_STATE_CHANGED   = 0x00000020

    private fun entry(platform: Platform = Platform.INSTAGRAM, t: Long = now) =
        CountAction.RecordEntry(platform, t, 0.70f)

    private fun transition(platform: Platform = Platform.INSTAGRAM, t: Long = now) =
        CountAction.RecordTransition(platform, t, 0.70f)

    @BeforeEach
    fun setUp() {
        now = 0L
        buffer = TransitionConfirmationBuffer(clock = { now })
    }

    // ── RecordEntry bypasses buffer ───────────────────────────────────────────

    @Test
    fun `RecordEntry is immediately Confirmed`() {
        val outcomes = buffer.submit(entry(), nowMs = 0L)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes[0] is PendingOutcome.Confirmed)
    }

    @Test
    fun `RecordEntry does not affect unconfirmedCount`() {
        buffer.submit(entry(), nowMs = 0L)
        assertEquals(0, buffer.unconfirmedCount)
    }

    // ── Single RecordTransition — expiry paths ────────────────────────────────

    @Test
    fun `single RecordTransition is pending, drain before window returns empty`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        val drained = buffer.drain(nowMs = 100L)   // 100 ms < 400 ms window
        assertTrue(drained.isEmpty())
    }

    @Test
    fun `single RecordTransition expiry without corroboration yields Unconfirmed`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        val drained = buffer.drain(nowMs = 400L)   // exactly at window boundary
        assertEquals(1, drained.size)
        assertTrue(drained[0] is PendingOutcome.Unconfirmed)
        assertEquals(1, buffer.unconfirmedCount)
    }

    @Test
    fun `unconfirmedCount increments on discard, not on confirmation`() {
        // Confirm via second transition
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        buffer.submit(transition(), scrollY = 0, nowMs = 250L) // auto-confirms first
        assertEquals(0, buffer.unconfirmedCount)

        // Expire second without corroboration
        buffer.drain(nowMs = 700L)
        assertEquals(1, buffer.unconfirmedCount)
    }

    // ── Same-gesture self-corroboration prevention (< GESTURE_SEPARATION_MIN_MS) ──

    @Test
    fun `TYPE_VIEW_SCROLLED within gesture window (80ms) does not corroborate`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        buffer.tryCorroborate(
            eventType        = TYPE_VIEW_SCROLLED,
            sourceViewId     = "reel_recycler_view",
            scrollY          = 120,
            eventTimestampMs = 80L,   // 80 ms < 150 ms GESTURE_SEPARATION_MIN_MS
            isFeedContainer  = true,
        )
        val drained = buffer.drain(nowMs = 400L)
        assertTrue(drained[0] is PendingOutcome.Unconfirmed)
    }

    @Test
    fun `unsuccessful swipe spring-back within 100ms does not corroborate`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        // Spring-back: backward scroll within gesture window
        buffer.tryCorroborate(
            eventType        = TYPE_VIEW_SCROLLED,
            sourceViewId     = "reel_recycler_view",
            scrollY          = -50,
            eventTimestampMs = 60L,   // 60 ms < 150 ms
            isFeedContainer  = true,
        )
        val drained = buffer.drain(nowMs = 400L)
        assertTrue(drained[0] is PendingOutcome.Unconfirmed)
    }

    @Test
    fun `multiple callbacks from one gesture WINDOW_CONTENT_CHANGED at 30ms does not corroborate`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        // Same gesture: WINDOW_CONTENT_CHANGED from same container 30 ms later
        buffer.tryCorroborate(
            eventType        = TYPE_WINDOW_CONTENT_CHANGED,
            sourceViewId     = "reel_recycler_view",
            scrollY          = 400,
            eventTimestampMs = 30L,   // 30 ms < 150 ms
            isFeedContainer  = true,
        )
        val drained = buffer.drain(nowMs = 400L)
        assertTrue(drained[0] is PendingOutcome.Unconfirmed)
    }

    // ── TYPE_VIEW_SCROLLED corroboration (gap ≥ 150 ms) ──────────────────────

    @Test
    fun `TYPE_VIEW_SCROLLED from feed container at 200ms corroborates candidate`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        buffer.tryCorroborate(
            eventType        = TYPE_VIEW_SCROLLED,
            sourceViewId     = "reel_recycler_view",
            scrollY          = 200,
            eventTimestampMs = 200L,  // 200 ms ≥ 150 ms
            isFeedContainer  = true,
        )
        val drained = buffer.drain(nowMs = 400L)
        assertTrue(drained[0] is PendingOutcome.Confirmed)
        assertEquals(0, buffer.unconfirmedCount)
    }

    @Test
    fun `TYPE_VIEW_SCROLLED from non-feed source at 200ms does not corroborate`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        buffer.tryCorroborate(
            eventType        = TYPE_VIEW_SCROLLED,
            sourceViewId     = "some_other_view",
            scrollY          = 200,
            eventTimestampMs = 200L,
            isFeedContainer  = false,   // not a feed container
        )
        val drained = buffer.drain(nowMs = 400L)
        assertTrue(drained[0] is PendingOutcome.Unconfirmed)
    }

    // ── TYPE_WINDOW_CONTENT_CHANGED corroboration ─────────────────────────────

    @Test
    fun `WINDOW_CONTENT_CHANGED from feed container with 450px advance at 200ms corroborates`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        buffer.tryCorroborate(
            eventType        = TYPE_WINDOW_CONTENT_CHANGED,
            sourceViewId     = "reel_recycler_view",
            scrollY          = 450,          // 450 − 0 = 450 ≥ 50
            eventTimestampMs = 200L,
            isFeedContainer  = true,
        )
        val drained = buffer.drain(nowMs = 400L)
        assertTrue(drained[0] is PendingOutcome.Confirmed)
    }

    @Test
    fun `WINDOW_CONTENT_CHANGED with 10px advance does not corroborate (below SCROLL_ADVANCE_MIN_PX)`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        buffer.tryCorroborate(
            eventType        = TYPE_WINDOW_CONTENT_CHANGED,
            sourceViewId     = "reel_recycler_view",
            scrollY          = 10,           // 10 − 0 = 10 < 50
            eventTimestampMs = 200L,
            isFeedContainer  = true,
        )
        val drained = buffer.drain(nowMs = 400L)
        assertTrue(drained[0] is PendingOutcome.Unconfirmed)
    }

    @Test
    fun `WINDOW_CONTENT_CHANGED from wrong source does not corroborate`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        buffer.tryCorroborate(
            eventType        = TYPE_WINDOW_CONTENT_CHANGED,
            sourceViewId     = "some_unrelated_view",
            scrollY          = 450,
            eventTimestampMs = 200L,
            isFeedContainer  = false,
        )
        val drained = buffer.drain(nowMs = 400L)
        assertTrue(drained[0] is PendingOutcome.Unconfirmed)
    }

    // ── Auto-confirm path: second RecordTransition confirms first ─────────────

    @Test
    fun `two rapid transitions T1 auto-confirmed by T2 arrival`() {
        val outcomes1 = buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        assertTrue(outcomes1.isEmpty(), "T1 enters pending, no immediate outcome")

        val outcomes2 = buffer.submit(transition(), scrollY = 400, nowMs = 250L)
        assertEquals(1, outcomes2.size)
        assertTrue(outcomes2[0] is PendingOutcome.Confirmed, "T1 auto-confirmed by T2")
        assertEquals(0, buffer.unconfirmedCount)
    }

    @Test
    fun `T2 pending after auto-confirm is unconfirmed if no further events`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        buffer.submit(transition(), scrollY = 400, nowMs = 250L) // confirms T1, T2 pending

        val drained = buffer.drain(nowMs = 700L)   // 250 + 400 = 650 ms < 700
        assertEquals(1, drained.size)
        assertTrue(drained[0] is PendingOutcome.Unconfirmed, "T2 unconfirmed — last in session")
        assertEquals(1, buffer.unconfirmedCount)
    }

    @Test
    fun `three-swipe chain T1 confirmed by T2 T2 confirmed by T3 T3 pending`() {
        buffer.submit(transition(), scrollY = 0,   nowMs = 0L)
        buffer.submit(transition(), scrollY = 400, nowMs = 300L) // T1 confirmed
        buffer.submit(transition(), scrollY = 800, nowMs = 600L) // T2 confirmed

        val drained = buffer.drain(nowMs = 1100L)
        assertEquals(1, drained.size)
        assertTrue(drained[0] is PendingOutcome.Unconfirmed) // T3 unconfirmed
        assertEquals(1, buffer.unconfirmedCount)
    }

    // ── cancel() ──────────────────────────────────────────────────────────────

    @Test
    fun `cancel while pending returns Unconfirmed and increments count`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        val result = buffer.cancel()
        assertNotNull(result)
        assertTrue(result is PendingOutcome.Unconfirmed)
        assertEquals(1, buffer.unconfirmedCount)
    }

    @Test
    fun `cancel with nothing pending returns null`() {
        val result = buffer.cancel()
        assertNull(result)
        assertEquals(0, buffer.unconfirmedCount)
    }

    @Test
    fun `cancel does not affect RecordEntry`() {
        // RecordEntry bypasses; cancel should have no pending state to cancel
        buffer.submit(entry(), nowMs = 0L)
        val result = buffer.cancel()
        assertNull(result)
    }

    // ── Service interruption during pending ───────────────────────────────────

    @Test
    fun `service interruption discards pending candidate`() {
        buffer.submit(transition(), scrollY = 0, nowMs = 0L)
        val cancelled = buffer.cancel()  // onInterrupt calls cancel()
        assertTrue(cancelled is PendingOutcome.Unconfirmed)
        // After cancel, drain should return nothing
        val drained = buffer.drain(nowMs = 500L)
        assertTrue(drained.isEmpty())
    }
}
