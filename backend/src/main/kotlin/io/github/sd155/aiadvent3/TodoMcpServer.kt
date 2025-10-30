package io.github.sd155.aiadvent3

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.serialization.json.*
import java.io.File

internal class TodoMcpServer {

    fun configureMcpServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "Todo_MCP_server",
                version = "0.0.1"
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true)
                )
            )
        )
        server.addTools(createTools())
        return server
    }

    private fun createTools(): List<RegisteredTool> {
        val factTool = RegisteredTool(
            Tool(
                title = null,
                outputSchema = null,
                annotations = null,
                name = "get_todo",
                description = "Reads todo list of the specified date.",
                inputSchema = Tool.Input(
                    properties = buildJsonObject {
                        putJsonObject("date") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Todos date, format of 'DD-MM-YYYY'."))
                        }
                    },
                    required = listOf("date")
                )
            )
        ) { callToolRequest ->
            println("TOOL_RQ::\nmethod:${callToolRequest.method}\nname:${callToolRequest.name}\narguments:${callToolRequest.arguments}")
            val dateString = callToolRequest.arguments["date"]!!.jsonPrimitive.content
            val file = File("$dateString.txt")
            val text = file.readText()
            CallToolResult(content = listOf(TextContent(text)))
        }
        return listOf(factTool)
    }
}