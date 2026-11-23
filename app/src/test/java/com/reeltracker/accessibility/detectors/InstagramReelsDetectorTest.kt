package com.reeltracker.accessibility.detectors

import com.reeltracker.accessibility.EventSnapshot
import com.reeltracker.accessibility.NodeSnapshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InstagramReelsDetectorTest {

    private val detector = InstagramReelsDetector()

    // TYPE_VIEW_SCROLLED = 8 (android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED)
    private val TYPE_VIEW_SCROLLED = 8
    private val TYPE_WINDOW_CONTENT_CHANGED = 2048

    private fun snapshot(
        viewIds: Set<String> = emptySet(),
        eventType: Int = TYPE_WINDOW_CONTENT_CHANGED,
        sourceViewId: String? = null,
        scrollDeltaY: Int = 0,
        scrollY: Int = 100,
        shortsNavTabSelected: Boolean = false,
    ) = EventSnapshot(
        packageName = "com.instagram.android",
        eventType = eventType,
        sourceViewId = sourceViewId,
        scrollY = scrollY,
        scrollMaxY = 1000,
        scrollDeltaY = scrollDeltaY,
        eventAction = 0,
        timestampMs = 1000L,
        nodeSnapshot = NodeSnapshot(presentViewIds = viewIds, shortsNavTabSelected = shortsNavTabSelected),
    )

    // ── Group A alone — below threshold ─────────────────────────────────────

    @Test
    fun `primary ID alone = score 0_50 = Unknown`() {
        val result = detector.detect(snapshot(viewIds = setOf("reel_recycler_view")))
        assertTrue(result is DetectionResult.Unknown)
        assertEquals(0.50f, (result as DetectionResult.Unknown).confidence, 0.001f)
    }

    @Test
    fun `secondary ID alone = score 0_25 = Unknown`() {
        val result = detector.detect(snapshot(viewIds = setOf("reels_tray_recycler_view")))
        assertTrue(result is DetectionResult.Unknown)
        assertEquals(0.25f, (result as DetectionResult.Unknown).confidence, 0.001f)
    }

    @Test
    fun `primary + secondary = score 0_50 = Unknown (capped at primary)`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("reel_recycler_view", "reels_tray_recycler_view"),
        ))
        assertTrue(result is DetectionResult.Unknown)
        assertEquals(0.50f, (result as DetectionResult.Unknown).confidence, 0.001f)
    }

    @Test
    fun `all IDs absent = score 0_00 = Unknown`() {
        val result = detector.detect(snapshot(viewIds = emptySet()))
        assertTrue(result is DetectionResult.Unknown)
        assertEquals(0.0f, (result as DetectionResult.Unknown).confidence, 0.001f)
    }

    // ── InFeed — at or above threshold ──────────────────────────────────────

    @Test
    fun `primary + scroll from container = score 0_70 = InFeed isTransition=true`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("reel_recycler_view"),
            eventType = TYPE_VIEW_SCROLLED,
            sourceViewId = "reel_recycler_view",
            scrollDeltaY = EventSnapshot.SCROLL_DELTA_UNAVAILABLE, // no nonzero-delta bonus
        ))
        assertTrue(result is DetectionResult.InFeed)
        result as DetectionResult.InFeed
        assertEquals(0.70f, result.confidence, 0.001f)
        assertTrue(result.isTransition)
    }

    @Test
    fun `primary + scroll + nonzero delta = score 0_80 = InFeed`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("reel_recycler_view"),
            eventType = TYPE_VIEW_SCROLLED,
            sourceViewId = "reel_recycler_view",
            scrollDeltaY = 400,
        ))
        assertTrue(result is DetectionResult.InFeed)
        assertEquals(0.80f, (result as DetectionResult.InFeed).confidence, 0.001f)
    }

    @Test
    fun `primary + scroll + UNAVAILABLE delta = score 0_70 = InFeed (no bonus for unavailable)`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("reel_recycler_view"),
            eventType = TYPE_VIEW_SCROLLED,
            sourceViewId = "reel_recycler_view",
            scrollDeltaY = EventSnapshot.SCROLL_DELTA_UNAVAILABLE,
        ))
        assertTrue(result is DetectionResult.InFeed)
        assertEquals(0.70f, (result as DetectionResult.InFeed).confidence, 0.001f)
    }

    @Test
    fun `primary + secondary + scroll = score 0_70 = InFeed`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("reel_recycler_view", "reels_tray_recycler_view"),
            eventType = TYPE_VIEW_SCROLLED,
            sourceViewId = "reel_recycler_view",
            scrollDeltaY = 0,
        ))
        assertTrue(result is DetectionResult.InFeed)
        assertEquals(0.70f, (result as DetectionResult.InFeed).confidence, 0.001f)
    }

    // ── Exclusion ID ──────────────────────────────────────────────────────────

    @Test
    fun `exclusion ID present = NotInFeed regardless of other signals`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("feed_recycler", "reel_recycler_view"),
            eventType = TYPE_VIEW_SCROLLED,
            sourceViewId = "reel_recycler_view",
        ))
        assertTrue(result is DetectionResult.NotInFeed)
    }

    @Test
    fun `clips_tray exclusion ID = NotInFeed`() {
        val result = detector.detect(snapshot(viewIds = setOf("clips_tray")))
        assertTrue(result is DetectionResult.NotInFeed)
    }

    // ── alternate primary ID ──────────────────────────────────────────────────

    @Test
    fun `clips_recycler_view is a valid primary ID`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("clips_recycler_view"),
            eventType = TYPE_VIEW_SCROLLED,
            sourceViewId = "clips_recycler_view",
            scrollDeltaY = 200,
        ))
        assertTrue(result is DetectionResult.InFeed)
    }

    // ── isTransition flag ─────────────────────────────────────────────────────

    @Test
    fun `isTransition is false when no scroll from container`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("reel_recycler_view"),
            eventType = TYPE_WINDOW_CONTENT_CHANGED,
            sourceViewId = null,
        ))
        // score = 0.50 → Unknown; but check there's no isTransition
        assertTrue(result is DetectionResult.Unknown)
    }

    @Test
    fun `isTransition is false when scrolled from non-container source`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("reel_recycler_view"),
            eventType = TYPE_VIEW_SCROLLED,
            sourceViewId = "some_other_view",  // not a feed container
        ))
        // primary alone = 0.50 → Unknown
        assertTrue(result is DetectionResult.Unknown)
    }
}
