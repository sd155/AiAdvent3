package io.github.sd155.aiadvent3.chat.domain

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor

internal class AgentDispatcher(
    apiKey: String,
) {
    private val _chatty = AIAgent(
        promptExecutor = simpleOpenRouterExecutor(apiKey),
        systemPrompt = "You are a chatty joyful body. Talk with user in ironically manner, use hi grade humor, sometimes be a little shady.",
        llmModel = OpenRouterFreeModels.Qwen3_235b_a22b_MoE,
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

    internal suspend fun chat(prompt: String): String =
        _chatty.run(prompt)
}