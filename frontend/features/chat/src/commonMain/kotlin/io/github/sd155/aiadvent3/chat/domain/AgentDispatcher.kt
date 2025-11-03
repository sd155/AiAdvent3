package io.github.sd155.aiadvent3.chat.domain

import ai.koog.agents.core.agent.AIAgent
import io.github.sd155.aiadvent3.chat.domain.agents.ChattyAgent
import io.github.sd155.aiadvent3.chat.domain.agents.CliAgent
import io.github.sd155.aiadvent3.chat.domain.agents.TaskSchedulerAgent

internal class AgentDispatcher private constructor(
    private val _apiKey: String,
    private val _chatty: AIAgent<String, String>,
    private val _cleo: AIAgent<String, String>,
) {

    companion object {
        suspend operator fun invoke(apiKey: String): AgentDispatcher {
            return AgentDispatcher(
                _apiKey = apiKey,
                _chatty = ChattyAgent.create(apiKey),
                _cleo = CliAgent.create(apiKey),
            )
        }
    }

    internal suspend fun toChatty(prompt: String): String {
        return _chatty.run(prompt)
    }

    internal suspend fun toTaskScheduler(prompt: String): String {
        return TaskSchedulerAgent.create(_apiKey)
            .run(prompt)
    }

    internal suspend fun toCleo(prompt: String): String {
        return _cleo.run(prompt)
    }
}