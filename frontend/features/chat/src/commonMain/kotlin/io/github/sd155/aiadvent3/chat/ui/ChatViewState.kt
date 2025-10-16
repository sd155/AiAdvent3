package io.github.sd155.aiadvent3.chat.ui

internal data class ChatViewState(
    val messages: List<ChatMessage> = emptyList(),
)

internal sealed class ChatMessage {
    data class UserMessage(val content: String) : ChatMessage()
    data object LlmProgress : ChatMessage()
    data class LlmMessage(val content: String): ChatMessage()
    data class LlmError(val content: String) : ChatMessage()
}