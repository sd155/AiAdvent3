package io.github.sd155.aiadvent3.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sd155.aiadvent3.chat.domain.AgentDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatViewModel(apiKey: String) : ViewModel() {
    private val _dispatcher by lazy { AgentDispatcher(apiKey) }
    private val _state = MutableStateFlow(ChatViewState())
    internal val state: StateFlow<ChatViewState> = _state.asStateFlow()

    internal fun onViewIntent(intent: ChatViewIntent) = viewModelScope.launch(Dispatchers.Default) {
        when (intent) {
            is ChatViewIntent.UserPrompted -> {
                _state.value.reduceWithUserMessage(intent.prompt)
                val response = _dispatcher.chat(intent.prompt)
                _state.value.reduceWithLlmMessage(response)
            }
        }
    }

    private fun ChatViewState.reduceWithLlmMessage(response: String) {
        val agentMessage = ChatMessage.AgentMessage(
            agentTag = "@Chatty",
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
