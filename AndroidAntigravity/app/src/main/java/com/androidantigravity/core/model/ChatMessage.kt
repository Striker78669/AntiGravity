package com.androidantigravity.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class MessageRole { USER, ASSISTANT, SYSTEM }

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
