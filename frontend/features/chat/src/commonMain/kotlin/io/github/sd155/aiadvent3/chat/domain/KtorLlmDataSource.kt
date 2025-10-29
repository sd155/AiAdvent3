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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.TimeSource

internal class ChatAgent(
    apiKey: String,
    private val scope: CoroutineScope,
) {
    private val _llm by lazy { KtorLlmDataSource(apiKey) }
    private val _mcp by lazy { KtorMcpClient() }
    private val _state = MutableStateFlow(ChatAgentState())
    internal val state: StateFlow<ChatAgentState> = _state.asStateFlow()
    private var funcs: List<LlmFunctionDto>? = null

    init { scope.launch(Dispatchers.IO) { initializeContext() } }

    private suspend fun initializeContext() {
        addToContext(LlmContextElement.System(prompt =
            """
            You are a personal assistant.
            """
        ))

        _mcp.fetchTools()
            .fold(
                onFailure = { error -> "I can't use tools, error: $error" },
                onSuccess = { tools ->
                    funcs = tools.map { tool ->
                        LlmFunctionDto(
                            type = "function",
                            function = buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", Json.parseToJsonElement(tool.inputSchema) as JsonObject)
                            }
                        )
                    }
                }
            )
    }

    internal suspend fun ask(prompt: String) {



        addToContext(LlmContextElement.User(prompt = prompt))
        scope.launch(Dispatchers.IO) {
            _llm.postChatCompletions(
                context = _state.value.context,
                creativity = _state.value.creativity,
                functions = funcs ?: throw IllegalStateException("No tools available!"),
            )
                .fold(
                    onSuccess = { element ->
                        addToContext(element)
                        if (element.toolArgs != null) {
                            val fileContent = _mcp.readFile(fileName = element.toolArgs)
                                .fold(
                                    onFailure = { error -> "Failed to read file, error: $error" },
                                    onSuccess = { fileContent -> fileContent }
                                )
                                .also { println("READ RESPONSE: $it") }
                            addToContext(LlmContextElement.Tool(content = fileContent))
                            _llm.postChatCompletions(
                                context = _state.value.context,
                                creativity = _state.value.creativity,
                                functions = funcs ?: throw IllegalStateException("No tools available!"),
                            )
                                .fold(
                                    onFailure = { error -> _state.value = _state.value.copy(error = error) },
                                    onSuccess = { element -> addToContext(element) }
                                )
                        }
                    },
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
        functions: List<LlmFunctionDto>,
    ): Result<String, LlmContextElement.Llm> {
        val payload = RequestDto(
            model = "z-ai/glm-4.5-air:free",
            messages = context.map { it.toMessageDto() },
//            responseFormat = FormatDto(type = "json_object"),
//            provider = ProviderDto(only = listOf("chutes")),
            temperature = creativity,
            tools = functions,
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
                                    val toolCall = responseDto.choices?.first()?.message?.toolCalls?.first()
                                    val content = responseDto.choices?.first()?.message?.content
                                    val reasoning = responseDto.choices?.first()?.message?.reasoning
                                    val usedTokens = responseDto.usage?.totalTokens
                                    if (content == null || usedTokens == null)
                                        "LLM Error: Invalid content or usedTokens".asFailure()
                                    else if (toolCall != null) {
                                        val toolFun = Json.parseToJsonElement(toolCall["function"].toString()) as JsonObject
                                        val toolArg = toolFun["arguments"]?.jsonPrimitive?.contentOrNull
                                            ?.let { Json.parseToJsonElement(it).jsonObject }
                                            ?: throw RuntimeException()
                                        LlmContextElement.Llm(
                                            content = content,
                                            reasoning = reasoning,
                                            usedTokens = usedTokens,
                                            elapsedMs = start.elapsedNow().inWholeMilliseconds,
                                            toolId = toolCall["id"].toString(),
                                            tool = toolFun["name"]?.jsonPrimitive?.content,
                                            toolArgs = toolArg["name"]?.jsonPrimitive?.content,
                                        ).asSuccess()
                                    }
                                    else {
                                        LlmContextElement.Llm(
                                            content = content,
                                            reasoning = reasoning,
                                            usedTokens = usedTokens,
                                            elapsedMs = start.elapsedNow().inWholeMilliseconds,
                                        ).asSuccess()
                                    }
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
                    content = content,
                    toolCalls = if (tool != null)
                        listOf(
                            buildJsonObject {
                                put("id", toolId)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", tool)
                                }
                            }
                        )
                    else null
                )
            is LlmContextElement.Tool ->
                MessageDto(
                    role = "tool",
                    content = content,
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
    val responseFormat: FormatDto? = null,
    @SerialName("provider")
    val provider: ProviderDto? = null,
    @SerialName("temperature")
    val temperature: Float,
    @SerialName("tools")
    val tools: List<LlmFunctionDto>?,
)

@Serializable
private data class LlmFunctionDto(
    @SerialName("type")
    val type: String,
    @SerialName("function")
    val function: JsonObject,
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
    @SerialName("tool_calls")
    val toolCalls: List<JsonObject>? = null,
)

@Serializable
private data class ToolCall(
    @SerialName("id")
    val id: String?,
    @SerialName("function")
    val functionCall: String?,
)

@Serializable
private data class FunctionCall(
    @SerialName("name")
    val name: String?,
    @SerialName("arguments")
    val arguments: String?,
)

@Serializable
private data class UsageDto(
    @SerialName("total_tokens")
    val totalTokens: Int?,
)