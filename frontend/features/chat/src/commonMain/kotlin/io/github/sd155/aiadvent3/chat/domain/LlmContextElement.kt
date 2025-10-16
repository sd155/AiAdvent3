package io.github.sd155.aiadvent3.chat.domain

internal data class LlmContextElement(
    val type: LlmContextElementType,
    val value: String,
)

internal enum class LlmContextElementType { System, User, Llm }