package io.github.sd155.aiadvent3

import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal class McpServer {
    private val _json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    fun configureMcpServer(application: Application) {
        application.install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }

        application.install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }

        application.routing {
            webSocket("/mcp") {
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val requestString = frame.readText()
                            println("MCP IN: $requestString")
                            val message = _json.decodeFromString<McpRequest>(requestString)
                            when (message.method) {
                                "tools/list" -> {
                                    val tools = mockedTools()
                                    val response = McpResponse(
                                        id = message.id,
                                        result = McpResult(tools = tools),
                                    )
                                    send(_json.encodeToString(response))
                                }
                                else -> {
                                    val error = McpResponse(
                                        id = message.id,
                                        error = McpError(
                                            code = 1,
                                            message = "Method not found: ${message.method}"
                                        )
                                    )
                                    send(_json.encodeToString(error))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun mockedTools(): Map<String, McpTool> =
        mapOf(
            "git" to McpTool(
                name = "git",
                description = "Execute local git commands",
                inputSchema = """
                    {
                    "type": "object",
                    "properties": {
                        "command": {
                            "type": "string",
                            "description": "CLI command for git"
                        }
                    },
                    "required": ["command"]
                    }
                """.trimIndent()
            ),
            "weather" to McpTool(
                name = "weather",
                description = "Get current weather information",
                inputSchema = """
                    {
                    "type": "object",
                    "properties": {
                        "location": {
                            "type": "string",
                            "description": "City name or zip code"
                        }
                    },
                    "required": ["location"]
                    }
                """.trimIndent()
            ),
        )
}

@Serializable
private data class McpRequest(
    @SerialName("jsonrpc")
    val jsonRpc: String = "2.0",
    @SerialName("id")
    val id: Int,
    @SerialName("method")
    val method: String,
    @SerialName("params")
    val params: Map<String, JsonElement>?
)

@Serializable
private data class McpResponse(
    @SerialName("jsonrpc")
    val jsonRpc: String = "2.0",
    @SerialName("id")
    val id: Int,
    @SerialName("result")
    val result: McpResult? = null,
    @SerialName("error")
    val error: McpError? = null,
)

@Serializable
private data class McpResult(
    @SerialName("tools")
    val tools: Map<String, McpTool>,
)

@Serializable
private data class McpTool(
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String,
    @SerialName("inputSchema")
    val inputSchema: String,
)

@Serializable
private data class McpError(
    @SerialName("code")
    val code: Int,
    @SerialName("message")
    val message: String,
)