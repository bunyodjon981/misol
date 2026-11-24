package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.ApkRecordEntity
import com.example.data.local.ApkRepository
import com.example.data.prefs.SettingsRepository
import com.example.data.remote.CreateBlobRequest
import com.example.data.remote.CreateCommitRequest
import com.example.data.remote.CreateRepoRequest
import com.example.data.remote.CreateTreeRequest
import com.example.data.remote.GitHubApiService
import com.example.data.remote.GitHubClientFactory
import com.example.data.remote.GitTreeEntryRequest
import com.example.data.remote.PutContentRequest
import com.example.data.remote.UpdateRefRequest
import com.example.service.ApkBuildForegroundService
import com.example.service.BuildNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BuildStage(val stepIndex: Int, val labelUz: String) {
    IDLE(0, "Tayyor"),
    AUTHENTICATING(1, "GitHub'ga ulanmoqda..."),
    CHECKING_REPO(2, "Repository tayyorlanmoqda..."),
    EXTRACTING_ZIP(3, "ZIP loyiha ochilmoqda..."),
    UPLOADING_FILES(4, "Fayllar GitHub'ga yuklanmoqda..."),
    TRIGGERING_WORKFLOW(5, "GitHub Actions ishga tushirilmoqda..."),
    BUILDING_APK(6, "APK yigʻilmoqda..."),
    DOWNLOADING_ARTIFACT(7, "Tayyor APK yuklab olinmoqda..."),
    EXTRACTING_APK(8, "APK arxivdan chiqarilmoqda..."),
    COMPLETED(9, "Ilova tayyor!"),
    ERROR(0, "Xatolik yuz berdi")
}

enum class LogLevel {
    INFO, STEP, SUCCESS, WARN, ERROR
}

data class TerminalLogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

data class BuildPipelineState(
    val stage: BuildStage = BuildStage.IDLE,
    val statusText: String = "",
    val progress: Float = 0f,
    val isRunning: Boolean = false,
    val logs: List<TerminalLogEntry> = emptyList(),
    val errorMessage: String? = null,
    val completedApkId: Long? = null,
    val completedFileName: String? = null
)

class BuildPipelineManager(
    private val context: Context,
    private val apkRepository: ApkRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: BuildNotificationHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeBuildJob: Job? = null
    private var logSequence = 0L

    @Volatile
    private var notificationsEnabledCache: Boolean = true

    init {
        scope.launch {
            settingsRepository.notificationsEnabledFlow.collect { enabled ->
                notificationsEnabledCache = enabled
            }
        }
    }

    private val _state = MutableStateFlow(BuildPipelineState())
    val state: StateFlow<BuildPipelineState> = _state.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun clearLogs() {
        if (!_state.value.isRunning) {
            _state.update {
                it.copy(
                    logs = emptyList(),
                    stage = BuildStage.IDLE,
                    statusText = "",
                    progress = 0f,
                    errorMessage = null,
                    completedApkId = null,
                    completedFileName = null
                )
            }
        } else {
            _state.update { it.copy(logs = emptyList()) }
        }
    }

    fun onNotificationPreferenceChanged(enabled: Boolean) {
        if (!enabled) {
            stopForegroundServiceIfRunning()
            notificationHelper.cancelAll()
        } else {
            val current = _state.value
            if (current.isRunning) {
                startForegroundServiceIfAllowed()
                notificationHelper.updateProgressNotification(
                    enabled = true,
                    statusText = current.statusText.ifBlank { current.stage.labelUz },
                    progressPercent = (current.progress * 100).toInt(),
                    indeterminate = current.progress <= 0.02f
                )
            }
        }
    }

    fun cancelBuild() {
        activeBuildJob?.cancel()
        activeBuildJob = null
        appendLog(LogLevel.WARN, "Jarayon foydalanuvchi tomonidan bekor qilindi.")
        _state.update {
            it.copy(
                isRunning = false,
                stage = BuildStage.IDLE,
                statusText = "Bekor qilindi",
                progress = 0f
            )
        }
        stopForegroundServiceIfRunning()
        notificationHelper.cancelAll()
    }

    fun startBuild(
        usernameInput: String,
        tokenInput: String,
        repoNameInput: String,
        isPrivateRepo: Boolean,
        zipUri: Uri,
        zipDisplayName: String
    ) {
        if (_state.value.isRunning) return

        val cleanUsername = usernameInput.trim()
        val cleanToken = tokenInput.trim()
        val cleanRepo = repoNameInput.trim()

        activeBuildJob = scope.launch {
            _state.value = BuildPipelineState(
                stage = BuildStage.AUTHENTICATING,
                statusText = "GitHub'ga ulanmoqda...",
                progress = 0.04f,
                isRunning = true,
                logs = emptyList()
            )

            val notificationsOn = settingsRepository.isNotificationsEnabled()
            if (notificationsOn) {
                startForegroundServiceIfAllowed()
            }

            try {
                appendLog(LogLevel.STEP, "GitAPK bulutli yig'ish jarayoni boshlandi...")
                appendLog(LogLevel.INFO, "Tanlangan fayl: $zipDisplayName")

                settingsRepository.saveCredentials(
                    username = cleanUsername,
                    token = cleanToken,
                    repoName = cleanRepo,
                    isPrivate = isPrivateRepo
                )

                val api = GitHubClientFactory.createApiService(cleanToken)

                // 1. Authenticate
                updateStage(BuildStage.AUTHENTICATING, "GitHub hisob tekshirilmoqda...", 0.08f)
                val userResp = api.getAuthenticatedUser()
                if (!userResp.isSuccessful || userResp.body() == null) {
                    val code = userResp.code()
                    error("GitHub autentifikatsiya xatosi (HTTP $code). Token va username ni tekshiring.")
                }
                val authedUser = userResp.body()!!
                val effectiveOwner = cleanUsername.ifBlank { authedUser.login }
                appendLog(LogLevel.SUCCESS, "GitHub'ga ulandi: @${authedUser.login}")

                // 2. Check or create repository
                updateStage(BuildStage.CHECKING_REPO, "Repository tekshirilmoqda: $effectiveOwner/$cleanRepo", 0.14f)
                val existingRepoResp = api.getRepository(effectiveOwner, cleanRepo)
                val repoDto = if (existingRepoResp.isSuccessful && existingRepoResp.body() != null) {
                    appendLog(LogLevel.INFO, "Mavjud repository topildi: $effectiveOwner/$cleanRepo")
                    existingRepoResp.body()!!
                } else if (existingRepoResp.code() == 404) {
                    appendLog(LogLevel.STEP, "Yangi repository yaratilmoqda: $cleanRepo (private=$isPrivateRepo)...")
                    val createResp = api.createRepository(
                        CreateRepoRequest(
                            name = cleanRepo,
                            description = "Android project built with GitAPK",
                            private = isPrivateRepo,
                            autoInit = true
                        )
                    )
                    if (!createResp.isSuccessful || createResp.body() == null) {
                        val errMsg = createResp.errorBody()?.string()?.take(200) ?: "HTTP ${createResp.code()}"
                        error("Repository yaratib bo'lmadi: $errMsg")
                    }
                    val created = createResp.body()!!
                    appendLog(LogLevel.SUCCESS, "Repository yaratildi: ${created.fullName}")
                    delay(1500)
                    created
                } else {
                    error("Repository tekshirishda xato (HTTP ${existingRepoResp.code()})")
                }

                val branch = repoDto.defaultBranch.ifBlank { "main" }

                // 3. Extract ZIP
                updateStage(BuildStage.EXTRACTING_ZIP, "ZIP fayl ochilmoqda va tahlil qilinmoqda...", 0.20f)
                val parsedZip = ZipProcessor.extractProjectZip(
                    context = context,
                    zipUri = zipUri,
                    fallbackAppName = cleanRepo
                )
                val appDisplayName = parsedZip.detectedAppName ?: cleanRepo
                appendLog(
                    LogLevel.SUCCESS,
                    "ZIP ochildi: ${parsedZip.files.size} ta fayl (${formatBytes(parsedZip.totalBytes)})"
                )
                if (parsedZip.injectedWorkflow) {
                    appendLog(
                        LogLevel.INFO,
                        "Avtomatik GitHub Actions workflow qo'shildi (.github/workflows/android-apk.yml)"
                    )
                } else {
                    appendLog(LogLevel.INFO, "Loyihadagi mavjud GitHub Actions workflow ishlatiladi")
                }

                // 4. Ensure initial branch commit exists
                val parentCommitSha = ensureBranchHeadCommit(api, effectiveOwner, cleanRepo, branch)
                appendLog(LogLevel.INFO, "Branch '$branch' tayyor (HEAD: ${parentCommitSha.take(7)})")

                // 5. Upload files as Git Blobs concurrently (throttled)
                updateStage(
                    BuildStage.UPLOADING_FILES,
                    "Loyiha fayllari GitHub'ga yuklanmoqda (0/${parsedZip.files.size})...",
                    0.25f
                )
                val semaphore = Semaphore(5)
                var uploadedCount = 0
                val countLock = Any()

                val treeEntries: List<GitTreeEntryRequest> = coroutineScope {
                    parsedZip.files.map { projectFile ->
                        async {
                            semaphore.withPermit {
                                val blobResp = api.createBlob(
                                    owner = effectiveOwner,
                                    repo = cleanRepo,
                                    request = CreateBlobRequest(content = projectFile.base64Content)
                                )
                                if (!blobResp.isSuccessful || blobResp.body() == null) {
                                    error("${projectFile.relativePath} faylini yuklashda xato (HTTP ${blobResp.code()})")
                                }
                                val currentCount = synchronized(countLock) {
                                    uploadedCount++
                                    uploadedCount
                                }
                                val uploadProgress = 0.25f + (0.28f * currentCount / parsedZip.files.size)
                                if (currentCount == 1 || currentCount % 5 == 0 || currentCount == parsedZip.files.size) {
                                    updateStage(
                                        BuildStage.UPLOADING_FILES,
                                        "Yuklanmoqda ($currentCount/${parsedZip.files.size}): ${projectFile.relativePath}",
                                        uploadProgress
                                    )
                                    appendLog(
                                        LogLevel.INFO,
                                        "[$currentCount/${parsedZip.files.size}] Yuklandi: ${projectFile.relativePath}"
                                    )
                                }
                                GitTreeEntryRequest(
                                    path = projectFile.relativePath,
                                    mode = if (projectFile.isExecutable) "100755" else "100644",
                                    type = "blob",
                                    sha = blobResp.body()!!.sha
                                )
                            }
                        }
                    }.awaitAll()
                }

                // 6. Create Tree + Commit + Update Branch Ref
                updateStage(BuildStage.TRIGGERING_WORKFLOW, "Git commit yaratilmoqda...", 0.55f)
                val treeResp = api.createTree(
                    owner = effectiveOwner,
                    repo = cleanRepo,
                    request = CreateTreeRequest(baseTree = null, tree = treeEntries)
                )
                if (!treeResp.isSuccessful || treeResp.body() == null) {
                    error("Git Tree yaratishda xato (HTTP ${treeResp.code()})")
                }

                val commitResp = api.createCommit(
                    owner = effectiveOwner,
                    repo = cleanRepo,
                    request = CreateCommitRequest(
                        message = "Upload Android project via GitAPK",
                        tree = treeResp.body()!!.sha,
                        parents = listOf(parentCommitSha)
                    )
                )
                if (!commitResp.isSuccessful || commitResp.body() == null) {
                    error("Git Commit yaratishda xato (HTTP ${commitResp.code()})")
                }
                val newCommitSha = commitResp.body()!!.sha

                val patchResp = api.updateBranchRef(
                    owner = effectiveOwner,
                    repo = cleanRepo,
                    branch = branch,
                    request = UpdateRefRequest(sha = newCommitSha, force = true)
                )
                if (!patchResp.isSuccessful) {
                    error("Branch '$branch' yangilashda xato (HTTP ${patchResp.code()})")
                }
                appendLog(
                    LogLevel.SUCCESS,
                    "Barcha fayllar push qilindi! Commit: ${newCommitSha.take(7)}"
                )

                // 7. Find & Poll GitHub Actions workflow run
                updateStage(BuildStage.TRIGGERING_WORKFLOW, "GitHub Actions ishga tushishi kutilmoqda...", 0.58f)
                appendLog(LogLevel.STEP, "GitHub Actions workflow qidirilmoqda...")

                var runId: Long? = null
                repeat(20) {
                    if (!isActive) throw CancellationException()
                    val runsResp = api.listWorkflowRuns(effectiveOwner, cleanRepo, perPage = 10)
                    val matched = runsResp.body()?.workflowRuns?.firstOrNull {
                        it.headSha.equals(newCommitSha, ignoreCase = true)
                    }
                    if (matched != null) {
                        runId = matched.id
                        return@repeat
                    }
                    delay(3000)
                }

                val activeRunId = runId
                    ?: error("GitHub Actions workflow ishga tushmadi. Token 'workflow' ruxsatiga ega ekanligini tekshiring.")

                appendLog(LogLevel.SUCCESS, "Workflow boshlandi (Run #$activeRunId)")
                updateStage(BuildStage.BUILDING_APK, "APK yigʻilmoqda...", 0.62f)

                val loggedSteps = mutableSetOf<String>()
                var finalConclusion: String? = null
                var pollIteration = 0

                while (isActive) {
                    val runResp = api.getWorkflowRun(effectiveOwner, cleanRepo, activeRunId)
                    val runDto = runResp.body()
                    val jobsResp = api.getWorkflowJobs(effectiveOwner, cleanRepo, activeRunId)
                    val jobs = jobsResp.body()?.jobs.orEmpty()

                    for (job in jobs) {
                        for (step in job.steps) {
                            val stepKey = "${job.id}:${step.number}:${step.status}:${step.conclusion}"
                            if (step.status == "in_progress" && loggedSteps.add("${job.id}:${step.number}:started")) {
                                val stepMsg = "APK yigʻilmoqda: ${step.name}"
                                appendLog(LogLevel.STEP, "▶ ${step.name}...")
                                val stepProgress = (0.63f + (pollIteration * 0.012f)).coerceAtMost(0.86f)
                                updateStage(BuildStage.BUILDING_APK, stepMsg, stepProgress)
                            } else if (step.status == "completed" && loggedSteps.add(stepKey)) {
                                val icon = if (step.conclusion == "success") "✓" else if (step.conclusion == "skipped") "↷" else "✗"
                                val level = when (step.conclusion) {
                                    "success" -> LogLevel.SUCCESS
                                    "skipped" -> LogLevel.INFO
                                    else -> LogLevel.ERROR
                                }
                                appendLog(level, "$icon ${step.name} (${step.conclusion ?: "done"})")
                            }
                        }
                    }

                    if (runDto?.status == "completed") {
                        finalConclusion = runDto.conclusion
                        break
                    }

                    pollIteration++
                    delay(4000)
                }

                if (finalConclusion != "success") {
                    error("APK yig'ish muvaffaqiyatsiz tugadi (holat: ${finalConclusion ?: "unknown"}). Loyiha Gradle xatolarini tekshiring.")
                }

                appendLog(LogLevel.SUCCESS, "GitHub Actions muvaffaqiyatli yakunlandi!")

                // 8. Fetch & Download Artifact ZIP
                updateStage(BuildStage.DOWNLOADING_ARTIFACT, "Tayyor APK arxivi yuklab olinmoqda...", 0.88f)
                val artifactsResp = api.listRunArtifacts(effectiveOwner, cleanRepo, activeRunId)
                val artifacts = artifactsResp.body()?.artifacts.orEmpty().filter { !it.expired }
                val targetArtifact = artifacts.firstOrNull {
                    it.name.contains("apk", ignoreCase = true)
                } ?: artifacts.firstOrNull()
                    ?: error("Yig'ilgan APK artifact topilmadi")

                appendLog(
                    LogLevel.INFO,
                    "Artifact topildi: ${targetArtifact.name} (${formatBytes(targetArtifact.sizeInBytes)})"
                )

                val tempArtifactZip = File(context.cacheDir, "artifact_${activeRunId}.zip")
                GitHubClientFactory.downloadArtifactZip(
                    archiveDownloadUrl = targetArtifact.archiveDownloadUrl,
                    token = cleanToken,
                    destinationZipFile = tempArtifactZip
                ) { read, total ->
                    if (total > 0) {
                        val dlProg = 0.88f + (0.07f * read / total)
                        updateStage(
                            BuildStage.DOWNLOADING_ARTIFACT,
                            "APK ZIP yuklab olinmoqda (${(read * 100 / total).toInt()}%)...",
                            dlProg
                        )
                    }
                }
                appendLog(LogLevel.SUCCESS, "Artifact ZIP yuklab olindi")

                // 9. Extract .apk from artifact ZIP automatically
                updateStage(BuildStage.EXTRACTING_APK, "ZIP ochilib APK ajratilmoqda...", 0.96f)
                val apksDir = File(context.filesDir, "apks")
                val extractedApk = ZipProcessor.extractApkFromArtifactZip(
                    artifactZipFile = tempArtifactZip,
                    apkOutputDir = apksDir,
                    repoName = cleanRepo
                )
                tempArtifactZip.delete()

                appendLog(
                    LogLevel.SUCCESS,
                    "APK chiqarildi: ${extractedApk.fileName} (${formatBytes(extractedApk.sizeBytes)})"
                )

                // 10. Save to Room DB
                val insertedId = apkRepository.insertApk(
                    ApkRecordEntity(
                        appName = appDisplayName,
                        repoName = cleanRepo,
                        ownerUsername = effectiveOwner,
                        fileName = extractedApk.fileName,
                        localFilePath = extractedApk.file.absolutePath,
                        fileSizeBytes = extractedApk.sizeBytes,
                        workflowRunId = activeRunId,
                        commitSha = newCommitSha,
                        branchName = branch
                    )
                )

                appendLog(
                    LogLevel.SUCCESS,
                    "Ilova tayyor! 'Mening APKlarim' bo'limiga saqlandi."
                )

                _state.update {
                    it.copy(
                        stage = BuildStage.COMPLETED,
                        statusText = "Ilova tayyor! ${extractedApk.fileName}",
                        progress = 1f,
                        isRunning = false,
                        completedApkId = insertedId,
                        completedFileName = extractedApk.fileName
                    )
                }

                val notifEnabledNow = settingsRepository.isNotificationsEnabled()
                notificationHelper.showCompletedNotification(
                    enabled = notifEnabledNow,
                    repoName = appDisplayName,
                    apkFileName = extractedApk.fileName
                )
                stopForegroundServiceIfRunning()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: "Noma'lum xatolik yuz berdi"
                appendLog(LogLevel.ERROR, "XATO: $message")
                _state.update {
                    it.copy(
                        stage = BuildStage.ERROR,
                        statusText = message,
                        isRunning = false,
                        errorMessage = message
                    )
                }
                val notifEnabledNow = settingsRepository.isNotificationsEnabled()
                notificationHelper.showErrorNotification(
                    enabled = notifEnabledNow,
                    repoName = cleanRepo.ifBlank { "GitAPK" },
                    errorMessage = message
                )
                stopForegroundServiceIfRunning()
            }
        }
    }

    private suspend fun ensureBranchHeadCommit(
        api: GitHubApiService,
        owner: String,
        repo: String,
        branch: String
    ): String {
        val refResp = api.getBranchRef(owner, repo, branch)
        if (refResp.isSuccessful && refResp.body() != null) {
            return refResp.body()!!.obj.sha
        }

        // Repository exists but has 0 commits (409 or 404). Initialize via README.md
        appendLog(LogLevel.INFO, "Bo'sh repository initsializatsiya qilinmoqda...")
        val readmeBase64 = android.util.Base64.encodeToString(
            "# $repo\nInitialized by GitAPK".toByteArray(),
            android.util.Base64.NO_WRAP
        )
        val initResp = api.putFileContent(
            owner = owner,
            repo = repo,
            path = "README.md",
            request = PutContentRequest(
                message = "Initial commit by GitAPK",
                content = readmeBase64
            )
        )
        if (!initResp.isSuccessful) {
            error("Bo'sh repository ni ishga tushirib bo'lmadi (HTTP ${initResp.code()})")
        }
        delay(1000)
        val newRefResp = api.getBranchRef(owner, repo, branch)
        return newRefResp.body()?.obj?.sha
            ?: error("Repository branch ($branch) SHA olinmadi")
    }

    private fun updateStage(stage: BuildStage, statusText: String, progress: Float) {
        _state.update {
            it.copy(
                stage = stage,
                statusText = statusText,
                progress = progress.coerceIn(0f, 1f)
            )
        }
        notificationHelper.updateProgressNotification(
            enabled = notificationsEnabledCache,
            statusText = statusText,
            progressPercent = (progress * 100).toInt(),
            indeterminate = progress <= 0.05f
        )
    }

    private fun appendLog(level: LogLevel, message: String) {
        val entry = TerminalLogEntry(
            id = ++logSequence,
            timestamp = timeFormat.format(Date()),
            level = level,
            message = message
        )
        _state.update { current ->
            val updated = (current.logs + entry).takeLast(400)
            current.copy(logs = updated)
        }
    }

    private fun startForegroundServiceIfAllowed() {
        runCatching {
            val intent = Intent(context, ApkBuildForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun stopForegroundServiceIfRunning() {
        runCatching {
            val intent = Intent(context, ApkBuildForegroundService::class.java)
            context.stopService(intent)
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            }
        }
    }
}
