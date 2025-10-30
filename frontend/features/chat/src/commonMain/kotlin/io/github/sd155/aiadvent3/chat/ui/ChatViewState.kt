package io.github.sd155.aiadvent3.chat.ui

internal data class ChatViewState(
    val messages: List<ChatMessage> = emptyList(),
)

internal sealed class ChatMessage {
    data class UserMessage(val content: String) : ChatMessage()
    data object AgentProgress : ChatMessage()
    data class AgentMessage(
        val agentTag: String,
        val content: String,
        val reasoning: String? = null,
    ) : ChatMessage()
//    data class LlmError(val content: String) : ChatMessage()
}

internal data class LlmMetrics(
    val creativity: Float,
    val usedTokens: Int,
    val elapsedMs: Long,
)