package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.message.Message
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruLlmClient
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruModels
import io.github.sd155.aiadvent3.chat.domain.providers.openrouter.OpenRouterFreeModels
import kotlinx.coroutines.flow.MutableStateFlow

internal object BuggyAgent : Agent<String, String> {
    internal val state = MutableStateFlow<String?>(null)
    override val tag: String = "@Buggy"

    override suspend fun create(llmApiKey: String): AIAgent<String, String> {
        val tools = McpToolRegistryProvider.fromTransport(
            transport = McpToolRegistryProvider.defaultSseTransport("http://127.0.0.3:8181"),
            name = "Dev_MCP_client",
            version = "0.0.1"
        )
        val reasoningInterval = 1
        return AIAgent(
            strategy = strategy("BugHunter") {
                require(reasoningInterval > 0) { "Reasoning interval must be greater than 0" }
                val reasoningStepKey = createStorageKey<Int>("reasoning_step")
                val nodeSetup by node<String, String> {
                    state.value = "Init.."
                    storage.set(reasoningStepKey, 0)
                    it
                }
                val nodeCallLLM by node<Unit, Message.Response> {
                    state.value = "Brain in use.."
                    llm.writeSession {
                        requestLLM()
                    }
                        .also { state.value = it.content }
                }
                val nodeExecuteTool by nodeExecuteTool()

                val reasoningPrompt = "Please give your thoughts about the task and plan the next steps."
                val nodeCallLLMReasonInput by node<String, Unit> { stageInput ->
                    state.value = "Puff.."
                    llm.writeSession {
                        appendPrompt {
                            user(stageInput)
                            user(reasoningPrompt)
                        }

                        requestLLMWithoutTools()
                    }
                }
                val nodeCallLLMReason by node<ReceivedToolResult, Unit> { result ->
                    val reasoningStep = storage.getValue(reasoningStepKey)
                    state.value = "Some data comes.."
                    llm.writeSession {
                        appendPrompt {
                            tool {
                                result(result)
                            }
                        }

                        if (reasoningStep % reasoningInterval == 0) {
                            appendPrompt {
                                user(reasoningPrompt)
                            }
                            requestLLMWithoutTools()
                        }
                    }
                    storage.set(reasoningStepKey, reasoningStep + 1)
                }

                edge(nodeStart forwardTo nodeSetup)
                edge(nodeSetup forwardTo nodeCallLLMReasonInput)
                edge(nodeCallLLMReasonInput forwardTo nodeCallLLM)
                edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
                edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeExecuteTool forwardTo nodeCallLLMReason)
                edge(nodeCallLLMReason forwardTo nodeCallLLM)
            },
            systemPrompt = """
                |You are an expert Kotlin software engineer and meticulous code reviewer. 
                |Your task is to analyze the entire project codebase to identify bugs, anti-patterns, 
                |security issues, performance problems, and Kotlin-specific code quality concerns.
                |Begin by listing the project root to map the structure. 
                |Ask for clarification only if the project layout is ambiguous.
                |Workflow:
                |1. Discover structure: Start by listing files from the project root to understand layout
                |2. Prioritize critical files: Focus on:
                |   - Entry points
                |   - Core logic
                |   - Configuration
                |   - Test files (to understand expected behavior)
                |3. Read and analyze to examine code. Pay special attention to:
                |   - Null safety violations
                |   - Resource leaks (unmanaged streams, unclosed connections)
                |   - Threading issues (shared mutable state, missing @Volatile/synchronized)
                |   - Ktor/Kotlin-specific bugs (misconfigured clients, coroutine scope leaks)
                |   - Exception handling (empty catch blocks, swallowed errors)
                |   - Security (hardcoded secrets, unsafe deserialization)
                |4. Synthesize findings: Report bugs with
                |   - File path and line with the bug
                |   - Severity (Critical/High/Medium/Low)
                |   - Concise explanation of the issue
                |   - Kotlin-idiomatic fix suggestion
                |**Strict Rules:**
                |1. Never assume file contents – always read file before analyzing.
                |2. If the project is large, focus on high-impact areas first (avoid reading every file).
                |3. If a file is >500 lines, scan strategically (focus on function signatures, error-handling blocks, and resource management).
                |""".trimMargin(),
            promptExecutor = SingleLLMPromptExecutor(CloudruLlmClient(llmApiKey)),
//            promptExecutor = simpleOpenRouterExecutor(llmApiKey),
            llmModel = CloudruModels.Text2Text.Qwen3_Next_80b_a3b_Instruct,
//            llmModel = OpenRouterFreeModels.Glm4_5_Air_Moe,
            temperature = 0.3,
            toolRegistry = tools,
        ) {
            handleEvents {
                onAgentStarting { context ->
                    println("${tag} started ${context.context}")
                }
                onAgentCompleted { context ->
                    println("${tag} finished with result: ${context.result}")
                }
                onAgentExecutionFailed { context ->
                    println("${tag} execution failed!")
                    context.throwable.printStackTrace()
                }
                onLLMCallStarting { context ->
                    println("${tag} LLM call started: prompt:${context.prompt},\ntools:\n${context.tools.map { "name:${it.name}\ndesc:${it.description}" }}")
                }
                onLLMCallCompleted { context ->
                    val responsesString = context.responses
                        .joinToString("\n") { """{"role":"${it.role}", "content":"${it.content}"}""" }
                    println("${tag} LLM call completed:\n$responsesString")
                }
                onToolCallFailed { context ->
                    println("${tag} Tool failed, tool:${context.tool}, agrs:${context.toolArgs}")
                    context.throwable.printStackTrace()
                }
                onToolCallStarting { context ->
                    println("${tag} Tool started, tool:${context.tool}, agrs:${context.toolArgs}")
                }
                onToolCallCompleted { context ->
                    println("${tag} Tool completed, result:${context.result}")
                }
            }
        }
    }
}