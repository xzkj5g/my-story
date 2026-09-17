package com.example.videocompiler.domain.model

/**
 * The user-defined, ordered list of [SourceMediaItem] entries (videos and/or photos) chosen for
 * one compilation job. Order is independent of the order in which items were originally
 * selected (FR-002).
 *
 * All mutating operations return a new [SelectionSequence] (immutable value type) and MUST leave
 * no gaps/duplicates, per spec.md's Acceptance Scenarios for User Story 2.
 *
 * @property items Ordered list defining playback/display order in the Compiled Output Video
 *   (FR-006). MAY contain as few as 1 entry (FR-012). No product-imposed maximum length; bounded
 *   only by device storage/memory (FR-014).
 */
data class SelectionSequence(
    val items: List<SourceMediaItem> = emptyList(),
) {
    /**
     * Adds a validated [SourceMediaItem] to the end of the sequence (FR-002).
     *
     * @throws IllegalArgumentException if [item]'s [SourceMediaItem.validationState] is not
     *   [ValidationState.VALID] (FR-015).
     */
    fun append(item: SourceMediaItem): SelectionSequence {
        require(item.validationState == ValidationState.VALID) {
            "cannot append a SourceMediaItem that is not VALID (FR-015)"
        }
        return copy(items = items + item)
    }

    /**
     * Reorders an entry from [fromIndex] to [toIndex] (FR-003). The displayed sequence updates
     * immediately (Acceptance Scenario 1, User Story 2).
     */
    fun move(fromIndex: Int, toIndex: Int): SelectionSequence {
        require(fromIndex in items.indices) { "fromIndex out of bounds: $fromIndex" }
        require(toIndex in items.indices) { "toIndex out of bounds: $toIndex" }
        if (fromIndex == toIndex) return this
        val mutable = items.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return copy(items = mutable)
    }

    /**
     * Removes the entry at [index], shifting subsequent entries up with no gap (FR-004).
     * Remaining items keep their relative order and no gap or duplicate remains (Acceptance
     * Scenario 3, User Story 2).
     */
    fun remove(index: Int): SelectionSequence {
        require(index in items.indices) { "index out of bounds: $index" }
        val mutable = items.toMutableList()
        mutable.removeAt(index)
        return copy(items = mutable)
    }
}
