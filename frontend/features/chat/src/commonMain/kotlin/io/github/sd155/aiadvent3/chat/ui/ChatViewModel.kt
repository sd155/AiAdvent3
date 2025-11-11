package io.github.sd155.aiadvent3.chat.ui

import ai.koog.prompt.message.Message
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sd155.aiadvent3.chat.domain.AgentDispatcher
import io.github.sd155.aiadvent3.chat.domain.agents.BuggyAgent
import io.github.sd155.aiadvent3.chat.domain.agents.ChattyAgent
import io.github.sd155.aiadvent3.chat.domain.agents.CliAgent
import io.github.sd155.aiadvent3.chat.domain.agents.EmbedderAgent
import io.github.sd155.aiadvent3.chat.domain.agents.GittyAgent
import io.github.sd155.aiadvent3.chat.domain.agents.ReviewerAgent
import io.github.sd155.aiadvent3.chat.domain.agents.TaskSchedulerAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatViewModel(llmApiKey: String, githubApiKey: String) : ViewModel() {
    private val _dispatcher = LazySuspendValue<AgentDispatcher> { AgentDispatcher(_llmApiKey = llmApiKey, _githubApiKey = githubApiKey) }
    private val _state = MutableStateFlow(ChatViewState())
    internal val state: StateFlow<ChatViewState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            ChattyAgent.loadContext().forEach {
                if (it.role == Message.Role.User.name)
                    _state.value.reduceWithUserMessage(it.content)
                if (it.role == Message.Role.Assistant.name)
                    _state.value.reduceWithAgentMessage(agentTag = ChattyAgent.tag, text = it.content)
            }
            BuggyAgent.state.collect { progress ->
                progress?.let { _state.value.reduceWithAgentMessage(agentTag = BuggyAgent.tag, text = it) }
            }
        }
    }

    internal fun onViewIntent(intent: ChatViewIntent) = viewModelScope.launch(Dispatchers.Default) {
        when (intent) {
            is ChatViewIntent.UserPrompted -> {
                _state.value.reduceWithUserMessage(intent.prompt)
                if (intent.prompt.contains(TaskSchedulerAgent.tag))
                    _state.value.reduceWithAgentMessage(
                        agentTag = TaskSchedulerAgent.tag,
                        text = _dispatcher.get().toTaskScheduler(intent.prompt)
                    )
                else if (intent.prompt.contains(CliAgent.tag))
                    _state.value.reduceWithAgentMessage(
                        agentTag = CliAgent.tag,
                        text = _dispatcher.get().toCleo(intent.prompt)
                    )
                else if (intent.prompt.contains(BuggyAgent.tag))
                    _state.value.reduceWithAgentMessage(
                        agentTag = BuggyAgent.tag,
                        text = _dispatcher.get().toBuggy(intent.prompt)
                    )
                else if (intent.prompt.contains(GittyAgent.TAG))
                    _state.value.reduceWithAgentMessage(
                        agentTag = GittyAgent.TAG,
                        text = _dispatcher.get().toGitty(intent.prompt)
                    )
                else if (intent.prompt.contains(ReviewerAgent.TAG))
                    _state.value.reduceWithAgentMessage(
                        agentTag = ReviewerAgent.TAG,
                        text = _dispatcher.get().toReviewer(intent.prompt)
                    )
                else if (intent.prompt.contains(EmbedderAgent.TAG))
                    _state.value.reduceWithAgentMessage(
                        agentTag = EmbedderAgent.TAG,
                        text = _dispatcher.get().toEmbedder(intent.prompt)
                    )
                else
                    _state.value.reduceWithAgentMessage(
                        agentTag = ChattyAgent.tag,
                        text = _dispatcher.get().toChatty(intent.prompt)
                    )
            }
        }
    }

    private fun ChatViewState.reduceWithAgentMessage(text: String, agentTag: String) {
        val agentMessage = ChatMessage.AgentMessage(
            agentTag = agentTag,
            content = text,
        )
        val updated =
            if (messages.last() is ChatMessage.AgentProgress)
                messages - messages.last() + agentMessage
            else
                messages + agentMessage
        _state.value.reduce {
            copy(updated)
        }
    }

    private fun ChatViewState.reduceWithUserMessage(prompt: String) {
        _state.value.reduce {
            copy(messages + ChatMessage.UserMessage(prompt) + ChatMessage.AgentProgress)
        }
    }

    private fun ChatViewState.reduce(reducer: ChatViewState.() -> ChatViewState): ChatViewState {
        _state.value = reducer(this)
        return this
    }
}

private class LazySuspendValue<T>(private val loader: suspend () -> T) {
    private var value: T? = null

    suspend fun get(): T = value
        ?: let {
            val loaded = loader()
            value = loaded
            loaded
        }
}