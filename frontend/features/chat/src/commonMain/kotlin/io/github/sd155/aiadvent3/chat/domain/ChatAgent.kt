package io.github.sd155.aiadvent3.chat.domain

import aiadvent3.frontend.features.chat.generated.resources.Res
import com.aallam.ktoken.Encoding
import com.aallam.ktoken.Tokenizer
import io.github.sd155.aiadvent3.utils.fold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatAgent(
    apiKey: String,
    private val scope: CoroutineScope,
) {
    private val _llm by lazy { KtorLlmDataSource(apiKey) }
    private val _state = MutableStateFlow(ChatAgentState())
    internal val state: StateFlow<ChatAgentState> = _state.asStateFlow()

    init { scope.launch(Dispatchers.IO) { initializeContext() } }

    private suspend fun initializeContext() {
        addToContext(LlmContextElement.System(prompt =
            """
            You are writer. You need to write a rich and large text.
            Your response must strictly follow these rules:
            1. Output valid JSON only. Do not wrap it in code markers or add any extra content.
            2. Use the provided JSON schema exactly as given. Do not extend or modify it. The schema: ${String(Res.readBytes("files/chat-agent-response-scheme.json"))}
            4. Use the 'success' type only.
            """
        ))
    }

    internal fun ask(prompt: String) {
        addToContext(LlmContextElement.User(prompt = prompt))
        scope.launch(Dispatchers.IO) {
            processContext()
            _llm.postChatCompletions(
                context = _state.value.context,
                creativity = _state.value.creativity,
            )
                .fold(
                    onSuccess = { element -> addToContext(element) },
                    onFailure = { error -> _state.value = _state.value.copy(error = error) }
                )
        }
    }

    private fun addToContext(element: LlmContextElement) {
        val context = _state.value.context
        _state.value = ChatAgentState(context + element)
    }

    private suspend fun processContext() {
        val tokensThreshold = 10000
        val contextString = _state.value.context.joinToString("\n\n") { element ->
            when (element) {
                is LlmContextElement.Llm -> {
                    when (element.content) {
                        is LlmContent.Failed -> element.content.description
                        is LlmContent.Queried -> element.content.question
                        is LlmContent.Succeed ->
                            "##${element.content.header}\n" +
                                    "${element.content.details.joinToString("\n")}\n" +
                                    "### Summary\n${element.content.summary}"
                    }
                }
                is LlmContextElement.System -> element.prompt
                is LlmContextElement.User -> element.prompt
            }
        }
        val promptTokenNumber = measureTokens(contextString)
        if (promptTokenNumber >= tokensThreshold)
            _llm.compressContext(
                context = _state.value.context,
                creativity = _state.value.creativity,
            )
                .fold(
                    onFailure = { error -> println("ERROR(processContext): $error") },
                    onSuccess = { compressedContext ->
                        _state.value = _state.value.copy(
                            compressed = true,
                            context = compressedContext,
                        )
                    }
                )
    }

    private suspend fun measureTokens(text: String): Int {
        val tokenizer = Tokenizer.of(encoding = Encoding.CL100K_BASE)
        return tokenizer.encode(text).size
    }
}

internal data class ChatAgentState(
    val context: List<LlmContextElement> = emptyList(),
    val error: String? = null,
    val creativity: Float = 0.7f,
    val compressed: Boolean = false,
) {
    init {
        if (this.creativity < 0f || this.creativity > 2f)
            throw IllegalArgumentException("Creativity should be within [0,2]!")
    }
}