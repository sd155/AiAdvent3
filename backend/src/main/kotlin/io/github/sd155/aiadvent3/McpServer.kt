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
import java.io.File

internal class McpServer {
    private val _json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        classDiscriminator = "name"
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
                            val request = _json.decodeFromString<McpRequest>(requestString)
                            when (request.method) {
                                "tools/list" -> {
                                    val tools = mockedTools()
                                    val response = ToolListResponse(
                                        id = request.id,
                                        result = ToolListResult(tools = tools),
                                    )
                                    send(_json.encodeToString(response))
                                }
                                "tools/call" -> {
                                    when (request.params) {
                                        is RequestParams.ReadFile -> {
                                            val text = readFile(request.params.arguments.name)
                                            val response = FileToolResponse(
                                                id = request.id,
                                                result = "File ${request.params.arguments.name} read successfully",
                                                fileName = request.params.arguments.name,
                                                data = text,
                                            )
                                            send(_json.encodeToString(response))
                                        }
                                        is RequestParams.WriteFile -> {
                                            writeFile(
                                                fileName = request.params.arguments.name,
                                                text = request.params.arguments.content,
                                            )
                                            val response = FileToolResponse(
                                                id = request.id,
                                                result = "File ${request.params.arguments.name} written successfully",
                                                fileName = request.params.arguments.name,
                                            )
                                            send(_json.encodeToString(response))
                                        }
                                        else -> {
                                            val error = ToolListResponse(
                                                id = request.id,
                                                error = McpError(
                                                    code = 1,
                                                    message = "Invalid params for: ${request.method}"
                                                )
                                            )
                                            send(_json.encodeToString(error))
                                        }
                                    }
                                }
                                else -> {
                                    val error = ToolListResponse(
                                        id = request.id,
                                        error = McpError(
                                            code = 1,
                                            message = "Method not found: ${request.method}"
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

    private fun writeFile(fileName: String, text: String) {
        File("./$fileName.txt").writeText(text = text)
    }

    private fun readFile(fileName: String): String =
        File("./$fileName.txt")
            .let { file ->
                if (file.exists())
                    file.readText()
                else
                    ""
            }

    private fun mockedTools(): Map<String, McpTool> =
        mapOf(
            "read_file" to McpTool(
                name = "read_file",
                description = "Read file content",
                inputSchema = """{"type": "object","properties": {"name": {"type": "string","description": "File name"}},"required": ["name"]}""".trimIndent()
            ),
            "write_file" to McpTool(
                name = "write_file",
                description = "Write to file. Overwrites existing file.",
                inputSchema = """{"type": "object","properties": {"name": {"type": "string","description": "File name"},"content": {"type": "string","description": "Text to write"}},"required": ["name", "content"]}""".trimIndent()
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
    val params: RequestParams,
)

@Serializable
private data class ToolListResponse(
    @SerialName("jsonrpc")
    val jsonRpc: String = "2.0",
    @SerialName("id")
    val id: Int,
    @SerialName("result")
    val result: ToolListResult? = null,
    @SerialName("error")
    val error: McpError? = null,
)

@Serializable
private data class FileToolResponse(
    @SerialName("jsonrpc")
    val jsonRpc: String = "2.0",
    @SerialName("id")
    val id: Int,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("result")
    val result: String? = null,
    @SerialName("data")
    val data: String? = null,
    @SerialName("error")
    val error: McpError? = null,
)

@Serializable
private data class ToolListResult(
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
private sealed class RequestParams {
    @Serializable
    @SerialName("list_tools")
    data class ListTools(
        @SerialName("name")
        val name: String = "list_tools",
    ) : RequestParams()
    @Serializable
    @SerialName("read_file")
    data class ReadFile(
        @SerialName("name")
        val name: String = "read_file",
        @SerialName("arguments")
        val arguments: ReadFileArguments,
    ) : RequestParams()
    @Serializable
    @SerialName("write_file")
    data class WriteFile(
        @SerialName("name")
        val name: String = "write_file",
        @SerialName("arguments")
        val arguments: WriteFileArguments,
    ) : RequestParams()
}

@Serializable
private data class ReadFileArguments(
    @SerialName("name")
    val name: String,
)

@Serializable
private data class WriteFileArguments(
    @SerialName("name")
    val name: String,
    @SerialName("content")
    val content: String,
)

@Serializable
private data class McpError(
    @SerialName("code")
    val code: Int,
    @SerialName("message")
    val message: String,
)