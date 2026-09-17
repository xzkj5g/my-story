package com.example.videocompiler

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.testutil.MediaTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@UnstableApi
class SelectionValidationInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<com.example.videocompiler.ui.MainActivity>()

    @Test
    fun corruptedVideoAndPhotoAreRejectedImmediately() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val corruptVideo = MediaTestUtils.importAsset(context, "corrupt_video.mp4", MediaType.VIDEO)
            createdUris += corruptVideo
            composeRule.activity.controller.addPickedUri(corruptVideo)

            val afterVideo = composeRule.activity.controller.uiState.value
            assertTrue(afterVideo.message.orEmpty().contains("corrupt_video.mp4"))
            assertEquals(0, afterVideo.selectionSequence.items.size)

            val corruptPhoto = MediaTestUtils.importAsset(context, "corrupt_photo.jpg", MediaType.PHOTO)
            createdUris += corruptPhoto
            composeRule.activity.controller.addPickedUri(corruptPhoto)

            val afterPhoto = composeRule.activity.controller.uiState.value
            assertTrue(afterPhoto.message.orEmpty().contains("corrupt_photo.jpg"))
            assertEquals(0, afterPhoto.selectionSequence.items.size)
            composeRule.onNodeWithTag("messageText").assertIsDisplayed()
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
        }
    }
}
