package com.example.gemma

import android.content.Context
import java.io.File

class LlamaModel private constructor(context: Context, modelFile: String, cpuOptimization: Boolean, contextSize: Int) {

    companion object {
        @Volatile
        private var instance: LlamaModel? = null
        private var currentModelFile: String? = null

        init {
            System.loadLibrary("llama-jni")
        }

        fun getInstance(context: Context, modelFile: String, cpuOptimization: Boolean = true, contextSize: Int = 2048): LlamaModel {
            val existing = instance
            if (existing != null && currentModelFile == modelFile) {
                return existing
            }
            synchronized(this) {
                val existing2 = instance
                if (existing2 != null && currentModelFile == modelFile) {
                    return existing2
                }
                existing2?.free()
                instance = null
                currentModelFile = null

                val newInstance = LlamaModel(context.applicationContext, modelFile, cpuOptimization, contextSize)
                instance = newInstance
                currentModelFile = modelFile
                return newInstance
            }
        }
    }

    private var loaded = false
    private var freed = false
    private var tokenCallback: ((String) -> Unit)? = null
    var maxTokens: Int = 1024

    init {
        val modelPath = File(context.filesDir, modelFile).absolutePath
        loaded = loadModel(modelPath, cpuOptimization, contextSize)
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

    fun countTokens(text: String): Int {
        if (!loaded) return 0
        return countTokensNative(text)
    }

    fun formatPrompt(systemPrompt: String, userMessage: String): String {
        return formatPromptNative(systemPrompt, userMessage)
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
                onFinished(result)
            }
        } catch (e: Exception) {
            onFinished(e.message)
        } finally {
            tokenCallback = null
        }
    }

    fun resetSession() {}

    fun free() {
        if (!freed) {
            freed = true
            loaded = false
            freeModelNative()
        }
    }

    external fun stopGeneration()
    private external fun countTokensNative(text: String): Int
    private external fun loadModel(modelPath: String, cpuOptimization: Boolean, contextSize: Int): Boolean
    private external fun generate(prompt: String, maxTokens: Int): String
    private external fun formatPromptNative(systemPrompt: String, userMessage: String): String
    private external fun updateSampler(temperature: Float, topK: Int, topP: Float, repeatPenalty: Float)
    private external fun freeModelNative()

    protected fun finalize() {
        if (instance === this && !freed) {
            free()
        }
    }
}
