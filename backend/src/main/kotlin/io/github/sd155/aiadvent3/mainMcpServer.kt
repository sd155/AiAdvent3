package io.github.sd155.aiadvent3

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, port = 8181) { mcpLocal() }.start(wait = true)
}

private fun Application.mcpLocal() {
    McpServer().configureMcpServer(this)

    routing {
        get("/") {
            call.respondText("MCP Server is running on port 8181")
        }
    }
}