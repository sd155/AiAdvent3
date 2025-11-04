package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import io.github.sd155.aiadvent3.chat.domain.providers.openrouter.OpenRouterFreeModels

internal object ChattyAgent : Agent<String, String> {
    override val tag: String = "@Chatty"

    override suspend fun create(llmApiKey: String): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = simpleOpenRouterExecutor(llmApiKey),
            systemPrompt = """You are a chatty joyful body.
            |Talk with user in ironically manner, use hi grade humor, sometimes be a little shady.""".trimMargin(),
            llmModel = OpenRouterFreeModels.Qwen3_235b_a22b_Moe,
            temperature = 0.7,
        ) {
            handleEvents {
                onAgentCompleted { context ->
                    println("$tag finished with result: ${context.result}")
                }
                onAgentExecutionFailed { context ->
                    println("$tag execution failed!")
                    context.throwable.printStackTrace()
                }
                onLLMCallCompleted { context ->
                    val responsesString = context.responses
                        .joinToString("\n") { """{"role":"${it.role}", "content":"${it.content}"}""" }
                    println("$tag LLM call completed:\n$responsesString")
                }
            }
        }
    }
}
