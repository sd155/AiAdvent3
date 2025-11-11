package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.embeddings.local.LLMEmbedder
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruLlmClient
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruModels
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

internal object EmbedderAgent {
    const val TAG: String = "Embedder"

    internal suspend fun buildEmbeddings(llmApiKey: String) {
        val embedder = LLMEmbedder(
            client = CloudruLlmClient(llmApiKey),
            model = CloudruModels.Embeddings.Qwen3_Embedding_06b,
        )
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

@Serializable
private data class SourceChunk(
    val id: String,
    val filePath: String,
    val fileName: String,
    val content: String,
    val startLine: Int,
    val endLine: Int
)

@Serializable
private data class IndexEntry(
    val chunk: SourceChunk,
    val embedding: List<Double>,
)

@Serializable
private data class EmbeddingIndex(
    val entries: List<IndexEntry>
)

private class EmbeddingStorage {

    fun save(index: EmbeddingIndex) {
        val directory = File("./rag")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, "embedding_index.json")
        val jsonContent = Json.encodeToString(index)
        file.writeText(jsonContent)
    }
}