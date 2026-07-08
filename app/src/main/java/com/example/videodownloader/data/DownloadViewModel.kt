package com.example.videodownloader.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.videodownloader.download.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadUiState(
    val url: String = "",
    val isChecking: Boolean = false,
    val checkResult: LinkCheckResult? = null,
    val downloadProgress: Int? = null,
    val downloadState: String? = null // "RUNNING", "SUCCEEDED", "FAILED"
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private val workManager = WorkManager.getInstance(application)

    fun onUrlChanged(newUrl: String) {
        _uiState.value = _uiState.value.copy(url = newUrl, checkResult = null)
    }

    fun checkLink() {
        val url = _uiState.value.url
        if (url.isBlank()) return

        _uiState.value = _uiState.value.copy(isChecking = true, checkResult = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { LinkChecker.check(url) }
            _uiState.value = _uiState.value.copy(isChecking = false, checkResult = result)
        }
    }

    fun startDownload() {
        val result = _uiState.value.checkResult
        if (result !is LinkCheckResult.DirectMedia) return

        val inputData = workDataOf(
            DownloadWorker.KEY_URL to result.url,
            DownloadWorker.KEY_FILE_NAME to result.fileName,
            DownloadWorker.KEY_MIME_TYPE to result.mimeType
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(request)
        _uiState.value = _uiState.value.copy(downloadState = "RUNNING", downloadProgress = 0)

        viewModelScope.launch {
            workManager.getWorkInfoByIdLiveData(request.id).asFlow().collectLatest { info ->
                if (info == null) return@collectLatest
                val progress = info.progress.getInt(DownloadWorker.KEY_PROGRESS, -1)
                if (progress >= 0) {
                    _uiState.value = _uiState.value.copy(downloadProgress = progress)
                }
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> _uiState.value =
                        _uiState.value.copy(downloadState = "SUCCEEDED", downloadProgress = 100)
                    WorkInfo.State.FAILED -> _uiState.value =
                        _uiState.value.copy(downloadState = "FAILED")
                    else -> Unit
                }
            }
        }
    }
}
