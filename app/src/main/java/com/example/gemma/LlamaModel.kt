package com.example.gemma

import android.content.Context
import java.io.File

class LlamaModel private constructor(context: Context, modelFile: String, nGpuLayers: Int) {

    companion object {
        @Volatile
        private var instance: LlamaModel? = null
        private var currentModelFile: String? = null
        private var currentGpuLayers: Int = 0

        init {
            System.loadLibrary("llama-jni")
        }

        fun getInstance(context: Context, modelFile: String, useVulkan: Boolean = false): LlamaModel {
            val nGpuLayers = if (useVulkan) 99 else 0
            val existing = instance
            if (existing != null && currentModelFile == modelFile && currentGpuLayers == nGpuLayers) {
                return existing
            }
            synchronized(this) {
                val existing2 = instance
                if (existing2 != null && currentModelFile == modelFile && currentGpuLayers == nGpuLayers) {
                    return existing2
                }
                existing2?.freeModel()
                instance = null
                currentModelFile = null

                val newInstance = LlamaModel(context.applicationContext, modelFile, nGpuLayers)
                instance = newInstance
                currentModelFile = modelFile
                currentGpuLayers = nGpuLayers
                return newInstance
            }
        }
    }

    private var loaded = false
    private var tokenCallback: ((String) -> Unit)? = null
    var maxTokens: Int = 1024

    init {
        val modelPath = File(context.filesDir, modelFile).absolutePath
        loaded = loadModel(modelPath, nGpuLayers)
        if (!loaded) {
            throw RuntimeException("Failed to load model: $modelFile")
        }
    }

    @Suppress("unused")
    fun onToken(token: String) {
        tokenCallback?.invoke(token)
    }

    fun applySettings(settings: ChatSettings) {
        maxTokens = settings.maxTokens
        updateSampler(settings.temperature, settings.topK, settings.topP, settings.repeatPenalty)
    }

    fun generateResponseAsync(
        prompt: String,
        onPartialResult: (String) -> Unit,
        onFinished: (String?) -> Unit
    ) {
        if (!loaded) {
            onFinished("Model not loaded")
            return
        }

        tokenCallback = onPartialResult
        try {
            val result = generate(prompt, maxTokens)
            if (result.startsWith("Error: ")) {
                onFinished(result)
            } else {
                onFinished(null)
            }
        } catch (e: Exception) {
            onFinished(e.message)
        } finally {
            tokenCallback = null
        }
    }

    fun resetSession() {}

    external fun stopGeneration()
    private external fun loadModel(modelPath: String, nGpuLayers: Int): Boolean
    private external fun generate(prompt: String, maxTokens: Int): String
    private external fun updateSampler(temperature: Float, topK: Int, topP: Float, repeatPenalty: Float)
    external fun freeModel()

    protected fun finalize() {
        freeModel()
    }
}
