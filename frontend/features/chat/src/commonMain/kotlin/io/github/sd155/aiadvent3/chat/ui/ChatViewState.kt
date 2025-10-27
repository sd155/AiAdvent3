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
        val promptTokens: Int,
        val completionTokens: Int,
        val usedTokens: Int,
        val reasoning: String?,
        val details: List<String>,
        val summary: String,
        val elapsedMs: Long,
    ) : ChatMessage()
    data class LlmQuery(
        val question: String,
        val creativity: Float,
        val promptTokens: Int,
        val completionTokens: Int,
        val usedTokens: Int,
        val reasoning: String?,
        val elapsedMs: Long,
    ) : ChatMessage()
    data class LlmFailure(
        val reason: String,
        val creativity: Float,
        val promptTokens: Int,
        val completionTokens: Int,
        val usedTokens: Int,
        val reasoning: String?,
        val elapsedMs: Long,
    ) : ChatMessage()
    data class LlmError(val content: String) : ChatMessage()
}