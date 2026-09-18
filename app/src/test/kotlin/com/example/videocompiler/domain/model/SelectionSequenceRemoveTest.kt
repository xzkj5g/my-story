package com.example.videocompiler.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for [SelectionSequence.remove] (US2, Acceptance Scenario 3): removing an entry must
 * shift subsequent entries up with no gap and no duplicate, preserving relative order.
 */
class SelectionSequenceRemoveTest {

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
    fun `remove shifts remaining entries up with no gap or duplicate`() {
        val a = validItem("A")
        val b = validItem("B")
        val c = validItem("C")
        val sequence = SelectionSequence(listOf(a, b, c))

        val result = sequence.remove(index = 1)

        assertEquals(listOf(a, c), result.items)
    }

    @Test
    fun `remove the only item leaves an empty sequence`() {
        val sequence = SelectionSequence(listOf(validItem("A")))

        val result = sequence.remove(index = 0)

        assertEquals(emptyList<SourceMediaItem>(), result.items)
    }

    @Test
    fun `remove the last item preserves order of remaining items`() {
        val a = validItem("A")
        val b = validItem("B")
        val c = validItem("C")
        val sequence = SelectionSequence(listOf(a, b, c))

        val result = sequence.remove(index = 2)

        assertEquals(listOf(a, b), result.items)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `remove rejects an out-of-bounds index`() {
        SelectionSequence(listOf(validItem("A"))).remove(index = 5)
    }
}
