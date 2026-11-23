package com.reeltracker.accessibility

/**
 * Test double for [NodeWrapper].
 *
 * Builds a simple in-memory tree suitable for unit tests.
 */
data class FakeNodeWrapper(
    override val resourceName: String? = null,
    override val text: CharSequence? = null,
    override val isSelected: Boolean = false,
    private val children: List<FakeNodeWrapper> = emptyList(),
) : NodeWrapper {

    override val childCount: Int get() = children.size

    override fun child(index: Int): NodeWrapper? = children.getOrNull(index)

    override fun findByViewId(viewId: String): List<NodeWrapper> {
        val results = mutableListOf<NodeWrapper>()
        walk(this, viewId, results)
        return results
    }

    private fun walk(node: FakeNodeWrapper, viewId: String, out: MutableList<NodeWrapper>) {
        val name = node.resourceName
        if (name != null && (name.endsWith(":id/$viewId") || name == viewId)) out += node
        for (child in node.children) walk(child, viewId, out)
    }

    companion object {
        /** Convenience builder: wraps a resource-name string. */
        fun withId(shortId: String, vararg children: FakeNodeWrapper): FakeNodeWrapper =
            FakeNodeWrapper(resourceName = "com.example:id/$shortId", children = children.toList())

        /** Node with text and selected state (for Shorts nav-tab tests). */
        fun tab(text: String, selected: Boolean): FakeNodeWrapper =
            FakeNodeWrapper(text = text, isSelected = selected)
    }
}
