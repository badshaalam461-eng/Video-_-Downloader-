package com.example.videodownloader.extractor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import java.util.concurrent.TimeUnit

class OkHttpDownloaderImpl private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        @Volatile
        private var instance: OkHttpDownloaderImpl? = null

        fun getInstance(): OkHttpDownloaderImpl =
            instance ?: synchronized(this) {
                instance ?: OkHttpDownloaderImpl().also { instance = it }
            }
    }

    override fun execute(request: NPRequest): NPResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()

        val builder = Request.Builder().url(url)

        for ((key, values) in headers) {
            for (value in values) {
                builder.addHeader(key, value)
            }
        }

        val dataToSend = request.dataToSend()
        when {
            httpMethod.equals("POST", ignoreCase = true) -> {
                val body = (dataToSend ?: ByteArray(0)).toRequestBody()
                builder.post(body)
            }
            httpMethod.equals("HEAD", ignoreCase = true) -> builder.head()
            else -> builder.get()
        }

        client.newCall(builder.build()).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            val responseHeaders = response.headers.toMultimap()
            return NPResponse(
                response.code,
                response.message,
                responseHeaders,
                bodyString,
                response.request.url.toString()
            )
        }
    }
}
