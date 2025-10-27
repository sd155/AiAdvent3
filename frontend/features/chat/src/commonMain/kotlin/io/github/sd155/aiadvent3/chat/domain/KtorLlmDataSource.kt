package io.github.sd155.aiadvent3.chat.domain

import io.github.sd155.aiadvent3.utils.Result
import io.github.sd155.aiadvent3.utils.asFailure
import io.github.sd155.aiadvent3.utils.asSuccess
import io.github.sd155.aiadvent3.utils.next
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

internal class KtorLlmDataSource(apiKey: String) {
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

    internal suspend fun compressContext(
        context: List<LlmContextElement>,
        creativity: Float,
    ): Result<String, List<LlmContextElement>> {
        val chatConversation = context
            .filter { it !is LlmContextElement.System }
            .let {
                if (it.last() is LlmContextElement.User)
                    it - it.last()
                else
                    it
            }
            .map { it.toMessageDto() }
        val payload = RequestDto(
            model = "mistralai/mistral-7b-instruct:free",
            messages = chatConversation + MessageDto(role = "user", content = "Give me a summary of our chat."),
            temperature = creativity,
        )
        return try {
            _httpClient
                .post { setBody(payload) }
                .let { response ->
                    when (response.status) {
                        HttpStatusCode.OK ->
                            response.body<ResponseDto>()
                                .let { responseDto ->
                                    val compressed = responseDto.choices?.first()?.message?.content
                                    if (compressed != null)
                                        LlmContextElement.User(compressed).asSuccess()
                                    else
                                        "LLM Error: Responded with null".asFailure()
                                }
                        else -> "LLM Error: Network failed".asFailure()
                    }
                }
                .next { compressedElement ->
                    val last = context.last()
                    if (last is LlmContextElement.User)
                        (context.filter { it is LlmContextElement.System } + compressedElement + last).asSuccess()
                    else
                        (context.filter { it is LlmContextElement.System } + compressedElement).asSuccess()
                }
        }
        catch (e: Exception) {
            e.printStackTrace()
            "LLM Error: Unexpected exception".asFailure()
        }
    }

    internal suspend fun postChatCompletions(
        context: List<LlmContextElement>,
        creativity: Float,
    ): Result<String, LlmContextElement> {
        val payload = RequestDto(
            model = "mistralai/mistral-7b-instruct:free",
            messages = context.map { it.toMessageDto() },
            responseFormat = FormatDto(type = "json_object"),
//            provider = ProviderDto(only = listOf("chutes")),
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
                                    val promptTokens = responseDto.usage?.promptTokens
                                    val completionTokens = responseDto.usage?.completionTokens
                                    if (content == null || usedTokens == null || promptTokens == null || completionTokens == null)
                                        "LLM Error: Invalid content or usage".asFailure()
                                    else
                                        LlmContextElement.Llm(
                                            content = _json.decodeFromString<LlmContent>(content),
                                            reasoning = reasoning,
                                            usedTokens = usedTokens,
                                            promptTokens = promptTokens,
                                            completionTokens = completionTokens,
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
                        is LlmContent.Succeed ->
                            "## ${content.header}\n" +
                                    "${content.details.joinToString("\n")}\n" +
                                    "### Summary\n${content.summary}"
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
    val responseFormat: FormatDto? = null,
//    @SerialName("provider")
//    val provider: ProviderDto,
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
    @SerialName("prompt_tokens")
    val promptTokens: Int?,
    @SerialName("completion_tokens")
    val completionTokens: Int?,
)