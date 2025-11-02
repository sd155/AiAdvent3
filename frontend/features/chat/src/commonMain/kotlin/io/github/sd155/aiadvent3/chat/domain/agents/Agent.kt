package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.agents.core.agent.AIAgent

internal interface Agent<I, O> {
    val tag: String
    suspend fun create(llmApiKey: String): AIAgent<I, O>
}
