package io.github.sd155.aiadvent3.chat.domain

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

internal object OpenRouterFreeModels : LLModelDefinitions {

    internal val Qwen3_235b_a22b_Moe: LLModel = LLModel(
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

    internal val Glm4_5_Air_Moe: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "z-ai/glm-4.5-air:free",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.Completion,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 131_072,
        maxOutputTokens = 131_072,
    )

    internal val Mistral_7b_Instruct: LLModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "mistralai/mistral-7b-instruct:free",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.Completion,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_768,
        maxOutputTokens = 32_768,
    )
}