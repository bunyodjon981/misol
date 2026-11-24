package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class BuildNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_PROGRESS_ID = "gitapk_build_progress"
        const val CHANNEL_READY_ID = "gitapk_build_ready"
        const val PROGRESS_NOTIFICATION_ID = 1001
        const val RESULT_NOTIFICATION_ID = 1002
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                context.getString(R.string.notif_channel_progress_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_progress_desc)
                setShowBadge(false)
            }

            val readyChannel = NotificationChannel(
                CHANNEL_READY_ID,
                context.getString(R.string.notif_channel_ready_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_ready_desc)
                enableVibration(true)
            }

            manager.createNotificationChannel(progressChannel)
            manager.createNotificationChannel(readyChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildProgressNotification(
        statusText: String,
        progressPercent: Int,
        indeterminate: Boolean
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(context.getString(R.string.notif_building_title))
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setProgress(100, progressPercent.coerceIn(0, 100), indeterminate)
            .build()
    }

    fun updateProgressNotification(
        enabled: Boolean,
        statusText: String,
        progressPercent: Int,
        indeterminate: Boolean
    ) {
        if (!enabled || !hasNotificationPermission()) return
        val notification = buildProgressNotification(statusText, progressPercent, indeterminate)
        NotificationManagerCompat.from(context).notify(PROGRESS_NOTIFICATION_ID, notification)
    }

    fun showCompletedNotification(enabled: Boolean, repoName: String, apkFileName: String) {
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
        if (!enabled || !hasNotificationPermission()) return

        val body = "$repoName tayyor! $apkFileName yuklab olindi va Mening APKlarim bo'limiga qo'shildi."
        val notification = NotificationCompat.Builder(context, CHANNEL_READY_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("${context.getString(R.string.notif_ready_title)} — $repoName.apk")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent())
            .build()

        NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, notification)
    }

    fun showErrorNotification(enabled: Boolean, repoName: String, errorMessage: String) {
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
        if (!enabled || !hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_READY_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("${context.getString(R.string.notif_failed_title)} ($repoName)")
            .setContentText(errorMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(errorMessage))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent())
            .build()

        NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, notification)
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
        NotificationManagerCompat.from(context).cancel(RESULT_NOTIFICATION_ID)
    }
}
