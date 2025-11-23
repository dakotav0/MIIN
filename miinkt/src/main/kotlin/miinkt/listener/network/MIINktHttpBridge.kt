package miinkt.listener.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import miinkt.listener.state.MCPCommand
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class MIINktHttpBridge(
    private val port: Int,
    private val commandQueue: LinkedBlockingQueue<MCPCommand>
) {
    private val logger = LoggerFactory.getLogger("MIINkt-http-bridge")

    fun start() {
        try {
            val server = HttpServer.create(InetSocketAddress(port), 0)
            server.executor = Executors.newFixedThreadPool(2)

            // Command endpoint
            server.createContext("/command") { exchange -> handleCommandRequest(exchange) }

            // Health check endpoint
            server.createContext("/health") { exchange ->
                val response = """{"status": "ok", "service": "MIINkt-mc-listener"}"""
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.length.toLong())
                exchange.responseBody.use { it.write(response.toByteArray()) }
            }

            server.start()
            logger.info("HTTP Bridge started on port $port")
        } catch (e: Exception) {
            logger.error("Failed to start HTTP Bridge", e)
        }
    }

    /** Handle incoming command requests from MCP */
    private fun handleCommandRequest(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "POST") {
                val error = """{"error": "Method not allowed"}"""
                exchange.sendResponseHeaders(405, error.length.toLong())
                exchange.responseBody.use { it.write(error.toByteArray()) }
                return
            }

            // Read request body
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            val json = JsonParser.parseString(body).asJsonObject

            val commandType = json.get("type")?.asString ?: "unknown"
            val data = json.get("data")?.asJsonObject ?: JsonObject()

            // Convert to map for MCPCommand
            val dataMap = mutableMapOf<String, Any>()
            data.entrySet().forEach { (key, value) ->
                dataMap[key] =
                        when {
                            value.isJsonPrimitive -> {
                                val prim = value.asJsonPrimitive
                                when {
                                    prim.isString -> prim.asString
                                    prim.isNumber -> prim.asNumber
                                    prim.isBoolean -> prim.asBoolean
                                    else -> prim.asString
                                }
                            }
                            else -> value.toString()
                        }
            }

            // Add command to queue
            val command = MCPCommand(commandType, dataMap)
            commandQueue.offer(command)

            logger.info("Received command: $commandType")

            val response = """{"success": true, "type": "$commandType", "queued": true}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error("Error handling command", e)
            val error = """{"error": "${e.message}"}"""
            exchange.sendResponseHeaders(500, error.length.toLong())
            exchange.responseBody.use { it.write(error.toByteArray()) }
        }
    }
}
