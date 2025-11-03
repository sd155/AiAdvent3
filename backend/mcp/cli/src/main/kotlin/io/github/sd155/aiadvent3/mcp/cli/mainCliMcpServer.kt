package io.github.sd155.aiadvent3.mcp.cli

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.modelcontextprotocol.kotlin.sdk.server.mcp

fun main() {
    embeddedServer(
        factory = Netty,
        host = "127.0.0.2",
        port = 8181,
    ) { mcp { CliMcpServer().configureMcpServer() } }
        .start(wait = true)
}