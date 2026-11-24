package com.example.engine

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

data class ExtractedProjectFile(
    val relativePath: String,
    val base64Content: String,
    val isExecutable: Boolean,
    val sizeBytes: Long
)

data class ParsedProjectZip(
    val files: List<ExtractedProjectFile>,
    val detectedAppName: String?,
    val injectedWorkflow: Boolean,
    val totalBytes: Long
)

data class ExtractedApkResult(
    val file: File,
    val fileName: String,
    val sizeBytes: Long
)

object ZipProcessor {

    private const val MAX_ENTRIES = 5000
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 150L * 1024L * 1024L
    private const val WORKFLOW_PATH = ".github/workflows/android-apk.yml"

    private val ignoredPrefixes = listOf(
        ".git/",
        ".gradle/",
        ".idea/",
        "build/",
        "app/build/",
        "captures/",
        "__MACOSX/"
    )

    private val ignoredExactFiles = setOf(
        ".DS_Store",
        "Thumbs.db",
        "local.properties"
    )

    fun extractProjectZip(
        context: Context,
        zipUri: Uri,
        fallbackAppName: String
    ): ParsedProjectZip {
        val rawEntries = mutableListOf<Pair<String, ByteArray>>()
        var totalSize = 0L
        var entryCount = 0

        val inputStream = context.contentResolver.openInputStream(zipUri)
            ?: error("Tanlangan ZIP faylni ochib bo'lmadi")

        ZipInputStream(inputStream.buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ENTRIES) {
                    error("ZIP ichida juda ko'p fayl bor (maksimum $MAX_ENTRIES)")
                }
                if (!entry.isDirectory) {
                    val sanitized = sanitizeRelativePath(entry.name)
                    if (sanitized != null) {
                        val buffer = ByteArray(8192)
                        val baos = ByteArrayOutputStream()
                        while (true) {
                            val read = zis.read(buffer)
                            if (read == -1) break
                            totalSize += read
                            if (totalSize > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                error("ZIP fayl hajmi juda katta (maksimum 150 MB)")
                            }
                            baos.write(buffer, 0, read)
                        }
                        rawEntries.add(sanitized to baos.toByteArray())
                    }
                }
                zis.closeEntry()
            }
        }

        if (rawEntries.isEmpty()) {
            error("ZIP fayl bo'sh yoki yaroqsiz")
        }

        // Strip single common root folder if all entries share one (e.g., "my-project/app/...")
        val strippedEntries = stripCommonRootFolder(rawEntries)

        // Filter out build artifacts, .git, local.properties, etc.
        val filteredEntries = strippedEntries.filter { (path, _) ->
            val fileName = path.substringAfterLast('/')
            !ignoredExactFiles.contains(fileName) &&
                ignoredPrefixes.none { prefix ->
                    path.startsWith(prefix) || path.contains("/$prefix")
                }
        }

        if (filteredEntries.isEmpty()) {
            error("ZIP ichida yuklash uchun loyiha fayllari topilmadi")
        }

        val hasWorkflow = filteredEntries.any { (path, _) ->
            path.startsWith(".github/workflows/") &&
                (path.endsWith(".yml") || path.endsWith(".yaml"))
        }

        var detectedAppName: String? = null
        val resultFiles = mutableListOf<ExtractedProjectFile>()

        for ((path, bytes) in filteredEntries) {
            if (path == "settings.gradle.kts" || path == "settings.gradle") {
                detectedAppName = parseRootProjectName(bytes.decodeToString()) ?: detectedAppName
            } else if (path == "app/src/main/res/values/strings.xml") {
                detectedAppName = parseAppNameFromStrings(bytes.decodeToString()) ?: detectedAppName
            }
            val isExec = path == "gradlew" || path.endsWith(".sh")
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            resultFiles.add(
                ExtractedProjectFile(
                    relativePath = path,
                    base64Content = encoded,
                    isExecutable = isExec,
                    sizeBytes = bytes.size.toLong()
                )
            )
        }

        var injectedWorkflow = false
        if (!hasWorkflow) {
            val workflowBytes = buildAndroidWorkflowYaml(
                artifactName = sanitizeArtifactName(fallbackAppName)
            ).toByteArray(Charsets.UTF_8)
            resultFiles.add(
                ExtractedProjectFile(
                    relativePath = WORKFLOW_PATH,
                    base64Content = Base64.encodeToString(workflowBytes, Base64.NO_WRAP),
                    isExecutable = false,
                    sizeBytes = workflowBytes.size.toLong()
                )
            )
            injectedWorkflow = true
        }

        return ParsedProjectZip(
            files = resultFiles,
            detectedAppName = detectedAppName,
            injectedWorkflow = injectedWorkflow,
            totalBytes = resultFiles.sumOf { it.sizeBytes }
        )
    }

    fun extractApkFromArtifactZip(
        artifactZipFile: File,
        apkOutputDir: File,
        repoName: String
    ): ExtractedApkResult {
        apkOutputDir.mkdirs()
        val canonicalBase = apkOutputDir.canonicalPath + File.separator
        val extractedApks = mutableListOf<File>()

        ZipInputStream(artifactZipFile.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    val safeName = sanitizeRelativePath(entry.name)?.substringAfterLast('/')
                    if (safeName != null && safeName.endsWith(".apk", ignoreCase = true)) {
                        val safeRepo = repoName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val timestamp = System.currentTimeMillis()
                        val targetFileName = "${safeRepo}_${timestamp}.apk"
                        val destFile = File(apkOutputDir, targetFileName)
                        if (!destFile.canonicalPath.startsWith(canonicalBase)) {
                            error("Xavfli ZIP yo'li aniqlandi")
                        }
                        destFile.outputStream().use { out ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = zis.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                            }
                        }
                        extractedApks.add(destFile)
                    }
                }
                zis.closeEntry()
            }
        }

        if (extractedApks.isEmpty()) {
            error("Yuklab olingan ZIP arxiv ichida .apk fayli topilmadi")
        }

        val chosen = extractedApks.first()
        return ExtractedApkResult(
            file = chosen,
            fileName = chosen.name,
            sizeBytes = chosen.length()
        )
    }

    private fun sanitizeRelativePath(rawName: String): String? {
        val normalized = rawName.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return null
        val segments = normalized.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            return null
        }
        return segments.joinToString("/")
    }

    private fun stripCommonRootFolder(
        entries: List<Pair<String, ByteArray>>
    ): List<Pair<String, ByteArray>> {
        val firstSegments = entries.map { it.first.substringBefore('/') }.distinct()
        val allHaveSlash = entries.all { it.first.contains('/') }
        return if (firstSegments.size == 1 && allHaveSlash) {
            val prefix = firstSegments.first() + "/"
            entries.map { (path, bytes) -> path.removePrefix(prefix) to bytes }
        } else {
            entries
        }
    }

    private fun parseRootProjectName(content: String): String? {
        val regex = Regex("""rootProject\.name\s*=\s*["']([^"']+)["']""")
        return regex.find(content)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun parseAppNameFromStrings(content: String): String? {
        val regex = Regex("""<string\s+name="app_name"[^>]*>([^<]+)</string>""")
        return regex.find(content)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun sanitizeArtifactName(name: String): String {
        val cleaned = name.replace(Regex("[^a-zA-Z0-9_-]"), "-").trim('-')
        return if (cleaned.isBlank()) "app-debug-apk" else "$cleaned-apk"
    }

    private fun buildAndroidWorkflowYaml(artifactName: String): String = """
        name: Build Android APK

        on:
          workflow_dispatch:
          push:
            branches: [ "main", "master" ]

        permissions:
          contents: read

        jobs:
          build:
            runs-on: ubuntu-latest
            steps:
              - name: Checkout repository
                uses: actions/checkout@v4

              - name: Set up JDK 21
                uses: actions/setup-java@v4
                with:
                  java-version: '21'
                  distribution: 'temurin'

              - name: Setup Android SDK
                uses: android-actions/setup-android@v3

              - name: Setup Gradle
                uses: gradle/actions/setup-gradle@v4

              - name: Build Debug APK
                run: |
                  if [ -f "./gradlew" ]; then
                    chmod +x ./gradlew
                    ./gradlew assembleDebug --no-daemon
                  else
                    WRAPPER_VER=${'$'}(grep -oE 'gradle-[0-9]+(\.[0-9]+)+' gradle/wrapper/gradle-wrapper.properties 2>/dev/null | head -n1 | sed 's/gradle-//' || true)
                    if [ -n "${'$'}WRAPPER_VER" ]; then
                      gradle wrapper --gradle-version "${'$'}WRAPPER_VER" || true
                    fi
                    if [ -f "./gradlew" ]; then
                      chmod +x ./gradlew
                      ./gradlew assembleDebug --no-daemon
                    else
                      gradle assembleDebug --no-daemon
                    fi
                  fi

              - name: Upload APK Artifact
                uses: actions/upload-artifact@v4
                with:
                  name: $artifactName
                  path: "**/build/outputs/apk/debug/*.apk"
                  if-no-files-found: error
    """.trimIndent() + "\n"
}
