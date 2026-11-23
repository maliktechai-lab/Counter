package com.reeltracker.accessibility.detectors

import com.reeltracker.accessibility.EventSnapshot

/**
 * Stateless interface for detecting whether an [EventSnapshot] represents activity
 * inside a short-form video feed.
 *
 * Implementations must be stateless — the same input always produces the same output.
 * State (e.g. previous event type for EVENT_SEQUENCE signal) is managed by the caller
 * which passes it into the snapshot or tracks it externally.
 */
interface FeedDetector {
    /**
     * Evaluate [snapshot] and return a [DetectionResult] indicating whether this event
     * occurred inside a known short-form feed, an excluded screen, or an unknown screen.
     */
    fun detect(snapshot: EventSnapshot): DetectionResult
}
