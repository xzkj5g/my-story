package com.mystory.app

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mystory.app.domain.model.MediaType
import com.mystory.app.domain.usecase.MediaValidationResult
import com.mystory.app.domain.usecase.ValidateSourceMediaItem
import com.mystory.app.testutil.MediaTestUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@UnstableApi
class SelectionValidationInstrumentedTest {

    @Test
    fun corruptedVideoAndPhotoAreRejectedImmediately() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val validator = ValidateSourceMediaItem(context)
        val createdUris = mutableListOf<android.net.Uri>()
        try {
            val corruptVideo = MediaTestUtils.importAsset(context, "corrupt_video.mp4", MediaType.VIDEO)
            createdUris += corruptVideo
            val videoResult = validator(corruptVideo, MediaType.VIDEO)
            assertTrue(videoResult is MediaValidationResult.Invalid)
            assertTrue((videoResult as MediaValidationResult.Invalid).displayName.orEmpty().contains("corrupt_video.mp4"))

            val corruptPhoto = MediaTestUtils.importAsset(context, "corrupt_photo.jpg", MediaType.PHOTO)
            createdUris += corruptPhoto
            val photoResult = validator(corruptPhoto, MediaType.PHOTO)
            assertTrue(photoResult is MediaValidationResult.Invalid)
            assertTrue((photoResult as MediaValidationResult.Invalid).displayName.orEmpty().contains("corrupt_photo.jpg"))
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
        }
    }
}
