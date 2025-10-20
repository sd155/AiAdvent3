package io.github.sd155.aiadvent3.chat.ui

internal data class ChatViewState(
    val messages: List<ChatMessage> = emptyList(),
)

internal sealed class ChatMessage {
    data class UserMessage(val content: String) : ChatMessage()
    data object LlmProgress : ChatMessage()
    data class LlmSuccess(
        val header: String,
        val creativity: Float,
        val details: List<String>,
        val summary: String,
    ) : ChatMessage()
    data class LlmQuery(
        val question: String,
        val creativity: Float,
    ) : ChatMessage()
    data class LlmFailure(
        val reason: String,
        val creativity: Float,
    ) : ChatMessage()
    data class LlmError(val content: String) : ChatMessage()
}