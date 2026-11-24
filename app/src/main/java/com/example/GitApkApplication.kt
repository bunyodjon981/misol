package com.example

import android.app.Application
import com.example.data.local.ApkRepository
import com.example.data.local.AppDatabase
import com.example.data.prefs.SettingsRepository
import com.example.engine.BuildPipelineManager
import com.example.service.BuildNotificationHelper

class GitApkApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var apkRepository: ApkRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var notificationHelper: BuildNotificationHelper
        private set

    lateinit var buildPipelineManager: BuildPipelineManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        apkRepository = ApkRepository(this, database.apkDao())
        settingsRepository = SettingsRepository(this)
        notificationHelper = BuildNotificationHelper(this)
        buildPipelineManager = BuildPipelineManager(
            context = this,
            apkRepository = apkRepository,
            settingsRepository = settingsRepository,
            notificationHelper = notificationHelper
        )
    }
}
