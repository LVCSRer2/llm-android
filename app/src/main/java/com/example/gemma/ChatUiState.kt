package com.example.gemma

import androidx.compose.runtime.mutableStateListOf

class ChatUiState {
    val messages = mutableStateListOf<ChatMessage>()
    var isGenerating: Boolean = false

    fun addUserMessage(text: String) {
        messages.add(ChatMessage(text = text, author = MessageAuthor.USER))
    }

    fun addModelMessage(text: String = "", isLoading: Boolean = true) {
        messages.add(ChatMessage(text = text, author = MessageAuthor.MODEL, isLoading = isLoading))
    }

    fun updateLastModelMessage(text: String, isLoading: Boolean = true) {
        val lastIndex = messages.lastIndex
        if (lastIndex >= 0 && messages[lastIndex].author == MessageAuthor.MODEL) {
            messages[lastIndex] = ChatMessage(text = text, author = MessageAuthor.MODEL, isLoading = isLoading)
        }
    }

    fun clear() {
        messages.clear()
    }
}
