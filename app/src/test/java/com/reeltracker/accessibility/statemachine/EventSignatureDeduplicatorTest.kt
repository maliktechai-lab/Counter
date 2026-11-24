package com.reeltracker.accessibility.statemachine

import com.reeltracker.accessibility.EventSnapshot
import com.reeltracker.accessibility.NodeSnapshot
import com.reeltracker.domain.model.Platform
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EventSignatureDeduplicator].
 *
 * Key contracts verified:
 * - First event always passes.
 * - Same full signature within 120 ms → second blocked.
 * - Same full signature after 120 ms → second passes.
 * - Different scrollY within 90 ms → both pass (critical: distinct gestures).
 * - API 26 simulation: SCROLL_DELTA_UNAVAILABLE on both sides → same field value.
 * - Platforms are independent.
 */
class EventSignatureDeduplicatorTest {

    private lateinit var dedup: EventSignatureDeduplicator

    private fun snapshot(
        platform: Platform = Platform.INSTAGRAM,
        eventType: Int = 8,
        sourceViewId: String? = "reel_recycler_view",
        scrollY: Int = 100,
        scrollDeltaY: Int = 0,
        eventAction: Int = 0,
        ts: Long,
    ) = EventSnapshot(
        packageName  = platform.packageName,
        eventType    = eventType,
        sourceViewId = sourceViewId,
        scrollY      = scrollY,
        scrollMaxY   = -1,
        scrollDeltaY = scrollDeltaY,
        eventAction  = eventAction,
        timestampMs  = ts,
        nodeSnapshot = NodeSnapshot.EMPTY,
    )

    @BeforeEach
    fun setUp() {
        dedup = EventSignatureDeduplicator()
    }

    @Test
    fun `first event always passes`() {
        val s = snapshot(ts = 0L)
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, s))
    }

    @Test
    fun `same full signature within 120ms is blocked`() {
        dedup.shouldPass(Platform.INSTAGRAM, snapshot(ts = 0L))
        val blocked = dedup.shouldPass(Platform.INSTAGRAM, snapshot(ts = 100L)) // 100 < 120
        assertFalse(blocked)
    }

    @Test
    fun `same full signature after 120ms passes`() {
        dedup.shouldPass(Platform.INSTAGRAM, snapshot(ts = 0L))
        val passed = dedup.shouldPass(Platform.INSTAGRAM, snapshot(ts = 120L)) // at boundary
        assertTrue(passed)
    }

    @Test
    fun `different scrollY within 90ms both pass (distinct gestures)`() {
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(scrollY = 0,   ts = 0L)))
        // Different scrollY → different signature → passes
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(scrollY = 400, ts = 90L)))
    }

    @Test
    fun `different scrollDeltaY within 90ms both pass`() {
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(scrollDeltaY = 0,   ts = 0L)))
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(scrollDeltaY = 400, ts = 90L)))
    }

    @Test
    fun `different eventType within 90ms both pass`() {
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(eventType = 8,    ts = 0L)))
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(eventType = 2048, ts = 90L)))
    }

    @Test
    fun `two genuine rapid swipes from container with different scrollY both pass`() {
        // Swipe 1: scrollY=0
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(scrollY = 0,   ts = 0L)))
        // Swipe 2: scrollY=400 (different position) at t=90ms
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(scrollY = 400, ts = 90L)))
    }

    @Test
    fun `API26 simulation both UNAVAILABLE same scrollY within 120ms second is blocked (TC-13 conservative)`() {
        // Expected conservative behaviour, not a bug — documented TC-13
        val unavailable = EventSnapshot.SCROLL_DELTA_UNAVAILABLE
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM,
            snapshot(scrollY = 0, scrollDeltaY = unavailable, ts = 0L)))
        // Same scrollY=0, same UNAVAILABLE delta → same full signature → blocked
        assertFalse(dedup.shouldPass(Platform.INSTAGRAM,
            snapshot(scrollY = 0, scrollDeltaY = unavailable, ts = 90L)),
            "Expected conservative block when API-26 delta unavailable and scrollY recycled (TC-13)"
        )
    }

    @Test
    fun `two genuine rapid swipes from recycled scrollY both UNAVAILABLE delta second blocked (TC-13 limitation)`() {
        val unavailable = EventSnapshot.SCROLL_DELTA_UNAVAILABLE
        // On API 26 with scrollY recycling, two genuine swipes look identical → second blocked.
        // This test asserts the documented limitation, not an error.
        dedup.shouldPass(Platform.INSTAGRAM, snapshot(scrollY = 0, scrollDeltaY = unavailable, ts = 0L))
        val blocked = dedup.shouldPass(Platform.INSTAGRAM,
            snapshot(scrollY = 0, scrollDeltaY = unavailable, ts = 90L))
        assertFalse(blocked, "TC-13 known limitation: second genuine swipe blocked due to recycled scrollY and unavailable delta")
    }

    @Test
    fun `platforms are independent`() {
        // Instagram passes
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(platform = Platform.INSTAGRAM, ts = 0L)))
        // YouTube with same signature at same time passes (different platform bucket)
        assertTrue(dedup.shouldPass(Platform.YOUTUBE,
            snapshot(platform = Platform.YOUTUBE, ts = 10L)))
    }

    @Test
    fun `null sourceViewId with identical remaining fields within 120ms second blocked`() {
        val s1 = snapshot(sourceViewId = null, ts = 0L)
        val s2 = snapshot(sourceViewId = null, ts = 50L)
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, s1))
        assertFalse(dedup.shouldPass(Platform.INSTAGRAM, s2))
    }

    @Test
    fun `reset clears state so next event from same platform passes`() {
        dedup.shouldPass(Platform.INSTAGRAM, snapshot(ts = 0L))
        dedup.reset(Platform.INSTAGRAM)
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(ts = 50L)))
    }

    @Test
    fun `reset all clears all platforms`() {
        dedup.shouldPass(Platform.INSTAGRAM, snapshot(platform = Platform.INSTAGRAM, ts = 0L))
        dedup.shouldPass(Platform.YOUTUBE,   snapshot(platform = Platform.YOUTUBE,   ts = 0L))
        dedup.reset()
        assertTrue(dedup.shouldPass(Platform.INSTAGRAM, snapshot(platform = Platform.INSTAGRAM, ts = 50L)))
        assertTrue(dedup.shouldPass(Platform.YOUTUBE,   snapshot(platform = Platform.YOUTUBE,   ts = 50L)))
    }
}
