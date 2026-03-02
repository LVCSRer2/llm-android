package com.example.gemma

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File
import android.util.Log

class InferenceModel private constructor(context: Context, modelFile: String) {
    private val TAG = "InferenceModel"
    private var engine: LlmInference? = null

    companion object {
        @Volatile
        private var instance: InferenceModel? = null
        private var currentModelFile: String? = null

        fun getInstance(context: Context, modelFile: String): InferenceModel {
            val existing = instance
            if (existing != null && currentModelFile == modelFile) {
                return existing
            }
            synchronized(this) {
                val existing2 = instance
                if (existing2 != null && currentModelFile == modelFile) {
                    return existing2
                }
                existing2?.close()
                instance = null
                currentModelFile = null

                val newInstance = InferenceModel(context.applicationContext, modelFile)
                instance = newInstance
                currentModelFile = modelFile
                return newInstance
            }
        }
    }

    init {
        val modelPath = File(context.filesDir, modelFile).absolutePath
        Log.i(TAG, "Loading MediaPipe model from: $modelPath")
        
        try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(2048)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()

            engine = LlmInference.createFromOptions(context, options)
            Log.i(TAG, "MediaPipe model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Failed to create LlmInference: ${e.message}")
            throw e
        }
    }

    fun countTokens(prompt: String): Int {
        return try {
            engine?.sizeInTokens(prompt)?.toInt() ?: 0
        } catch (e: Exception) {
            prompt.length / 4
        }
    }

    fun generateResponseAsync(
        prompt: String,
        onPartialResult: (String) -> Unit,
        onFinished: (String?, Double, Double, Int, Int) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long = 0
        var outputTokenCount = 0
        var firstTokenReceived = false
        
        val inputTokenCount = countTokens(prompt)

        try {
            engine?.generateResponseAsync(prompt) { partialResult: String, done: Boolean ->
                if (!firstTokenReceived && partialResult.isNotEmpty()) {
                    firstTokenTime = System.currentTimeMillis()
                    firstTokenReceived = true
                }
                outputTokenCount++
                onPartialResult(partialResult)
                
                if (done) {
                    val endTime = System.currentTimeMillis()
                    val actualFirstTokenTime = if (firstTokenTime > 0) firstTokenTime else endTime
                    val encTime = (actualFirstTokenTime - startTime) / 1000.0
                    val decTime = (endTime - actualFirstTokenTime) / 1000.0
                    onFinished(null, encTime, decTime, inputTokenCount, outputTokenCount)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation error: ${e.message}")
            onFinished(e.message, 0.0, 0.0, 0, 0)
        }
    }

    fun resetSession() {}

    fun close() {
        Log.i(TAG, "Closing InferenceModel")
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during close: ${e.message}")
        }
        synchronized(Companion) {
            if (instance === this) {
                instance = null
                currentModelFile = null
            }
        }
    }
}
