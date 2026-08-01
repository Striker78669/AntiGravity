package com.androidantigravity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidantigravity.feature.chat.ChatScreen
import com.androidantigravity.feature.chat.ChatViewModel
import com.androidantigravity.ui.theme.AntigravityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AntigravityTheme {
                val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(application))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ChatScreen(
                    state = state,
                    onDraftChange = viewModel::updateDraft,
                    onSend = viewModel::send,
                    onClear = viewModel::clearConversation,
                    onSignIn = { viewModel.signIn(this) },
                )
            }
        }
    }
}
