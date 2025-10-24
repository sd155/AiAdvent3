package io.github.sd155.aiadvent3.chat.domain

import aiadvent3.frontend.features.chat.generated.resources.Res
import io.github.sd155.aiadvent3.utils.Result

internal class TranslatorAgent(
    apiKey: String,
) {
    private val _creativity = 0.7f
    private val _llm by lazy {
        KtorLlmDataSource(
            apiKey = apiKey,
            model = "qwen/qwen3-235b-a22b:free",
        )
    }

    private suspend fun systemPrompt(): LlmContextElement.System =
        LlmContextElement.System(
            prompt =
                """
                You are an experienced translator. You must take the user's prompt and translate it to russian.                
                Your response must strictly follow these rules:
                1. Output valid JSON only. Do not wrap it in code markers or add any extra content.
                2. Use the provided JSON schema exactly as given. Do not extend or modify it. The schema: ${String(Res.readBytes("files/agent-response-scheme.json"))}
                """
        )

    internal suspend fun translate(
        prompt: LlmContent.Succeed
    ): Result<String, LlmContextElement.Llm> {
        val context = listOf(
            systemPrompt(),
            LlmContextElement.User(
                prompt = "##${prompt.header}\n${prompt.details.joinToString("\n")}\n### Summary\n${prompt.summary}"
            )
        )
        return _llm.postChatCompletions(
            context = context,
            creativity = _creativity,
        )
    }
}