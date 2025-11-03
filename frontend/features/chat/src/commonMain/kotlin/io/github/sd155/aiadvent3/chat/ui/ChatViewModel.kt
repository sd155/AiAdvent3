package io.github.sd155.aiadvent3.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sd155.aiadvent3.chat.domain.AgentDispatcher
import io.github.sd155.aiadvent3.chat.domain.agents.ChattyAgent
import io.github.sd155.aiadvent3.chat.domain.agents.CliAgent
import io.github.sd155.aiadvent3.chat.domain.agents.TaskSchedulerAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatViewModel(apiKey: String) : ViewModel() {
    private val _dispatcher = LazySuspendValue<AgentDispatcher> { AgentDispatcher(apiKey) }
    private val _state = MutableStateFlow(ChatViewState())
    internal val state: StateFlow<ChatViewState> = _state.asStateFlow()

    internal fun onViewIntent(intent: ChatViewIntent) = viewModelScope.launch(Dispatchers.Default) {
        when (intent) {
            is ChatViewIntent.UserPrompted -> {
                _state.value.reduceWithUserMessage(intent.prompt)
                if (intent.prompt.contains(TaskSchedulerAgent.tag))
                    _state.value.reduceWithTaskerUpdate(_dispatcher.get().toTaskScheduler(intent.prompt))
                else if (intent.prompt.contains(CliAgent.tag))
                    _state.value.reduceWithCleoMessage(_dispatcher.get().toCleo(intent.prompt))
                else
                    _state.value.reduceWithChattyMessage(_dispatcher.get().toChatty(intent.prompt))
            }
        }
    }

    private fun ChatViewState.reduceWithCleoMessage(text: String) {
        val agentMessage = ChatMessage.AgentMessage(
            agentTag = CliAgent.tag,
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

    private fun ChatViewState.reduceWithTaskerUpdate(text: String) {
        val agentMessage = ChatMessage.AgentMessage(
            agentTag = TaskSchedulerAgent.tag,
            content = text,
        )
        val updated =
            if (messages.isNotEmpty() && messages.last() is ChatMessage.AgentProgress)
                messages - messages.last() + agentMessage + ChatMessage.AgentProgress
            else
                messages + agentMessage
        _state.value.reduce {
            copy(updated)
        }
    }

    private fun ChatViewState.reduceWithChattyMessage(response: String) {
        val agentMessage = ChatMessage.AgentMessage(
            agentTag = ChattyAgent.tag,
            content = response,
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