package com.example.videocompiler.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for [SelectionSequence.move] (US2, Acceptance Scenario 1): reordering an entry must
 * not introduce gaps or duplicates, and must leave all other items in their relative order.
 */
class SelectionSequenceMoveTest {

    private fun validItem(name: String) = SourceMediaItem(
        uri = "content://media/$name",
        mediaType = MediaType.VIDEO,
        displayName = name,
        durationMs = 1000L,
        width = 1920,
        height = 1080,
        frameRate = 30f,
        hasAudioTrack = true,
        validationState = ValidationState.VALID,
    )

    @Test
    fun `move reorders an entry to a later index without gaps or duplicates`() {
        val a = validItem("A")
        val b = validItem("B")
        val c = validItem("C")
        val sequence = SelectionSequence(listOf(a, b, c))

        val reordered = sequence.move(fromIndex = 0, toIndex = 2)

        assertEquals(listOf(b, c, a), reordered.items)
    }

    @Test
    fun `move reorders an entry to an earlier index without gaps or duplicates`() {
        val a = validItem("A")
        val b = validItem("B")
        val c = validItem("C")
        val sequence = SelectionSequence(listOf(a, b, c))

        val reordered = sequence.move(fromIndex = 2, toIndex = 0)

        assertEquals(listOf(c, a, b), reordered.items)
    }

    @Test
    fun `move to the same index is a no-op`() {
        val a = validItem("A")
        val b = validItem("B")
        val sequence = SelectionSequence(listOf(a, b))

        val reordered = sequence.move(fromIndex = 1, toIndex = 1)

        assertEquals(sequence.items, reordered.items)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `move rejects an out-of-bounds fromIndex`() {
        SelectionSequence(listOf(validItem("A"))).move(fromIndex = 5, toIndex = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `move rejects an out-of-bounds toIndex`() {
        SelectionSequence(listOf(validItem("A"))).move(fromIndex = 0, toIndex = 5)
    }
}
