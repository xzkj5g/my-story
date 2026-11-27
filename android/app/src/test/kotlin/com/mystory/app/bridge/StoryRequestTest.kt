package com.mystory.app.bridge

import com.mystory.app.domain.model.AspectRatio
import com.mystory.app.domain.model.FrameRatePreset
import com.mystory.app.domain.model.MediaType
import com.mystory.app.domain.model.ResolutionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StoryRequestTest {

    @Test
    fun parsesAssetsInGivenOrder() {
        val assets = StoryRequest.parseAssets(
            listOf(
                mapOf("id" to "42", "isVideo" to true),
                mapOf("id" to "7", "isVideo" to false),
            ),
        )
        assertEquals(listOf(PickedAsset(42, MediaType.VIDEO), PickedAsset(7, MediaType.PHOTO)), assets)
    }

    @Test
    fun rejectsNonNumericAssetId() {
        assertFailsWith<IllegalArgumentException> {
            StoryRequest.parseAssets(listOf(mapOf("id" to "abc", "isVideo" to true)))
        }
    }

    @Test
    fun parsesSettingsWithDefaultResolution() {
        val settings = StoryRequest.parseSettings("9:16", 60, null)
        assertEquals(ResolutionTier.R1080P, settings.resolutionTier)
        assertEquals(AspectRatio.RATIO_9_16, settings.aspectRatio)
        assertEquals(FrameRatePreset.FPS_60, settings.frameRate)
    }

    @Test
    fun rejectsUnsupportedFrameRate() {
        assertFailsWith<IllegalArgumentException> { StoryRequest.parseSettings("16:9", 25, "1080p") }
    }

    @Test
    fun extractsAssetIdFromContentUri() {
        assertEquals("123", StoryRequest.assetIdFromUri("content://media/external/video/media/123"))
        assertEquals("123", StoryRequest.assetIdFromUri("content://media/external/video/media/123?x=1"))
        assertNull(StoryRequest.assetIdFromUri(""))
    }
}
