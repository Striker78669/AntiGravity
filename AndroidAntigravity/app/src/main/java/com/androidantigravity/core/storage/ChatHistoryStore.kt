package com.androidantigravity.core.storage

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.androidantigravity.core.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.chatDataStore by preferencesDataStore("chat_history")
private val historyKey = stringPreferencesKey("messages")

class ChatHistoryStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val messages: Flow<List<ChatMessage>> = context.chatDataStore.data.map { preferences ->
        preferences[historyKey]?.let { encoded ->
            runCatching { json.decodeFromString(ListSerializer(ChatMessage.serializer()), encoded) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun save(messages: List<ChatMessage>) {
        context.chatDataStore.edit { preferences: MutablePreferences ->
            preferences[historyKey] = json.encodeToString(ListSerializer(ChatMessage.serializer()), messages)
        }
    }

    suspend fun clear() = context.chatDataStore.edit { it.remove(historyKey) }
}
