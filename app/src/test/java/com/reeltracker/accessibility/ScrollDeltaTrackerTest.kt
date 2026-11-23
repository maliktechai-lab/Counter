package com.reeltracker.accessibility

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScrollDeltaTrackerTest {

    private lateinit var tracker: ScrollDeltaTracker

    @BeforeEach
    fun setUp() {
        tracker = ScrollDeltaTracker()
    }

    // ───────────────────────── unavailable sentinel ─────────────────────────

    @Test
    fun `returns UNAVAILABLE when scrollY is negative`() {
        val result = tracker.delta("view_id", -1)
        assertEquals(EventSnapshot.SCROLL_DELTA_UNAVAILABLE, result)
    }

    @Test
    fun `returns UNAVAILABLE when scrollY is minus 1 on first call`() {
        assertEquals(EventSnapshot.SCROLL_DELTA_UNAVAILABLE, tracker.delta(null, -1))
    }

    // ───────────────────────── delta computation ─────────────────────────

    @Test
    fun `returns 0 on first call for a new view ID`() {
        assertEquals(0, tracker.delta("view_a", 100))
    }

    @Test
    fun `returns positive delta when scroll position increases`() {
        tracker.delta("view_a", 100)
        assertEquals(50, tracker.delta("view_a", 150))
    }

    @Test
    fun `returns negative delta when scroll position decreases (spring-back)`() {
        tracker.delta("view_a", 200)
        assertEquals(-200, tracker.delta("view_a", 0))
    }

    @Test
    fun `returns zero delta when scroll position unchanged`() {
        tracker.delta("view_a", 300)
        assertEquals(0, tracker.delta("view_a", 300))
    }

    // ───────────────────────── per-view independence ─────────────────────────

    @Test
    fun `tracks different view IDs independently`() {
        tracker.delta("view_a", 100)
        tracker.delta("view_b", 200)
        // view_a delta from 100→150 should be 50, independent of view_b
        assertEquals(50, tracker.delta("view_a", 150))
        // view_b delta from 200→210 should be 10
        assertEquals(10, tracker.delta("view_b", 210))
    }

    @Test
    fun `null sourceViewId is a valid key`() {
        tracker.delta(null, 50)
        assertEquals(100, tracker.delta(null, 150))
    }

    // ───────────────────────── reset ─────────────────────────

    @Test
    fun `reset clears all tracked state`() {
        tracker.delta("view_a", 100)
        tracker.delta("view_b", 200)
        tracker.reset()
        // After reset, first call should return 0 (no previous)
        assertEquals(0, tracker.delta("view_a", 100))
        assertEquals(0, tracker.delta("view_b", 200))
    }

    @Test
    fun `after reset unavailable still returned for negative scrollY`() {
        tracker.delta("v", 100)
        tracker.reset()
        assertEquals(EventSnapshot.SCROLL_DELTA_UNAVAILABLE, tracker.delta("v", -1))
    }
}
