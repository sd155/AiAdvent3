package io.github.sd155.aiadvent3.chat.domain

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class AgentDispatcher(
    private val apiKey: String,
) {
    private val _chatty = AIAgent(
        promptExecutor = simpleOpenRouterExecutor(apiKey),
        systemPrompt = """You are a chatty joyful body.
            |Talk with user in ironically manner, use hi grade humor, sometimes be a little shady.""".trimMargin(),
        llmModel = OpenRouterFreeModels.Qwen3_235b_a22b_Moe,
        temperature = 0.7,
    ) {
        handleEvents {
            onAgentCompleted { context ->
                println("CHATTY finished with result: ${context.result}")
            }
            onAgentExecutionFailed { context ->
                println("CHATTY execution failed!")
                context.throwable.printStackTrace()
            }
            onLLMCallCompleted { context ->
                val responsesString = context.responses
                    .joinToString("\n") { """{"role":"${it.role}", "content":"${it.content}"}""" }
                println("CHATTY:LLM call completed:\n$responsesString")
            }
        }
    }
    private lateinit var checkerToolRegistry: ToolRegistry

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            checkerToolRegistry = McpToolRegistryProvider.fromTransport(
                transport = McpToolRegistryProvider.defaultSseTransport("http://127.0.0.1:8181"),
                name = "Todo_MCP_client",
                version = "0.0.2"
            )
        }
    }

    internal suspend fun chat(prompt: String): String {
        return _chatty.run(prompt)
    }

    internal suspend fun check(prompt: String): String {
        val checker = AIAgent(
            promptExecutor = simpleOpenRouterExecutor(apiKey),
            systemPrompt = """You are an assistant to help user deal with todos.
                    |Todo rules:
                    |1. one todo is one line.
                    |2. `*` means pending todo.
                    |3. `+` means completed todo.
                    |4. `-` means failed/canceled todo.""".trimMargin(),
            llmModel = OpenRouterFreeModels.Glm4_5_Air_Moe,
            temperature = 0.1,
            toolRegistry = checkerToolRegistry,
        ) {
            handleEvents {
                onAgentStarting { context ->
                    println("CHECKER started ${context.context}")
                }
                onAgentCompleted { context ->
                    println("CHECKER finished with result: ${context.result}")
                }
                onAgentExecutionFailed { context ->
                    println("CHECKER execution failed!")
                    context.throwable.printStackTrace()
                }
                onLLMCallStarting { context ->
                    println("CHECKER: LLM call started: prompt:${context.prompt},\ntools:\n${context.tools.map { "name:${it.name}\ndesc:${it.description}" }}")
                }
                onLLMCallCompleted { context ->
                    val responsesString = context.responses
                        .joinToString("\n") { """{"role":"${it.role}", "content":"${it.content}"}""" }
                    println("CHECKER:LLM call completed:\n$responsesString")
                }
                onToolCallFailed { context ->
                    println("CHECKER: Tool failed, tool:${context.tool}, agrs:${context.toolArgs}")
                    context.throwable.printStackTrace()
                }
                onToolCallStarting { context ->
                    println("CHECKER: Tool started, tool:${context.tool}, agrs:${context.toolArgs}")
                }
                onToolCallCompleted { context ->
                    println("CHECKER: Tool completed, result:${context.result}")
                }
            }
        }
        return checker.run(prompt)
    }
}