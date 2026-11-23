package com.reeltracker.accessibility

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EventSnapshotTest {

    private fun snapshot(
        eventType: Int = 1,
        sourceViewId: String? = "some_view",
        scrollY: Int = 100,
        scrollDeltaY: Int = 10,
        eventAction: Int = 0,
        packageName: String = "com.foo",
    ) = EventSnapshot(
        packageName = packageName,
        eventType = eventType,
        sourceViewId = sourceViewId,
        scrollY = scrollY,
        scrollMaxY = 1000,
        scrollDeltaY = scrollDeltaY,
        eventAction = eventAction,
        timestampMs = 1_000L,
        nodeSnapshot = NodeSnapshot.EMPTY,
    )

    // ───────────────────────── toSignature ─────────────────────────

    @Test
    fun `toSignature copies all five fields correctly`() {
        val snap = snapshot(eventType = 32, sourceViewId = "my_id", scrollY = 200,
            scrollDeltaY = 50, eventAction = 4)
        val sig = snap.toSignature()
        assertEquals(32, sig.eventType)
        assertEquals("my_id", sig.sourceViewId)
        assertEquals(200, sig.scrollY)
        assertEquals(50, sig.scrollDeltaY)
        assertEquals(4, sig.eventAction)
    }

    @Test
    fun `toSignature with null sourceViewId produces signature with null sourceViewId`() {
        val snap = snapshot(sourceViewId = null)
        assertNull(snap.toSignature().sourceViewId)
    }

    // ───────────────────────── SCROLL_DELTA_UNAVAILABLE equality ─────────────────────────

    @Test
    fun `two snapshots with SCROLL_DELTA_UNAVAILABLE produce equal signatures on delta field`() {
        val snap1 = snapshot(scrollDeltaY = EventSnapshot.SCROLL_DELTA_UNAVAILABLE,
            scrollY = 50, eventType = 8)
        val snap2 = snapshot(scrollDeltaY = EventSnapshot.SCROLL_DELTA_UNAVAILABLE,
            scrollY = 50, eventType = 8)
        // Both have the same unavailability sentinel; signatures should be equal
        assertEquals(snap1.toSignature(), snap2.toSignature())
    }

    @Test
    fun `snapshot with UNAVAILABLE delta differs from snapshot with real delta`() {
        val snap1 = snapshot(scrollDeltaY = EventSnapshot.SCROLL_DELTA_UNAVAILABLE)
        val snap2 = snapshot(scrollDeltaY = 0)
        assertNotEquals(snap1.toSignature(), snap2.toSignature())
    }

    @Test
    fun `SCROLL_DELTA_UNAVAILABLE sentinel is Int MIN_VALUE`() {
        assertEquals(Int.MIN_VALUE, EventSnapshot.SCROLL_DELTA_UNAVAILABLE)
    }

    // ───────────────────────── data class equality ─────────────────────────

    @Test
    fun `two identical snapshots are equal`() {
        val a = snapshot()
        val b = snapshot()
        assertEquals(a, b)
    }

    @Test
    fun `snapshots with different scrollY are not equal`() {
        assertNotEquals(snapshot(scrollY = 100), snapshot(scrollY = 200))
    }
}
