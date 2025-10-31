package io.github.sd155.aiadvent3.mcp.todo

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
import java.time.LocalDate

internal class TodoMcpServer {

    fun configureMcpServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "Todo_MCP_server",
                version = "0.0.2"
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
        val readTool = RegisteredTool(
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
            println("TODO_TOOL_RQ::\nmethod:${callToolRequest.method}\nname:${callToolRequest.name}\narguments:${callToolRequest.arguments}")
            val dateString = callToolRequest.arguments["date"]!!.jsonPrimitive.content
            val file = File("$dateString.txt")
            if (!file.exists()) {
                CallToolResult(content = listOf(TextContent("No todos for $dateString")))
            }
            else {
                val text = file.readText()
                if (text.isNotBlank())
                    CallToolResult(content = listOf(TextContent(text)))
                else
                    CallToolResult(content = listOf(TextContent("No todos for $dateString")))
            }
        }
        val writeTool = RegisteredTool(
            Tool(
                title = null,
                outputSchema = null,
                annotations = null,
                name = "post_todo",
                description = "Writes todo list of the specified date.",
                inputSchema = Tool.Input(
                    properties = buildJsonObject {
                        putJsonObject("date") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Todos date, format of 'DD-MM-YYYY'."))
                        }
                        putJsonObject("content") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("List of todos."))
                        }
                    },
                    required = listOf("date", "content")
                )
            )
        ) { callToolRequest ->
            println("TODO_TOOL_RQ::\nmethod:${callToolRequest.method}\nname:${callToolRequest.name}\narguments:${callToolRequest.arguments}")
            val dateString = callToolRequest.arguments["date"]!!.jsonPrimitive.content
            val fileContent = callToolRequest.arguments["content"]!!.jsonPrimitive.content
            val file = File("$dateString.txt")
            file.writeText(fileContent)
            CallToolResult(content = listOf(TextContent("Todos were written successfully.")))
        }
        val dateTool = RegisteredTool(
            Tool(
                title = null,
                outputSchema = null,
                annotations = null,
                name = "get_date",
                description = "Gets current date.",
                inputSchema = Tool.Input(
                    properties = buildJsonObject {},
                    required = emptyList()
                )
            )
        ) { callToolRequest ->
            println("DATE_TOOL_RQ::\nmethod:${callToolRequest.method}\nname:${callToolRequest.name}\narguments:${callToolRequest.arguments}")
            val today = LocalDate.now()
            val year = today.year
            val month = today.monthValue
            val day = today.dayOfMonth
            CallToolResult(content = listOf(TextContent("$day-$month-$year")))
        }
        return listOf(readTool, writeTool, dateTool)
    }
}