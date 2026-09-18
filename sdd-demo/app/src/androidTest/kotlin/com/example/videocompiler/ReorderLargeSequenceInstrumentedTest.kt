package com.example.videocompiler

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.testutil.MediaTestUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * US2 SC-005: reordering a sequence of at least 10 selected media items must not lose or
 * misorder any entry. Exercises [com.example.videocompiler.domain.model.SelectionSequence.move]
 * / `.remove` against real `ValidateSourceMediaItem`-produced items (not synthetic domain
 * objects), matching how the reorder screen actually operates.
 */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class ReorderLargeSequenceInstrumentedTest {

    @Test
    fun reorderingTenItemsPreservesEveryEntryWithoutLossOrMisorder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val itemCount = 10
            val uris = (0 until itemCount).map {
                MediaTestUtils.importAsset(context, "video_red_audio.mp4", MediaType.VIDEO)
            }
            createdUris += uris

            var sequence = MediaTestUtils.sequenceOf(
                *uris.map { it to MediaType.VIDEO }.toTypedArray(),
                context = context,
            )
            val originalUriOrder = sequence.items.map { it.uri }
            assertEquals(itemCount, originalUriOrder.distinct().size)

            // Apply a fixed, deterministic series of moves (chosen to touch every position at
            // least once) and mirror the exact same operations on a plain MutableList using
            // SelectionSequence.move's documented semantics (remove at fromIndex, insert at
            // toIndex), so the test doesn't depend on deriving a closed-form reordering formula
            // -- only on `move`/`remove` never losing or duplicating an entry.
            val expected = originalUriOrder.toMutableList()
            val moves = listOf(9 to 0, 0 to 5, 8 to 1, 2 to 7, 6 to 3, 4 to 9, 1 to 8, 7 to 2)
            for ((from, to) in moves) {
                sequence = sequence.move(fromIndex = from, toIndex = to)
                expected.add(to, expected.removeAt(from))
            }
            assertEquals(expected, sequence.items.map { it.uri })
            assertEquals(itemCount, sequence.items.map { it.uri }.distinct().size)

            sequence = sequence.remove(index = 0)
            expected.removeAt(0)
            assertEquals(expected, sequence.items.map { it.uri })
            assertEquals(itemCount - 1, sequence.items.size)
            // No duplicates and no entry lost (aside from the one intentionally removed).
            assertEquals(sequence.items.map { it.uri }.distinct().size, sequence.items.size)
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
        }
    }
}
