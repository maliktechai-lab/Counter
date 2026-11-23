package com.reeltracker.accessibility

/**
 * Tracks the last-known [scrollY] per [sourceViewId] so the service can compute
 * [EventSnapshot.scrollDeltaY] on API 26–27 where `getScrollDeltaY()` is unavailable.
 *
 * Not thread-safe. Must be accessed only from the accessibility service main thread.
 */
class ScrollDeltaTracker {
    private val lastScrollY: MutableMap<String?, Int> = mutableMapOf()

    /**
     * Returns the scroll delta (current minus previous) for [sourceViewId].
     *
     * Returns [EventSnapshot.SCROLL_DELTA_UNAVAILABLE] when [currentScrollY] < 0
     * (scroll position not present in this event).
     *
     * On first call for a given [sourceViewId], returns 0 (no previous value to diff against).
     */
    fun delta(sourceViewId: String?, currentScrollY: Int): Int {
        if (currentScrollY < 0) return EventSnapshot.SCROLL_DELTA_UNAVAILABLE
        val previous = lastScrollY[sourceViewId]
        lastScrollY[sourceViewId] = currentScrollY
        return if (previous == null) 0 else currentScrollY - previous
    }

    /** Call when the foreground package changes so stale position data is discarded. */
    fun reset() {
        lastScrollY.clear()
    }
}
