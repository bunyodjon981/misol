package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class ApkRepository(
    private val context: Context,
    private val apkDao: ApkDao
) {
    val allApks: Flow<List<ApkRecordEntity>> = apkDao.getAllApks()

    suspend fun insertApk(record: ApkRecordEntity): Long = apkDao.insertApk(record)

    suspend fun deleteApk(record: ApkRecordEntity) = withContext(Dispatchers.IO) {
        val file = File(record.localFilePath)
        if (file.exists()) {
            file.delete()
        }
        apkDao.deleteApkById(record.id)
    }

    suspend fun exportApkToDownloads(record: ApkRecordEntity): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceFile = File(record.localFilePath)
                require(sourceFile.exists()) { "APK fayli topilmadi" }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, record.fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GitAPK")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Yuklamalar papkasiga fayl yaratib bo'lmadi")

                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: error("Fayl oqimini ochib bo'lmadi")

                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Downloads/GitAPK/${record.fileName}"
                } else {
                    error("SAF_REQUIRED")
                }
            }
        }

    suspend fun copyApkToUri(record: ApkRecordEntity, destinationUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceFile = File(record.localFilePath)
                require(sourceFile.exists()) { "APK fayli topilmadi" }
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: error("Tanlangan manzilga yozib bo'lmadi")
                Unit
            }
        }

    fun createShareIntent(record: ApkRecordEntity): Intent? {
        val file = File(record.localFilePath)
        if (!file.exists()) return null
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, record.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
