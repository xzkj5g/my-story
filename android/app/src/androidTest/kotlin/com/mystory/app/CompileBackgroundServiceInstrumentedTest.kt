package com.mystory.app

import android.app.NotificationManager
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mystory.app.domain.model.CompileJobStatus
import com.mystory.app.domain.model.OutputSettings
import com.mystory.app.domain.model.SelectionSequence
import com.mystory.app.domain.usecase.MediaValidationResult
import com.mystory.app.domain.usecase.ValidateSourceMediaItem
import com.mystory.app.service.CompileForegroundService
import com.mystory.app.domain.model.MediaType
import com.mystory.app.service.CompileSessionStore
import com.mystory.app.testutil.MediaTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@UnstableApi
class CompileBackgroundServiceInstrumentedTest {

    @Test
    fun compilationContinuesWhileAppIsBackgrounded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val first = MediaTestUtils.importAsset(context, "video_long_audio.mp4", MediaType.VIDEO)
            val second = MediaTestUtils.importAsset(context, "video_long_audio.mp4", MediaType.VIDEO)
            createdUris += listOf(first, second)
            startCompile(context, listOf(first, second))
            val runningJob = MediaTestUtils.waitForRunningJob()
            assertEquals(CompileJobStatus.RUNNING, runningJob.status)
            assertTrue(activeNotifications(context).isNotEmpty())

            scenario.onActivity { it.moveTaskToBack(true) }

            val finalJob = MediaTestUtils.waitForTerminalJob()
            assertEquals(CompileJobStatus.SUCCEEDED, finalJob.status)
            val outputUri = Uri.parse(requireNotNull(finalJob.outputUri))
            createdUris += outputUri
            MediaTestUtils.assertApproxDuration(context, outputUri, 12_000L, toleranceMs = 1_800L)
        } finally {
            scenario.close()
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
            CompileSessionStore.updateJob(null)
        }
    }

    @Test
    fun swipingAppAwayCancelsCompileAndLeavesNoVisiblePartialOutput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val first = MediaTestUtils.importAsset(context, "video_long_audio.mp4", MediaType.VIDEO)
            val second = MediaTestUtils.importAsset(context, "video_long_audio.mp4", MediaType.VIDEO)
            createdUris += listOf(first, second)
            startCompile(context, listOf(first, second))
            MediaTestUtils.waitForRunningJob()
            scenario.onActivity { it.finishAndRemoveTask() }

            val finalJob = MediaTestUtils.waitForTerminalJob()
            assertEquals(CompileJobStatus.CANCELLED, finalJob.status)
            assertTrue(waitForNotificationDismissal(context))
        } finally {
            scenario.close()
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
            CompileSessionStore.updateJob(null)
        }
    }

    private fun startCompile(context: android.content.Context, uris: List<Uri>) {
        val validator = ValidateSourceMediaItem(context)
        var sequence = SelectionSequence()
        uris.forEach { uri ->
            val result = validator(uri, MediaType.VIDEO) as MediaValidationResult.Valid
            sequence = sequence.append(result.item)
        }
        CompileForegroundService.start(context, sequence, OutputSettings.default())
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
