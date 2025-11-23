package miinkt.listener.state

/** MCP Command - commands from LLM to Minecraft */
data class MCPCommand(val type: String, val data: Map<String, Any>)
