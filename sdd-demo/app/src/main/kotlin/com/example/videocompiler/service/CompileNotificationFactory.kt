package com.example.videocompiler.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.videocompiler.R
import com.example.videocompiler.ui.MainActivity

internal object CompileNotificationFactory {

    const val channelId = "compile-progress"
    const val notificationId = 1001

    fun createChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Compilation",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    fun build(
        context: Context,
        text: String,
        cancelAction: String,
        progress: Int,
        ongoing: Boolean = true,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, CompileForegroundService::class.java).setAction(cancelAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .addAction(0, "Cancel", cancelIntent)
            .build()
    }
}
