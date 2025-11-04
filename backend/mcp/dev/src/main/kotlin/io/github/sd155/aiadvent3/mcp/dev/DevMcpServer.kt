package io.github.sd155.aiadvent3.mcp.dev

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

internal class DevMcpServer {

    fun configureMcpServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "Dev_MCP_server",
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

        val listTool = RegisteredTool(
            Tool(
                title = null,
                outputSchema = null,
                annotations = null,
                name = "list_kotlin_sources",
                description = "Lists all kotlin source files of the given project.",
                inputSchema = Tool.Input(
                    properties = buildJsonObject {
                        putJsonObject("project_dir_name") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Project directory name."))
                        }
                    },
                    required = listOf("project_dir_name")
                )
            )
        ) { callToolRequest ->
            println("TODO_TOOL_RQ::\nmethod:${callToolRequest.method}\nname:${callToolRequest.name}\narguments:${callToolRequest.arguments}")
            val dirName = callToolRequest.arguments["project_dir_name"]!!.jsonPrimitive.content
            val projectsPath = "/home/skydiver/Dev/Projects/"
            val dir = File(projectsPath, dirName)
            if (!dir.exists() || !dir.isDirectory) {
                CallToolResult(content = listOf(TextContent("Directory '${dirName}' does not exist!")))
            }
            else {
                dir
                    .walkTopDown()
                    .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
                    .toList()
                    .joinToString("\n- ") { file -> file.toString() }
                    .let { CallToolResult(content = listOf(TextContent("- $it"))) }
            }
        }

        val readTool = RegisteredTool(
            Tool(
                title = null,
                outputSchema = null,
                annotations = null,
                name = "read_source",
                description = "Reads source file by specified path.",
                inputSchema = Tool.Input(
                    properties = buildJsonObject {
                        putJsonObject("path") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Full path of the file (including file name)."))
                        }
                    },
                    required = listOf("path")
                )
            )
        ) { callToolRequest ->
            println("TODO_TOOL_RQ::\nmethod:${callToolRequest.method}\nname:${callToolRequest.name}\narguments:${callToolRequest.arguments}")
            val path = callToolRequest.arguments["path"]!!.jsonPrimitive.content
            val file = File(path)
            if (file.exists() && file.isFile) {
                val text = file.readText()
                if (text.isNotBlank())
                    CallToolResult(content = listOf(TextContent(text)))
                else
                    CallToolResult(content = listOf(TextContent("File `$path` is empty.")))
            }
            else {
                CallToolResult(content = listOf(TextContent("File `$path` is a directory or does not exist.")))
            }
        }
        return listOf(listTool, readTool)
    }
}