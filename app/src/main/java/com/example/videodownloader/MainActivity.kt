package com.example.videodownloader

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.videodownloader.data.DownloadViewModel
import com.example.videodownloader.data.LinkCheckResult

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: download still works without notifications, just silent */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // If the app was opened via "Share" from another app with a link, prefill it.
        val sharedText = if (intent?.action == android.content.Intent.ACTION_SEND) {
            intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
        } else null

        setContent {
            MaterialTheme {
                var showHistory by remember { mutableStateOf(false) }
                if (showHistory) {
                    HistoryScreen(onBack = { showHistory = false })
                } else {
                    DownloadScreen(
                        viewModel = viewModel,
                        initialUrl = sharedText,
                        onOpenHistory = { showHistory = true }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    initialUrl: String?,
    onOpenHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            viewModel.onUrlChanged(initialUrl)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Video Downloader", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onOpenHistory) { Text("Downloads") }
            }

            OutlinedTextField(
                value = state.url,
                onValueChange = { viewModel.onUrlChanged(it) },
                label = { Text("Paste video URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.checkLink() },
                    enabled = state.url.isNotBlank() && !state.isChecking
                ) {
                    Text(if (state.isChecking) "Checking..." else "Check Link")
                }

                if (state.checkResult is LinkCheckResult.DirectMedia) {
                    Button(onClick = { viewModel.startDownload() }) {
                        Text("Download")
                    }
                }
            }

            when (val result = state.checkResult) {
                is LinkCheckResult.DirectMedia -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("File: ${result.fileName}")
                            Text("Type: ${result.mimeType}")
                            result.sizeBytes?.let {
                                Text("Size: ${it / (1024 * 1024)} MB")
                            }
                        }
                    }
                }
                is LinkCheckResult.Unsupported -> {
                    Text(
                        result.reason,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is LinkCheckResult.Error -> {
                    Text(
                        "Error: ${result.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                null -> Unit
            }

            state.downloadProgress?.let { progress ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        when (state.downloadState) {
                            "SUCCEEDED" -> "Saved to Movies/VideoDownloader ✔"
                            "FAILED" -> "Download failed"
                            else -> "Downloading... $progress%"
                        }
                    )
                }
            }
        }
    }
}
