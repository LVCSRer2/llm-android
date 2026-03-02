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
        sizeMb = "~1.1GB",
        url = "https://huggingface.co/google/gemma-3n-E2B-it-litert/resolve/main/gemma-3n-E2B-it-int4.litertlm",
        needsAuth = true
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
                    val errorMsg = when (response.code) {
                        401 -> "Unauthorized: HF Token is invalid or missing."
                        403 -> "Forbidden: You may need to accept the model license on Hugging Face website."
                        404 -> "Not Found: Model file not found at the URL."
                        else -> "HTTP ${response.code}: ${response.message}"
                    }
                    onFinished(false, errorMsg)
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
