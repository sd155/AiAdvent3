package io.github.sd155.aiadvent3.chat.domain.providers.cloudru

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel

internal object CloudruModels : LLModelDefinitions {

    internal object Text2Text {
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

        internal val GigaChat_2_Max: LLModel = LLModel(
            provider = CloudRuLlmProvider,
            id = "GigaChat/GigaChat-2-Max",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Speculation,
                LLMCapability.Completion,
            ),
            contextLength = 131_000,
            maxOutputTokens = 131_000,
        )

        internal val MiniMax_M2: LLModel = LLModel(
            provider = CloudRuLlmProvider,
            id = "MiniMaxAI/MiniMax-M2",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.Completion,
                LLMCapability.Schema.JSON.Standard,
            ),
            contextLength = 196_000,
            maxOutputTokens = 196_000,
        )
    }

    internal object Embeddings {
        internal val Qwen3_Embedding_06b: LLModel = LLModel(
            provider = CloudRuLlmProvider,
            id = "Qwen/Qwen3-Embedding-0.6B",
            capabilities = listOf(
                LLMCapability.Embed
            ),
            contextLength = 32_000,
        )
    }
}