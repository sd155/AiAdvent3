package io.github.sd155.aiadvent3.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sd155.aiadvent3.chat.domain.KtorLlmDataSource
import io.github.sd155.aiadvent3.chat.domain.LlmContextElement
import io.github.sd155.aiadvent3.chat.domain.LlmContextElementType
import io.github.sd155.aiadvent3.utils.fold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatViewModel(apiKey: String) : ViewModel() {
    private val _llm by lazy { KtorLlmDataSource(apiKey = apiKey) }
    private val _state = MutableStateFlow(ChatViewState())
    internal val state: StateFlow<ChatViewState> = _state.asStateFlow()

    internal fun onViewIntent(intent: ChatViewIntent) = viewModelScope.launch(Dispatchers.Default) {
        when (intent) {
            is ChatViewIntent.UserPrompted -> {
                _state.value.reduce {
                    val userMsg = ChatMessage.UserMessage(intent.prompt)
                    copy(messages = messages + userMsg)
                }
                _state.value.reduce { copy(messages = messages + ChatMessage.LlmProgress) }
                _llm.postChatCompletions(_state.value.toContext())
                    .fold(
                        onFailure = { error ->
                            _state.value.reduce { copy(messages = messages - messages.last()) }
                            _state.value.reduce { copy(messages = messages + ChatMessage.LlmError(error)) }
                        },
                        onSuccess = { response ->
                            _state.value.reduce { copy(messages = messages - messages.last()) }
                            _state.value.reduce { copy(messages = messages + ChatMessage.LlmMessage(response.value)) }
                        }
                    )
            }
        }
    }

    private fun ChatViewState.reduce(reducer: ChatViewState.() -> ChatViewState): ChatViewState {
        _state.value = reducer(this)
        return this
    }

    private fun ChatViewState.toContext(): List<LlmContextElement> =
        this.messages.mapNotNull { message ->
            when (message) {
                is ChatMessage.LlmMessage -> LlmContextElement(
                    type = LlmContextElementType.Llm,
                    value = message.content
                )
                is ChatMessage.UserMessage -> LlmContextElement(
                    type = LlmContextElementType.User,
                    value = message.content
                )
                else -> null
            }
        }
}
