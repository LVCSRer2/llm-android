package com.example.gemma

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    data class Progress(val percent: Int, val downloadedMb: Long, val totalMb: Long) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

enum class ModelType(val fileName: String, val displayName: String, val sizeMb: String, val url: String, val needsAuth: Boolean) {
    GEMMA3(
        fileName = "gemma3-1b-it-int4.task",
        displayName = "Gemma 3 1B (MediaPipe GPU)",
        sizeMb = "~529MB",
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
        needsAuth = true
    ),
    GEMMA3N_E2B(
        fileName = "gemma-3n-E2B-it-int4.litertlm",
        displayName = "Gemma 3n E2B (MediaPipe GPU)",
        sizeMb = "~3.7GB",
        url = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm",
        needsAuth = true
    ),
    GEMMA3_GGUF(
        fileName = "gemma-3-1b-it-Q4_K_M.gguf",
        displayName = "Gemma 3 1B (llama.cpp CPU)",
        sizeMb = "~806MB",
        url = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
        needsAuth = false
    ),
    QWEN3(
        fileName = "qwen3-1.7b-q4_k_m.gguf",
        displayName = "Qwen3 1.7B (Q4_K_M)",
        sizeMb = "~1.1GB",
        url = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
        needsAuth = false
    ),
    LLAMA3_2(
        fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        displayName = "Llama 3.2 3B (Q4_K_M)",
        sizeMb = "~2.0GB",
        url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        needsAuth = false
    ),
    TINYLLAMA(
        fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        displayName = "TinyLlama 1.1B (Q4_K_M)",
        sizeMb = "~0.7GB",
        url = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        needsAuth = false
    ),
    PHI3_5(
        fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
        displayName = "Phi-3.5 Mini 3.8B (Q4_K_M)",
        sizeMb = "~2.2GB",
        url = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
        needsAuth = false
    ),
    SMOLLM2(
        fileName = "SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
        displayName = "SmolLM2 1.7B (Q4_K_M)",
        sizeMb = "~1.0GB",
        url = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
        needsAuth = false
    )
}

object ModelDownloader {

    fun modelExists(context: Context, modelType: ModelType): Boolean {
        return File(context.filesDir, modelType.fileName).exists()
    }

    fun download(context: Context, modelType: ModelType, hfToken: String = ""): Flow<DownloadState> = flow {
        val destFile = File(context.filesDir, modelType.fileName)
        val tmpFile = File(context.filesDir, "${modelType.fileName}.tmp")

        if (destFile.exists()) {
            emit(DownloadState.Success)
            return@flow
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val requestBuilder = Request.Builder().url(modelType.url)
        if (hfToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $hfToken")
        }
        val request = requestBuilder.build()

        try {
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error("Download failed: HTTP ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadState.Error("Empty response body"))
                return@flow
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            emit(
                                DownloadState.Progress(
                                    percent = percent,
                                    downloadedMb = downloadedBytes / (1024 * 1024),
                                    totalMb = totalBytes / (1024 * 1024)
                                )
                            )
                        }
                    }
                }
            }

            tmpFile.renameTo(destFile)
            emit(DownloadState.Success)
        } catch (e: Exception) {
            tmpFile.delete()
            emit(DownloadState.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
}
