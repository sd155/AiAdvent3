package io.github.sd155.aiadvent3.chat.domain

import aiadvent3.frontend.features.chat.generated.resources.Res
import io.github.sd155.aiadvent3.utils.Result
import io.github.sd155.aiadvent3.utils.asFailure
import io.github.sd155.aiadvent3.utils.asSuccess
import io.github.sd155.aiadvent3.utils.fold
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class ChatAgent(
    apiKey: String,
    private val scope: CoroutineScope,
) {
    private val _llm by lazy { KtorLlmDataSource(apiKey) }
    private val _state = MutableStateFlow(ChatAgentState())
    internal val state: StateFlow<ChatAgentState> = _state.asStateFlow()

    init { scope.launch(Dispatchers.IO) { initializeContext() } }

    private suspend fun initializeContext() {
        addToContext(LlmContextElement(
            type = LlmContextElementType.System,
            value = """
            You are an adviser. You must take the user's prompt and respond with expert-level advice. You must ask user questions until you're absolutely certain of your advice.
            Your response must strictly follow these rules:
            1. Output valid JSON only. Do not wrap it in code markers or add any extra content.
            2. Use the provided JSON schema exactly as given. Do not extend or modify it. The schema: ${String(Res.readBytes("files/chat-agent-response-scheme.json"))}
            3. Use the 'query' type to ask the user for additional details. The 'question' property must contain only one your question.
            4. Use the 'success' type only when you are absolutely certain of your advice, have no unresolved questions, and can provide clear, confident advice.
            """
        ))
    }

    internal fun ask(prompt: String) {
        addToContext(LlmContextElement(
            type = LlmContextElementType.User,
            value = prompt
        ))
        scope.launch(Dispatchers.IO) {
            _llm.postChatCompletions(_state.value.context)
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
}

internal data class ChatAgentState(
    val context: List<LlmContextElement> = emptyList(),
    val error: String? = null,
)

private class KtorLlmDataSource(apiKey: String) {
    private val _httpClient by lazy {
        HttpClient(CIO) {
            install(Logging) {
                level = LogLevel.ALL
            }

            install(ContentNegotiation) {
                json(Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 15000
                requestTimeoutMillis = 60000
            }

            val baseUrl = "https://openrouter.ai/api/v1/chat/completions"
            defaultRequest {
                url(baseUrl)
                header("Content-Type", "application/json; charset=UTF-8")
                header("Authorization", "Bearer $apiKey")
            }
        }
    }

    internal suspend fun postChatCompletions(
        context: List<LlmContextElement>
    ): Result<String, LlmContextElement> {
        println("postChatCompletions")
        val payload = RequestDto(
            model = "tngtech/deepseek-r1t2-chimera:free",
            messages = context.map { it.toMessageDto() },
            responseFormat = FormatDto(type = "json_object"),
            provider = ProviderDto(only = listOf("chutes")),
        )
        return try {
            _httpClient
                .post { setBody(payload) }
                .let { response ->
                    when (response.status) {
                        HttpStatusCode.OK -> response.body<ResponseDto>().choices
                            ?.first()?.message?.toDomain()?.asSuccess()
                            ?: "LLM Error".asFailure()
                        else -> "LLM Error".asFailure()
                    }
                }
        }
        catch (e: Exception) {
            e.printStackTrace()
            "LLM Error".asFailure()
        }
    }

    private fun LlmContextElement.toMessageDto(): MessageDto =
        MessageDto(
            role = when (type) {
                LlmContextElementType.System -> "system"
                LlmContextElementType.User -> "user"
                LlmContextElementType.Llm -> "assistant"
            },
            content = value,
        )
}

@Serializable
private data class RequestDto(
    @SerialName("model")
    val model: String,
    @SerialName("messages")
    val messages: List<MessageDto>,
    @SerialName("response_format")
    val responseFormat: FormatDto,
    @SerialName("provider")
    val provider: ProviderDto,
)

@Serializable
private data class ProviderDto(
    @SerialName("only")
    val only: List<String>,
)

@Serializable
private data class FormatDto(
    @SerialName("type")
    val type: String,
)

@Serializable
private data class ResponseDto(
    @SerialName("choices")
    val choices: List<ChoiceDto>?,
)

@Serializable
private data class ChoiceDto(
    @SerialName("message")
    val message: MessageDto?,
)

@Serializable
private data class MessageDto(
    @SerialName("role")
    val role: String?,
    @SerialName("content")
    val content: String?,
) {

    fun toDomain(): LlmContextElement =
        LlmContextElement(
            type = when (role) {
                "assistant" -> LlmContextElementType.Llm
                "user" -> LlmContextElementType.User
                "system" -> LlmContextElementType.System
                else -> throw IllegalArgumentException("Message has illegal role: $role!")
            },
            value = requireNotNull(content),
        )
}