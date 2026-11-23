package com.reeltracker.accessibility

/**
 * Testable abstraction over [android.view.accessibility.AccessibilityNodeInfo].
 *
 * Production code uses [RealNodeWrapper]; unit tests use a [FakeNodeWrapper].
 * Neither detectors nor the state machine import the Android framework class directly.
 */
interface NodeWrapper {
    val resourceName: String?
    val text: CharSequence?
    val isSelected: Boolean
    val childCount: Int
    fun child(index: Int): NodeWrapper?
    /** Returns all descendant nodes whose resource-name ends with [viewId]. */
    fun findByViewId(viewId: String): List<NodeWrapper>
}
