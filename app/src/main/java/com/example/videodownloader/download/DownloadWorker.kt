package com.example.videodownloader.download

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var currentFileName: String = "video"

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "video_${System.currentTimeMillis()}.mp4"
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "video/mp4"
        currentFileName = fileName

        // Show the initial notification immediately so Android treats this as a
        // legitimate foreground-service-backed background task (required on
        // Android 12+ for long-running work, and gives the user visible progress).
        setForeground(makeForegroundInfo(percent = -1))

        return try {
            downloadToMediaStore(url, fileName, mimeType)
            notifyDone(fileName, success = true)
            Result.success()
        } catch (e: Exception) {
            notifyDone(fileName, success = false)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        }
    }

    private fun makeForegroundInfo(percent: Int): ForegroundInfo {
        val notification = DownloadNotifications.buildProgressNotification(
            applicationContext, currentFileName, percent
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(DownloadNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun notifyDone(fileName: String, success: Boolean) {
        val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        val notification = DownloadNotifications.buildCompleteNotification(applicationContext, fileName, success)
        manager?.notify(DownloadNotifications.NOTIFICATION_ID, notification)
    }

    private suspend fun downloadToMediaStore(url: String, fileName: String, mimeType: String) {
        val resolver = applicationContext.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/VideoDownloader")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, contentValues)
            ?: throw IllegalStateException("Could not create MediaStore entry")

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Server returned ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()

            resolver.openOutputStream(itemUri)?.use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesCopied = 0L
                    var lastReportedPercent = -1
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read == -1) break
                        outputStream.write(buffer, 0, read)
                        bytesCopied += read

                        if (totalBytes > 0) {
                            val percent = ((bytesCopied * 100) / totalBytes).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                setProgress(workDataOf(KEY_PROGRESS to percent))
                                setForeground(makeForegroundInfo(percent))
                            }
                        }
                    }
                }
            } ?: throw IllegalStateException("Could not open output stream")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
        }
    }
}
