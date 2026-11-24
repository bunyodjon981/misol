package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUserDto(
    val login: String,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    val name: String,
    val description: String = "Android project uploaded by GitAPK",
    val private: Boolean = true,
    @Json(name = "auto_init") val autoInit: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GitHubRepoDto(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val private: Boolean,
    @Json(name = "default_branch") val defaultBranch: String = "main"
)

@JsonClass(generateAdapter = true)
data class GitRefObjectDto(
    val sha: String,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class GitRefResponseDto(
    val ref: String,
    @Json(name = "object") val obj: GitRefObjectDto
)

@JsonClass(generateAdapter = true)
data class GitTreeRefDto(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class GitCommitResponseDto(
    val sha: String,
    val tree: GitTreeRefDto
)

@JsonClass(generateAdapter = true)
data class PutContentRequest(
    val message: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class CreateBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

@JsonClass(generateAdapter = true)
data class CreateBlobResponseDto(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class GitTreeEntryRequest(
    val path: String,
    val mode: String,
    val type: String = "blob",
    val sha: String
)

@JsonClass(generateAdapter = true)
data class CreateTreeRequest(
    @Json(name = "base_tree") val baseTree: String? = null,
    val tree: List<GitTreeEntryRequest>
)

@JsonClass(generateAdapter = true)
data class CreateTreeResponseDto(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class CreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String>
)

@JsonClass(generateAdapter = true)
data class CreateCommitResponseDto(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class UpdateRefRequest(
    val sha: String,
    val force: Boolean = true
)

@JsonClass(generateAdapter = true)
data class WorkflowRunsResponseDto(
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "workflow_runs") val workflowRuns: List<WorkflowRunDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkflowRunDto(
    val id: Long,
    val name: String? = null,
    @Json(name = "head_sha") val headSha: String = "",
    val status: String? = null,
    val conclusion: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class WorkflowJobsResponseDto(
    @Json(name = "total_count") val totalCount: Int = 0,
    val jobs: List<WorkflowJobDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkflowJobDto(
    val id: Long,
    val name: String,
    val status: String? = null,
    val conclusion: String? = null,
    val steps: List<WorkflowStepDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkflowStepDto(
    val name: String,
    val status: String? = null,
    val conclusion: String? = null,
    val number: Int = 0
)

@JsonClass(generateAdapter = true)
data class ArtifactsResponseDto(
    @Json(name = "total_count") val totalCount: Int = 0,
    val artifacts: List<ArtifactDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArtifactDto(
    val id: Long,
    val name: String,
    @Json(name = "size_in_bytes") val sizeInBytes: Long = 0L,
    @Json(name = "archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean = false
)
