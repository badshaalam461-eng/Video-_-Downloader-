package com.example.videodownloader.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DownloadedVideo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val dateAddedSeconds: Long
)

object DownloadHistoryRepository {

    suspend fun listDownloads(context: Context): List<DownloadedVideo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DownloadedVideo>()

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.RELATIVE_PATH
        )

        // Only show videos this app saved, identified by our RELATIVE_PATH folder.
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        } else null
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("Movies/VideoDownloader%")
        } else null

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                results.add(
                    DownloadedVideo(
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "video",
                        sizeBytes = cursor.getLong(sizeCol),
                        dateAddedSeconds = cursor.getLong(dateCol)
                    )
                )
            }
        }

        results
    }

    suspend fun delete(context: Context, video: DownloadedVideo): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(video.uri, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }
}
