package com.mystory.app.bridge

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import com.mystory.app.domain.model.CompileJob
import com.mystory.app.domain.model.CompileJobStatus
import com.mystory.app.domain.model.SelectionSequence
import com.mystory.app.domain.usecase.MediaValidationResult
import com.mystory.app.domain.usecase.ValidateSourceMediaItem
import com.mystory.app.service.CompileForegroundService
import com.mystory.app.service.CompileSessionStore
import com.mystory.app.service.DeviceResourceMonitor
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Exposes the Media3-backed compile engine to Flutter.
 *
 * Methods (`my_story/compiler`): `startCompile`, `cancelCompile`, `resourceWarning`.
 * Events (`my_story/compiler_events`): one map per job update with `status`, `progress`,
 * `error`, `outputUri` and `assetId`.
 */
@UnstableApi
class StoryBridge(
    private val activity: Activity,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private val methodChannel = MethodChannel(messenger, METHOD_CHANNEL)
    private val eventChannel = EventChannel(messenger, EVENT_CHANNEL)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null

    fun attach() {
        methodChannel.setMethodCallHandler(this)
        eventChannel.setStreamHandler(this)
    }

    fun detach() {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        collector?.cancel()
        scope.coroutineContext[Job]?.cancel()
        ioExecutor.shutdown()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startCompile" -> startCompile(call, result)
            "cancelCompile" -> {
                CompileForegroundService.cancel(activity.applicationContext)
                result.success(null)
            }
            "resourceWarning" -> result.success(DeviceResourceMonitor(activity).warningMessage())
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        collector?.cancel()
        collector = scope.launch {
            var first = true
            CompileSessionStore.jobState.filterNotNull().collect { job ->
                // StateFlow replays its latest value; a finished job from an earlier run must not
                // be mistaken for the result of the job Flutter is about to start.
                val stale = first && job.status != CompileJobStatus.RUNNING
                first = false
                if (!stale) events.success(job.toEvent())
            }
        }
    }

    override fun onCancel(arguments: Any?) {
        collector?.cancel()
        collector = null
    }

    @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught")
    private fun startCompile(call: MethodCall, result: MethodChannel.Result) {
        val rawItems = call.argument<List<Map<String, Any?>>>("items")
        val aspectRatio = call.argument<String>("aspectRatio")
        val fps = call.argument<Int>("fps")
        val resolution = call.argument<String>("resolution")
        ioExecutor.execute {
            try {
                val assets = StoryRequest.parseAssets(requireNotNull(rawItems) { "items missing" })
                val settings = StoryRequest.parseSettings(aspectRatio, fps, resolution)
                require(assets.isNotEmpty()) { "Select at least one photo or video." }
                val validator = ValidateSourceMediaItem(activity.applicationContext)
                var sequence = SelectionSequence()
                for (asset in assets) {
                    when (val validation = validator(asset.contentUri(), asset.mediaType)) {
                        is MediaValidationResult.Valid -> sequence = sequence.append(validation.item)
                        is MediaValidationResult.Invalid -> {
                            val name = validation.displayName ?: asset.id.toString()
                            throw IllegalStateException("$name can't be used: ${validation.reason.name.lowercase().replace('_', ' ')}")
                        }
                    }
                }
                mainHandler.post {
                    requestNotificationPermissionIfNeeded()
                    CompileForegroundService.start(activity.applicationContext, sequence, settings)
                    result.success(null)
                }
            } catch (e: Exception) {
                mainHandler.post { result.error("invalid_request", e.message, null) }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST_CODE)
        }
    }

    private fun CompileJob.toEvent(): Map<String, Any?> = mapOf(
        "status" to when (status) {
            CompileJobStatus.RUNNING -> "running"
            CompileJobStatus.SUCCEEDED -> "succeeded"
            CompileJobStatus.FAILED -> "failed"
            CompileJobStatus.CANCELLED -> "cancelled"
        },
        "progress" to progressPercent,
        "error" to failureReason,
        "outputUri" to outputUri,
        "assetId" to outputUri?.let(StoryRequest::assetIdFromUri),
    )

    private companion object {
        const val METHOD_CHANNEL = "my_story/compiler"
        const val EVENT_CHANNEL = "my_story/compiler_events"
        const val NOTIFICATION_REQUEST_CODE = 4101
    }
}
