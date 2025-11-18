package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.feature.writer.FeatureMessageLogWriter
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruLlmClient
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruModels
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

internal object VoiceAgent : Agent<File, String> {
    private var apiKey: String? = null
    override val tag: String = "Voice"
    val progress = MutableStateFlow<String?>(null)

    override suspend fun create(llmApiKey: String): AIAgent<File, String> {
        apiKey = llmApiKey
        return AIAgent(
            strategy = transcribeStrategy(),
            promptExecutor = SingleLLMPromptExecutor(CloudruLlmClient(llmApiKey)),
            llmModel = CloudruModels.Text2Text.Qwen3_Next_80b_a3b_Instruct,
            temperature = 0.5,
        ) {
            install(Tracing) {
                addMessageProcessor(TraceFeatureMessageLogWriter(
                    targetLogger = KotlinLogging.logger {},
                    logLevel = FeatureMessageLogWriter.LogLevel.DEBUG
                ))
            }
            handleEvents {
                onAgentExecutionFailed { context ->
                    println("${ChattyAgent.tag} execution failed!")
                    context.throwable.printStackTrace()
                }
            }
        }
    }

    private fun transcribeStrategy(): AIAgentGraphStrategy<File, String> = strategy("voice_input") {
        val chattySystemPrompt = """
        |You are a chatty joyful body.
        |Talk with user in ironically manner, use hi grade humor, sometimes be a little shady.
        |""".trimMargin()

        val transcribe by node<File, String> { voiceFile ->
            ApiClient(apiKey ?: throw IllegalStateException("NO API KEY!")) { progress.value = it }
                .post(voiceFile)
        }

        val callLlm by node<String, Message.Response> { userPrompt ->
            llm.writeSession {
                appendPrompt {
                    system(chattySystemPrompt)
                    user(userPrompt)
                }
                requestLLMWithoutTools()
            }
        }

        edge(nodeStart forwardTo transcribe)
        edge(transcribe forwardTo callLlm)
        edge(callLlm forwardTo nodeFinish transformed { it.content })
    }
}

private class ApiClient(apiKey: String, val onProgress: (String) -> Unit) {
    private val _httpClient by lazy {
        HttpClient(CIO) {
            install(Logging) {
                level = LogLevel.HEADERS
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

            val baseUrl = "https://foundation-models.api.cloud.ru/v1/audio/transcriptions"
            defaultRequest {
                url(baseUrl)
                header("Content-Type", "application/json; charset=UTF-8")
                header("Authorization", "Bearer $apiKey")
            }
        }
    }

    suspend fun post(voiceFile: File): String {
        return try {
            _httpClient
                .post {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("file", voiceFile.readBytes(), Headers.build {
                                    append(HttpHeaders.ContentType, "audio/mp3")
                                    append(HttpHeaders.ContentDisposition, "filename=\"${voiceFile.absolutePath}\"")
                                })
                                append("model", "openai/whisper-large-v3")
                                append("response_format", "text")
                                append("temperature", "0.5")
                                append("language", "ru")
                            }
                        )
                    )

                }
                .let { response ->
                    when (response.status) {
                        HttpStatusCode.OK -> {
                            response.body<ResponseDto>().text
                                ?.also { onProgress(it) }
                                ?: "NULL RESPONSE TEXT"
                        }
                        else -> "LLM Error: Network failed"
                    }
                }
        }
        catch (e: Exception) {
            e.printStackTrace()
            e.toString()
        }
    }
}

@Serializable
private data class ResponseDto(
    val text: String?
)