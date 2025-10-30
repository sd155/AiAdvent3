package io.github.sd155.aiadvent3.chat.domain

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

internal object OpenRouterFreeModels : LLModelDefinitions {

    internal val Qwen3_235b_a22b_MoE: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "qwen/qwen3-235b-a22b:free",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.Completion,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 40_960,
        maxOutputTokens = 40_960,
    )
}