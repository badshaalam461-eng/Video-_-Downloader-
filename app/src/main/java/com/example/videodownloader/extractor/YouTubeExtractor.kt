package com.example.videodownloader.extractor

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo

data class YouTubeStreamResult(
    val streamUrl: String,
    val title: String,
    val mimeType: String
)

object YouTubeExtractor {

    fun isYouTubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com/watch") ||
            lower.contains("youtu.be/") ||
            lower.contains("youtube.com/shorts/")
    }

    fun extract(url: String): Result<YouTubeStreamResult> {
        return try {
            val service = NewPipe.getServiceByUrl(url)
            val extractor = service.getStreamExtractor(url)
            extractor.fetchPage()
            val info = StreamInfo.getInfo(extractor)

            val progressiveStreams = info.videoStreams.filter { !it.isVideoOnly }
            val best = progressiveStreams.maxByOrNull { it.height ?: 0 }
                ?: progressiveStreams.firstOrNull()

            if (best == null || best.url.isNullOrBlank()) {
                Result.failure(IllegalStateException("No downloadable stream found for this video"))
            } else {
                Result.success(
                    YouTubeStreamResult(
                        streamUrl = best.url!!,
                        title = info.name ?: "youtube_video",
                        mimeType = "video/mp4"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
