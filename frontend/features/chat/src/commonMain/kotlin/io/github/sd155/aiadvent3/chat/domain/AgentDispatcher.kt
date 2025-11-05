package io.github.sd155.aiadvent3.chat.domain

import io.github.sd155.aiadvent3.chat.domain.agents.BuggyAgent
import io.github.sd155.aiadvent3.chat.domain.agents.ChattyAgent
import io.github.sd155.aiadvent3.chat.domain.agents.CliAgent
import io.github.sd155.aiadvent3.chat.domain.agents.TaskSchedulerAgent

internal class AgentDispatcher(private val _apiKey: String) {

    internal suspend fun toChatty(prompt: String): String {
        return ChattyAgent.create(_apiKey)
            .run(prompt)
    }

    internal suspend fun toTaskScheduler(prompt: String): String {
        return TaskSchedulerAgent.create(_apiKey)
            .run(prompt)
    }

    internal suspend fun toCleo(prompt: String): String {
        return CliAgent.create(_apiKey)
            .run(prompt)
    }

    internal suspend fun toBuggy(prompt: String): String {
        return BuggyAgent.create(_apiKey)
            .run(prompt)
    }
}