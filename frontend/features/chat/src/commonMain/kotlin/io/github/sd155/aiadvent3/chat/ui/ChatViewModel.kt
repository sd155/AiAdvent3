package io.github.sd155.aiadvent3.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sd155.aiadvent3.chat.domain.LlmContent
import io.github.sd155.aiadvent3.chat.domain.LlmContextElement
import io.github.sd155.aiadvent3.chat.domain.ResearchAnalystAgent
import io.github.sd155.aiadvent3.chat.domain.TranslatorAgent
import io.github.sd155.aiadvent3.utils.asFailure
import io.github.sd155.aiadvent3.utils.fold
import io.github.sd155.aiadvent3.utils.next
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatViewModel(apiKey: String) : ViewModel() {
    private val _researcher by lazy { ResearchAnalystAgent(apiKey = apiKey) }
    private val _translator by lazy { TranslatorAgent(apiKey = apiKey) }
    private val _state = MutableStateFlow(ChatViewState())
    internal val state: StateFlow<ChatViewState> = _state.asStateFlow()

    internal fun onViewIntent(intent: ChatViewIntent) = viewModelScope.launch(Dispatchers.Default) {
        when (intent) {
            is ChatViewIntent.UserPrompted -> {
                _state.value.reduce { ChatViewState(listOf(ChatMessage.UserMessage(intent.prompt))) }
                _state.value.messages.reduceWithProgress()
                _researcher.research(LlmContextElement.User(intent.prompt))
                    .next { researcherResponse ->
                        if (researcherResponse.content is LlmContent.Succeed) {
                            _state.value.messages
                                .clearProgress()
                                .reduceWithLlmMessage(
                                    tag = "Researcher",
                                    element = researcherResponse
                                )
                                .reduceWithProgress()
                            _translator.translate(researcherResponse.content)
                        }
                        else
                            "Bad researcher response!".asFailure()
                    }
                    .fold(
                        onFailure = { error ->
                            _state.value.messages
                                .clearProgress()
                                .let { messages ->
                                    _state.value.reduce { ChatViewState(messages + ChatMessage.LlmError(error)) }
                                }
                        },
                        onSuccess = { translatorResponse ->
                            _state.value.messages
                                .clearProgress()
                                .reduceWithLlmMessage(
                                    tag = "Translator",
                                    element = translatorResponse
                                )
                        }
                    )
            }
        }
    }

    private fun List<ChatMessage>.reduceWithProgress() {
        _state.value.messages.let { messages ->
            _state.value.reduce { ChatViewState(messages + ChatMessage.LlmProgress) }
        }
    }

    private fun List<ChatMessage>.clearProgress(): List<ChatMessage> =
        if (isNotEmpty() && last() is ChatMessage.LlmProgress)
            this - last()
        else
            this

    private fun List<ChatMessage>.reduceWithLlmMessage(
        element: LlmContextElement.Llm,
        tag: String
    ): List<ChatMessage> {
        val llmMessage = when (element.content) {
            is LlmContent.Failed ->
                ChatMessage.LlmFailure(
                    reason = element.content.description,
                    creativity = element.creativity,
                    usedTokens = element.usedTokens,
                    reasoning = element.reasoning,
                    elapsedMs = element.elapsedMs,
                )
            is LlmContent.Succeed ->
                ChatMessage.LlmSuccess(
                    header = element.content.header,
                    creativity = element.creativity,
                    usedTokens = element.usedTokens,
                    reasoning = element.reasoning,
                    details = element.content.details,
                    summary = element.content.summary,
                    elapsedMs = element.elapsedMs,
                    agentTag = tag,
                )
        }
        val updatedMessages = this + llmMessage
        _state.value.reduce { ChatViewState(updatedMessages) }
        return updatedMessages
    }

    private fun ChatViewState.reduce(reducer: ChatViewState.() -> ChatViewState): ChatViewState {
        _state.value = reducer(this)
        return this
    }
}
