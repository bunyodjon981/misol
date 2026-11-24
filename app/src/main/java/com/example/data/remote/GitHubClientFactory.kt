package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object GitHubClientFactory {

    private const val GITHUB_API_BASE_URL = "https://api.github.com/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    fun createApiService(token: String): GitHubApiService {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "GitAPK-Android-Builder/1.0")
                .build()
            chain.proceed(request)
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApiService::class.java)
    }

    /**
     * Downloads an artifact ZIP from GitHub's archive_download_url.
     * Handles the 302 redirect cleanly without forwarding the GitHub Authorization
     * header to Azure Blob Storage signed URLs.
     */
    fun downloadArtifactZip(
        archiveDownloadUrl: String,
        token: String,
        destinationZipFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ) {
        val noRedirectClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        val initialRequest = Request.Builder()
            .url(archiveDownloadUrl)
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "GitAPK-Android-Builder/1.0")
            .build()

        val downloadTargetUrl: String = noRedirectClient.newCall(initialRequest).execute().use { resp ->
            when (resp.code) {
                in 300..399 -> {
                    resp.header("Location")
                        ?: error("GitHub artifact yo'naltirish manzili topilmadi")
                }
                in 200..299 -> {
                    writeResponseBodyToFile(resp.body, destinationZipFile, onProgress)
                    return
                }
                else -> error("Artifact yuklab olishda HTTP ${resp.code} xatosi")
            }
        }

        val cleanDownloadClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()

        val redirectRequest = Request.Builder()
            .url(downloadTargetUrl)
            .build()

        cleanDownloadClient.newCall(redirectRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("Artifact faylni yuklashda HTTP ${resp.code} xatosi")
            }
            writeResponseBodyToFile(resp.body, destinationZipFile, onProgress)
        }
    }

    private fun writeResponseBodyToFile(
        body: okhttp3.ResponseBody?,
        destination: File,
        onProgress: (Long, Long) -> Unit
    ) {
        val responseBody = body ?: error("Bo'sh artifact javobi")
        val contentLength = responseBody.contentLength()
        destination.parentFile?.mkdirs()

        responseBody.byteStream().use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                var totalRead = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    totalRead += read
                    onProgress(totalRead, contentLength)
                }
            }
        }
    }
}
