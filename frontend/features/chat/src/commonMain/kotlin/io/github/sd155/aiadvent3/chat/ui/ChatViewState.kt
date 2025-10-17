package io.github.sd155.aiadvent3.chat.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal data class ChatViewState(
    val messages: List<ChatMessage> = emptyList(),
)

@Serializable
internal sealed class ChatMessage {
    data class UserMessage(val content: String) : ChatMessage()
    data object LlmProgress : ChatMessage()
    @Serializable
    sealed class LlmMessage : ChatMessage() {
        @Serializable
        @SerialName("success")
        data class Succeed(
            @SerialName("result")
            val result: String,
            @SerialName("header")
            val header: String,
            @SerialName("summary")
            val summary: String,
            @SerialName("details")
            val details: List<String>,
        ) : LlmMessage()
        @Serializable
        @SerialName("failure")
        data class Failed(
            @SerialName("result")
            val result: String,
            @SerialName("description")
            val description: String,
        ) : LlmMessage()
    }
    data class LlmError(val content: String) : ChatMessage()
}