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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class Context(
    val role: String,
    val content: String
)

internal object ChattyAgent : Agent<String, String> {
    private val _context = MutableStateFlow<List<Message>>(emptyList())
    override val tag: String = "@Chatty"
    private val _json =  Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun saveContext() {
        if (_context.value.size <= 1) return

        _json.encodeToString(
            _context.value.map { message ->
                Context(
                    role = message.role.name,
                    content = message.content,
                )
            })
            .also { json ->
                File("chatty.mem").writeText(json)
            }
    }

    fun loadContext(): List<Context> =
        File("chatty.mem")
            .let { if (it.exists()) it else null }
            ?.readText()
            ?.let { if (it.isBlank()) null else it }
            ?.let { _json.decodeFromString<List<Context>>(it)}
            ?: emptyList()

    fun loadUserMemory(): String? =
        File("user.mem")
            .let { if (it.exists()) it else null }
            ?.readText()
            ?.let { if (it.isBlank()) null else it }
            ?.replace("\n", "\n- ")
            ?.let { "Facts about user:\n$it"}

    override suspend fun create(llmApiKey: String): AIAgent<String, String> {
        return AIAgent(
            strategy = strategy("chat") {

                val loadUserPortrait by node<String, String> { input ->
                    loadUserMemory()
                        ?.let { userMemory ->
                            llm.writeSession {
                                appendPrompt {
                                    assistant(userMemory)
                                }
                            }
                        }
                    input
                }

                val loadHistory by node<String, String> { input ->
                    llm.writeSession {
                        if (_context.value.isEmpty()) {
                            appendPrompt { loadContext().forEach { context ->
                                when (context.role) {
                                    Message.Role.User.name -> user(context.content)
                                    Message.Role.Assistant.name -> assistant(context.content)
                                    else -> {}
                                }
                            } }
                        }
                        else {
                            appendPrompt { messages(_context.value) }
                        }
                    }
                    input
                }

                val compressHistory by node<String, String> { input ->
                    val preserveMemory = true
                    val strategy = HistoryCompressionStrategy.Chunked(10)
                    llm.writeSession {
                        if (prompt.messages.size > 10) {
                            replaceHistoryWithTLDR(strategy, preserveMemory)
                        }
                    }
                    input
                }

                val nodeCallLLM by node<String, Message.Response> { userPrompt ->
                    llm.writeSession {
                        appendPrompt { user(userPrompt) }
                        requestLLMWithoutTools()
                    }
                }

                val saveHistory by node<String, String> { input ->
                    llm.readSession {
                        _context.value = prompt.messages
                            .filter { it.role != Message.Role.System }
                    }
                    saveContext()
                    input
                }

                edge(nodeStart forwardTo loadUserPortrait)
                edge(loadUserPortrait forwardTo loadHistory)
                edge(loadHistory forwardTo compressHistory)
                edge(compressHistory forwardTo nodeCallLLM)
                edge(nodeCallLLM forwardTo saveHistory transformed {it.content})
                edge(saveHistory forwardTo nodeFinish)
            },
//            promptExecutor = simpleOpenRouterExecutor(llmApiKey),
            promptExecutor = SingleLLMPromptExecutor(CloudruLlmClient(llmApiKey)),
//            llmModel = OpenRouterFreeModels.Qwen3_235b_a22b_Moe,
            llmModel = CloudruModels.Text2Text.MiniMax_M2,
            temperature = 0.7,
            systemPrompt = """
                |You are a chatty joyful body.
                |Talk with user in ironically manner, use hi grade humor, sometimes be a little shady.
                |Use facts about user to answer more personally.
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
