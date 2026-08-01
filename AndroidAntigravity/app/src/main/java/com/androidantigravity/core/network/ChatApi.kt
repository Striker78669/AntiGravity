package com.androidantigravity.core.network

import com.androidantigravity.BuildConfig
import com.androidantigravity.core.model.ChatMessage
import com.androidantigravity.core.model.MessageRole
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** HTTP/SSE boundary. Swap this implementation for a WebSocket transport in a later phase. */
class ChatApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun streamReply(history: List<ChatMessage>, onDelta: (String) -> Unit) {
        client.preparePost("$baseUrl/v1/chat/stream") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(history.map { ChatTurn(it.role.name.lowercase(), it.content) }))
        }.execute { response ->
            if (response.status.value !in 200..299) {
                error("The assistant service returned ${response.status.value}.")
            }
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val event = json.decodeFromString<StreamEvent>(line.removePrefix("data: "))
                when (event.type) {
                    "delta" -> onDelta(event.text.orEmpty())
                    "error" -> error(event.message ?: "The assistant service failed.")
                    "done" -> return@execute
                }
            }
        }
    }
}

@Serializable private data class ChatRequest(val messages: List<ChatTurn>)
@Serializable private data class ChatTurn(val role: String, val content: String)
@Serializable private data class StreamEvent(val type: String, val text: String? = null, val message: String? = null)
