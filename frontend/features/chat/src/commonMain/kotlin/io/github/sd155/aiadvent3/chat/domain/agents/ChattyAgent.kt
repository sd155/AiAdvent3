package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.core.feature.writer.FeatureMessageLogWriter
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruLlmClient
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruModels
import kotlinx.coroutines.flow.MutableStateFlow

internal object ChattyAgent : Agent<String, String> {
    private val _context = MutableStateFlow<List<Message>>(emptyList())
    override val tag: String = "@Chatty"

    override suspend fun create(llmApiKey: String): AIAgent<String, String> {
        return AIAgent(
            strategy = strategy("chat") {
                val nodeCallLLM by node<String, Message.Response> { userPrompt ->
                    llm.writeSession {
                        appendPrompt { user(userPrompt) }
                        requestLLMWithoutTools()
                            .also {
                                _context.value = prompt.messages
                                    .filter { it.role != Message.Role.System }
                            }
                    }
                }
                val compressHistory by node<String, String> { input ->
                    val preserveMemory = true
                    val strategy = HistoryCompressionStrategy.Chunked(10)
                    llm.writeSession {
                        appendPrompt { messages(_context.value) }
                        if (prompt.messages.size > 10) {
                            replaceHistoryWithTLDR(strategy, preserveMemory)
                        }
                    }
                    input
                }

                edge(nodeStart forwardTo compressHistory)
                edge(compressHistory forwardTo nodeCallLLM)
                edge(nodeCallLLM forwardTo nodeFinish transformed {it.content})
            },
//            promptExecutor = simpleOpenRouterExecutor(llmApiKey),
            promptExecutor = SingleLLMPromptExecutor(CloudruLlmClient(llmApiKey)),
//            llmModel = OpenRouterFreeModels.Qwen3_235b_a22b_Moe,
            llmModel = CloudruModels.MiniMax_M2,
            temperature = 0.7,
            systemPrompt = """
                |You are a chatty joyful body.
                |Talk with user in ironically manner, use hi grade humor, sometimes be a little shady.
                |""".trimMargin(),
        ) {
            install(Tracing) {
                addMessageProcessor(TraceFeatureMessageLogWriter(
                    targetLogger = KotlinLogging.logger {},
                    logLevel = FeatureMessageLogWriter.LogLevel.DEBUG
                ))
            }
            handleEvents {
                onAgentExecutionFailed { context ->
                    println("$tag execution failed!")
                    context.throwable.printStackTrace()
                }
            }
        }
    }
}
