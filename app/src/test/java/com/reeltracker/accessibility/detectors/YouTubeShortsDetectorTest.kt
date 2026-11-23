package com.reeltracker.accessibility.detectors

import com.reeltracker.accessibility.EventSnapshot
import com.reeltracker.accessibility.NodeSnapshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class YouTubeShortsDetectorTest {

    private val detector = YouTubeShortsDetector()

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
        packageName = "com.google.android.youtube",
        eventType = eventType,
        sourceViewId = sourceViewId,
        scrollY = scrollY,
        scrollMaxY = 1000,
        scrollDeltaY = scrollDeltaY,
        eventAction = 0,
        timestampMs = 1000L,
        nodeSnapshot = NodeSnapshot(presentViewIds = viewIds, shortsNavTabSelected = shortsNavTabSelected),
    )

    // ── Group A alone — below threshold ──────────────────────────────────────

    @Test
    fun `primary ID alone = score 0_50 = Unknown`() {
        val result = detector.detect(snapshot(viewIds = setOf("shorts_container")))
        assertTrue(result is DetectionResult.Unknown)
        assertEquals(0.50f, (result as DetectionResult.Unknown).confidence, 0.001f)
    }

    @Test
    fun `secondary ID alone = score 0_25 = Unknown`() {
        val result = detector.detect(snapshot(viewIds = setOf("shorts_recycler_view")))
        assertTrue(result is DetectionResult.Unknown)
        assertEquals(0.25f, (result as DetectionResult.Unknown).confidence, 0.001f)
    }

    // ── NAV_TAB_SHORTS brings primary to threshold ────────────────────────────

    @Test
    fun `primary + nav_tab_selected = score 0_60 = InFeed (exactly at threshold)`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("shorts_container"),
            shortsNavTabSelected = true,
        ))
        assertTrue(result is DetectionResult.InFeed)
        assertEquals(0.60f, (result as DetectionResult.InFeed).confidence, 0.001f)
    }

    @Test
    fun `secondary + nav_tab_selected = score 0_35 = Unknown`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("shorts_recycler_view"),
            shortsNavTabSelected = true,
        ))
        assertTrue(result is DetectionResult.Unknown)
        assertEquals(0.35f, (result as DetectionResult.Unknown).confidence, 0.001f)
    }

    // ── InFeed via scroll ──────────────────────────────────────────────────────

    @Test
    fun `primary + scroll from container = score 0_70 = InFeed isTransition=true`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("shorts_container"),
            eventType = TYPE_VIEW_SCROLLED,
            sourceViewId = "shorts_container",
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
            viewIds = setOf("reel_player_page"),
            eventType = TYPE_VIEW_SCROLLED,
            sourceViewId = "reel_player_page",
            scrollDeltaY = 500,
        ))
        assertTrue(result is DetectionResult.InFeed)
        assertEquals(0.80f, (result as DetectionResult.InFeed).confidence, 0.001f)
    }

    @Test
    fun `primary + scroll + UNAVAILABLE delta = score 0_70 = InFeed (no bonus)`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("shorts_container"),
            eventType = TYPE_VIEW_SCROLLED,
            sourceViewId = "shorts_container",
            scrollDeltaY = EventSnapshot.SCROLL_DELTA_UNAVAILABLE,
        ))
        assertTrue(result is DetectionResult.InFeed)
        assertEquals(0.70f, (result as DetectionResult.InFeed).confidence, 0.001f)
    }

    // ── Shelf exclusion ───────────────────────────────────────────────────────

    @Test
    fun `shelf-only IDs (no player) = NotInFeed`() {
        val result = detector.detect(snapshot(viewIds = setOf("shorts_shelf_header")))
        assertTrue(result is DetectionResult.NotInFeed)
    }

    @Test
    fun `shorts_shelf_pivot alone = NotInFeed`() {
        val result = detector.detect(snapshot(viewIds = setOf("shorts_shelf_pivot")))
        assertTrue(result is DetectionResult.NotInFeed)
    }

    @Test
    fun `shelf + player IDs = InFeed (player wins over shelf)`() {
        // Player overrides shelf veto
        val result = detector.detect(snapshot(
            viewIds = setOf("shorts_shelf_header", "shorts_container"),
            shortsNavTabSelected = true,
        ))
        // shorts_container is a primary → 0.50 + nav_tab 0.10 = 0.60 ≥ threshold
        assertTrue(result is DetectionResult.InFeed)
    }

    // ── NAV_TAB_SHORTS signal ─────────────────────────────────────────────────

    @Test
    fun `nav_tab not selected does not add signal`() {
        val result = detector.detect(snapshot(
            viewIds = setOf("shorts_container"),
            shortsNavTabSelected = false,
        ))
        // primary only = 0.50 → Unknown
        assertTrue(result is DetectionResult.Unknown)
    }

    // ── no signals ────────────────────────────────────────────────────────────

    @Test
    fun `no known IDs = score 0_00 = Unknown`() {
        val result = detector.detect(snapshot(viewIds = emptySet()))
        assertTrue(result is DetectionResult.Unknown)
        assertEquals(0.0f, (result as DetectionResult.Unknown).confidence, 0.001f)
    }
}
