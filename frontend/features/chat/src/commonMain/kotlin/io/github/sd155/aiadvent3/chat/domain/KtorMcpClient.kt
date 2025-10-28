package io.github.sd155.aiadvent3.chat.domain

import io.github.sd155.aiadvent3.utils.Result
import io.github.sd155.aiadvent3.utils.asFailure
import io.github.sd155.aiadvent3.utils.asSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.lang.AutoCloseable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class KtorMcpClient : AutoCloseable {
    private var webSocket: WebSocket? = null
    private var toolsLatch: CountDownLatch? = null
    private var receivedTools: List<McpTool>? = null
    private var error: String? = null
    private var requestId = 0
    private val _client = OkHttpClient()
    private val _json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    internal suspend fun fetchTools(serverUrl: String = "http://0.0.0.0:8181/mcp"): Result<String, List<McpTool>> {
        return try {
            toolsLatch = CountDownLatch(1)
            receivedTools = null
            error = null
            val request = Request.Builder().url(serverUrl).build()
            webSocket = _client.newWebSocket(request, createWebSocketListener())
            val success = toolsLatch?.await(15, TimeUnit.SECONDS) == true
            if (success) {
                error?.asFailure()
                    ?: receivedTools?.asSuccess()
                    ?: "No tools received".asFailure()
            }
            else {
                "Connection timeout".asFailure()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            "MCP unexpected error".asFailure()
        } finally {
            close()
        }
    }

    private fun createWebSocketListener(): WebSocketListener =
        object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                McpRequest(
                    id = ++requestId,
                    method = "tools/list",
                    params = buildJsonObject {
                        put("protocolVersion", "a1")
                        putJsonObject("capabilities") {
                            putJsonObject("tools") { put("listChanged", true) }
                        }
                        putJsonObject("clientInfo") {
                            put("name", "McpClient")
                            put("version", "1.0.0")
                        }
                    }
                )
                    .let { _json.encodeToString(it) }
                    .also {
                        println("McpClient sends: $it")
                        webSocket.send(it)
                    }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    println("McpClient: Received: $text")
                    val response = _json.decodeFromString<McpResponse>(text)
                    if (response.error != null) {
                        error = "Tools response failed: ${response.error.message}"
                        toolsLatch?.countDown()
                    }
                    else {
                        response.result?.tools?.let { tools ->
                            receivedTools = tools.values.toList()
                            toolsLatch?.countDown()
                        } ?: run {
                            error = "No tools in response"
                            toolsLatch?.countDown()
                        }
                    }

                } catch (e: Exception) {
                    println("McpClient ERROR: ${e.message}")
                    error = "McpClient ERROR: ${e.message}"
                    toolsLatch?.countDown()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("McpClient: Connection failed: ${t.message}")
                error = "Connection failed: ${t.message}"
                toolsLatch?.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                println("McpClient: WebSocket closed: $reason")
                toolsLatch?.countDown()
            }
        }

    override fun close() {
        webSocket?.close(1000, "Disconnect")
        webSocket = null
        toolsLatch?.countDown()
    }
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
    val params: Map<String, JsonElement>
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
internal data class McpTool(
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