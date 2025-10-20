package io.github.sd155.aiadvent3.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal sealed class LlmContextElement {
    data class System(val prompt: String) : LlmContextElement()
    data class User(val prompt: String) : LlmContextElement()
    @Serializable
    sealed class Llm : LlmContextElement() {
        @Serializable
        @SerialName("success")
        data class Succeed(
            @SerialName("result")
            val result: String,
            @SerialName("header")
            val header: String,
            @SerialName("summary")
            val summary: String,
            @SerialName("details")
            val details: List<String>,
        ) : Llm()
        @Serializable
        @SerialName("failure")
        data class Failed(
            @SerialName("result")
            val result: String,
            @SerialName("description")
            val description: String,
        ) : Llm()
        @Serializable
        @SerialName("query")
        data class Queried(
            @SerialName("result")
            val result: String,
            @SerialName("question")
            val question: String,
        ) : Llm()
    }
}