package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import io.github.sd155.aiadvent3.chat.domain.providers.openrouter.OpenRouterFreeModels

internal object TaskSchedulerAgent : Agent<String, String> {
    override val tag = "Tasker"

    override suspend fun create(llmApiKey: String): AIAgent<String, String> {
        val tools = McpToolRegistryProvider.fromTransport(
            transport = McpToolRegistryProvider.defaultSseTransport("http://127.0.0.1:8181"),
            name = "Todo_MCP_client",
            version = "0.0.2"
        )
        return AIAgent(
            promptExecutor = simpleOpenRouterExecutor(llmApiKey),
            systemPrompt = """You are an assistant to help user deal with todos.
                    |Todo rules:
                    |1. one todo is one line.
                    |2. `*` means pending todo.
                    |3. `+` means completed todo.
                    |4. `-` means failed/canceled todo.""".trimMargin(),
            llmModel = OpenRouterFreeModels.Glm4_5_Air_Moe,
            temperature = 0.1,
            toolRegistry = tools,
        ) {
            handleEvents {
                onAgentStarting { context ->
                    println("$tag started ${context.context}")
                }
                onAgentCompleted { context ->
                    println("$tag finished with result: ${context.result}")
                }
                onAgentExecutionFailed { context ->
                    println("$tag execution failed!")
                    context.throwable.printStackTrace()
                }
                onLLMCallStarting { context ->
                    println("$tag LLM call started: prompt:${context.prompt},\ntools:\n${context.tools.map { "name:${it.name}\ndesc:${it.description}" }}")
                }
                onLLMCallCompleted { context ->
                    val responsesString = context.responses
                        .joinToString("\n") { """{"role":"${it.role}", "content":"${it.content}"}""" }
                    println("$tag LLM call completed:\n$responsesString")
                }
                onToolCallFailed { context ->
                    println("$tag Tool failed, tool:${context.tool}, agrs:${context.toolArgs}")
                    context.throwable.printStackTrace()
                }
                onToolCallStarting { context ->
                    println("$tag Tool started, tool:${context.tool}, agrs:${context.toolArgs}")
                }
                onToolCallCompleted { context ->
                    println("$tag Tool completed, result:${context.result}")
                }
            }
        }
    }
}