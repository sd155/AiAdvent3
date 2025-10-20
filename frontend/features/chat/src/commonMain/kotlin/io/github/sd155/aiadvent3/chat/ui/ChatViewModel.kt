package io.github.sd155.aiadvent3.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sd155.aiadvent3.chat.domain.ChatAgent
import io.github.sd155.aiadvent3.chat.domain.ChatAgentState
import io.github.sd155.aiadvent3.chat.domain.LlmContent
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
                is LlmContextElement.Llm -> {
                    when (element.content) {
                        is LlmContent.Failed ->
                            ChatMessage.LlmFailure(
                                reason = element.content.description,
                                creativity = creativity,
                                usedTokens = element.usedTokens,
                                reasoning = element.reasoning,
                            )
                        is LlmContent.Queried ->
                            ChatMessage.LlmQuery(
                                question = element.content.question,
                                creativity = creativity,
                                usedTokens = element.usedTokens,
                                reasoning = element.reasoning,
                            )
                        is LlmContent.Succeed ->
                            ChatMessage.LlmSuccess(
                                header = element.content.header,
                                creativity = creativity,
                                usedTokens = element.usedTokens,
                                reasoning = element.reasoning,
                                details = element.content.details,
                                summary = element.content.summary,
                            )
                    }
                }
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
