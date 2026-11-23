package com.reeltracker.accessibility

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NodeSnapshotBuilderTest {

    // ───────────────────────── presentViewIds ─────────────────────────

    @Test
    fun `short resource names are stripped of package prefix`() {
        val root = FakeNodeWrapper(
            resourceName = "com.foo:id/my_view",
            children = listOf(
                FakeNodeWrapper(resourceName = "com.bar:id/child_view"),
            ),
        )
        val snap = NodeSnapshotBuilder.build(root, "com.foo")
        assertTrue("my_view" in snap.presentViewIds)
        assertTrue("child_view" in snap.presentViewIds)
    }

    @Test
    fun `long-form IDs are stripped correctly`() {
        val root = FakeNodeWrapper(resourceName = "com.instagram.android:id/reel_recycler_view")
        val snap = NodeSnapshotBuilder.build(root, "com.instagram.android")
        assertTrue("reel_recycler_view" in snap.presentViewIds)
        assertFalse("com.instagram.android:id/reel_recycler_view" in snap.presentViewIds)
    }

    @Test
    fun `null root returns EMPTY snapshot`() {
        val snap = NodeSnapshotBuilder.build(null, "com.foo")
        assertEquals(NodeSnapshot.EMPTY, snap)
    }

    @Test
    fun `tree walk collects IDs up to and including depth 8`() {
        // The builder visits depths 0–8 (guard: depth > 8 returns).
        // Build a chain: root (depth 0) → … → leaf (depth 8) → extra (depth 9).
        // Name nodes by their depth index so the assertions are unambiguous.
        var leaf: FakeNodeWrapper = FakeNodeWrapper(resourceName = "com.x:id/depth_9_should_not_appear")
        var node: FakeNodeWrapper = FakeNodeWrapper(resourceName = "com.x:id/depth_8", children = listOf(leaf))
        for (d in 7 downTo 0) {
            node = FakeNodeWrapper(resourceName = "com.x:id/depth_$d", children = listOf(node))
        }
        val snap = NodeSnapshotBuilder.build(node, "com.x")
        // Depths 0–8 (9 levels) should all be present
        for (d in 0..8) assertTrue("depth_$d" in snap.presentViewIds, "Missing depth_$d")
    }

    @Test
    fun `node at depth 9 is not collected`() {
        // depth_9 is 10 levels below root (depth 0 → … → depth 9)
        var node: FakeNodeWrapper = FakeNodeWrapper(resourceName = "com.x:id/depth_9")
        for (d in 8 downTo 0) {
            node = FakeNodeWrapper(resourceName = "com.x:id/depth_$d", children = listOf(node))
        }
        val snap = NodeSnapshotBuilder.build(node, "com.x")
        // Depths 0–8 are present; depth_9 is beyond the limit
        for (d in 0..8) assertTrue("depth_$d" in snap.presentViewIds, "Missing depth_$d")
        assertFalse("depth_9" in snap.presentViewIds, "depth_9 should not be collected")
    }

    @Test
    fun `node with null resource name is ignored`() {
        val root = FakeNodeWrapper(
            resourceName = null,
            children = listOf(FakeNodeWrapper(resourceName = "com.a:id/visible")),
        )
        val snap = NodeSnapshotBuilder.build(root, "com.a")
        assertTrue("visible" in snap.presentViewIds)
        assertFalse(snap.presentViewIds.any { it.isNullOrEmpty() })
    }

    // ───────────────────────── shortsNavTabSelected ─────────────────────────

    @Test
    fun `shortsNavTabSelected is false for non-YouTube packages`() {
        val navBar = FakeNodeWrapper(
            resourceName = "com.google.android.youtube:id/bottom_navigation_bar",
            children = listOf(FakeNodeWrapper.tab("Shorts", selected = true)),
        )
        val snap = NodeSnapshotBuilder.build(navBar, "com.instagram.android")
        assertFalse(snap.shortsNavTabSelected)
    }

    @Test
    fun `shortsNavTabSelected is true when Shorts tab is selected in YouTube nav bar`() {
        val navBar = FakeNodeWrapper(
            resourceName = "com.google.android.youtube:id/bottom_navigation_bar",
            children = listOf(
                FakeNodeWrapper.tab("Home", selected = false),
                FakeNodeWrapper.tab("Shorts", selected = true),
                FakeNodeWrapper.tab("Subscriptions", selected = false),
            ),
        )
        val snap = NodeSnapshotBuilder.build(navBar, "com.google.android.youtube")
        assertTrue(snap.shortsNavTabSelected)
    }

    @Test
    fun `shortsNavTabSelected is false when Shorts tab is not selected`() {
        val navBar = FakeNodeWrapper(
            resourceName = "com.google.android.youtube:id/bottom_navigation_bar",
            children = listOf(
                FakeNodeWrapper.tab("Home", selected = true),
                FakeNodeWrapper.tab("Shorts", selected = false),
            ),
        )
        val snap = NodeSnapshotBuilder.build(navBar, "com.google.android.youtube")
        assertFalse(snap.shortsNavTabSelected)
    }

    @Test
    fun `shortsNavTabSelected uses bottom_tabs_container as alternative container ID`() {
        val navBar = FakeNodeWrapper(
            resourceName = "com.google.android.youtube:id/bottom_tabs_container",
            children = listOf(FakeNodeWrapper.tab("Shorts", selected = true)),
        )
        val snap = NodeSnapshotBuilder.build(navBar, "com.google.android.youtube")
        assertTrue(snap.shortsNavTabSelected)
    }

    @Test
    fun `non-nav-tab text nodes do not set shortsNavTabSelected`() {
        // A node with text "Shorts" but not inside a nav container
        val root = FakeNodeWrapper(
            resourceName = "com.google.android.youtube:id/some_other_view",
            children = listOf(FakeNodeWrapper(text = "Shorts", isSelected = true)),
        )
        val snap = NodeSnapshotBuilder.build(root, "com.google.android.youtube")
        assertFalse(snap.shortsNavTabSelected)
    }
}
