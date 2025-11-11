package io.github.sd155.aiadvent3.chat.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.clearHistory
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.message.Message
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruLlmClient
import io.github.sd155.aiadvent3.chat.domain.providers.cloudru.CloudruModels
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.hc.core5.http.message.MessageSupport.header

@Serializable
private data class Commit(
    val sha: String?,
    val commit: GitCommit?
)

@Serializable
private data class GitCommit(
    val message: String?,
)

@Serializable
private data class CommentRequest(
    val body: String
)

internal object GittyAgent {
    private const val FIND_OUT_PROMPT = """
        |From the user's input, extract the GitHub repository owner and name. 
        |The input may refer to them in natural language. 
        |If both owner and repo can be confidently identified, output only: 
        |{"owner": "repo owner name", "repo": "repo name"}
        |If either field cannot be extracted reliably, output only the string:
        |"Please include GitHub repository owner name and repo name." """
    private const val NOTES_PROMPT = """
        |The user will provide a list of JSON objects, first containing head commit sha, others containing commit messages.
        |Generate concise, user-facing release notes from the commit messages.
        |- Group entries by type if conventional commit format is used (e.g., feat, fix, docs).
        |- Use bullet points.
        |- Omit technical jargon; focus on user impact.
        |- If there are no messages, output an empty string for "release_notes".
        |Output only:
        |{"sha": "head commit sha", "release_notes": "release notes combined on commit messages"}
    """
    const val TAG: String = "Gitty"
    private var githubKey: String? = null

    private suspend fun fetchCommitsData(repoData: Pair<String, String>): String? {
        githubKey?.let { apiKey ->
            val client = HttpClient(CIO) {
                install(Logging)
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                    })
                }
            }
            return try {
                client
                    .get("https://api.github.com/repos/${repoData.first}/${repoData.second}/commits") {
                        headers {
                            append(HttpHeaders.UserAgent, "AiCommitFetcher")
                        }
                        url {
                            parameters.append("per_page", "100")
                        }
                    }
                    .body<List<Commit>>()
                    .mapIndexed { index, commit ->
                        if (index > 0)
                            """{"commit_message": "${commit.commit?.message}"}"""
                        else
                            """{"head_commit_sha": "${commit.sha}"}"""
                    }
                    .joinToString("\n")
            }
            catch (e: Exception) {
                e.printStackTrace()
                null
            }
            finally {
                client.close()
            }
        }
            ?: return null
    }

    private suspend fun publishNotes(
        repoOwner: String,
        repoName: String,
        message: String
    ): String {
        val client = HttpClient(CIO) {
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(githubKey!!, null)
                    }
                }
            }
            install(Logging)
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
        }
        return try {
            val json = Json.parseToJsonElement(message.trim())
            if (json !is JsonObject) {
                "Failed to parse (not a JSON) and publish release notes comment!"
            }
            else {
                val sha = json["sha"]?.jsonPrimitive?.content
                val notes = json["release_notes"]?.jsonPrimitive?.content
                if (sha.isNullOrBlank() || notes.isNullOrBlank()) {
                    "Failed to parse (bad properties) and publish release notes comment!"
                }
                else {
                    client
                        .post("https://api.github.com/repos/$repoOwner/$repoName/commits/$sha/comments") {
                            header(HttpHeaders.UserAgent, "AiCommitPoster")
                            contentType(ContentType.Application.Json)
                            setBody(CommentRequest(body = notes))
                        }
                    "Published comment:\n$notes"
                }
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
            "Failed to publish release notes comment!"
        }
        finally {
            client.close()
        }
    }

    private fun extractRepoDataOrNull(message: String): Pair<String, String>? {
        return try {
            val json = Json.parseToJsonElement(message.trim())
            if (json !is JsonObject) {
                null
            }
            else {
                val owner = json["owner"]?.jsonPrimitive?.content
                val repo = json["repo"]?.jsonPrimitive?.content
                if (owner.isNullOrBlank() || repo.isNullOrBlank())
                    null
                else
                    owner to repo
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun create(llmApiKey: String, githubApiKey: String): AIAgent<String, String> {
        githubKey = githubApiKey
        return AIAgent(
            strategy = strategy("github_commentator") {
                var failed = true
                var owner = ""
                var repo = ""
                val findOutOwnerAndRepo by node<String, Message.Response> { userPrompt ->
                    println("FIND_OUT IN :: $userPrompt")
                    llm.writeSession {
                        appendPrompt {
                            system(FIND_OUT_PROMPT)
                            user(userPrompt)
                        }
                        requestLLMWithoutTools()
                    }
                }

                val loadCommitsData by node<Message.Response, String> { findOutResponse ->
                    println("COMMITS_DATA IN :: ${findOutResponse.content}")
                    extractRepoDataOrNull(findOutResponse.content)
                        ?.let { repoData ->
                            owner = repoData.first
                            repo = repoData.second
                            fetchCommitsData(repoData) }
                        ?.also { failed = false }
                        ?: ""
                }

                val callLlm by node<String, Message.Response> { commitsData ->
                    println("LLM IN :: $commitsData")
                    llm.writeSession {
                        appendPrompt {
                            clearHistory()
                            system(NOTES_PROMPT)
                            user(commitsData) }
                        requestLLMWithoutTools()
                    }
                }

                val commentWithNotes by node<Message.Response, String> { notesResponse ->
                    println("COMMENT IN :: ${notesResponse.content}")
                    publishNotes(
                        repoOwner = owner,
                        repoName = repo,
                        message = notesResponse.content
                    )
                }

                edge(nodeStart forwardTo findOutOwnerAndRepo)
                edge(findOutOwnerAndRepo forwardTo loadCommitsData)
                edge(loadCommitsData forwardTo callLlm onCondition {failed == false})
                edge(loadCommitsData forwardTo nodeFinish onCondition {failed == true})
                edge(callLlm forwardTo commentWithNotes)
                edge(commentWithNotes forwardTo nodeFinish)
            },
            promptExecutor = SingleLLMPromptExecutor(CloudruLlmClient(llmApiKey)),
            llmModel = CloudruModels.Text2Text.Qwen3_Next_80b_a3b_Instruct,
            temperature = 0.3,
        ) {
            handleEvents {
                onAgentExecutionFailed { context ->
                    println("$TAG execution failed!")
                    context.throwable.printStackTrace()
                }
            }
        }
    }
}