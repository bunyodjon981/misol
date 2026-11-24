package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApiService {

    @GET("user")
    suspend fun getAuthenticatedUser(): Response<GitHubUserDto>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRepoDto>

    @POST("user/repos")
    suspend fun createRepository(
        @Body request: CreateRepoRequest
    ): Response<GitHubRepoDto>

    @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    suspend fun getBranchRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): Response<GitRefResponseDto>

    @GET("repos/{owner}/{repo}/git/commits/{commitSha}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("commitSha") commitSha: String
    ): Response<GitCommitResponseDto>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body request: PutContentRequest
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateBlobRequest
    ): Response<CreateBlobResponseDto>

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateTreeRequest
    ): Response<CreateTreeResponseDto>

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateCommitRequest
    ): Response<CreateCommitResponseDto>

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateBranchRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body request: UpdateRefRequest
    ): Response<GitRefResponseDto>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10
    ): Response<WorkflowRunsResponseDto>

    @GET("repos/{owner}/{repo}/actions/runs/{runId}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): Response<WorkflowRunDto>

    @GET("repos/{owner}/{repo}/actions/runs/{runId}/jobs")
    suspend fun getWorkflowJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): Response<WorkflowJobsResponseDto>

    @GET("repos/{owner}/{repo}/actions/runs/{runId}/artifacts")
    suspend fun listRunArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): Response<ArtifactsResponseDto>
}
