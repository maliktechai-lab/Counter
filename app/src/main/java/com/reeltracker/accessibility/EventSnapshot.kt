package com.reeltracker.accessibility

/**
 * Immutable snapshot of all data extracted from one [android.view.accessibility.AccessibilityEvent]
 * and the corresponding [android.view.accessibility.AccessibilityNodeInfo] tree.
 *
 * All fields are plain Kotlin types. No live framework object reference is held.
 * Safe to pass to coroutines and pure functions after [android.accessibilityservice.AccessibilityService.onAccessibilityEvent] returns.
 */
data class EventSnapshot(
    val packageName: String,
    val eventType: Int,
    val sourceViewId: String?,

    /**
     * Scroll Y position from [android.view.accessibility.AccessibilityEvent.getScrollY].
     * -1 when the event type does not carry scroll info.
     * Available from API 26.
     */
    val scrollY: Int,

    /** Max scroll Y from [android.view.accessibility.AccessibilityEvent.getScrollMaxY]. -1 if unavailable. */
    val scrollMaxY: Int,

    /**
     * Computed scroll delta: current [scrollY] minus previous [scrollY] for the same [sourceViewId],
     * tracked by [ScrollDeltaTracker].
     *
     * [SCROLL_DELTA_UNAVAILABLE] when:
     *   - Running below API 28 (getScrollDeltaY() not available)
     *   - [scrollY] < 0 (scroll info not present in this event)
     *
     * Important: [SCROLL_DELTA_UNAVAILABLE] is NOT equivalent to 0.
     * Two events both carrying [SCROLL_DELTA_UNAVAILABLE] are considered equal on this field
     * (same unavailability), not distinguishable by it.
     */
    val scrollDeltaY: Int,

    /**
     * Accessibility action from [android.view.accessibility.AccessibilityEvent.getAction].
     * Reflects the action on the source node — NOT a physical gesture descriptor.
     * Included in [EventSignature] as a tiebreaker only. See TC-13.
     */
    val eventAction: Int,

    /** Wall-clock timestamp captured inside the callback. UTC epoch ms. */
    val timestampMs: Long,

    /** Immutable tree snapshot extracted during the callback. */
    val nodeSnapshot: NodeSnapshot,
) {
    companion object {
        /**
         * Sentinel value meaning "scroll delta not available on this API level or event".
         * Distinguishable from a genuine zero delta.
         */
        const val SCROLL_DELTA_UNAVAILABLE = Int.MIN_VALUE
    }

    fun toSignature(): EventSignature = EventSignature(
        eventType    = eventType,
        sourceViewId = sourceViewId,
        scrollY      = scrollY,
        scrollDeltaY = scrollDeltaY,
        eventAction  = eventAction,
    )
}

/**
 * Immutable snapshot of the accessibility tree relevant to detection.
 *
 * Contains only structural identifiers and one boolean derived from text — no raw text values.
 */
data class NodeSnapshot(
    /**
     * Short view-ID names (the part after ":id/") found anywhere in the tree up to depth 8.
     * Package prefix is stripped. No text, no content, no account information.
     */
    val presentViewIds: Set<String>,

    /**
     * YouTube only. True when the bottom navigation bar contains a tab whose text equals
     * exactly "Shorts" and whose isSelected == true.
     * The text value is NOT stored; only this boolean result is retained.
     * Always false for Instagram events.
     */
    val shortsNavTabSelected: Boolean,
) {
    companion object {
        val EMPTY = NodeSnapshot(emptySet(), false)
    }
}

/**
 * Five-field event signature used by [EventSignatureDeduplicator].
 *
 * Two events with identical signatures arriving within the deduplication window
 * are assumed to be duplicate accessibility callbacks from the same physical gesture.
 *
 * Known limitations (TC-13):
 * - [scrollY] may be recycled by feed containers; two genuine swipes can produce the same value.
 * - [scrollDeltaY] == [EventSnapshot.SCROLL_DELTA_UNAVAILABLE] on API < 28.
 * - [eventAction] is not a reliable gesture discriminator; it reflects the accessibility action
 *   on the source node, not the physical gesture type.
 */
data class EventSignature(
    val eventType: Int,
    val sourceViewId: String?,
    val scrollY: Int,
    val scrollDeltaY: Int,
    val eventAction: Int,
)
