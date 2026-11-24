package com.example.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.GitApkApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ApkBuildForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as GitApkApplication
        val notificationHelper = app.notificationHelper
        val pipelineManager = app.buildPipelineManager

        val initialState = pipelineManager.state.value
        val initialNotification = notificationHelper.buildProgressNotification(
            statusText = initialState.statusText.ifBlank { "APK yigʻilmoqda..." },
            progressPercent = (initialState.progress * 100).toInt(),
            indeterminate = initialState.progress <= 0.05f
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                BuildNotificationHelper.PROGRESS_NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(
                BuildNotificationHelper.PROGRESS_NOTIFICATION_ID,
                initialNotification
            )
        }

        observerJob?.cancel()
        observerJob = serviceScope.launch {
            pipelineManager.state.collectLatest { state ->
                val enabled = app.settingsRepository.isNotificationsEnabled()
                if (!state.isRunning || !enabled) {
                    androidx.core.app.ServiceCompat.stopForeground(
                        this@ApkBuildForegroundService,
                        androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                } else {
                    notificationHelper.updateProgressNotification(
                        enabled = true,
                        statusText = state.statusText.ifBlank { state.stage.labelUz },
                        progressPercent = (state.progress * 100).toInt(),
                        indeterminate = state.progress <= 0.05f
                    )
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
