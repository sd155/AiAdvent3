package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.embeddings.base.Vector
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.message.Message
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruLlmClient
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruModels
import java.io.File

internal object RepoAgent {
    const val NO_RAG_TAG: String = "Repo"
    const val WITH_RAG_TAG: String = "RepoRag"
    private const val PROMPT = """You are senior kotlin developer. Your task is to describe and explain code to junior level developers."""

    suspend fun create(llmApiKey: String, useRag: Boolean): AIAgent<String, String> {
        val tools = McpToolRegistryProvider.fromTransport(
            transport = McpToolRegistryProvider.defaultSseTransport("http://127.0.0.3:8181"),
            name = "Dev_MCP_client",
            version = "0.0.1"
        )
        return AIAgent(
            strategy = if (useRag) ragStrategy(llmApiKey) else noRagStrategy(),
            promptExecutor = SingleLLMPromptExecutor(CloudruLlmClient(llmApiKey)),
            llmModel = CloudruModels.Text2Text.Qwen3_Next_80b_a3b_Instruct,
            temperature = 0.3,
            toolRegistry = if (useRag) ToolRegistry.EMPTY else tools,
            systemPrompt = PROMPT
        )
    }

    private fun noRagStrategy(reasoningInterval: Int = 1): AIAgentGraphStrategy<String, String> = strategy("NoRag") {
        require(reasoningInterval > 0) { "Reasoning interval must be greater than 0" }
        val reasoningStepKey = createStorageKey<Int>("reasoning_step")
        val nodeSetup by node<String, String> {
            println("Init..")
            storage.set(reasoningStepKey, 0)
            it
        }

        val nodeCallLLM by node<Unit, Message.Response> {
            println("Brain in use..")
            llm.writeSession {
                requestLLM()
            }
                .also { println(it.content) }
        }

        val nodeExecuteTool by nodeExecuteTool()

        val reasoningPrompt = "Please give your thoughts about the task and plan the next steps."
        val nodeCallLLMReasonInput by node<String, Unit> { stageInput ->
            println("Puff..")
            llm.writeSession {
                appendPrompt {
                    user(stageInput)
                    user(reasoningPrompt)
                }
                requestLLMWithoutTools()
            }
        }

        val nodeCallLLMReason by node<ReceivedToolResult, Unit> { result ->
            val reasoningStep = storage.getValue(reasoningStepKey)
            println("Some data comes..\n$result")
            llm.writeSession {
                appendPrompt {
                    tool {
                        result(result)
                    }
                }

                if (reasoningStep % reasoningInterval == 0) {
                    appendPrompt {
                        user(reasoningPrompt)
                    }
                    requestLLMWithoutTools()
                }
            }
            storage.set(reasoningStepKey, reasoningStep + 1)
        }

        edge(nodeStart forwardTo nodeSetup)
        edge(nodeSetup forwardTo nodeCallLLMReasonInput)
        edge(nodeCallLLMReasonInput forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
        edge(nodeExecuteTool forwardTo nodeCallLLMReason)
        edge(nodeCallLLMReason forwardTo nodeCallLLM)
    }

    private fun ragStrategy(llmApiKey: String): AIAgentGraphStrategy<String, String> = strategy("WithRag") {
        val loadData by node<String, String> { userPrompt ->
            val embedder = LLMEmbedder(
                client = CloudruLlmClient(llmApiKey),
                model = CloudruModels.Embeddings.Qwen3_Embedding_06b,
            )
            val indexFile = File("./rag/embedding_index.json")
            if (!indexFile.exists()) {
                buildEmbeddings(embedder)
            }
            val index = EmbeddingStorage().load()
            val promptEmbedding = embedder.embed(userPrompt)
            val scoredChunks = index.entries
                .map { indexEntry ->
                    val diff = embedder.diff(promptEmbedding, Vector(indexEntry.embedding))
                    Pair(indexEntry.chunk, diff)
                }
                .sortedBy { it.second }
                .take(5)
                .map { it.first }
                .joinToString("\n")
            "$userPrompt\n$scoredChunks"
        }

        val callLlm by node<String, Message.Response> { promptWithData ->
            println("LLM IN :: $promptWithData")
            llm.writeSession {
                appendPrompt {
                    user(promptWithData) }
                requestLLMWithoutTools()
            }
        }

        edge(nodeStart forwardTo loadData)
        edge(loadData forwardTo callLlm)
        edge(callLlm forwardTo nodeFinish transformed {it.content})
    }

    private suspend fun buildEmbeddings(embedder: LLMEmbedder) {
        val entries = mutableListOf<IndexEntry>()

        findSourceFiles().forEach { file ->
            println("FILE >> $file")
            chunk(file).map { chunk ->
                println("CHUNK\n$chunk")
                IndexEntry(
                    chunk = chunk,
                    embedding = embedder.embed(chunk.content).values
                )
            }
                .also { entries.addAll(it) }
        }

        EmbeddingStorage().save(EmbeddingIndex(entries))
    }

    private fun chunk(file: File): List<SourceChunk> {
        // Skip files from build directories
        if (file.absolutePath.contains(File.separator + "build" + File.separator)) {
            println("SKIPPED (ignored directory)")
            return emptyList()
        }

        val content = file.readText()
        val lines = content.lines()
        val chunks = mutableListOf<SourceChunk>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.contains("fun ") ||
                line.contains("class ") ||
                line.contains("interface ") ||
                line.contains("object ") ||
                line.contains("data class ") ||
                line.contains("enum class ") ||
                line.contains("annotation class ")) {

                var endLine = i
                var braceCount = 0
                var inMultilineComment = false

                for (j in i until lines.size) {
                    val currentLine = lines[j]

                    // Check for multiline comments
                    if (currentLine.contains("/*")) {
                        inMultilineComment = true
                    }
                    if (inMultilineComment && currentLine.contains("*/")) {
                        inMultilineComment = false
                        continue
                    }
                    if (inMultilineComment) {
                        continue
                    }

                    // Skip single line comments
                    val lineWithoutComment = if (currentLine.indexOf("//") != -1) {
                        currentLine.substring(0, currentLine.indexOf("//"))
                    } else {
                        currentLine
                    }

                    braceCount += lineWithoutComment.count { it == '{' }
                    braceCount -= lineWithoutComment.count { it == '}' }

                    endLine = j
                    if (braceCount == 0 && j > i) {
                        break
                    }
                }

                val chunkContent = lines.subList(i, endLine + 1).joinToString("\n")
                val chunkId = "${file.absolutePath}:${i + 1}"

                chunks.add(
                    SourceChunk(
                        id = chunkId,
                        filePath = file.absolutePath,
                        fileName = file.name,
                        content = chunkContent,
                        startLine = i + 1,
                        endLine = endLine + 1
                    )
                )

                i = endLine + 1
            } else {
                i++
            }
        }

        return chunks
    }

    private fun findSourceFiles(): List<File> {
        val currentDir = File("../../")
        val kotlinFiles = mutableListOf<File>()
        currentDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
            .forEach { kotlinFiles.add(it) }
        return kotlinFiles
    }
}