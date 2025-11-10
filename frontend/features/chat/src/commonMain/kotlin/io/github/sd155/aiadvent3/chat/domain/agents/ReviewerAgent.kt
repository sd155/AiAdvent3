package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.clearHistory
import ai.koog.agents.core.feature.writer.FeatureMessageLogWriter
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.sd155.aiadvent3.chat.domain.agents.TaskSchedulerAgent.tag
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruLlmClient
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruModels
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.hc.core5.http.message.MessageSupport.header

@Serializable
private data class InitialData(
    val owner: String,
    val repo: String,
    val sha: String,
)

@Serializable
private data class GetCommitResponse(
    val files: List<CommitFile>?,
)

@Serializable
private data class CommitFile(
    val filename: String?,
    val patch: String?,
    @SerialName("raw_url")
    val rawUrl: String?,
)

@Serializable
private data class CommitCommentRequest(
    val body: String,
)

internal object ReviewerAgent {
    private const val FIND_OUT_PROMPT = """
    |From the user's input, extract the GitHub repository owner, repository name, commit sha. 
    |The input may refer to them in natural language. 
    |If owner, repo and sha can be confidently identified, output only: 
    |{"owner": "repo owner name", "repo": "repo name", "sha": "commit sha"}
    |If either field cannot be extracted reliably, output only the string:
    |"Please include GitHub repository owner name, repository name and commit sha to review." """
    private const val REVIEW_PROMPT = """
    |You are an expert Kotlin software engineer and meticulous code reviewer.
    |Your task is to analyze the commit data to identify bugs, anti-patterns, 
    |security issues, performance problems, and Kotlin-specific code quality concerns.
    |Answer with .md format only.
    |Pay special attention to:
    |   - Null safety violations
    |   - Resource leaks (unmanaged streams, unclosed connections)
    |   - Threading issues (shared mutable state, missing @Volatile/synchronized)
    |   - Ktor/Kotlin-specific bugs (misconfigured clients, coroutine scope leaks)
    |   - Exception handling (empty catch blocks, swallowed errors)
    |   - Security (hardcoded secrets, unsafe deserialization)
    |Synthesize findings and report bugs with
    |   - File path and line with the bug
    |   - Severity (Critical/High/Medium/Low)
    |   - Concise explanation of the issue
    |   - Kotlin-idiomatic fix suggestion
    """
    const val TAG: String = "@Reviewer"

    fun create(llmApiKey: String, githubApiKey: String): AIAgent<String, String> {
        return AIAgent(
            strategy = strategy("github_reviewer") {
                var initialData: InitialData? = null
                var failed = true

                val findOutInitialData by node<String, Message.Response> { userPrompt ->
                    println("FIND_OUT IN :: $userPrompt")
                    llm.writeSession {
                        appendPrompt {
                            system(FIND_OUT_PROMPT)
                            user(userPrompt)
                        }
                        requestLLMWithoutTools()
                    }
                }

                val loadCommitData by node<Message.Response, String> { findOutResponse ->
                    println("COMMIT_DATA IN :: ${findOutResponse.content}")
                    extractInitDataOrNull(findOutResponse.content)
                        ?.let { data ->
                            initialData = data
                            fetchCommitData(apiKey = githubApiKey, data = data) }
                        ?.also { failed = false }
                        ?: ""
                }

                val callLlm by node<String, Message.Response> { commitData ->
                    println("LLM IN :: $commitData")
                    llm.writeSession {
                        appendPrompt {
                            clearHistory()
                            system(REVIEW_PROMPT)
                            user(commitData) }
                        requestLLMWithoutTools()
                    }
                }

                val commentCommit by node<Message.Response, String> { commentResponse ->
                    println("COMMENT IN :: ${commentResponse.content}")
                    publishComment(
                        apiKey = githubApiKey,
                        data = initialData!!,
                        comment = commentResponse.content,
                    )
                }

                edge(nodeStart forwardTo findOutInitialData)
                edge(findOutInitialData forwardTo loadCommitData)
                edge(loadCommitData forwardTo callLlm onCondition {failed == false})
                edge(loadCommitData forwardTo nodeFinish onCondition {failed == true})
                edge(callLlm forwardTo commentCommit)
                edge(commentCommit forwardTo nodeFinish)
            },
            promptExecutor = SingleLLMPromptExecutor(CloudruLlmClient(llmApiKey)),
            llmModel = CloudruModels.Qwen3_Next_80b_a3b_Instruct,
            temperature = 0.3,
        ) {
            install(Tracing) {
                addMessageProcessor(TraceFeatureMessageLogWriter(
                    targetLogger = KotlinLogging.logger {},
                    logLevel = FeatureMessageLogWriter.LogLevel.DEBUG
                ))
            }
            handleEvents {
                onAgentExecutionFailed { context ->
                    println("$TAG execution failed!")
                    context.throwable.printStackTrace()
                }
                onLLMCallStarting { context ->
                    println("$tag LLM call started: prompt:${context.prompt}}")
                }
                onLLMCallCompleted { context ->
                    val responsesString = context.responses
                        .joinToString("\n") { """{"role":"${it.role}", "content":"${it.content}"}""" }
                    println("$tag LLM call completed:\n$responsesString")
                }
            }
        }
    }

    private fun extractInitDataOrNull(message: String): InitialData? {
        return try {
            val json = Json.parseToJsonElement(message.trim())
            if (json !is JsonObject) {
                null
            }
            else {
                val owner = json["owner"]?.jsonPrimitive?.content
                val repo = json["repo"]?.jsonPrimitive?.content
                val sha = json["sha"]?.jsonPrimitive?.content
                if (owner.isNullOrBlank() || repo.isNullOrBlank() || sha.isNullOrBlank())
                    null
                else
                    InitialData(
                        owner = owner,
                        repo = repo,
                        sha = sha,
                    )
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun fetchCommitData(data: InitialData, apiKey: String): String? {
        val client = HttpClient(CIO) {
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(apiKey, null)
                    }
                }
            }
            install(Logging) {
                level = LogLevel.HEADERS
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        return try {
            val changes = mutableListOf<String>()
            client
                .get("https://api.github.com/repos/${data.owner}/${data.repo}/commits/${data.sha}") {
                    headers {
                        append(HttpHeaders.UserAgent, "AiCommitFetcher")
                    }
                    url {
                        parameters.append("per_page", "100")
                    }
                }
                .body<GetCommitResponse>().files?.forEach { file ->
                    val fileContent = client.get(file.rawUrl!!).body<String>()
                    changes.add("**${file.filename}**\nContent:\n```\n$fileContent\n```\nDiff:\n```\n${file.patch}\n```")
                }
            "## Commit ${data.sha}\n\n- ${changes.joinToString("\n\n- ")}"
        }
        catch (e: Exception) {
            e.printStackTrace()
            null
        }
        finally {
            client.close()
        }
    }

    private suspend fun publishComment(apiKey: String, data: InitialData, comment: String): String {
        val client = HttpClient(CIO) {
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(apiKey, null)
                    }
                }
            }
            install(Logging) {
                level = LogLevel.HEADERS
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        return try {
            client
                .post("https://api.github.com/repos/${data.owner}/${data.repo}/commits/${data.sha}/comments") {
                    header(HttpHeaders.UserAgent, "AiCommitReviewer")
                    contentType(ContentType.Application.Json)
                    setBody(CommitCommentRequest(body = comment))
                }
            comment
        }
        catch (e: Exception) {
            e.printStackTrace()
            "Failed to publish commit review comment!"
        }
        finally {
            client.close()
        }
    }
}