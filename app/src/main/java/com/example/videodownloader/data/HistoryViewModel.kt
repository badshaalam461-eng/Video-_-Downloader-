package com.example.videodownloader.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = true,
    val videos: List<DownloadedVideo> = emptyList()
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val videos = DownloadHistoryRepository.listDownloads(getApplication())
            _uiState.value = HistoryUiState(isLoading = false, videos = videos)
        }
    }

    fun delete(video: DownloadedVideo) {
        viewModelScope.launch {
            DownloadHistoryRepository.delete(getApplication(), video)
            refresh()
        }
    }
}
