package com.example.videocompiler.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.videocompiler.domain.model.CompileJob
import com.example.videocompiler.domain.model.CompileJobStatus
import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.model.SelectionSequence
import com.example.videocompiler.media.compiler.CompileEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CompileForegroundService : Service() {

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val compileEngine by lazy { CompileEngine(applicationContext) }
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var currentJob: CompileJob? = null

    override fun onCreate() {
        super.onCreate()
        CompileNotificationFactory.createChannel(notificationManager)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_CANCEL -> {
                cancelCurrentJob()
                START_NOT_STICKY
            }
            else -> {
                startQueuedCompile()
                START_NOT_STICKY
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        cancelCurrentJob()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (currentJob?.status == CompileJobStatus.RUNNING) {
            cancelCurrentJob()
        }
        executor.shutdown()
        super.onDestroy()
    }

    private fun startQueuedCompile() {
        if (currentJob?.status == CompileJobStatus.RUNNING) {
            return
        }
        val request = CompileSessionStore.takePendingRequest() ?: run {
            stopSelf()
            return
        }
        startForeground(
            CompileNotificationFactory.notificationId,
            notification("Preparing compilation", progress = 0),
        )
        executor.execute { runCompile(request.sequence, request.settings) }
    }

    private fun runCompile(sequence: SelectionSequence, settings: OutputSettings) {
        val finalJob = compileEngine.compile(sequence, settings, ::publishJob)
        publishJob(finalJob)
        when (finalJob.status) {
            CompileJobStatus.SUCCEEDED ->
                notificationManager.notify(
                    CompileNotificationFactory.notificationId,
                    notification("Compilation complete", progress = 100, ongoing = false),
                )
            CompileJobStatus.FAILED ->
                notificationManager.notify(
                    CompileNotificationFactory.notificationId,
                    notification(
                        finalJob.failureReason ?: "Compilation failed",
                        progress = finalJob.progressPercent,
                        ongoing = false,
                    ),
                )
            CompileJobStatus.CANCELLED -> stopForeground(STOP_FOREGROUND_REMOVE)
            CompileJobStatus.RUNNING -> Unit
        }
        stopSelf()
    }

    private fun publishJob(job: CompileJob) {
        currentJob = job
        CompileSessionStore.updateJob(job)
        if (job.status == CompileJobStatus.RUNNING) {
            notificationManager.notify(
                CompileNotificationFactory.notificationId,
                notification("Compiling media sequence", progress = job.progressPercent),
            )
        }
    }

    private fun cancelCurrentJob() {
        val runningJob = currentJob ?: return
        if (runningJob.status != CompileJobStatus.RUNNING) {
            return
        }
        val cancelled = compileEngine.cancel(runningJob)
        currentJob = cancelled
        CompileSessionStore.updateJob(cancelled)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(text: String, progress: Int, ongoing: Boolean = true) =
        CompileNotificationFactory.build(this, text, ACTION_CANCEL, progress, ongoing)

    companion object {
        private const val ACTION_CANCEL = "com.example.videocompiler.action.CANCEL_COMPILE"

        fun start(context: Context, sequence: SelectionSequence, settings: OutputSettings) {
            CompileSessionStore.queueRequest(sequence, settings)
            CompileSessionStore.updateJob(null)
            ContextCompat.startForegroundService(
                context,
                Intent(context, CompileForegroundService::class.java),
            )
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, CompileForegroundService::class.java).setAction(ACTION_CANCEL),
            )
        }
    }
}
