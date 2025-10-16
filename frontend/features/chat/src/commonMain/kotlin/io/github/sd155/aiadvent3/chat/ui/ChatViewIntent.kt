package io.github.sd155.aiadvent3.chat.ui

internal sealed class ChatViewIntent {
    data class UserPrompted(val prompt: String) : ChatViewIntent()
}