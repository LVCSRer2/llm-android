package com.example.gemma

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gemma.ui.theme.ModelBubble
import com.example.gemma.ui.theme.ModelText
import com.example.gemma.ui.theme.UserBubble
import com.example.gemma.ui.theme.UserText
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatViewModel: ChatViewModel = viewModel(), onOpenSettings: () -> Unit = {}) {
    val uiState = chatViewModel.uiState
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatViewModel.modelType.displayName) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    TextButton(onClick = { onOpenSettings() }) {
                        Text("\u2699")
                    }
                    TextButton(onClick = { chatViewModel.resetChat() }) {
                        Text("Reset")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (chatViewModel.isModelLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading ${chatViewModel.modelType.displayName}...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        reverseLayout = true,
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        items(uiState.messages.reversed()) { message ->
                            MessageBubble(message)
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    if (uiState.isGenerating) {
                        Button(
                            onClick = { chatViewModel.stopGeneration() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Stop")
                        }
                    }

                    MessageInput(
                        enabled = !uiState.isGenerating,
                        tokenCount = uiState.inputTokenCount,
                        onTextChanged = { chatViewModel.onInputTextChanged(it) },
                        onSend = { chatViewModel.sendMessage(it) }
                    )
                }
            }

            // Memory overlay
            MemoryOverlay(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun MemoryOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nativeHeapMb by remember { mutableLongStateOf(0L) }
    var availableRamMb by remember { mutableLongStateOf(0L) }
    var appRssMb by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            availableRamMb = memInfo.availMem / (1024 * 1024)

            nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

            val runtime = Runtime.getRuntime()
            val javaHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            appRssMb = javaHeapMb + nativeHeapMb

            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = "APP  ${appRssMb}MB",
            color = Color.Green,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp
        )
        Text(
            text = "NAT  ${nativeHeapMb}MB",
            color = Color.Cyan,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp
        )
        Text(
            text = "FREE ${availableRamMb}MB",
            color = Color.Yellow,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp
        )
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.author == MessageAuthor.USER
    val bubbleColor = if (isUser) UserBubble else ModelBubble
    val textColor = if (isUser) UserText else ModelText
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            if (message.isLoading && message.text.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun MessageInput(
    enabled: Boolean, 
    tokenCount: Int,
    onTextChanged: (String) -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    fun send() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
            text = ""
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { 
                    text = it
                    onTextChanged(it)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )

            IconButton(
                onClick = { send() },
                enabled = enabled && text.isNotBlank()
            ) {
                Text(
                    text = "\u27A4",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (enabled && text.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        Color.Gray
                )
            }
        }
        
        if (text.isNotEmpty()) {
            Text(
                text = "Input: $tokenCount tokens",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
            )
        }
    }
}
