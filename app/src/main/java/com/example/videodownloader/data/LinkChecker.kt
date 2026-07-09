package com.example.videodownloader.data

import com.example.videodownloader.extractor.YouTubeExtractor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class LinkCheckResult {
    data class DirectMedia(
        val url: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long?
    ) : LinkCheckResult()

    data class Unsupported(val reason: String) : LinkCheckResult()
    data class Error(val message: String) : LinkCheckResult()
}

object LinkChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val supportedMimePrefixes = listOf("video/", "audio/")
    private val supportedExtensions = listOf(
        ".mp4", ".mov", ".mkv", ".webm", ".m4v", ".3gp", ".avi"
    )

    fun check(rawUrl: String): LinkCheckResult {
        val url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return LinkCheckResult.Error("Please enter a valid http(s) URL")
        }

        if (YouTubeExtractor.isYouTubeUrl(url)) {
            val result = YouTubeExtractor.extract(url)
            return result.fold(
                onSuccess = { stream ->
                    val safeTitle = stream.title
                        .replace(Regex("[^A-Za-z0-9 _-]"), "")
                        .trim()
                        .ifBlank { "youtube_video" }
                    LinkCheckResult.DirectMedia(
                        url = stream.streamUrl,
                        fileName = "$safeTitle.mp4",
                        mimeType = stream.mimeType,
                        sizeBytes = null
                    )
                },
                onFailure = { e ->
                    LinkCheckResult.Error(e.message ?: "Could not extract this YouTube video")
                }
            )
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return LinkCheckResult.Error("Server returned ${response.code}")
                }

                val contentType = response.header("Content-Type")?.substringBefore(";")?.trim()
                val contentLength = response.header("Content-Length")?.toLongOrNull()
                val looksLikeMediaExtension = supportedExtensions.any {
                    url.substringBefore("?").endsWith(it, ignoreCase = true)
                }
                val looksLikeMediaMime = contentType != null &&
                    supportedMimePrefixes.any { contentType.startsWith(it, ignoreCase = true) }

                if (looksLikeMediaMime || looksLikeMediaExtension) {
                    val fileName = url.substringAfterLast("/").substringBefore("?").ifBlank {
                        "video_${System.currentTimeMillis()}.mp4"
                    }
                    LinkCheckResult.DirectMedia(
                        url = url,
                        fileName = fileName,
                        mimeType = contentType ?: "video/mp4",
                        sizeBytes = contentLength
                    )
                } else {
                    LinkCheckResult.Unsupported(
                        "This link doesn't point directly to a video file. " +
                            "Links from social apps (Instagram, TikTok, etc.) aren't supported yet."
                    )
                }
            }
        } catch (e: Exception) {
            LinkCheckResult.Error(e.message ?: "Could not reach that URL")
        }
    }
}
