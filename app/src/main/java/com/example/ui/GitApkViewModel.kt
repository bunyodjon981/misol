package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GitApkApplication
import com.example.data.local.ApkRecordEntity
import com.example.engine.BuildPipelineState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BuildFormState(
    val username: String = "",
    val token: String = "",
    val repoName: String = "",
    val isPrivateRepo: Boolean = true,
    val selectedZipUri: Uri? = null,
    val selectedZipName: String = "",
    val selectedZipSizeBytes: Long = 0L,
    val validationError: String? = null
)

class GitApkViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GitApkApplication
    private val settingsRepository = app.settingsRepository
    private val apkRepository = app.apkRepository
    private val pipelineManager = app.buildPipelineManager

    private val _formState = MutableStateFlow(BuildFormState())
    val formState: StateFlow<BuildFormState> = _formState.asStateFlow()

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.notificationsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val pipelineState: StateFlow<BuildPipelineState> = pipelineManager.state

    val apksList: StateFlow<List<ApkRecordEntity>> = apkRepository.allApks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _safExportRequest = MutableSharedFlow<ApkRecordEntity>(extraBufferCapacity = 4)
    val safExportRequest: SharedFlow<ApkRecordEntity> = _safExportRequest.asSharedFlow()

    init {
        viewModelScope.launch {
            val saved = settingsRepository.settingsFlow.first()
            _formState.update {
                it.copy(
                    username = saved.githubUsername,
                    token = saved.githubToken,
                    repoName = saved.lastRepoName,
                    isPrivateRepo = saved.isPrivateRepo
                )
            }
        }
    }

    fun onUsernameChanged(value: String) {
        _formState.update { it.copy(username = value, validationError = null) }
    }

    fun onTokenChanged(value: String) {
        _formState.update { it.copy(token = value, validationError = null) }
    }

    fun onRepoNameChanged(value: String) {
        _formState.update { it.copy(repoName = value, validationError = null) }
    }

    fun onPrivateRepoChanged(isPrivate: Boolean) {
        _formState.update { it.copy(isPrivateRepo = isPrivate) }
    }

    fun onZipUriSelected(uri: Uri) {
        val resolver = app.contentResolver
        var displayName = "project.zip"
        var fileSize = 0L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
            }
        }

        if (!displayName.endsWith(".zip", ignoreCase = true)) {
            _formState.update {
                it.copy(
                    validationError = "Iltimos, faqat .zip kengaytmali loyiha faylini tanlang"
                )
            }
            return
        }

        _formState.update {
            it.copy(
                selectedZipUri = uri,
                selectedZipName = displayName,
                selectedZipSizeBytes = fileSize,
                validationError = null
            )
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
            pipelineManager.onNotificationPreferenceChanged(enabled)
            val msg = if (enabled) {
                "Bildirishnomalar yoqildi"
            } else {
                "Bildirishnomalar o'chirildi — jarayon jimgina ishlaydi"
            }
            _snackbarEvents.tryEmit(msg)
        }
    }

    fun startBuild() {
        val form = _formState.value
        val username = form.username.trim()
        val token = form.token.trim()
        val repoName = form.repoName.trim()
        val zipUri = form.selectedZipUri

        val error = when {
            username.isEmpty() -> "GitHub username kiriting"
            token.isEmpty() -> "GitHub token (parol) kiriting"
            repoName.isEmpty() -> "Repository nomini kiriting"
            !repoName.matches(Regex("^[a-zA-Z0-9._-]+$")) ->
                "Repository nomida faqat harf, raqam, '.', '-', '_' bo'lishi mumkin"
            zipUri == null -> "Loyiha .zip faylini tanlang"
            else -> null
        }

        if (error != null) {
            _formState.update { it.copy(validationError = error) }
            return
        }

        pipelineManager.startBuild(
            usernameInput = username,
            tokenInput = token,
            repoNameInput = repoName,
            isPrivateRepo = form.isPrivateRepo,
            zipUri = zipUri!!,
            zipDisplayName = form.selectedZipName
        )
    }

    fun cancelBuild() {
        pipelineManager.cancelBuild()
    }

    fun clearLogs() {
        pipelineManager.clearLogs()
    }

    fun downloadApk(record: ApkRecordEntity) {
        viewModelScope.launch {
            val result = apkRepository.exportApkToDownloads(record)
            result.onSuccess { path ->
                _snackbarEvents.tryEmit("Yuklab olindi: $path")
            }.onFailure { err ->
                if (err.message == "SAF_REQUIRED") {
                    _safExportRequest.tryEmit(record)
                } else {
                    _snackbarEvents.tryEmit(err.message ?: "APK ni saqlab bo'lmadi")
                }
            }
        }
    }

    fun completeSafExport(record: ApkRecordEntity, destinationUri: Uri) {
        viewModelScope.launch {
            val result = apkRepository.copyApkToUri(record, destinationUri)
            result.onSuccess {
                _snackbarEvents.tryEmit("APK muvaffaqiyatli saqlandi: ${record.fileName}")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Saqlashda xatolik")
            }
        }
    }

    fun createShareIntent(record: ApkRecordEntity): Intent? {
        return apkRepository.createShareIntent(record)
    }

    fun deleteApk(record: ApkRecordEntity) {
        viewModelScope.launch {
            apkRepository.deleteApk(record)
            _snackbarEvents.tryEmit("${record.fileName} o'chirildi")
        }
    }
}
