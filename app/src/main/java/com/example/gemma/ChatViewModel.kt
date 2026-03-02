package com.example.gemma

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface ChatEngine {
    fun generateResponseAsync(
        prompt: String,
        onPartialResult: (String) -> Unit,
        onFinished: (String?) -> Unit
    )
    fun resetSession()
    fun stopGeneration()
    fun applySettings(settings: ChatSettings)
    fun countTokens(text: String): Int
    fun close()
}

class MediaPipeEngine(context: android.content.Context, modelFile: String) : ChatEngine {
    private val model = InferenceModel.getInstance(context, modelFile)

    override fun generateResponseAsync(
        prompt: String,
        onPartialResult: (String) -> Unit,
        onFinished: (String?) -> Unit
    ) {
        model.generateResponseAsync(
            prompt,
            { text: String -> onPartialResult(text) },
            { error: String?, encTime: Double, decTime: Double, inTokens: Int, outTokens: Int ->
                if (error != null) {
                    onFinished("Error: $error")
                } else {
                    val encTps = if (encTime > 0) inTokens / encTime else 0.0
                    val decTps = if (decTime > 0) outTokens / decTime else 0.0
                    val perfInfo = "\n\n[MediaPipe GPU Stats]\n" +
                            "- Prefill: %.2f t/s (%.2fs)\n".format(encTps, encTime) +
                            "- Decode: %.2f t/s (%.2fs)".format(decTps, decTime)
                    onFinished(perfInfo)
                }
            }
        )
    }

    override fun resetSession() = model.resetSession()
    override fun stopGeneration() {}
    override fun applySettings(settings: ChatSettings) {}
    override fun countTokens(text: String): Int = model.countTokens(text)
    override fun close() = model.close()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val uiState = ChatUiState()
    private var engine: ChatEngine? = null
    var modelType: ModelType by mutableStateOf(ModelType.GEMMA3)
    var settings by mutableStateOf(ChatSettings.load(application))
    var isModelLoading by mutableStateOf(false)
    val isModelLoaded: Boolean get() = engine != null
    
    private var tokenCountJob: Job? = null

    fun loadModel(type: ModelType) {
        modelType = type
        isModelLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prevEngine = engine
                prevEngine?.close()
                engine = null

                val app = getApplication<Application>()
                if (!ModelDownloader.modelExists(app, type)) {
                    uiState.addModelMessage(
                        text = "Model not downloaded: ${type.displayName}",
                        isLoading = false
                    )
                    return@launch
                }

                // MediaPipe only branch
                engine = MediaPipeEngine(app, type.fileName)
            } catch (e: Exception) {
                engine = null
                uiState.addModelMessage(
                    text = "Failed to load model: ${e.message}",
                    isLoading = false
                )
            } finally {
                isModelLoading = false
            }
        }
    }

    fun updateSettings(newSettings: ChatSettings, newModel: ModelType?) {
        settings = newSettings
        if (newModel != null && newModel != modelType) {
            uiState.clear()
            uiState.isGenerating = false
            loadModel(newModel)
        } else {
            engine?.applySettings(newSettings)
        }
    }

    fun onInputTextChanged(text: String) {
        tokenCountJob?.cancel()
        if (text.isBlank()) {
            uiState.inputTokenCount = 0
            return
        }
        
        tokenCountJob = viewModelScope.launch(Dispatchers.Default) {
            delay(300)
            val count = engine?.countTokens(text) ?: 0
            uiState.inputTokenCount = count
        }
    }

    fun sendMessage(userMessage: String) {
        val eng = engine ?: return
        if (uiState.isGenerating) return

        uiState.isGenerating = true
        uiState.addUserMessage(userMessage)
        uiState.addModelMessage(text = "", isLoading = true)
        uiState.inputTokenCount = 0

        eng.applySettings(settings)
        
        val fullResponse = StringBuilder()

        viewModelScope.launch(Dispatchers.IO) {
            eng.generateResponseAsync(
                prompt = userMessage,
                onPartialResult = { partial ->
                    fullResponse.append(partial)
                    uiState.updateLastModelMessage(
                        text = fullResponse.toString(),
                        isLoading = true
                    )
                },
                onFinished = { finalPerfTag ->
                    if (finalPerfTag != null && finalPerfTag.startsWith("Error: ")) {
                        uiState.updateLastModelMessage(
                            text = finalPerfTag,
                            isLoading = false
                        )
                    } else if (finalPerfTag != null) {
                        uiState.updateLastModelMessage(
                            text = fullResponse.toString() + finalPerfTag,
                            isLoading = false
                        )
                    }
                    uiState.isGenerating = false
                }
            )
        }
    }

    fun stopGeneration() {
        engine?.stopGeneration()
    }

    fun resetChat() {
        try {
            engine?.resetSession()
        } catch (_: Exception) {}
        uiState.clear()
        uiState.isGenerating = false
    }
}
