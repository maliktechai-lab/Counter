package com.reeltracker.accessibility

/**
 * Builds an immutable [NodeSnapshot] from a live [NodeWrapper] tree.
 *
 * Must be called synchronously inside [android.accessibilityservice.AccessibilityService.onAccessibilityEvent]
 * before the callback returns, because node references are pooled and recycled by the framework.
 *
 * Privacy contract:
 * - Only view resource-ID short names are collected (the part after ":id/"). No text, no content.
 * - For YouTube only: the text of bottom navigation tab nodes is compared to the string "Shorts".
 *   The result is a Boolean. The text value itself is not stored anywhere.
 */
object NodeSnapshotBuilder {

    private const val YOUTUBE_PKG = "com.google.android.youtube"
    private const val SHORTS_TAB_TEXT = "Shorts"  // compile-time constant; not stored
    private const val MAX_DEPTH = 8

    fun build(root: NodeWrapper?, pkg: String): NodeSnapshot {
        if (root == null) return NodeSnapshot.EMPTY
        val ids = mutableSetOf<String>()
        collectIds(root, ids, 0)
        val shortsNavTabSelected = if (pkg == YOUTUBE_PKG) checkShortsTab(root) else false
        return NodeSnapshot(presentViewIds = ids, shortsNavTabSelected = shortsNavTabSelected)
    }

    private fun collectIds(node: NodeWrapper, out: MutableSet<String>, depth: Int) {
        if (depth > MAX_DEPTH) return
        node.resourceName?.let { fullName ->
            // Strip package prefix: "com.foo:id/bar" → "bar"
            val shortName = fullName.substringAfterLast(":id/", fullName)
            out += shortName
        }
        for (i in 0 until node.childCount) {
            node.child(i)?.let { collectIds(it, out, depth + 1) }
        }
    }

    /**
     * Returns true iff the bottom navigation bar contains a tab whose text equals "Shorts"
     * and whose isSelected == true.
     *
     * Text is compared but NOT stored. This is the only text access permitted by TC-04.
     */
    private fun checkShortsTab(root: NodeWrapper): Boolean {
        // Try known nav bar container IDs
        for (navId in listOf("bottom_navigation_bar", "bottom_tabs_container")) {
            val navNodes = findByShortId(root, navId, 0)
            for (nav in navNodes) {
                for (i in 0 until nav.childCount) {
                    val tab = nav.child(i) ?: continue
                    // text comparison only — text value is not retained
                    if (tab.text?.toString() == SHORTS_TAB_TEXT && tab.isSelected) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun findByShortId(node: NodeWrapper, shortId: String, depth: Int): List<NodeWrapper> {
        if (depth > MAX_DEPTH) return emptyList()
        val results = mutableListOf<NodeWrapper>()
        val name = node.resourceName
        if (name != null && (name.endsWith(":id/$shortId") || name == shortId)) results += node
        for (i in 0 until node.childCount) {
            node.child(i)?.let { results += findByShortId(it, shortId, depth + 1) }
        }
        return results
    }
}
