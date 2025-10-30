package io.github.sd155.aiadvent3

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.modelcontextprotocol.kotlin.sdk.server.mcp

fun main() {
    embeddedServer(
        factory = Netty,
        host = "0.0.0.0",
        port = 8181
    ) { mcp { TodoMcpServer().configureMcpServer() } }
        .start(wait = true)
}