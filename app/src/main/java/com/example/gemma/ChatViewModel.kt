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
    fun formatPrompt(systemPrompt: String, userMessage: String): String
    fun close()
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
    override fun formatPrompt(systemPrompt: String, userMessage: String): String = userMessage
    override fun close() = model.close()
}

class LlamaCppEngine(context: android.content.Context, modelFile: String) : ChatEngine {
    private val model = LlamaModel.getInstance(context, modelFile)

    override fun generateResponseAsync(
        prompt: String,
        onPartialResult: (String) -> Unit,
        onFinished: (String?) -> Unit
    ) = model.generateResponseAsync(prompt, onPartialResult, onFinished)

    override fun resetSession() = model.resetSession()
    override fun stopGeneration() = model.stopGeneration()
    override fun applySettings(settings: ChatSettings) = model.applySettings(settings)
    override fun formatPrompt(systemPrompt: String, userMessage: String): String =
        model.formatPrompt(systemPrompt, userMessage)
    override fun close() = model.free()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val uiState = ChatUiState()
    private var engine: ChatEngine? = null
    var modelType: ModelType by mutableStateOf(ModelType.GEMMA3)
    var settings by mutableStateOf(ChatSettings.load(application))
    var isModelLoading by mutableStateOf(false)
    val isModelLoaded: Boolean get() = engine != null

    fun loadModel(type: ModelType) {
        modelType = type
        isModelLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prevEngine = engine
                prevEngine?.stopGeneration()
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

                engine = when (type) {
                    ModelType.GEMMA3, ModelType.GEMMA3N_E2B ->
                        MediaPipeEngine(app, type.fileName)
                    else -> LlamaCppEngine(app, type.fileName)
                }
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

    fun sendMessage(userMessage: String) {
        val eng = engine ?: return
        if (uiState.isGenerating) return

        uiState.isGenerating = true
        uiState.addUserMessage(userMessage)
        uiState.addModelMessage(text = "", isLoading = true)

        eng.applySettings(settings)
        
        // Let the engine format the prompt based on the specific model's template
        val fullPrompt = eng.formatPrompt(settings.systemPrompt, userMessage)
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
        try {
            engine?.stopGeneration()
            engine?.resetSession()
        } catch (_: Exception) {}
        uiState.clear()
        uiState.isGenerating = false
    }
}
