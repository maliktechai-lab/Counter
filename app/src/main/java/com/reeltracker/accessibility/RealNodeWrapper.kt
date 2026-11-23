package com.reeltracker.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Production adapter. The tree walk is depth-limited to [MAX_DEPTH] to avoid
 * CPU spikes on deep view hierarchies during fast scroll gestures.
 *
 * Child nodes obtained via [AccessibilityNodeInfo.getChild] are recycled after visiting
 * to prevent accessibility memory leaks.
 */
class RealNodeWrapper(
    private val node: AccessibilityNodeInfo,
    private val depth: Int = 0,
) : NodeWrapper {

    override val resourceName: String? get() = node.viewIdResourceName
    override val text: CharSequence?   get() = node.text
    override val isSelected: Boolean   get() = node.isSelected
    override val childCount: Int       get() = node.childCount

    override fun child(index: Int): NodeWrapper? =
        node.getChild(index)?.let { RealNodeWrapper(it, depth + 1) }

    override fun findByViewId(viewId: String): List<NodeWrapper> {
        if (depth >= MAX_DEPTH) return emptyList()
        // Fast path: use the framework's own lookup by full resource name
        val pkg  = node.packageName?.toString() ?: return fallback(viewId)
        val full = "$pkg:id/$viewId"
        val found = node.findAccessibilityNodeInfosByViewId(full)
        if (found.isNotEmpty()) return found.map { RealNodeWrapper(it, depth) }
        return fallback(viewId)
    }

    private fun fallback(viewId: String): List<NodeWrapper> {
        val results = mutableListOf<NodeWrapper>()
        walk(this, viewId, results, 0)
        return results
    }

    companion object {
        private const val MAX_DEPTH = 8

        private fun walk(node: NodeWrapper, viewId: String, out: MutableList<NodeWrapper>, d: Int) {
            if (d > MAX_DEPTH) return
            val name = node.resourceName
            if (name != null && (name.endsWith(":id/$viewId") || name == viewId)) out += node
            for (i in 0 until node.childCount) node.child(i)?.let { walk(it, viewId, out, d + 1) }
        }
    }
}
