package com.androidantigravity.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androidantigravity.core.model.ChatMessage
import com.androidantigravity.core.model.MessageRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onSignIn: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Workspace", fontWeight = FontWeight.SemiBold)
                        Text("Antigravity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    if (state.signedInEmail != null) Text(state.signedInEmail.substringBefore("@"), style = MaterialTheme.typography.labelMedium)
                    else TextButton(onClick = onSignIn, enabled = !state.isSigningIn) { Text(if (state.isSigningIn) "Signing in…" else "Sign in") }
                    IconButton(onClick = onClear, enabled = state.messages.isNotEmpty()) { Icon(Icons.Default.DeleteOutline, "Clear chat") }
                },
            )
        },
        bottomBar = {
            Column {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask Antigravity…") },
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onSend, enabled = state.draft.isNotBlank() && !state.isStreaming) {
                        if (state.isStreaming) CircularProgressIndicator(modifier = Modifier.width(24.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
        },
    ) { padding ->
        if (state.messages.isEmpty()) EmptyConversation(Modifier.padding(padding))
        else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.messages, key = { it.id }) { message -> MessageBubble(message) }
        }
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("What are we building?", style = MaterialTheme.typography.titleLarge)
            Text("Ask for a feature, debugging help, or a project plan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val fromUser = message.role == MessageRole.USER
    Column(horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start) {
        Text(if (fromUser) "You" else "Antigravity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (message.content.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp).width(18.dp), strokeWidth = 2.dp)
            } else MarkdownContent(message.content, Modifier.padding(14.dp))
        }
    }
}

/** Small, dependency-free Markdown renderer for Phase 1: paragraphs, bold, inline code and fenced blocks. */
@Composable
private fun MarkdownContent(markdown: String, modifier: Modifier) {
    val pieces = markdown.split("```")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pieces.forEachIndexed { index, piece ->
            if (index % 2 == 0) {
                if (piece.isNotBlank()) Text(markdownInline(piece.trim()), style = MaterialTheme.typography.bodyLarge)
            } else {
                val code = piece.substringAfter('\n', piece).trimEnd()
                Surface(color = Color(0xFF15171C), shape = RoundedCornerShape(8.dp)) {
                    Text(code, modifier = Modifier.padding(12.dp), color = Color(0xFFE6EAF2), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun markdownInline(value: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < value.length) {
        val marker = value.indexOf("**", cursor)
        if (marker < 0) { append(value.substring(cursor)); break }
        append(value.substring(cursor, marker))
        val end = value.indexOf("**", marker + 2)
        if (end < 0) { append(value.substring(marker)); break }
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(value.substring(marker + 2, end))
        pop()
        cursor = end + 2
    }
}
