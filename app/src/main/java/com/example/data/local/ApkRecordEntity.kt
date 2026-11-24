package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apk_records")
data class ApkRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val appName: String,
    val repoName: String,
    val ownerUsername: String,
    val fileName: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val workflowRunId: Long,
    val commitSha: String,
    val branchName: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
