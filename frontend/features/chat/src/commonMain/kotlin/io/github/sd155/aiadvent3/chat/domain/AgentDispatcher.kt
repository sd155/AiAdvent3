package io.github.sd155.aiadvent3.chat.domain

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class AgentDispatcher(
    private val apiKey: String,
    private val onTodoCheck: (String) -> Unit,
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
                transport = McpToolRegistryProvider.defaultSseTransport("http://0.0.0.0:8181"),
                name = "Todo_MCP_client",
                version = "0.0.1"
            )
            while(true) {
                delay(5000)
                check()
            }
        }
    }

    internal suspend fun chat(prompt: String): String {
        return _chatty.run(prompt)
    }

    internal suspend fun check() {
        val checker = AIAgent(
            promptExecutor = simpleOpenRouterExecutor(apiKey),
            systemPrompt = """Your job is to group loaded todos.
                    |Todo rules:
                    |`*` - means pending todo,
                    |`+` - means completed todo,
                    |`-` - means failed/canceled todo.""".trimMargin(),
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
        val checkerResult = checker.run("Load todos for 29.10.2025.")
        onTodoCheck(checkerResult)
    }
}