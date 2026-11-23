package com.reeltracker.accessibility

import com.reeltracker.domain.model.Platform

/**
 * Tracks which app is currently in the foreground and how long it has been there.
 *
 * Updated by the accessibility service whenever a [android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]
 * event arrives (which is the most reliable foreground-change signal from the
 * accessibility API).
 *
 * Not thread-safe. Must be accessed only from the accessibility service main thread.
 */
class ForegroundAppTracker(private val clock: () -> Long = { System.currentTimeMillis() }) {

    private var currentPkg: String? = null
    private var foregroundSinceMs: Long = 0L

    /**
     * Update with the latest foreground package.
     *
     * @return `true` if the package changed (caller should fire AppLeft/AppReturned events).
     */
    fun update(packageName: String, nowMs: Long = clock()): Boolean {
        val changed = packageName != currentPkg
        if (changed) {
            currentPkg = packageName
            foregroundSinceMs = nowMs
        }
        return changed
    }

    /** The package name of the currently-tracked foreground app, or null before first update. */
    fun currentPackage(): String? = currentPkg

    /** How long the current package has been in the foreground, in milliseconds. */
    fun foregroundDurationMs(nowMs: Long = clock()): Long =
        if (currentPkg == null) 0L else maxOf(0L, nowMs - foregroundSinceMs)

    /**
     * Returns true when the foreground app is one of the two target platforms
     * (Instagram or YouTube).
     */
    fun isTargetAppInForeground(): Boolean =
        currentPkg != null && Platform.values().any { it.packageName == currentPkg }

    /** Reset to initial state. Called on service destroy. */
    fun reset() {
        currentPkg = null
        foregroundSinceMs = 0L
    }
}
