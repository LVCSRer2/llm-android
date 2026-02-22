package com.example.gemma

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import android.util.Log
import java.io.File

class InferenceModel private constructor(context: Context, modelFile: String) {
    private val TAG = "InferenceModel"

    companion object {
        private const val MAX_TOKENS = 1024

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

    private val engine: LlmInference
    private var session: LlmInferenceSession

    init {
        val modelPath = File(context.filesDir, modelFile).absolutePath

        val engineOptions = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .setPreferredBackend(LlmInference.Backend.GPU)
            .build()

        engine = LlmInference.createFromOptions(context, engineOptions)

        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTemperature(0.8f)
            .build()

        session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
    }

    fun generateResponseAsync(
        prompt: String,
        onPartialResult: (String) -> Unit,
        onFinished: (String?) -> Unit
    ) {
        session.addQueryChunk(prompt)
        session.generateResponseAsync { partialResult, done ->
            onPartialResult(partialResult)
            if (done) {
                onFinished(null)
            }
        }
    }

    fun resetSession() {
        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTemperature(0.8f)
            .build()
        session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
    }

    fun close() {
        Log.i(TAG, "Closing InferenceModel session and engine")
        try { session.close() } catch (_: Exception) {}
        try { engine.close() } catch (_: Exception) {}
        synchronized(Companion) {
            if (instance === this) {
                instance = null
                currentModelFile = null
            }
        }
        System.gc()
        Log.i(TAG, "InferenceModel closed and GC requested")
    }
}
