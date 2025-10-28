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
import kotlin.time.TimeSource

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
            You are a logical solver. Your task is to solve the user's logic problem interactively by decomposing it into a sequence of clear reasoning steps. You must present these steps one at a time and wait for user confirmation before proceeding.
            Your response must strictly follow these rules:
            1. Output valid JSON only. Do not wrap it in code markers or add any extra content.
            2. Use the provided JSON schema exactly as given. Do not extend or modify it. The schema: ${String(Res.readBytes("files/chat-agent-response-scheme.json"))}
            3. Always begin with a 'query' type, presenting the very first reasoning step and asking the user to confirm before continuing.
            4. Use the 'query' type for every intermediate step, including the final reasoning step that leads to the answer—do not reveal the final answer until explicitly confirmed.
            5. Use the 'success' type only after the user has confirmed the last reasoning step, and only then include the complete solution.
            """
//            """
//            You are an adviser. You must take the user's prompt and respond with expert-level advice. You must ask user questions until you're absolutely certain of your advice.
//            Your response must strictly follow these rules:
//            1. Output valid JSON only. Do not wrap it in code markers or add any extra content.
//            2. Use the provided JSON schema exactly as given. Do not extend or modify it. The schema: ${String(Res.readBytes("files/chat-agent-response-scheme.json"))}
//            3. Use the 'query' type to ask the user for additional details. The 'question' property must contain only one your question.
//            4. Use the 'success' type only when you are absolutely certain of your advice, have no unresolved questions, and can provide clear, confident advice.
//            """
        ))
    }

    internal suspend fun ask(prompt: String) {
        val toolsResponse = KtorMcpClient().fetchTools()
            .fold(
                onFailure = { error -> "I can't use tools, error: $error" },
                onSuccess = { tools ->
                    tools.joinToString("\n- ") { tool -> "name: ${tool.name}, description: ${tool.description}" }
                        .let { "I can use ${tools.size} tools:\n- $it" }
                }
            )
        addToContext(LlmContextElement.Llm(
            usedTokens = 0,
            elapsedMs = 0L,
            content = LlmContent.Queried(question = toolsResponse)
        ))

//        addToContext(LlmContextElement.User(prompt = prompt))
//        scope.launch(Dispatchers.IO) {
//            _llm.postChatCompletions(
//                context = _state.value.context,
//                creativity = _state.value.creativity,
//            )
//                .fold(
//                    onSuccess = { element -> addToContext(element) },
//                    onFailure = { error -> _state.value = _state.value.copy(error = error) }
//                )
//        }
    }

    private fun addToContext(element: LlmContextElement) {
        val context = _state.value.context
        _state.value = ChatAgentState(context + element)
    }
}

internal data class ChatAgentState(
    val context: List<LlmContextElement> = emptyList(),
    val error: String? = null,
    val creativity: Float = 0.7f,
) {
    init {
        if (this.creativity < 0f || this.creativity > 2f)
            throw IllegalArgumentException("Creativity should be within [0,2]!")
    }
}

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
    private val _json =  Json {
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "result"
    }

    suspend fun postChatCompletions(
        context: List<LlmContextElement>,
        creativity: Float,
    ): Result<String, LlmContextElement> {
        println("postChatCompletions")
        val payload = RequestDto(
            model = "tngtech/deepseek-r1t2-chimera:free",
            messages = context.map { it.toMessageDto() },
            responseFormat = FormatDto(type = "json_object"),
            provider = ProviderDto(only = listOf("chutes")),
            temperature = creativity,
        )
        val start = TimeSource.Monotonic.markNow()
        return try {
            _httpClient
                .post { setBody(payload) }
                .let { response ->
                    when (response.status) {
                        HttpStatusCode.OK ->
                            response.body<ResponseDto>()
                                .let { responseDto ->
                                    val content = responseDto.choices?.first()?.message?.content
                                    val reasoning = responseDto.choices?.first()?.message?.reasoning
                                    val usedTokens = responseDto.usage?.totalTokens
                                    if (content == null || usedTokens == null)
                                        "LLM Error: Invalid content or usedTokens".asFailure()
                                    else
                                        LlmContextElement.Llm(
                                            content = _json.decodeFromString<LlmContent>(content),
                                            reasoning = reasoning,
                                            usedTokens = usedTokens,
                                            elapsedMs = start.elapsedNow().inWholeMilliseconds,
                                        ).asSuccess()
                                }
                        else -> "LLM Error: Network failed".asFailure()
                    }
                }
        }
        catch (e: Exception) {
            e.printStackTrace()
            "LLM Error: Unexpected exception".asFailure()
        }
    }

    private fun LlmContextElement.toMessageDto(): MessageDto =
        when (this) {
            is LlmContextElement.System ->
                MessageDto(
                    role = "system",
                    content = prompt,
                )
            is LlmContextElement.User ->
                MessageDto(
                    role = "user",
                    content = prompt,
                )
            is LlmContextElement.Llm ->
                MessageDto(
                    role = "assistant",
                    content = when (content) {
                        is LlmContent.Failed -> content.description
                        is LlmContent.Queried -> content.question
                        is LlmContent.Succeed -> content.summary
                    },
                )

        }
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
    @SerialName("temperature")
    val temperature: Float,
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
    @SerialName("usage")
    val usage: UsageDto?,
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
    @SerialName("reasoning")
    val reasoning: String? = null,
)

@Serializable
private data class UsageDto(
    @SerialName("total_tokens")
    val totalTokens: Int?,
)