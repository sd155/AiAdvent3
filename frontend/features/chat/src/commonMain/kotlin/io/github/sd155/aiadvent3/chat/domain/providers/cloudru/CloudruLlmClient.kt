package io.github.sd155.aiadvent3.chat.domain.providers.cloudru

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBasedSettings
import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrameFlowBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Configuration settings for connecting to the Cloud.ru API.
 *
 * @property baseUrl The base URL of the Cloud.ru API. Default is "https://foundation-models.api.cloud.ru/v1".
 * @property timeoutConfig Configuration for connection timeouts including request, connection, and socket timeouts.
 * @property chatCompletionsPath - The path of the Cloud.ru Chat Completions API. Defaults to "v1/chat/completions".
 * @property embeddingsPath - The path of the Cloud.ru Embeddings API. Defaults to "v1/embeddings".
 */
internal class CloudruClientSettings(
    baseUrl: String = "https://foundation-models.api.cloud.ru",
    chatCompletionsPath: String = "v1/chat/completions",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    val embeddingsPath: String = "v1/embeddings",
) : OpenAIBasedSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Represents the Cloud.ru provider within the available set of large language model providers.
 *
 * Cloud.ru is identified by its unique ID ("cloudru") and display name ("CloudRu").
 * It extends the `LLMProvider` sealed class, which serves as a base class for all supported language model providers.
 *
 * This data object adheres to the structure and serialization requirements defined by the parent class.
 * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
 * providers for large language model functionalities and capabilities.
 */
@Serializable
internal data object CloudRuLlmProvider : LLMProvider("cloudru", "CloudRu")

/**
 * Implementation of [ai.koog.prompt.executor.clients.LLMClient] for Cloud.ru API.
 * Cloud.ru is an API that routes requests to multiple LLM providers.
 *
 * @param apiKey The API key for the Cloud.ru API
 * @param settings The base URL and timeouts for the Cloud.ru API, defaults to "https://foundation-models.api.cloud.ru" and 900s
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
internal class CloudruLlmClient(
    apiKey: String,
    baseClient: HttpClient = HttpClient().config { install(Logging) { level = LogLevel.ALL } },
    clock: Clock = Clock.System,
    private val settings: CloudruClientSettings = CloudruClientSettings(),
) : AbstractOpenAILLMClient<CloudruChatCompletionResponse, CloudruChatCompletionStreamResponse>(
    apiKey,
    settings,
    baseClient,
    clock,
    staticLogger
),
    LLMEmbeddingProvider {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }

        init {
            // On class load register custom OpenAI JSON schema generators for structured output.
            registerOpenAIJsonSchemaGenerators(CloudRuLlmProvider)
        }
    }

    /**
     * Returns the specific implementation of the `LLMProvider` associated with this client.
     *
     * In this case, it identifies the `CloudRu` provider as the designated LLM provider
     * for the client.
     *
     * @return The `LLMProvider` instance representing CloudRu.
     */
    override fun llmProvider(): LLMProvider = CloudRuLlmProvider

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val cloudruParams = params.toCloudruParams()
        val responseFormat = createResponseFormat(params.schema, model)

        val request = CloudruChatCompletionRequest(
            messages = messages,
            model = model.id,
            stream = stream,
            temperature = cloudruParams.temperature,
            tools = tools,
            toolChoice = cloudruParams.toolChoice?.toOpenAIToolChoice(),
            topP = cloudruParams.topP,
            topLogprobs = cloudruParams.topLogprobs,
            maxTokens = cloudruParams.maxTokens,
            frequencyPenalty = cloudruParams.frequencyPenalty,
            presencePenalty = cloudruParams.presencePenalty,
            responseFormat = responseFormat,
            stop = cloudruParams.stop,
            logprobs = cloudruParams.logprobs,
            topK = cloudruParams.topK,
            repetitionPenalty = cloudruParams.repetitionPenalty,
            minP = cloudruParams.minP,
            topA = cloudruParams.topA,
            prediction = cloudruParams.speculation?.let { OpenAIStaticContent(Content.Text(it)) },
            transforms = cloudruParams.transforms,
            models = cloudruParams.models,
            route = cloudruParams.route,
            user = cloudruParams.user,
            additionalProperties = cloudruParams.additionalProperties,
        )

        return json.encodeToString(CloudruChatCompletionRequestSerializer, request)
    }

    override fun processProviderChatResponse(response: CloudruChatCompletionResponse): List<LLMChoice> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map {
            it.message.toMessageResponses(
                it.finishReason,
                createMetaInfo(response.usage),
            )
        }
    }

    override fun decodeStreamingResponse(data: String): CloudruChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): CloudruChatCompletionResponse =
        json.decodeFromString(data)

    override suspend fun StreamFrameFlowBuilder.processStreamingChunk(chunk: CloudruChatCompletionStreamResponse) {
        chunk.choices.firstOrNull()?.let { choice ->
            choice.delta.content?.let { emitAppend(it) }
            choice.delta.toolCalls?.forEachIndexed { index, openAIToolCall ->
                val id = openAIToolCall.id
                val name = openAIToolCall.function.name
                val arguments = openAIToolCall.function.arguments
                upsertToolCall(index, id, name, arguments)
            }
            choice.finishReason?.let { emitEnd(it, createMetaInfo(chunk.usage)) }
        }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by Cloud.ru API" }
        throw UnsupportedOperationException("Moderation is not supported by Cloud.ru API.")
    }

    /**
     * Embeds the given text using the OpenAI embeddings API.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        model.requireCapability(LLMCapability.Embed)

        logger.debug { "Embedding text with model: ${model.id}" }

        val request = CloudruEmbeddingRequest(
            model = model.id,
            input = text
        )

        @Suppress("UnstableApiUsage")
        val response = httpClient.post(
            path = settings.embeddingsPath,
            request = request,
            requestBodyType = CloudruEmbeddingRequest::class,
            responseType = CloudruEmbeddingResponse::class
        )
        if (response.data.isEmpty()) {
            logger.error { "Empty data in Cloud.ru embedding response" }
            error("Empty data in Cloud.ru embedding response")
        }
        return response.data.first().embedding
    }
}

@Serializable
internal data class CloudruEmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
internal data class CloudruEmbeddingResponse(
    val data: List<CloudruEmbeddingData>,
    val model: String,
    val usage: OpenAIUsage? = null
)

@Serializable
internal data class CloudruEmbeddingData(
    val embedding: List<Double>,
    val index: Int
)