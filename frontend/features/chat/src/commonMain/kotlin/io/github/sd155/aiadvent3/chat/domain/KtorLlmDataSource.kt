package io.github.sd155.aiadvent3.chat.domain

import io.github.sd155.aiadvent3.utils.Result
import io.github.sd155.aiadvent3.utils.asFailure
import io.github.sd155.aiadvent3.utils.asSuccess
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

    internal suspend fun postChatCompletions(
        context: List<LlmContextElement>
    ): Result<String, LlmContextElement> {
        println("postChatCompletions")
        val payload = RequestDto(
            model = "qwen/qwen3-235b-a22b:free",
            messages = context.map { it.toMessageDto() },
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