/**
 * MIINkt Listener - Minecraft Fabric Mod
 *
 * Listens to Minecraft events and sends them to the Minecraft MCP Server
 * for intelligence analysis.
 *
 * This integrates with your existing MIINkt.listener mod template.
 */

package miinkt.listener

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.BlockState
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import miinkt.listener.entity.NpcRegistry
import miinkt.listener.entity.NpcManager
import miinkt.listener.entity.MIINNpcEntity
import miinkt.listener.item.ItemRegistry
import miinkt.listener.component.MIINDataComponents
import miinkt.listener.state.BuildSession
import miinkt.listener.state.DialogueState
import miinkt.listener.state.MCPCommand
import miinkt.listener.state.PlayerState
import miinkt.listener.network.MIINktHttpBridge
import miinkt.listener.event.MIINEventHandler
import miinkt.listener.command.MIINCommandRegistry
import miinkt.listener.dialogue.DialogueManager
import miinkt.listener.service.MIINLoreService
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.Items
import net.minecraft.item.ItemStack
import net.minecraft.component.type.WrittenBookContentComponent
import net.minecraft.text.RawFilteredPair
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import java.io.File

class MIINListener : ModInitializer {
    companion object {
        private val LOGGER = LoggerFactory.getLogger("MIINkt-mc-listener")
        private val gson = Gson()
        private val httpClient = HttpClient.newBuilder().build()

        // Track player build sessions
        private val playerSessions = ConcurrentHashMap<String, BuildSession>()

        // Track player states
        private val playerStates = ConcurrentHashMap<String, PlayerState>()

        // Track player dialogue states
        private val playerDialogues = ConcurrentHashMap<String, DialogueState>()

        // Queue for incoming MCP commands (from LLM → Minecraft)
        private val commandQueue = LinkedBlockingQueue<MCPCommand>()

        // Debounce dialogue interactions to prevent duplicates
        private val dialogueCooldowns = ConcurrentHashMap<String, Long>()

        // MCP Server endpoint (via HTTP bridge if needed, or direct stdio)
        private const val MCP_ENDPOINT = "http://localhost:5557/mcp/call"

        // HTTP Bridge port (for receiving commands from MCP)
        private const val HTTP_BRIDGE_PORT = 5558
        private const val HTTP_PORT = 5558  // Alias for consistency
        private const val DIALOGUE_COOLDOWN_MS = 500L  // 500ms cooldown

        // Rate limiting
        private var lastEventTime = 0L
        private const val MIN_EVENT_INTERVAL_MS = 50L // Max 20 events/second

        // Services
        private lateinit var httpBridge: MIINktHttpBridge
        private lateinit var eventHandler: MIINEventHandler
        private lateinit var commandRegistry: MIINCommandRegistry
        private lateinit var dialogueManager: DialogueManager
        private lateinit var loreService: MIINLoreService
    }

    override fun onInitialize() {
        LOGGER.info("MIINkt Listener initializing...")

        // Register data components (must be first)
        MIINDataComponents.register()

        // Register NPC entity types
        NpcRegistry.register()

        // Register Dynamic Spawn Rules
        _root_ide_package_.miinkt.listener.spawn.SpawnRuleManager.loadConfig()
        _root_ide_package_.miinkt.listener.spawn.SpawnListener.register()

        // Register custom items
        ItemRegistry.register()

        // Spawn NPCs when server starts
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val configPath = "${System.getProperty("user.dir")}/config/miinkt/npcs.json"
            val fallbackPath = "${System.getProperty("user.dir")}/mods/MIIN/npcs.json"
            val devPath = "npcs.json"

            LOGGER.info("Looking for NPC config...")
            LOGGER.info("  Primary path: $configPath")

            when {
                File(configPath).exists() -> {
                    LOGGER.info("Found NPC config at: $configPath")
                    NpcManager.loadAndSpawnNpcs(server, configPath)
                }
                File(fallbackPath).exists() -> {
                    LOGGER.info("Found NPC config at fallback: $fallbackPath")
                    NpcManager.loadAndSpawnNpcs(server, fallbackPath)
                }
                File(devPath).exists() -> {
                    LOGGER.info("Found NPC config in working directory: $devPath")
                    NpcManager.loadAndSpawnNpcs(server, devPath)
                }
                else -> {
                    LOGGER.warn("No NPC config found!")
                    LOGGER.warn("Please create: $configPath")
                    LOGGER.warn("You can copy npcs.json from the MIINkt repository")
                }
            }
        }

        // Clear NPC map when server stops (prevents issues when switching worlds)
        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            NpcManager.clearSpawnedNpcs()
            LOGGER.info("Server stopping - cleared NPC manager state")
        }

        // Initialize Services
        loreService = MIINLoreService()

        eventHandler = MIINEventHandler(MCP_ENDPOINT, playerStates)
        eventHandler.registerEvents()

        dialogueManager = DialogueManager(
            playerDialogues,
            dialogueCooldowns,
            MCP_ENDPOINT,
            DIALOGUE_COOLDOWN_MS
        )
        dialogueManager.registerNpcInteractionHandler()

        commandRegistry = MIINCommandRegistry(
            playerDialogues,
            { player, state -> dialogueManager.sendDialogueOptions(player, state) },
            { context -> listAllNpcs(context) },
            loreService,
            { player, option -> dialogueManager.handleDialogueSelection(player, option) },
            { player, message ->
                val state = playerDialogues[player.name.string]
                if (state != null) {
                    dialogueManager.sendNpcTalkRequest(player, state.npcId, state.npcName, message)
                } else {
                    player.sendMessage(Text.literal("§cYou are not in a conversation."), false)
                }
            },
            { player, type ->
                // Handle NPC actions
                val state = playerDialogues[player.name.string]
                if (state != null) {
                    player.sendMessage(Text.literal("§7Action '§e$type§7' requested (Not yet implemented)"), false)
                } else {
                    player.sendMessage(Text.literal("§cYou are not in a conversation!"), false)
                }
            },
            { player ->
                // Handle Back
                val state = playerDialogues[player.name.string]
                if (state != null) {
                    dialogueManager.sendDialogueOptions(player, state)
                } else {
                    player.sendMessage(Text.literal("§cYou are not in a conversation!"), false)
                }
            },
            { player, templateId, name -> spawnNpc(player, templateId, name) }
        )
        commandRegistry.registerCommands()

        httpBridge = MIINktHttpBridge(HTTP_PORT, commandQueue)
        httpBridge.start()

        // Register block place listener
        registerBlockPlaceListener()

        // Register block break listener
        registerBlockBreakListener()

        // Register build session tracker
        registerSessionTracker()

        // Register Command Processor (executes commands from queue)
        registerCommandProcessor()

        LOGGER.info("MIINkt Listener initialized successfully!")
    }

    /**
     * Register global entity interaction handler for NPCs
     */

    /**
     * Start or continue dialogue with an NPC
     */

    /**
     * Fetch dialogue options from MCP service
     */

    /**
     * Use fallback dialogue options when MCP is unavailable
     */

    /**
     * Send dialogue options to player as chat messages
     */

    /**
     * Get color code for dialogue tone
     */

    /**
     * Handle player selecting a dialogue option
     */

    /**
     * Get NPC response to player choice
     */

    /**
     * Get farewell message for NPC
     */

    /**
     * Show NPC action menu
     */
    private fun showNpcActions(player: ServerPlayerEntity) {
        val playerName = player.name.string
        val state = playerDialogues[playerName]

        if (state == null) {
            player.sendMessage(Text.literal("§7You're not in a conversation. Right-click an NPC to talk."), false)
            return
        }

        player.sendMessage(Text.literal(""), false)
        player.sendMessage(Text.literal("§6═══════ §e§lNPC Actions §6═══════"), false)
        player.sendMessage(Text.literal("§7Command ${state.npcName} to:"), false)
        player.sendMessage(Text.literal(""), false)
        player.sendMessage(Text.literal("  §f/npc action follow §8- §aNPC will follow you"), false)
        player.sendMessage(Text.literal("  §f/npc action roam §8- §eNPC will wander nearby"), false)
        player.sendMessage(Text.literal("  §f/npc action stay §8- §7NPC will stay in place"), false)
        player.sendMessage(Text.literal(""), false)
        player.sendMessage(Text.literal("  §f/npc back §8- §7Return to dialogue"), false)
        player.sendMessage(Text.literal("§6═════════════════════════════════"), false)
    }

    /**
     * Handle NPC action command
     */

    /**
     * Get greeting for NPC by ID
     */

    /**
     * Register the /lore command for viewing and manipulating lore
     */

    /**
     * List all NPCs with their locations and descriptions
     */
    private fun listAllNpcs(context: CommandContext<ServerCommandSource>) {
        val player = context.source.player ?: return
        val npcIds = NpcManager.getSpawnedNpcIds()

        if (npcIds.isEmpty()) {
            player.sendMessage(Text.literal("§7No NPCs are currently spawned."), false)
            return
        }

        player.sendMessage(Text.literal("§6═══════ §e§lNPC Locations §6═══════"), false)
        player.sendMessage(Text.literal("§7Found ${npcIds.size} NPCs:"), false)
        player.sendMessage(Text.literal(""), false)

        for (npcId in npcIds) {
            val npc = NpcManager.getNpc(npcId)
            if (npc != null) {
                val pos = npc.blockPos
                val biome = npc.entityWorld.getBiome(pos).key.map { it.value.path }.orElse("unknown")
                val distance = player.blockPos.getManhattanDistance(pos)

                // Get NPC description based on ID
                val description = getNpcDescription(npcId)

                // Direction indicator
                val direction = getDirectionTo(player.blockPos, pos)

                player.sendMessage(Text.literal("§e${npc.npcName}"), false)
                player.sendMessage(Text.literal("  §7$description"), false)
                player.sendMessage(Text.literal("  §8Location: §f${pos.x}, ${pos.y}, ${pos.z} §7($biome)"), false)
                player.sendMessage(Text.literal("  §8Distance: §f$distance blocks §7$direction"), false)
                player.sendMessage(Text.literal(""), false)
            }
        }

        player.sendMessage(Text.literal("§6═══════════════════════════════"), false)
    }

    /**
     * Get direction from player to target
     */
    private fun getDirectionTo(from: net.minecraft.util.math.BlockPos, to: net.minecraft.util.math.BlockPos): String {
        val dx = to.x - from.x
        val dz = to.z - from.z

        if (kotlin.math.abs(dx) < 10 && kotlin.math.abs(dz) < 10) {
            return "(nearby)"
        }

        val ns = when {
            dz < -10 -> "North"
            dz > 10 -> "South"
            else -> ""
        }
        val ew = when {
            dx > 10 -> "East"
            dx < -10 -> "West"
            else -> ""
        }

        return "($ns$ew)"
    }

    /**
     * Get NPC role description
     */
    private fun getNpcDescription(npcId: String): String {
        return when (npcId) {
            "marina" -> "Fisher - Ocean lore, weather patterns, rare catches"
            "vex" -> "Voidwalker - Dimensional travel, End/Nether secrets"
            "rowan" -> "Merchant - Trading, rare items, market knowledge"
            "kira" -> "Monster Hunter - Combat tactics, survival, protection"
            "sage" -> "Herbalist - Nature lore, plants, biome spirits"
            "thane" -> "Craftsman - Redstone, building, resource efficiency"
            "lyra" -> "Artist - Aesthetics, constellations, creative builds"
            "grimm" -> "Miner - Deep dark, ore veins, cave systems"
            else -> "Wanderer"
        }
    }

    /**
     * Create a lore book from /lore write command
     */

    /**
     * Rename held item from /lore rename command
     */

    /**
     * Save current map to satchel from /lore savemap command
     */

    /**
     * Give a lore satchel to player from /lore give command
     */

    /**
     * Show collected lore to player
     */

    private fun registerBlockPlaceListener() {
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (!world.isClient && player is ServerPlayerEntity) {
                val pos = hitResult.blockPos
                val blockState = world.getBlockState(pos)

                // Track block placement
                trackBlockPlace(player, blockState)
            }
            ActionResult.PASS
        }
    }

    private fun registerBlockBreakListener() {
        // Note: You'll need to add appropriate event listener for block break
        // This is a placeholder showing the pattern
        LOGGER.info("Block break listener registered")
    }

    private fun registerSessionTracker() {
        var tickCounter = 0

        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickCounter++

            // Every 5 seconds (100 ticks), check for session updates
            if (tickCounter >= 100) {
                tickCounter = 0

                server.playerManager.playerList.forEach { player ->
                    val session = getOrCreateSession(player)

                    // Check if build session should be finalized
                    if (session.shouldFinalize()) {
                        finalizeBuildSession(player, session)
                    }
                }
            }
        }
    }

    private fun trackBlockPlace(player: ServerPlayerEntity, blockState: BlockState) {
        val session = getOrCreateSession(player)
        val blockName = blockState.block.translationKey.replace("block.minecraft.", "")

        session.addBlock(blockName)

        LOGGER.debug("Player ${player.name.string} placed $blockName")
    }

    private fun getOrCreateSession(player: ServerPlayerEntity): BuildSession {
        val playerId = player.uuidAsString
        return playerSessions.computeIfAbsent(playerId) {
            BuildSession(playerId, player.name.string)
        }
    }

    private fun finalizeBuildSession(player: ServerPlayerEntity, session: BuildSession) {
        if (session.blockCount < 5) {
            // Don't track very small builds
            session.reset()
            return
        }

        LOGGER.info("Finalizing build session for ${player.name.string}: ${session.blockCount} blocks")

        // Send to MCP server for analysis
        sendBuildAnalysis(session)

        // Reset session for next build
        session.reset()
    }

    private fun sendBuildAnalysis(session: BuildSession) {
        try {
            // Prepare MCP tool call
            val toolCall = JsonObject().apply {
                addProperty("tool", "minecraft_track_event")
                add("arguments", JsonObject().apply {
                    addProperty("eventType", "build_complete")
                    add("data", JsonObject().apply {
                        addProperty("playerName", session.playerName)
                        addProperty("playerId", session.playerId)
                        add("blocks", gson.toJsonTree(session.getUniqueBlocks()))
                        add("blockCounts", gson.toJsonTree(session.blockCounts))
                        addProperty("buildTime", session.getDuration())
                        addProperty("timestamp", System.currentTimeMillis())
                    })
                })
            }

            // Send HTTP request to MCP server
            val request = HttpRequest.newBuilder()
                .uri(URI.create(MCP_ENDPOINT))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(toolCall)))
                .build()

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept { response ->
                    if (response.statusCode() == 200) {
                        LOGGER.info("Build analysis sent successfully")
                    } else {
                        LOGGER.warn("Failed to send build analysis: ${response.statusCode()}")
                    }
                }

        } catch (e: Exception) {
            LOGGER.error("Error sending build analysis", e)
        }
    }

    // === NEW EVENT LISTENERS ===

    /**
     * Chat event forwarding - send player chat messages to MCP
     */

    /**
     * Player state monitoring - track position, health, dimension, biome
     */

    /**
     * Mob interaction tracking - track mobs killed by player
     */

    /**
     * Connection listener - track session start/end
     */

    /**
     * Command processor - process commands from MCP (LLM → Minecraft)
     */
    private fun registerCommandProcessor() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            // Process pending commands from MCP
            while (true) {
                val command = commandQueue.poll() ?: break

                when (command.type) {
                    "send_chat" -> {
                        // Send message to player(s)
                        val message = command.data["message"] as? String ?: continue
                        val targetPlayer = command.data["player"] as? String

                        if (targetPlayer != null) {
                            // Send to specific player
                            server.playerManager.playerList
                                .find { it.name.string == targetPlayer }
                                ?.sendMessage(Text.literal("§6[MIINkt AI]§r $message"))
                        } else {
                            // Broadcast to all players
                            server.playerManager.broadcast(Text.literal("§6[MIINkt AI]§r $message"), false)
                        }
                    }

                    "get_inventory" -> {
                        // Return player inventory
                        val targetPlayer = command.data["player"] as? String ?: continue
                        server.playerManager.playerList
                            .find { it.name.string == targetPlayer }
                            ?.let { player ->
                                sendInventorySnapshot(player)
                            }
                    }

                    "teleport" -> {
                        // Teleport player (admin only - add permission check in production)
                        val targetPlayer = command.data["player"] as? String ?: continue
                        val x = (command.data["x"] as? Number)?.toDouble() ?: continue
                        val y = (command.data["y"] as? Number)?.toDouble() ?: continue
                        val z = (command.data["z"] as? Number)?.toDouble() ?: continue

                        server.playerManager.playerList
                            .find { it.name.string == targetPlayer }
                            ?.teleport(x, y, z, true)
                    }

                    "npc_action" -> {
                        // Set NPC behavior (follow/roam/stay)
                        val npcId = command.data["npc_id"] as? String ?: continue
                        val playerName = command.data["player"] as? String ?: continue
                        val action = command.data["action"] as? String ?: continue

                        val npc = NpcManager.getNpc(npcId)
                        val player = server.playerManager.playerList.find { it.name.string == playerName }

                        if (npc != null && player != null) {
                            when (action) {
                                "follow" -> {
                                    npc.setFollowTarget(player)
                                    LOGGER.info("NPC $npcId now following $playerName")
                                }
                                "roam" -> {
                                    npc.setBehavior(MIINNpcEntity.BehaviorMode.ROAMING, 8.0)
                                    LOGGER.info("NPC $npcId now roaming")
                                }
                                "stay" -> {
                                    npc.setBehavior(MIINNpcEntity.BehaviorMode.STATIONARY)
                                    LOGGER.info("NPC $npcId now stationary")
                                }
                                else -> {
                                    LOGGER.warn("Unknown NPC action: $action")
                                }
                            }
                        } else {
                            LOGGER.warn("NPC $npcId or player $playerName not found for action")
                        }
                    }

                    "create_lore_book" -> {
                        // Create a written book and give to player
                        val playerName = command.data["player"] as? String ?: continue
                        val title = command.data["title"] as? String ?: continue
                        val content = command.data["content"] as? String ?: continue

                        val player = server.playerManager.playerList.find { it.name.string == playerName }
                        if (player != null) {
                            // Create written book
                            val bookStack = ItemStack(Items.WRITTEN_BOOK)

                            // Split content into pages (max 100 chars per page for readability)
                            val pages = mutableListOf<RawFilteredPair<Text>>()
                            val words = content.split(" ")
                            var currentPage = StringBuilder()

                            for (word in words) {
                                if (currentPage.length + word.length + 1 > 256) {
                                    pages.add(RawFilteredPair.of(Text.literal(currentPage.toString().trim())))
                                    currentPage = StringBuilder()
                                }
                                currentPage.append(word).append(" ")
                            }
                            if (currentPage.isNotEmpty()) {
                                pages.add(RawFilteredPair.of(Text.literal(currentPage.toString().trim())))
                            }

                            // Set book content
                            val bookContent = WrittenBookContentComponent(
                                RawFilteredPair.of(title),
                                playerName,
                                0,
                                pages,
                                true
                            )
                            bookStack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, bookContent)

                            // Give to player
                            player.giveItemStack(bookStack)
                            player.sendMessage(Text.literal("§aCreated book: $title"), false)
                            LOGGER.info("Created lore book '$title' for $playerName")
                        }
                    }

                    "rename_held_item" -> {
                        // Rename the item player is holding
                        val playerName = command.data["player"] as? String ?: continue
                        val newName = command.data["new_name"] as? String ?: continue

                        val player = server.playerManager.playerList.find { it.name.string == playerName }
                        if (player != null) {
                            val heldItem = player.mainHandStack
                            if (!heldItem.isEmpty) {
                                // Set custom name
                                heldItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal(newName))
                                player.sendMessage(Text.literal("§aRenamed to: $newName"), false)
                                LOGGER.info("$playerName renamed item to '$newName'")
                            } else {
                                player.sendMessage(Text.literal("§cNo item in hand"), false)
                            }
                        }
                    }

                    "save_map" -> {
                        // Save map data from held map
                        val playerName = command.data["player"] as? String ?: continue

                        val player = server.playerManager.playerList.find { it.name.string == playerName }
                        if (player != null) {
                            val heldItem = player.mainHandStack
                            if (heldItem.item == Items.FILLED_MAP) {
                                // Get map ID
                                val mapId = heldItem.get(DataComponentTypes.MAP_ID)
                                if (mapId != null) {
                                    // TODO: Save map data to satchel storage
                                    // For now just confirm the action
                                    player.sendMessage(Text.literal("§aMap saved to satchel!"), false)
                                    LOGGER.info("$playerName saved map ${mapId.id}")
                                } else {
                                    player.sendMessage(Text.literal("§cInvalid map"), false)
                                }
                            } else {
                                player.sendMessage(Text.literal("§cHold a map to save it"), false)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Start HTTP bridge server to receive commands from MCP
     */

    /**
     * Handle incoming command requests from MCP
     */

    // === HELPER FUNCTIONS ===

    /**
     * Send inventory snapshot to MCP
     */
    private fun sendInventorySnapshot(player: ServerPlayerEntity) {
        val inventory = mutableListOf<JsonObject>()

        player.inventory.mainStacks.forEachIndexed { index, stack ->
            if (!stack.isEmpty) {
                inventory.add(JsonObject().apply {
                    addProperty("slot", index)
                    addProperty("item", stack.item.toString())
                    addProperty("count", stack.count)
                    addProperty("displayName", stack.name.string)
                })
            }
        }

        eventHandler.sendMCPEvent("inventory_snapshot", JsonObject().apply {
            addProperty("playerName", player.name.string)
            addProperty("playerId", player.uuidAsString)
            add("inventory", gson.toJsonTree(inventory))
            addProperty("timestamp", System.currentTimeMillis())
        })
    }

    /**
     * Get biome name for player location
     */
    private fun getBiomeName(player: ServerPlayerEntity): String {
        val biome = player.entityWorld.getBiome(player.blockPos)
        return biome.key.orElse(null)?.value?.path ?: "unknown"
    }

    /**
     * Get weather state
     */
    private fun getWeatherState(player: ServerPlayerEntity): String {
        return when {
            player.entityWorld.isThundering -> "thundering"
            player.entityWorld.isRaining -> "raining"
            else -> "clear"
        }
    }

    /**
     * Get time of day (dawn, day, dusk, night)
     */
    private fun getTimeOfDay(player: ServerPlayerEntity): String {
        val time = player.entityWorld.timeOfDay % 24000
        return when {
            time < 450 -> "dawn"
            time < 11616 -> "day"
            time < 13800 -> "dusk"
            else -> "night"
        }
    }

    /**
     * Get or create player state tracker
     */
    private fun getOrCreatePlayerState(player: ServerPlayerEntity): PlayerState {
        val playerId = player.uuidAsString
        return playerStates.computeIfAbsent(playerId) {
            PlayerState(playerId)
        }
    }

    /**
     * Generic MCP event sender with rate limiting
     */

    // === DATA CLASSES ===

    /**
     * Player State - tracks player state for change detection
     */

    /**
     * Spawn a dynamic NPC via MCP
     */
    private fun spawnNpc(player: ServerPlayerEntity, templateId: String, name: String?) {
        player.sendMessage(Text.literal("§7Requesting NPC spawn: §e$templateId..."), false)

        val pos = player.blockPos
        val dimension = player.entityWorld.registryKey.value.toString()
        val biome = getBiomeName(player)

        // Run in background
        Thread {
            try {
                val args = JsonObject().apply {
                    addProperty("template_id", templateId)
                    addProperty("x", pos.x)
                    addProperty("y", pos.y)
                    addProperty("z", pos.z)
                    addProperty("dimension", dimension)
                    addProperty("biome", biome)
                    if (name != null) {
                        addProperty("name", name)
                    }
                }

                val toolCall = JsonObject().apply {
                    addProperty("tool", "minecraft_npc_create")
                    add("arguments", args)
                }

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(MCP_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(toolCall)))
                    .timeout(java.time.Duration.ofSeconds(30)) // Longer timeout for LLM generation
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() == 200) {
                    val jsonResponse = JsonParser.parseString(response.body()).asJsonObject
                    
                    // Check for tool error
                    if (jsonResponse.has("isError") && jsonResponse.get("isError").asBoolean) {
                        val errorContent = jsonResponse.getAsJsonArray("content").get(0).asJsonObject.get("text").asString
                        player.entityWorld.server.execute {
                            player.sendMessage(Text.literal("§cFailed to spawn NPC: $errorContent"), false)
                        }
                        return@Thread
                    }

                    // Parse result content
                    val contentArray = jsonResponse.getAsJsonArray("content")
                    if (contentArray.size() > 0) {
                        val contentText = contentArray.get(0).asJsonObject.get("text").asString
                        val npcData = JsonParser.parseString(contentText).asJsonObject

                        val npcId = npcData.get("id").asString
                        player.sendMessage(Text.literal("§aSpawned NPC: $npcId"), false)
                        LOGGER.info("$player.name spawned NPC '$npcId'")
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to spawn NPC: ${e.message}", e)
                player.sendMessage(Text.literal("§cFailed to spawn NPC: ${e.message}"), false)
            }
        }
    }
}
