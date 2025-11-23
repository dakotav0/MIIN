package miinkt.listener

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import miinkt.listener.entity.MIINNpcRenderer
import miinkt.listener.entity.NpcRegistry
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.time.Duration

class MIINListenerClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("MIINkt-client")
    private val gson = Gson()
    private val httpClient = HttpClient.newBuilder().build()

    // MCP Server endpoint (HTTP bridge)
    private val MCP_ENDPOINT = "http://localhost:5557/mcp/call"

    override fun onInitializeClient() {
        logger.info("MIIN Listener Client initializing...")

        // Register NPC entity renderer
        EntityRendererRegistry.register(NpcRegistry.MIIN_NPC, ::MIINNpcRenderer)
        logger.info("Registered MIIN NPC renderer with player model")

        // NPC interaction and /npc command are handled server-side
        // Lore satchel interaction is handled server-side via Chat

        // REMOVED: Client-side /npc command was conflicting with server-side command
        // All dialogue, talk, and quest functionality is now server-side only
        // registerNPCCommand()

        logger.info("MIIN Listener Client initialized!")
    }

    // NPC interaction is now handled server-side via chat dialogue
    // No client-side handler needed

    private fun registerNPCCommand() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                    ClientCommandManager.literal("npc")
                            .then(
                                    // /npc list
                                    ClientCommandManager.literal("list").executes { context ->
                                        handleNPCList(context)
                                    }
                            )
                            .then(
                                    // /npc talk <npc_id> <message>
                                    ClientCommandManager.literal("talk")
                                            .then(
                                                    ClientCommandManager.argument(
                                                                    "npc_id",
                                                                    StringArgumentType.word()
                                                            )
                                                            .then(
                                                                    ClientCommandManager.argument(
                                                                                    "message",
                                                                                    StringArgumentType
                                                                                            .greedyString()
                                                                            )
                                                                            .executes { context ->
                                                                                handleNPCTalk(
                                                                                        context
                                                                                )
                                                                            }
                                                            )
                                            )
                            )
                            .then(
                                    // /npc quest <npc_id>
                                    ClientCommandManager.literal("quest")
                                            .then(
                                                    ClientCommandManager.argument(
                                                                    "npc_id",
                                                                    StringArgumentType.word()
                                                            )
                                                            .executes { context ->
                                                                handleNPCQuest(context)
                                                            }
                                            )
                            )
                            .then(
                                    // /npc quests
                                    ClientCommandManager.literal("quests").executes { context ->
                                        handleNPCQuests(context)
                                    }
                            )
            )
        }

        logger.info("Registered /npc command with subcommands: list, talk, quest, quests")
    }

    /** Handle /npc list */
    private fun handleNPCList(context: CommandContext<FabricClientCommandSource>): Int {
        val source = context.source

        source.sendFeedback(Text.literal("§6Fetching NPC list..."))

        try {
            val toolCall =
                    JsonObject().apply {
                        addProperty("tool", "minecraft_npc_list")
                        add("arguments", JsonObject())
                    }

            val request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(MCP_ENDPOINT))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(toolCall)))
                            .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val responseBody = response.body()
                logger.info("NPC list response: $responseBody")

                // Parse MCP protocol response: {result: {content: [{type: "text", text: "..."}]}}
                val mcpResponse = JsonParser.parseString(responseBody).asJsonObject
                val result = mcpResponse.getAsJsonObject("result")
                val content = result?.getAsJsonArray("content")
                val textContent = content?.get(0)?.asJsonObject?.get("text")?.asString

                // Parse the actual NPC data from the text field
                val npcData =
                        if (textContent != null) {
                            JsonParser.parseString(textContent).asJsonObject
                        } else {
                            logger.error("No text content in MCP response: $responseBody")
                            null
                        }
                val npcsArray = npcData?.getAsJsonArray("npcs")

                if (npcsArray != null && npcsArray.size() > 0) {
                    source.sendFeedback(Text.literal("§6§l=== Available NPCs ==="))

                    npcsArray.forEach { npcElement ->
                        val npc = npcElement.asJsonObject
                        val name = npc.get("name")?.asString ?: "Unknown"
                        val id = npc.get("id")?.asString ?: ""
                        val personality = npc.get("personality")?.asString ?: ""

                        source.sendFeedback(Text.literal(""))
                        source.sendFeedback(Text.literal("§e$name §7($id)"))
                        source.sendFeedback(Text.literal("  §7Personality: §f$personality"))
                    }

                    source.sendFeedback(Text.literal(""))
                    source.sendFeedback(Text.literal("§7Use /npc talk <id> <message> to interact"))
                } else {
                    logger.warn("No NPCs in parsed data. Text content: $textContent")
                    source.sendError(
                            Text.literal("§cNo NPCs found! Check if NPC service is running.")
                    )
                    source.sendError(Text.literal("§7Run: python MIIN/npc_service.py"))
                }
            } else {
                source.sendError(
                        Text.literal("§cFailed to fetch NPCs (HTTP ${response.statusCode()})")
                )
                logger.error("HTTP error ${response.statusCode()}: ${response.body()}")
            }
        } catch (e: Exception) {
            logger.error("Error fetching NPC list", e)
            source.sendError(Text.literal("§cError: ${e.message}"))
        }

        return 1
    }

    /** Handle /npc talk <npc_id> <message> */
    private fun handleNPCTalk(context: CommandContext<FabricClientCommandSource>): Int {
        val source = context.source
        val npcId = StringArgumentType.getString(context, "npc_id")
        val message = StringArgumentType.getString(context, "message")

        val playerName = source.player?.name?.string ?: "Player"

        source.sendFeedback(Text.literal("§7You → §e$npcId§7: $message"))
        source.sendFeedback(Text.literal("§6Waiting for response..."))

        try {
            val toolCall =
                    JsonObject().apply {
                        addProperty("tool", "minecraft_npc_talk")
                        add(
                                "arguments",
                                JsonObject().apply {
                                    addProperty("npc", npcId)
                                    addProperty("player", playerName)
                                    addProperty("message", message)
                                }
                        )
                    }

            val request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(MCP_ENDPOINT))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(toolCall)))
                            .timeout(Duration.ofSeconds(30))
                            .build()

            httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept { response ->
                        // IMPORTANT: Schedule chat messages on the render thread
                        MinecraftClient.getInstance().execute {
                            if (response.statusCode() == 200) {
                                // Parse MCP protocol response
                                val mcpResponse =
                                        JsonParser.parseString(response.body()).asJsonObject
                                val result = mcpResponse.getAsJsonObject("result")
                                val content = result?.getAsJsonArray("content")
                                val textContent =
                                        content?.get(0)?.asJsonObject?.get("text")?.asString

                                // Parse the actual NPC response from the text field
                                val npcData =
                                        if (textContent != null) {
                                            JsonParser.parseString(textContent).asJsonObject
                                        } else {
                                            null
                                        }
                                val npcResponse = npcData?.get("response")?.asString

                                if (npcResponse != null && npcResponse.isNotEmpty()) {
                                    source.sendFeedback(Text.literal(""))
                                    source.sendFeedback(Text.literal("§e$npcId§7: §f$npcResponse"))
                                } else {
                                    logger.warn("No response from NPC. Text content: $textContent")
                                    source.sendError(Text.literal("§cNPC didn't respond!"))
                                }
                            } else {
                                source.sendError(
                                        Text.literal(
                                                "§cFailed to talk to NPC (HTTP ${response.statusCode()})"
                                        )
                                )
                            }
                        }
                    }
                    .exceptionally { e ->
                        logger.error("Error talking to NPC", e)
                        MinecraftClient.getInstance().execute {
                            source.sendError(Text.literal("§cError: ${e.message}"))
                        }
                        null
                    }
        } catch (e: Exception) {
            logger.error("Error talking to NPC", e)
            source.sendError(Text.literal("§cError: ${e.message}"))
        }

        return 1
    }

    /** Handle /npc quest <npc_id> */
    private fun handleNPCQuest(context: CommandContext<FabricClientCommandSource>): Int {
        val source = context.source
        val npcId = StringArgumentType.getString(context, "npc_id")
        val playerName = source.player?.name?.string ?: "Player"

        source.sendFeedback(Text.literal("§6Requesting quest from §e$npcId§6..."))

        try {
            val toolCall =
                    JsonObject().apply {
                        addProperty("tool", "minecraft_quest_request")
                        add(
                                "arguments",
                                JsonObject().apply {
                                    addProperty("npc", npcId)
                                    addProperty("player", playerName)
                                }
                        )
                    }

            val request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(MCP_ENDPOINT))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(toolCall)))
                            .timeout(Duration.ofSeconds(30))
                            .build()

            httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept { response ->
                        // IMPORTANT: Schedule chat messages on the render thread
                        MinecraftClient.getInstance().execute {
                            if (response.statusCode() == 200) {
                                // Parse MCP protocol response
                                val mcpResponse =
                                        JsonParser.parseString(response.body()).asJsonObject
                                val result = mcpResponse.getAsJsonObject("result")
                                val content = result?.getAsJsonArray("content")
                                val textContent =
                                        content?.get(0)?.asJsonObject?.get("text")?.asString

                                // Parse the actual quest data from the text field
                                val quest =
                                        if (textContent != null) {
                                            JsonParser.parseString(textContent).asJsonObject
                                        } else {
                                            null
                                        }

                                if (quest != null && !quest.has("error")) {
                                    val title = quest.get("title")?.asString ?: "Unknown Quest"
                                    val description = quest.get("description")?.asString ?: ""

                                    source.sendFeedback(Text.literal(""))
                                    source.sendFeedback(Text.literal("§6§l=== New Quest ==="))
                                    source.sendFeedback(Text.literal("§e$title"))
                                    source.sendFeedback(Text.literal(""))
                                    source.sendFeedback(Text.literal("§f$description"))
                                    source.sendFeedback(Text.literal(""))

                                    // Show objectives if present
                                    val objectives = quest.getAsJsonArray("objectives")
                                    if (objectives != null && objectives.size() > 0) {
                                        source.sendFeedback(Text.literal("§6Objectives:"))
                                        objectives.forEach { objElement ->
                                            val obj = objElement.asJsonObject
                                            val type = obj.get("type")?.asString
                                            source.sendFeedback(Text.literal("  §7- $type"))
                                        }
                                    }
                                } else {
                                    logger.warn("No quest data. Text content: $textContent")
                                    source.sendError(Text.literal("§cNo quest available!"))
                                }
                            } else {
                                source.sendError(
                                        Text.literal(
                                                "§cFailed to get quest (HTTP ${response.statusCode()})"
                                        )
                                )
                            }
                        }
                    }
                    .exceptionally { e ->
                        logger.error("Error requesting quest", e)
                        MinecraftClient.getInstance().execute {
                            source.sendError(Text.literal("§cError: ${e.message}"))
                        }
                        null
                    }
        } catch (e: Exception) {
            logger.error("Error requesting quest", e)
            source.sendError(Text.literal("§cError: ${e.message}"))
        }

        return 1
    }

    /** Handle /npc quests */
    private fun handleNPCQuests(context: CommandContext<FabricClientCommandSource>): Int {
        val source = context.source
        val playerName = source.player?.name?.string ?: "Player"

        source.sendFeedback(Text.literal("§6Fetching your quests..."))

        try {
            val toolCall =
                    JsonObject().apply {
                        addProperty("tool", "minecraft_quest_status")
                        add("arguments", JsonObject().apply { addProperty("player", playerName) })
                    }

            val request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(MCP_ENDPOINT))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(toolCall)))
                            .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                // Parse MCP protocol response
                val mcpResponse = JsonParser.parseString(response.body()).asJsonObject
                val result = mcpResponse.getAsJsonObject("result")
                val content = result?.getAsJsonArray("content")
                val textContent = content?.get(0)?.asJsonObject?.get("text")?.asString

                // Parse the actual quest data from the text field
                val questData =
                        if (textContent != null) {
                            JsonParser.parseString(textContent).asJsonObject
                        } else {
                            null
                        }

                val active = questData?.getAsJsonArray("active")
                val completed = questData?.getAsJsonArray("completed")

                source.sendFeedback(Text.literal("§6§l=== Your Quests ==="))
                source.sendFeedback(Text.literal(""))

                // Active quests
                if (active != null && active.size() > 0) {
                    source.sendFeedback(Text.literal("§eActive Quests (${active.size()}):"))
                    active.forEach { questElement ->
                        val quest = questElement.asJsonObject
                        val title = quest.get("title")?.asString ?: "Unknown"
                        val status = quest.get("status")?.asString ?: "active"
                        source.sendFeedback(Text.literal("  §7- §f$title §7($status)"))
                    }
                } else {
                    source.sendFeedback(Text.literal("§7No active quests"))
                }

                source.sendFeedback(Text.literal(""))

                // Completed quests
                if (completed != null && completed.size() > 0) {
                    source.sendFeedback(Text.literal("§aCompleted Quests (${completed.size()}):"))
                    completed.forEach { questElement ->
                        val quest = questElement.asJsonObject
                        val title = quest.get("title")?.asString ?: "Unknown"
                        source.sendFeedback(Text.literal("  §7- §a✓ §f$title"))
                    }
                }
            } else {
                source.sendError(
                        Text.literal("§cFailed to fetch quests (HTTP ${response.statusCode()})")
                )
            }
        } catch (e: Exception) {
            logger.error("Error fetching quests", e)
            source.sendError(Text.literal("§cError: ${e.message}"))
        }

        return 1
    }
}
