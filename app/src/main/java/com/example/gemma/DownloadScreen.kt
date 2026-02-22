package com.example.gemma

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun DownloadScreen(modelType: ModelType, onDownloadComplete: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Preparing download...") }
    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hfToken by remember { mutableStateOf("") }

    fun startDownload() {
        if (modelType.needsAuth && hfToken.isBlank()) {
            errorMessage = "Please enter your HuggingFace token"
            return
        }
        isDownloading = true
        errorMessage = null
    }

    LaunchedEffect(isDownloading) {
        if (!isDownloading) return@LaunchedEffect

        ModelDownloader.download(context, modelType, hfToken.trim()).collect { state ->
            when (state) {
                is DownloadState.Progress -> {
                    progress = state.percent
                    statusText = "Downloading: ${state.downloadedMb}MB / ${state.totalMb}MB"
                }
                is DownloadState.Success -> {
                    statusText = "Download complete!"
                    onDownloadComplete()
                }
                is DownloadState.Error -> {
                    isDownloading = false
                    errorMessage = state.message
                    statusText = "Download failed"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = modelType.displayName,
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Download model to device",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isDownloading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (modelType.needsAuth) {
                OutlinedTextField(
                    value = hfToken,
                    onValueChange = { hfToken = it },
                    label = { Text("HuggingFace Token") },
                    placeholder = { Text("hf_...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Model size: ${modelType.sizeMb}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { startDownload() },
                enabled = !modelType.needsAuth || hfToken.isNotBlank()
            ) {
                Text("Download Model")
            }
        }
    }
}
