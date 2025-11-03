package io.github.sd155.aiadvent3.mcp.cli

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.serialization.json.*

internal class CliMcpServer {

    fun configureMcpServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "Cli_MCP_server",
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
        val runTool = RegisteredTool(
            Tool(
                title = null,
                outputSchema = null,
                annotations = null,
                name = "run",
                description = "Runs CLI command.",
                inputSchema = Tool.Input(
                    properties = buildJsonObject {
                        putJsonObject("cmd") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("CLI command to run."))
                        }
                    },
                    required = listOf("cmd")
                )
            )
        ) { callToolRequest ->
            println("CLI_TOOL_RQ::\nmethod:${callToolRequest.method}\nname:${callToolRequest.name}\narguments:${callToolRequest.arguments}")
            val cmd = callToolRequest.arguments["cmd"]!!.jsonPrimitive.content
            val process = ProcessBuilder(*cmd.split(" ").toTypedArray())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            CallToolResult(content = listOf(TextContent(output)))
        }
        return listOf(runTool)
    }
}