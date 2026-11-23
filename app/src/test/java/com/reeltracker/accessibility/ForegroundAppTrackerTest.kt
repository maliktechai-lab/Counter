package com.reeltracker.accessibility

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForegroundAppTrackerTest {

    private lateinit var tracker: ForegroundAppTracker

    @BeforeEach
    fun setUp() {
        tracker = ForegroundAppTracker(clock = { 1000L })
    }

    // ── update / package change ──────────────────────────────────────────────

    @Test
    fun `update returns true on first call (null to package)`() {
        assertTrue(tracker.update("com.instagram.android", 1000L))
    }

    @Test
    fun `update returns true when package changes`() {
        tracker.update("com.instagram.android", 1000L)
        assertTrue(tracker.update("com.google.android.youtube", 2000L))
    }

    @Test
    fun `update returns false when same package is reported again`() {
        tracker.update("com.instagram.android", 1000L)
        assertFalse(tracker.update("com.instagram.android", 2000L))
    }

    // ── currentPackage ──────────────────────────────────────────────────────

    @Test
    fun `currentPackage is null before any update`() {
        assertNull(tracker.currentPackage())
    }

    @Test
    fun `currentPackage returns last reported package`() {
        tracker.update("com.instagram.android", 1000L)
        assertEquals("com.instagram.android", tracker.currentPackage())
    }

    @Test
    fun `currentPackage reflects latest update`() {
        tracker.update("com.instagram.android", 1000L)
        tracker.update("com.google.android.youtube", 2000L)
        assertEquals("com.google.android.youtube", tracker.currentPackage())
    }

    // ── foregroundDurationMs ──────────────────────────────────────────────

    @Test
    fun `foregroundDurationMs is 0 before any update`() {
        assertEquals(0L, tracker.foregroundDurationMs(5000L))
    }

    @Test
    fun `foregroundDurationMs measures time since last package change`() {
        tracker.update("com.instagram.android", 1000L)
        assertEquals(4000L, tracker.foregroundDurationMs(5000L))
    }

    @Test
    fun `foregroundDurationMs resets when package changes`() {
        tracker.update("com.instagram.android", 1000L)
        tracker.update("com.google.android.youtube", 3000L)
        assertEquals(2000L, tracker.foregroundDurationMs(5000L))
    }

    @Test
    fun `foregroundDurationMs does not reset when same package reported again`() {
        tracker.update("com.instagram.android", 1000L)
        tracker.update("com.instagram.android", 2000L) // same, no reset
        assertEquals(4000L, tracker.foregroundDurationMs(5000L))
    }

    @Test
    fun `foregroundDurationMs returns 0 when nowMs is before foregroundSince`() {
        tracker.update("com.instagram.android", 5000L)
        assertEquals(0L, tracker.foregroundDurationMs(3000L))
    }

    // ── isTargetAppInForeground ──────────────────────────────────────────────

    @Test
    fun `isTargetAppInForeground is false before any update`() {
        assertFalse(tracker.isTargetAppInForeground())
    }

    @Test
    fun `isTargetAppInForeground is true for Instagram`() {
        tracker.update("com.instagram.android", 1000L)
        assertTrue(tracker.isTargetAppInForeground())
    }

    @Test
    fun `isTargetAppInForeground is true for YouTube`() {
        tracker.update("com.google.android.youtube", 1000L)
        assertTrue(tracker.isTargetAppInForeground())
    }

    @Test
    fun `isTargetAppInForeground is false for unrelated app`() {
        tracker.update("com.example.otherap", 1000L)
        assertFalse(tracker.isTargetAppInForeground())
    }

    @Test
    fun `isTargetAppInForeground transitions to false when user leaves target app`() {
        tracker.update("com.instagram.android", 1000L)
        tracker.update("com.example.browser", 2000L)
        assertFalse(tracker.isTargetAppInForeground())
    }

    // ── reset ──────────────────────────────────────────────────────────────

    @Test
    fun `reset clears current package`() {
        tracker.update("com.instagram.android", 1000L)
        tracker.reset()
        assertNull(tracker.currentPackage())
    }

    @Test
    fun `reset clears foreground duration`() {
        tracker.update("com.instagram.android", 1000L)
        tracker.reset()
        assertEquals(0L, tracker.foregroundDurationMs(5000L))
    }

    @Test
    fun `after reset isTargetAppInForeground is false`() {
        tracker.update("com.instagram.android", 1000L)
        tracker.reset()
        assertFalse(tracker.isTargetAppInForeground())
    }

    @Test
    fun `after reset first update is treated as a package change`() {
        tracker.update("com.instagram.android", 1000L)
        tracker.reset()
        assertTrue(tracker.update("com.instagram.android", 2000L))
    }
}
