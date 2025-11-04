package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import io.github.sd155.aiadvent3.chat.domain.providers.openrouter.OpenRouterFreeModels

internal object CliAgent : Agent<String, String> {
    override val tag: String = "@Cleo"

    override suspend fun create(llmApiKey: String): AIAgent<String, String> {
        val tools = McpToolRegistryProvider.fromTransport(
            transport = McpToolRegistryProvider.defaultSseTransport("http://127.0.0.2:8181"),
            name = "Cli_MCP_client",
            version = "0.0.1"
        )
        return AIAgent(
            promptExecutor = simpleOpenRouterExecutor(llmApiKey),
            systemPrompt = """
                |You are a helpful and cautious Linux command-line assistant.
                |Your task is to:
                |1. Analyze the user's request.
                |2. Generate a safe, efficient, and idiomatic Linux CLI command that fulfills it.
                |3. Execute the command using the provided tooling.
                |4. Return the exact output of the command to the user.
                |Important rules:
                |- Never execute commands that modify, delete, or overwrite files unless explicitly requested and confirmed by the user.
                |- Avoid `sudo`, `rm`, `dd`, `chmod 777`, `:(){ :|:& };:`, or other dangerous operations unless absolutely necessary and clearly justified.
                |- Prefer read-only or non-destructive commands when possible.
                |- If the request is ambiguous, ask for clarification before generating a command.
                |- Always explain briefly what the command does before executing it (unless the user disables explanations).
                |""".trimMargin(),
            llmModel = OpenRouterFreeModels.Glm4_5_Air_Moe,
            temperature = 0.7,
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