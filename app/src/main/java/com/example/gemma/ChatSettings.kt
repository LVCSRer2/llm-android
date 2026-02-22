package com.example.gemma

import android.content.Context
import android.content.SharedPreferences

data class ChatSettings(
    val systemPrompt: String = "You are a helpful assistant.\nAlways respond in Korean.\nAnswer concisely and accurately.\nIf you don't know something, say \"잘 모르겠습니다.\"\nDo not make up facts or personal information.",
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxTokens: Int = 1024,
    val repeatPenalty: Float = 1.1f
) {
    companion object {
        private const val PREFS_NAME = "chat_settings"

        fun load(context: Context): ChatSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ChatSettings(
                systemPrompt = prefs.getString("system_prompt", null)
                    ?: ChatSettings().systemPrompt,
                temperature = prefs.getFloat("temperature", 0.8f),
                topK = prefs.getInt("top_k", 40),
                topP = prefs.getFloat("top_p", 0.95f),
                maxTokens = prefs.getInt("max_tokens", 1024),
                repeatPenalty = prefs.getFloat("repeat_penalty", 1.1f)
            )
        }

        fun save(context: Context, settings: ChatSettings) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("system_prompt", settings.systemPrompt)
                .putFloat("temperature", settings.temperature)
                .putInt("top_k", settings.topK)
                .putFloat("top_p", settings.topP)
                .putInt("max_tokens", settings.maxTokens)
                .putFloat("repeat_penalty", settings.repeatPenalty)
                .apply()
        }
    }
}
