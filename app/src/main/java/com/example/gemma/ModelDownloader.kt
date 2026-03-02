package com.example.gemma

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

enum class ModelType(
    val fileName: String,
    val displayName: String,
    val sizeMb: String,
    val url: String,
    val needsAuth: Boolean = false
) {
    GEMMA3_GGUF(
        fileName = "gemma-3-1b-it-Q4_K_M.gguf",
        displayName = "Gemma 3 1B (llama.cpp CPU)",
        sizeMb = "~806MB",
        url = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
        needsAuth = false
    ),
    DEEPSEEK_R1_QWEN_1_5B(
        fileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        displayName = "DeepSeek R1 1.5B (llama.cpp CPU)",
        sizeMb = "~1.1GB",
        url = "https://huggingface.co/unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        needsAuth = false
    ),
    QWEN3(
        fileName = "qwen3-1.7b-q4_k_m.gguf",
        displayName = "Qwen3 1.7B (Q4_K_M)",
        sizeMb = "~1.1GB",
        url = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
        needsAuth = false
    )
}

object ModelDownloader {
    private const val TAG = "ModelDownloader"
    private val client = OkHttpClient()

    fun modelExists(context: Context, modelType: ModelType): Boolean {
        return File(context.filesDir, modelType.fileName).exists()
    }

    fun deleteModel(context: Context, modelType: ModelType) {
        val file = File(context.filesDir, modelType.fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    suspend fun downloadModel(
        context: Context,
        modelType: ModelType,
        hfToken: String?,
        onProgress: (Int) -> Unit,
        onFinished: (Boolean, String?) -> Unit
    ) {
        val file = File(context.filesDir, modelType.fileName)
        if (file.exists()) {
            onFinished(true, null)
            return
        }

        try {
            val requestBuilder = Request.Builder().url(modelType.url)
            if (modelType.needsAuth && !hfToken.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $hfToken")
            }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onFinished(false, "HTTP ${response.code}: ${response.message}")
                    return
                }

                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }
                onFinished(true, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            if (file.exists()) file.delete()
            onFinished(false, e.message)
        }
    }
}
