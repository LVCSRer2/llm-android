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
import androidx.compose.material3.AlertDialog
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
    onBack: () -> Unit,
    onDownload: (ModelType) -> Unit = {}
) {
    val context = LocalContext.current
    var systemPrompt by remember { mutableStateOf(currentSettings.systemPrompt) }
    var temperature by remember { mutableFloatStateOf(currentSettings.temperature) }
    var topK by remember { mutableIntStateOf(currentSettings.topK) }
    var topP by remember { mutableFloatStateOf(currentSettings.topP) }
    var maxTokens by remember { mutableIntStateOf(currentSettings.maxTokens) }
    var repeatPenalty by remember { mutableFloatStateOf(currentSettings.repeatPenalty) }
    var cpuOptimization by remember { mutableStateOf(currentSettings.cpuOptimization) }
    var contextSize by remember { mutableIntStateOf(currentSettings.contextSize) }
    var selectedModel by remember { mutableStateOf(currentModel) }
    var deleteTarget by remember { mutableStateOf<ModelType?>(null) }

    deleteTarget?.let { modelType ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Model") },
            text = { Text("${modelType.displayName} (${modelType.sizeMb}) will be deleted. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    ModelDownloader.deleteModel(context, modelType)
                    if (selectedModel == modelType) selectedModel = currentModel
                    deleteTarget = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("llama.cpp CPU Settings") },
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
                            cpuOptimization = cpuOptimization,
                            contextSize = contextSize
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
            Text(
                text = "GGUF Models (CPU)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            ModelType.entries.forEach { modelType ->
                val isSelected = modelType == selectedModel
                val isDownloaded = ModelDownloader.modelExists(context, modelType)
                val isCurrentModel = modelType == currentModel

                Card(
                    onClick = {
                        if (isDownloaded) selectedModel = modelType else onDownload(modelType)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(text = modelType.displayName)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Size: ${modelType.sizeMb}", style = MaterialTheme.typography.bodySmall)
                            if (isDownloaded) {
                                Row {
                                    Text(text = if (isSelected) "Selected" else "Ready", color = MaterialTheme.colorScheme.primary)
                                    if (!isCurrentModel) {
                                        TextButton(onClick = { deleteTarget = modelType }) {
                                            Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            } else {
                                Text(text = "Download", color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "CPU Optimization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "OpenMP / mmap", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Enable multi-core acceleration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = cpuOptimization, onCheckedChange = { cpuOptimization = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            SliderSetting("Context Size", contextSize.toFloat(), 512f..8192f, { it.roundToInt().toString() }) { contextSize = it.roundToInt() }

            Spacer(modifier = Modifier.height(20.dp))

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

            Text(
                text = "Parameters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SliderSetting("Temperature", temperature, 0f..2f, { "%.2f".format(it) }) { temperature = it }
            SliderSetting("Top-K", topK.toFloat(), 1f..100f, { it.roundToInt().toString() }) { topK = it.roundToInt() }
            SliderSetting("Top-P", topP, 0f..1f, { "%.2f".format(it) }) { topP = it }
            SliderSetting("Max Tokens", maxTokens.toFloat(), 64f..2048f, { it.roundToInt().toString() }) { maxTokens = it.roundToInt() }
            SliderSetting("Repeat Penalty", repeatPenalty, 1f..2f, { "%.2f".format(it) }) { repeatPenalty = it }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String, onValueChange: (Float) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(0.35f), style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = range, modifier = Modifier.weight(0.45f))
        Text(text = format(value), modifier = Modifier.weight(0.2f), style = MaterialTheme.typography.bodySmall)
    }
}
