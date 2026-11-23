package com.reeltracker.accessibility.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SignalGroupTest {

    private fun score(vararg signals: DetectionSignal) =
        SignalGroup.computeScore(signals.toSet())

    private fun scoreExcluded(vararg signals: DetectionSignal) =
        SignalGroup.computeScoreWithExclusion(signals.toSet(), hasExclusion = true)

    // ── Group A ──────────────────────────────────────────────────────────────

    @Test
    fun `primary alone = 0_50`() =
        assertEquals(0.50f, score(DetectionSignal.PRIMARY_VIEW_ID), 0.001f)

    @Test
    fun `secondary alone = 0_25`() =
        assertEquals(0.25f, score(DetectionSignal.SECONDARY_VIEW_ID), 0.001f)

    @Test
    fun `primary + secondary = 0_50 (capped, not 0_75)`() =
        assertEquals(0.50f, score(DetectionSignal.PRIMARY_VIEW_ID, DetectionSignal.SECONDARY_VIEW_ID), 0.001f)

    @Test
    fun `no group A signals = 0_00`() =
        assertEquals(0.0f, score(), 0.001f)

    // ── Group B ──────────────────────────────────────────────────────────────

    @Test
    fun `scroll from container alone = 0_20`() =
        assertEquals(0.20f, score(DetectionSignal.SCROLL_FROM_CONTAINER), 0.001f)

    @Test
    fun `nonzero delta alone = 0_10`() =
        assertEquals(0.10f, score(DetectionSignal.NONZERO_SCROLL_DELTA), 0.001f)

    @Test
    fun `scroll from container + nonzero delta = 0_30`() =
        assertEquals(0.30f, score(DetectionSignal.SCROLL_FROM_CONTAINER, DetectionSignal.NONZERO_SCROLL_DELTA), 0.001f)

    @Test
    fun `scroll from container + zero delta (no NONZERO signal) = 0_20`() {
        // When delta is zero, caller does NOT raise NONZERO_SCROLL_DELTA
        assertEquals(0.20f, score(DetectionSignal.SCROLL_FROM_CONTAINER), 0.001f)
    }

    @Test
    fun `scroll from container + UNAVAILABLE delta (no NONZERO signal raised) = 0_20`() {
        // When scrollDeltaY == SCROLL_DELTA_UNAVAILABLE, caller does NOT raise NONZERO_SCROLL_DELTA
        assertEquals(0.20f, score(DetectionSignal.SCROLL_FROM_CONTAINER), 0.001f)
    }

    // ── Group C ──────────────────────────────────────────────────────────────

    @Test
    fun `event sequence alone = 0_10`() =
        assertEquals(0.10f, score(DetectionSignal.EVENT_SEQUENCE), 0.001f)

    @Test
    fun `nav tab shorts alone = 0_10`() =
        assertEquals(0.10f, score(DetectionSignal.NAV_TAB_SHORTS), 0.001f)

    @Test
    fun `event sequence + nav tab = 0_20`() =
        assertEquals(0.20f, score(DetectionSignal.EVENT_SEQUENCE, DetectionSignal.NAV_TAB_SHORTS), 0.001f)

    // ── Exclusion veto ──────────────────────────────────────────────────��─────

    @Test
    fun `exclusion veto forces 0_00 regardless of other signals`() {
        assertEquals(0.0f, scoreExcluded(
            DetectionSignal.PRIMARY_VIEW_ID,
            DetectionSignal.SCROLL_FROM_CONTAINER,
            DetectionSignal.NONZERO_SCROLL_DELTA,
            DetectionSignal.EVENT_SEQUENCE,
            DetectionSignal.NAV_TAB_SHORTS,
        ), 0.001f)
    }

    @Test
    fun `exclusion veto on empty signals = 0_00`() =
        assertEquals(0.0f, scoreExcluded(), 0.001f)

    // ── Maximum score ─────────────────────────────────────────────────────────

    @Test
    fun `maximum combined score = 1_00 (A + B + C)`() =
        assertEquals(1.00f, score(
            DetectionSignal.PRIMARY_VIEW_ID,
            DetectionSignal.SCROLL_FROM_CONTAINER,
            DetectionSignal.NONZERO_SCROLL_DELTA,
            DetectionSignal.EVENT_SEQUENCE,
            DetectionSignal.NAV_TAB_SHORTS,
        ), 0.001f)

    // ── Combined group scores ─────────────────────────────────────────────────

    @Test
    fun `primary + scroll from container = 0_70`() =
        assertEquals(0.70f, score(DetectionSignal.PRIMARY_VIEW_ID, DetectionSignal.SCROLL_FROM_CONTAINER), 0.001f)

    @Test
    fun `primary + scroll + nonzero delta = 0_80`() =
        assertEquals(0.80f, score(
            DetectionSignal.PRIMARY_VIEW_ID,
            DetectionSignal.SCROLL_FROM_CONTAINER,
            DetectionSignal.NONZERO_SCROLL_DELTA,
        ), 0.001f)

    @Test
    fun `primary + nav_tab = 0_60 (exactly at threshold)`() =
        assertEquals(0.60f, score(DetectionSignal.PRIMARY_VIEW_ID, DetectionSignal.NAV_TAB_SHORTS), 0.001f)
}
