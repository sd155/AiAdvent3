package io.github.sd155.aiadvent3.chat.domain

import io.github.sd155.aiadvent3.chat.domain.agents.BuggyAgent
import io.github.sd155.aiadvent3.chat.domain.agents.ChattyAgent
import io.github.sd155.aiadvent3.chat.domain.agents.CliAgent
import io.github.sd155.aiadvent3.chat.domain.agents.EmbedderAgent
import io.github.sd155.aiadvent3.chat.domain.agents.GittyAgent
import io.github.sd155.aiadvent3.chat.domain.agents.RepoAgent
import io.github.sd155.aiadvent3.chat.domain.agents.ReviewerAgent
import io.github.sd155.aiadvent3.chat.domain.agents.TaskSchedulerAgent
import io.github.sd155.aiadvent3.chat.domain.agents.VoiceAgent
import java.io.File

internal class AgentDispatcher(private val _llmApiKey: String, private val _githubApiKey: String) {

    internal suspend fun toChatty(prompt: String): String {
        return ChattyAgent.create(_llmApiKey)
            .run(prompt)
    }

    internal suspend fun toTaskScheduler(prompt: String): String {
        return TaskSchedulerAgent.create(_llmApiKey)
            .run(prompt)
    }

    internal suspend fun toCleo(prompt: String): String {
        return CliAgent.create(_llmApiKey)
            .run(prompt)
    }

    internal suspend fun toBuggy(prompt: String): String {
        return BuggyAgent.create(_llmApiKey)
            .run(prompt)
    }

    internal suspend fun toGitty(prompt: String): String {
        return GittyAgent.create(llmApiKey = _llmApiKey, githubApiKey = _githubApiKey)
            .run(prompt)
    }

    internal suspend fun toReviewer(prompt: String): String {
        return ReviewerAgent.create(llmApiKey = _llmApiKey, githubApiKey = _githubApiKey)
            .run(prompt)
    }

    internal suspend fun toEmbedder(): String {
        return EmbedderAgent.buildEmbeddings(llmApiKey = _llmApiKey)
            .let { "Done" }
    }

    internal suspend fun toRepoNoRag(prompt: String): String {
        return RepoAgent.create(llmApiKey = _llmApiKey, useRag = false)
            .run(prompt)
    }

    internal suspend fun toRepoWithRag(prompt: String): String {
        return RepoAgent.create(llmApiKey = _llmApiKey, useRag = true)
            .run(prompt)
    }

    internal suspend fun toVoice(voiceFile: File): String {
        return VoiceAgent.create(llmApiKey = _llmApiKey)
            .run(voiceFile)
    }
}