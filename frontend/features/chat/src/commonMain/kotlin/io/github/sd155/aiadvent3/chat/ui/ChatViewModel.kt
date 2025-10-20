package io.github.sd155.aiadvent3.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sd155.aiadvent3.chat.domain.ChatAgent
import io.github.sd155.aiadvent3.chat.domain.ChatAgentState
import io.github.sd155.aiadvent3.chat.domain.LlmContextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatViewModel(apiKey: String) : ViewModel() {
    private val _agent by lazy { ChatAgent(apiKey = apiKey, scope = viewModelScope) }
    private val _state = MutableStateFlow(ChatViewState())
    internal val state: StateFlow<ChatViewState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            _agent.state.collect { agentState ->
                _state.value.reduce { agentState.toChatViewState() }
            }
        }
    }

    internal fun onViewIntent(intent: ChatViewIntent) = viewModelScope.launch(Dispatchers.Default) {
        when (intent) {
            is ChatViewIntent.UserPrompted -> _agent.ask(intent.prompt)
        }
    }

    private fun ChatViewState.reduce(reducer: ChatViewState.() -> ChatViewState): ChatViewState {
        _state.value = reducer(this)
        return this
    }

    private fun ChatAgentState.toChatViewState(): ChatViewState {
        val messages = this.context.mapNotNull { element ->
            when (element) {
                is LlmContextElement.User -> ChatMessage.UserMessage(element.prompt)
                is LlmContextElement.Llm.Succeed ->
                    ChatMessage.LlmSuccess(
                        header = element.header,
                        creativity = creativity,
                        details = element.details,
                        summary = element.summary,
                    )
                is LlmContextElement.Llm.Queried ->
                    ChatMessage.LlmQuery(
                        question = element.question,
                        creativity = creativity,
                    )
                is LlmContextElement.Llm.Failed ->
                    ChatMessage.LlmFailure(
                        reason = element.description,
                        creativity = creativity,
                    )
                else -> null
            }
        }
        return if (this.error != null)
            ChatViewState(messages + ChatMessage.LlmError(this.error))
        else if (messages.isNotEmpty() && messages.last() is ChatMessage.UserMessage)
            ChatViewState(messages + ChatMessage.LlmProgress)
        else
            ChatViewState(messages)
    }
}
