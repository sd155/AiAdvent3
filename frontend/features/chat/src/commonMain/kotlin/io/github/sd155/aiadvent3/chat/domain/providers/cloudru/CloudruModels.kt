package io.github.sd155.aiadvent3.chat.domain.providers.cloudru

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel

internal object CloudruModels : LLModelDefinitions {

    internal val Qwen3_Next_80b_a3b_Instruct: LLModel = LLModel(
        provider = CloudRuLlmProvider,
        id = "Qwen/Qwen3-Next-80B-A3B-Instruct",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.Completion,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 262_144,
        maxOutputTokens = 262_144,
    )
}