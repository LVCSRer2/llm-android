package com.example.gemma

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: ChatSettings,
    currentModel: ModelType,
    onSave: (ChatSettings, ModelType?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var systemPrompt by remember { mutableStateOf(currentSettings.systemPrompt) }
    var temperature by remember { mutableFloatStateOf(currentSettings.temperature) }
    var topK by remember { mutableIntStateOf(currentSettings.topK) }
    var topP by remember { mutableFloatStateOf(currentSettings.topP) }
    var maxTokens by remember { mutableIntStateOf(currentSettings.maxTokens) }
    var repeatPenalty by remember { mutableFloatStateOf(currentSettings.repeatPenalty) }
    var useVulkan by remember { mutableStateOf(currentSettings.useVulkan) }
    var selectedModel by remember { mutableStateOf(currentModel) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("\u2190", style = MaterialTheme.typography.headlineSmall)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val newSettings = ChatSettings(
                            systemPrompt = systemPrompt,
                            temperature = temperature,
                            topK = topK,
                            topP = topP,
                            maxTokens = maxTokens,
                            repeatPenalty = repeatPenalty,
                            useVulkan = useVulkan
                        )
                        ChatSettings.save(context, newSettings)
                        val modelChanged = if (selectedModel != currentModel) selectedModel else null
                        onSave(newSettings, modelChanged)
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Model Selection
            Text(
                text = "Model",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            ModelType.entries.forEach { modelType ->
                val isSelected = modelType == selectedModel
                val isDownloaded = ModelDownloader.modelExists(context, modelType)

                Card(
                    onClick = { if (isDownloaded) selectedModel = modelType },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected)
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = modelType.displayName)
                            Text(
                                text = if (isDownloaded) {
                                    if (isSelected) "Selected" else "Ready"
                                } else "Not downloaded",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isDownloaded)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "Size: ${modelType.sizeMb}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // System Prompt
            Text(
                text = "System Prompt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Parameters
            Text(
                text = "Parameters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SliderSetting(
                label = "Temperature",
                value = temperature,
                range = 0f..2f,
                format = { "%.2f".format(it) },
                onValueChange = { temperature = it }
            )

            SliderSetting(
                label = "Top-K",
                value = topK.toFloat(),
                range = 1f..100f,
                format = { it.roundToInt().toString() },
                onValueChange = { topK = it.roundToInt() }
            )

            SliderSetting(
                label = "Top-P",
                value = topP,
                range = 0f..1f,
                format = { "%.2f".format(it) },
                onValueChange = { topP = it }
            )

            SliderSetting(
                label = "Max Tokens",
                value = maxTokens.toFloat(),
                range = 64f..2048f,
                format = { it.roundToInt().toString() },
                onValueChange = { maxTokens = it.roundToInt() }
            )

            SliderSetting(
                label = "Repeat Penalty",
                value = repeatPenalty,
                range = 1f..2f,
                format = { "%.2f".format(it) },
                onValueChange = { repeatPenalty = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // GPU Acceleration
            Text(
                text = "GPU Acceleration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vulkan GPU (llama.cpp)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "GGUF models only. Requires model reload.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useVulkan,
                    onCheckedChange = { useVulkan = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = format(value),
            modifier = Modifier.weight(0.2f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
