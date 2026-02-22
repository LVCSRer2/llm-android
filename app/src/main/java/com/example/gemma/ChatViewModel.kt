package com.example.gemma

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
}

class MediaPipeEngine(context: android.content.Context, modelFile: String) : ChatEngine {
    private val model = InferenceModel.getInstance(context, modelFile)

    override fun generateResponseAsync(
        prompt: String,
        onPartialResult: (String) -> Unit,
        onFinished: (String?) -> Unit
    ) = model.generateResponseAsync(prompt, onPartialResult, onFinished)

    override fun resetSession() = model.resetSession()
    override fun stopGeneration() {}
    override fun applySettings(settings: ChatSettings) {}
}

class LlamaCppEngine(context: android.content.Context, modelFile: String, useVulkan: Boolean = false) : ChatEngine {
    private val model = LlamaModel.getInstance(context, modelFile, useVulkan)

    override fun generateResponseAsync(
        prompt: String,
        onPartialResult: (String) -> Unit,
        onFinished: (String?) -> Unit
    ) = model.generateResponseAsync(prompt, onPartialResult, onFinished)

    override fun resetSession() = model.resetSession()
    override fun stopGeneration() = model.stopGeneration()
    override fun applySettings(settings: ChatSettings) = model.applySettings(settings)
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val uiState = ChatUiState()
    private var engine: ChatEngine? = null
    var modelType: ModelType by mutableStateOf(ModelType.GEMMA3)
    var settings by mutableStateOf(ChatSettings.load(application))
    var isModelLoading by mutableStateOf(false)

    fun loadModel(type: ModelType) {
        modelType = type
        isModelLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                engine = when (type) {
                    ModelType.GEMMA3, ModelType.GEMMA3N_E2B ->
                        MediaPipeEngine(getApplication(), type.fileName)
                    else -> LlamaCppEngine(getApplication(), type.fileName, settings.useVulkan)
                }
            } catch (e: Exception) {
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
        val vulkanChanged = newSettings.useVulkan != settings.useVulkan
        settings = newSettings
        if (newModel != null && newModel != modelType) {
            resetChat()
            loadModel(newModel)
        } else if (vulkanChanged && modelType != ModelType.GEMMA3 && modelType != ModelType.GEMMA3N_E2B) {
            resetChat()
            loadModel(modelType)
        }
    }

    fun sendMessage(userMessage: String) {
        val eng = engine ?: return
        if (uiState.isGenerating) return

        uiState.isGenerating = true
        uiState.addUserMessage(userMessage)
        uiState.addModelMessage(text = "", isLoading = true)

        // Apply settings to engine
        eng.applySettings(settings)

        // Build prompt with system prompt
        val fullPrompt = if (settings.systemPrompt.isNotBlank()) {
            "<|system|>\n${settings.systemPrompt}\n<|user|>\n$userMessage\n<|assistant|>\n"
        } else {
            userMessage
        }

        val fullResponse = StringBuilder()

        viewModelScope.launch(Dispatchers.IO) {
            eng.generateResponseAsync(
                prompt = fullPrompt,
                onPartialResult = { partial ->
                    fullResponse.append(partial)
                    uiState.updateLastModelMessage(
                        text = fullResponse.toString(),
                        isLoading = true
                    )
                },
                onFinished = { error ->
                    if (error != null) {
                        uiState.updateLastModelMessage(
                            text = "Error: $error",
                            isLoading = false
                        )
                    } else {
                        uiState.updateLastModelMessage(
                            text = fullResponse.toString(),
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
        engine?.stopGeneration()
        engine?.resetSession()
        uiState.clear()
        uiState.isGenerating = false
    }
}
