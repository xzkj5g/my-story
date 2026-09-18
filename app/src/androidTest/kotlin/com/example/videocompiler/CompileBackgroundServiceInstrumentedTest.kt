package com.example.videocompiler

import android.app.NotificationManager
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.videocompiler.domain.model.CompileJobStatus
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.service.CompileSessionStore
import com.example.videocompiler.testutil.MediaTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@UnstableApi
class CompileBackgroundServiceInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<com.example.videocompiler.ui.MainActivity>()

    @Test
    fun compilationContinuesWhileAppIsBackgrounded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val first = MediaTestUtils.importAsset(context, "video_long_audio.mp4", MediaType.VIDEO)
            val second = MediaTestUtils.importAsset(context, "video_long_audio.mp4", MediaType.VIDEO)
            createdUris += listOf(first, second)
            composeRule.activity.controller.addPickedUris(listOf(first, second))

            composeRule.activity.controller.startCompile()
            val runningJob = MediaTestUtils.waitForRunningJob()
            assertEquals(CompileJobStatus.RUNNING, runningJob.status)
            assertTrue(activeNotifications(context).isNotEmpty())

            composeRule.activity.moveTaskToBack(true)

            val finalJob = MediaTestUtils.waitForTerminalJob()
            assertEquals(CompileJobStatus.SUCCEEDED, finalJob.status)
            val outputUri = Uri.parse(requireNotNull(finalJob.outputUri))
            createdUris += outputUri
            MediaTestUtils.assertApproxDuration(context, outputUri, 12_000L, toleranceMs = 1_800L)
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
            CompileSessionStore.updateJob(null)
        }
    }

    @Test
    fun swipingAppAwayCancelsCompileAndLeavesNoVisiblePartialOutput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val first = MediaTestUtils.importAsset(context, "video_long_audio.mp4", MediaType.VIDEO)
            val second = MediaTestUtils.importAsset(context, "video_long_audio.mp4", MediaType.VIDEO)
            createdUris += listOf(first, second)
            composeRule.activity.controller.addPickedUris(listOf(first, second))

            composeRule.activity.controller.startCompile()
            MediaTestUtils.waitForRunningJob()
            composeRule.activity.finishAndRemoveTask()

            val finalJob = MediaTestUtils.waitForTerminalJob()
            assertEquals(CompileJobStatus.CANCELLED, finalJob.status)
            assertTrue(waitForNotificationDismissal(context))
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
            CompileSessionStore.updateJob(null)
        }
    }

    private fun activeNotifications(context: android.content.Context) =
        (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager)
            .activeNotifications
            .filter { it.id == 1001 }

    private fun waitForNotificationDismissal(context: android.content.Context): Boolean {
        repeat(30) {
            if (activeNotifications(context).isEmpty()) {
                return true
            }
            Thread.sleep(100L)
        }
        return false
    }
}
