package com.androidantigravity.feature.chat

import android.app.Application
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androidantigravity.core.model.ChatMessage
import com.androidantigravity.core.model.MessageRole
import com.androidantigravity.core.auth.GoogleAuthRepository
import com.androidantigravity.core.network.ChatApi
import com.androidantigravity.core.storage.ChatHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isStreaming: Boolean = false,
    val signedInEmail: String? = null,
    val isSigningIn: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(
    application: Application,
    private val api: ChatApi = ChatApi(),
    private val history: ChatHistoryStore = ChatHistoryStore(application),
    private val googleAuth: GoogleAuthRepository = GoogleAuthRepository(application),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            history.messages.collectLatest { stored ->
                if (!mutableUiState.value.isStreaming) mutableUiState.value = mutableUiState.value.copy(messages = stored)
            }
        }
    }

    fun updateDraft(value: String) { mutableUiState.value = mutableUiState.value.copy(draft = value) }

    fun send() {
        val prompt = mutableUiState.value.draft.trim()
        if (prompt.isEmpty() || mutableUiState.value.isStreaming) return
        val user = ChatMessage(role = MessageRole.USER, content = prompt)
        val placeholder = ChatMessage(role = MessageRole.ASSISTANT, content = "")
        val conversation = mutableUiState.value.messages + user + placeholder
        mutableUiState.value = ChatUiState(messages = conversation, isStreaming = true)

        viewModelScope.launch {
            runCatching {
                api.streamReply(conversation.dropLast(1)) { chunk ->
                    val latest = mutableUiState.value.messages.toMutableList()
                    latest[latest.lastIndex] = latest.last().copy(content = latest.last().content + chunk)
                    mutableUiState.value = mutableUiState.value.copy(messages = latest)
                }
            }.onFailure { throwable ->
                mutableUiState.value = mutableUiState.value.copy(error = throwable.message ?: "Could not contact the assistant.")
            }
            mutableUiState.value = mutableUiState.value.copy(isStreaming = false)
            history.save(mutableUiState.value.messages.filter { it.content.isNotBlank() })
        }
    }

    fun clearConversation() = viewModelScope.launch {
        history.clear()
        mutableUiState.value = ChatUiState()
    }

    fun signIn(activity: Activity) {
        if (mutableUiState.value.isSigningIn || mutableUiState.value.signedInEmail != null) return
        mutableUiState.value = mutableUiState.value.copy(isSigningIn = true, error = null)
        viewModelScope.launch {
            runCatching { googleAuth.signIn(activity) }
                .onSuccess { user -> mutableUiState.value = mutableUiState.value.copy(signedInEmail = user.email) }
                .onFailure { error -> mutableUiState.value = mutableUiState.value.copy(error = error.message ?: "Google sign-in failed.") }
            mutableUiState.value = mutableUiState.value.copy(isSigningIn = false)
        }
    }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(application) as T
        }
    }
}
