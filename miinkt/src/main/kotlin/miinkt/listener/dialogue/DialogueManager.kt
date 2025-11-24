package miinkt.listener.dialogue

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import miinkt.listener.entity.MIINNpcEntity
import miinkt.listener.entity.NpcManager
import miinkt.listener.state.DialogueOption
import miinkt.listener.state.DialogueState
import miinkt.listener.state.RollCheck
import kotlin.random.Random
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import org.slf4j.LoggerFactory

/**
 * DialogueManager - Handles all NPC dialogue interactions
 *
 * Responsibilities:
 * - NPC interaction events (right-click)
 * - Dialogue state management
 * - LLM-driven dialogue generation
 * - Fallback dialogue options
 * - Free-text dialogue (/npc talk)
 */
class DialogueManager(
    private val playerDialogues: ConcurrentHashMap<String, DialogueState>,
    private val dialogueCooldowns: ConcurrentHashMap<String, Long>,
    private val mcpEndpoint: String,
    private val cooldownMs: Long = 500L
) {
    private val playerLocks: ConcurrentHashMap<String, ReentrantLock> = ConcurrentHashMap()

    private fun getPlayerLock(playerId: String): ReentrantLock {
        return playerLocks.computeIfAbsent(playerId) { ReentrantLock() }
    }
    companion object {
        private val LOGGER = LoggerFactory.getLogger("MIIN-dialogue")
        private val gson = Gson()
        private val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
        private const val BRIDGE_URL = "http://localhost:5558/command"

        /**
         * Parse MCP response format and extract the actual result.
         * MCP returns: {"content": [{"type": "text", "text": "{...}"}]}
         * We need to unwrap to get the actual JSON object.
         */
        private fun parseMcpResponse(response: JsonObject?): JsonObject? {
            if (response == null) {
                LOGGER.info("[PARSE] MCP response is null")
                return null
            }

            try {
                LOGGER.info("[PARSE] Input keys: ${response.keySet()}")

                // Try standard format first: {"result": {...}}
                if (response.has("result")) {
                    val result = response.getAsJsonObject("result")
                    LOGGER.info("[PARSE] Has result field, keys: ${result.keySet()}")
                    
                    // Check if result is still MCP-wrapped
                    if (result.has("content")) {
                        LOGGER.info("[PARSE] Result contains content array, unwrapping...")
                        val content = result.getAsJsonArray("content")
                        if (content.size() > 0) {
                            val firstContent = content[0].asJsonObject
                            if (firstContent.has("text")) {
                                val textContent = firstContent.get("text").asString
                                val parsed = JsonParser.parseString(textContent).asJsonObject
                                LOGGER.info("[PARSE] ✅ Successfully parsed! Keys: ${parsed.keySet()}")
                                return parsed
                            }
                        }
                    }
                    return result
                }

                LOGGER.warn("[PARSE] ❌ Unexpected format, returning NULL")
                return null
            } catch (e: Exception) {
                LOGGER.error("[PARSE] ❌ Exception: ${e.message}", e)
                return null
            }
        }

        /**
         * Parse roll_check from JSON object
         */
        private fun parseRollCheck(optObj: JsonObject): RollCheck? {
            if (!optObj.has("roll_check")) return null

            return try {
                val rollCheckObj = optObj.getAsJsonObject("roll_check")
                RollCheck(
                    skill = rollCheckObj.get("skill")?.asString ?: return null,
                    difficulty = rollCheckObj.get("difficulty")?.asInt ?: return null,
                    advantage = rollCheckObj.get("advantage")?.asBoolean ?: false,
                    disadvantage = rollCheckObj.get("disadvantage")?.asBoolean ?: false
                )
            } catch (e: Exception) {
                LOGGER.warn("Failed to parse roll_check: ${e.message}")
                null
            }
        }

        /**
         * Roll a d20 die (returns 1-20)
         */
        private fun rollD20(): Int = Random.nextInt(1, 21)

        /**
         * Perform a D&D-style skill check
         *
         * @param rollCheck The roll check parameters
         * @return Pair of (total roll, success)
         */
        fun performSkillCheck(rollCheck: RollCheck): Pair<Int, Boolean> {
            val roll = when {
                rollCheck.advantage -> maxOf(rollD20(), rollD20())
                rollCheck.disadvantage -> minOf(rollD20(), rollD20())
                else -> rollD20()
            }

            val success = roll >= rollCheck.difficulty
            return Pair(roll, success)
        }
    }

    /** Register global entity interaction handler for NPCs */
    fun registerNpcInteractionHandler() {
        UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            // Only handle MIIN NPCs on server side
            if (entity is MIINNpcEntity && hand == Hand.MAIN_HAND && !world.isClient) {
                // Debounce to prevent duplicate dialogue triggers
                val key = "${player.uuidAsString}_${entity.uuid}"
                val now = System.currentTimeMillis()
                val lastInteraction = dialogueCooldowns[key] ?: 0L

                if (now - lastInteraction < cooldownMs) {
                    return@register ActionResult.SUCCESS  // Skip duplicate within cooldown
                }

                dialogueCooldowns[key] = now

                LOGGER.info("=== SERVER NPC INTERACTION ===")
                LOGGER.info("Player: ${player.name.string}, NPC: ${entity.npcId}")

                if (player is ServerPlayerEntity) {
                    // Start dialogue with NPC
                    startNpcDialogue(player, entity)
                }
                return@register ActionResult.SUCCESS
            }
            return@register ActionResult.PASS
        }
        LOGGER.info("NPC interaction handler registered")
    }

    /** Start or continue dialogue with an NPC */
    fun startNpcDialogue(player: ServerPlayerEntity, npc: MIINNpcEntity) {
        val playerName = player.name.string
        val lock = getPlayerLock(playerName)
        lock.lock()
        try {
            // Cancel any existing dialogue for this player
            playerDialogues[playerName]?.let { existing ->
                LOGGER.warn("[DIALOGUE-STATE] Player: $playerName, Action: CANCEL, Reason: New dialogue with ${npc.npcId}")
                existing.pendingRequest?.cancel(true)
            }

            LOGGER.info("[DIALOGUE-STATE] Player: $playerName, Action: START, NPC: ${npc.npcId}, Thread: ${Thread.currentThread().name}")

            // Create dialogue state
            val state = DialogueState(npcId = npc.npcId, npcName = npc.npcName, npcEntity = npc)
            playerDialogues[playerName] = state

            // Send header
            player.sendMessage(Text.literal(""), false)
            player.sendMessage(Text.literal("§6═══════ §e§l${npc.npcName} §6═══════"), false)

            // Start LLM dialogue
            startLlmDialogue(player, state)
        } finally {
            lock.unlock()
        }
    }

    /** Start LLM-driven dialogue */
    private fun startLlmDialogue(player: ServerPlayerEntity, state: DialogueState) {
        val playerName = player.name.string
        val server = player.entityWorld.server

        state.pendingRequest = CompletableFuture.runAsync {
            try {
                val args = JsonObject().apply {
                    addProperty("npc", state.npcId)  // MCP expects "npc" not "npc_id"
                    addProperty("player", playerName)  // MCP expects "player" not "player_name"
                }

                val response = sendMcpToolCall("minecraft_dialogue_start_llm", args)

                // Parse MCP response format: {"content": [{"type": "text", "text": "{...}"}]}
                val result = parseMcpResponse(response)

                // DEBUG: Log what we actually got
                if (result != null) {
                    LOGGER.info("Parsed dialogue start result keys: ${result.keySet()}")
                    LOGGER.info("Full result: ${result.toString()}")
                    if (result.has("options")) {
                        LOGGER.info("Options found: ${result.getAsJsonArray("options").size()} options")
                    } else {
                        LOGGER.warn("NO OPTIONS FIELD in parsed result!")
                    }
                }

                if (result != null) {
                    // Check if response is an error
                    if (result.has("error")) {
                        val errorMsg = result.get("error")?.asString ?: "Unknown error"
                        LOGGER.warn("MCP dialogue error: $errorMsg")

                        server.execute {
                            val greeting = getNpcGreeting(state.npcId, state.npcName)
                            player.sendMessage(Text.literal("§f$greeting"), false)
                            player.sendMessage(Text.literal(""), false)
                            useFallbackOptions(player, state)
                        }
                        return@runAsync
                    }

                    val greeting = result.get("greeting")?.asString ?: getNpcGreeting(state.npcId, state.npcName)
                    val conversationId = result.get("conversation_id")?.asString ?: ""
                    val optionsArray = result.getAsJsonArray("options")

                    // NULL SAFETY: Check if options exist before iterating
                    if (optionsArray != null && optionsArray.size() > 0) {
                        server.execute {
                            state.conversationId = conversationId
                            player.sendMessage(Text.literal("§f$greeting"), false)
                            player.sendMessage(Text.literal(""), false)

                            state.options.clear()
                            optionsArray.forEach { opt ->
                                val optObj = opt.asJsonObject
                                state.options.add(
                                    DialogueOption(
                                        id = optObj.get("id").asInt,
                                        text = optObj.get("text").asString,
                                        tone = optObj.get("tone")?.asString ?: "neutral",
                                        rollCheck = parseRollCheck(optObj)
                                    )
                                )
                            }
                            sendDialogueOptions(player, state)
                        }
                    } else {
                        // Options missing or empty - use fallback
                        LOGGER.warn("MCP dialogue result missing options array")
                        server.execute {
                            player.sendMessage(Text.literal("§f$greeting"), false)
                            player.sendMessage(Text.literal(""), false)
                            useFallbackOptions(player, state)
                        }
                    }
                } else {
                    // Fallback
                    server.execute {
                        val greeting = getNpcGreeting(state.npcId, state.npcName)
                        player.sendMessage(Text.literal("§f$greeting"), false)
                        player.sendMessage(Text.literal(""), false)
                        useFallbackOptions(player, state)
                    }
                }
            } catch (e: Exception) {
                LOGGER.warn("Failed to start LLM dialogue: ${e.message}")
                server.execute {
                    val greeting = getNpcGreeting(state.npcId, state.npcName)
                    player.sendMessage(Text.literal("§f$greeting"), false)
                    player.sendMessage(Text.literal(""), false)
                    useFallbackOptions(player, state)
                }
            }
        }
    }

    /** Helper to send MCP tool call with retry */
    fun sendMcpToolCall(toolName: String, args: JsonObject, maxRetries: Int = 2): JsonObject? {
        val toolCall = JsonObject().apply {
            addProperty("tool", toolName)
            add("arguments", args)
        }

        var attempt = 0
        while (attempt <= maxRetries) {
            attempt++
            LOGGER.info("[MCP] Attempt $attempt/${maxRetries + 1} for tool: $toolName")

            val request = HttpRequest.newBuilder()
                .uri(URI.create(mcpEndpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30)) // Reduced from 100s
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(toolCall)))
                .build()

            try {
                val startTime = System.currentTimeMillis()
                LOGGER.info("[PERF] MCP call started: $toolName")
                
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                
                val endTime = System.currentTimeMillis()
                LOGGER.info("[PERF] MCP call completed: $toolName, Time: ${endTime - startTime}ms")

                if (response.statusCode() == 200) {
                    LOGGER.info("MCP response received: ${response.statusCode()}")
                    return JsonParser.parseString(response.body()).asJsonObject
                } else {
                    LOGGER.warn("MCP error: ${response.statusCode()} - ${response.body()}")
                    if (response.statusCode() < 500) return null // Don't retry client errors
                }
            } catch (e: java.net.http.HttpTimeoutException) {
                LOGGER.warn("[MCP] Timeout on attempt $attempt")
            } catch (e: Exception) {
                LOGGER.warn("[MCP] Request failed: ${e.message}")
            }

            if (attempt <= maxRetries) {
                try {
                    Thread.sleep(2000) // 2s delay between retries
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
        
        LOGGER.error("[MCP] All retries failed for tool: $toolName")
        return null
    }

    /** Fetch dialogue options from MCP service (Legacy/Fallback) */
    fun fetchDialogueOptions(player: ServerPlayerEntity, state: DialogueState) {
        val playerName = player.name.string
        val server = player.entityWorld.server

        Thread {
            try {
                val args = JsonObject().apply {
                    addProperty("npc", state.npcId)
                    addProperty("player", playerName)
                    addProperty("context", "greeting")
                }

                val response = sendMcpToolCall("minecraft_dialogue_options", args)

                if (response != null && response.has("result")) {
                    val result = response.getAsJsonObject("result")
                    if (result.has("options")) {
                        val optionsArray = result.getAsJsonArray("options")
                        state.options.clear()

                        optionsArray.forEach { opt ->
                            val optObj = opt.asJsonObject
                            state.options.add(
                                DialogueOption(
                                    id = optObj.get("id").asInt,
                                    text = optObj.get("text").asString,
                                    tone = optObj.get("tone")?.asString ?: "neutral"
                                )
                            )
                        }

                        server.execute { sendDialogueOptions(player, state) }
                        return@Thread
                    }
                }

                server.execute { useFallbackOptions(player, state) }
            } catch (e: Exception) {
                LOGGER.warn("Failed to fetch dialogue options: ${e.message}")
                server.execute { useFallbackOptions(player, state) }
            }
        }.start()
    }

    /** Use fallback dialogue options when MCP is unavailable */
    private fun useFallbackOptions(player: ServerPlayerEntity, state: DialogueState) {
        state.options.clear()

        // Generate context-aware fallback options based on NPC
        val fallbackOptions =
                when (state.npcId) {
                    "marina" ->
                            listOf(
                                    DialogueOption(
                                            1,
                                            "What's the best fishing spot around here?",
                                            "curious"
                                    ),
                                    DialogueOption(
                                            2,
                                            "Tell me about the ocean's secrets.",
                                            "friendly"
                                    ),
                                    DialogueOption(3, "Any rare catches lately?", "curious"),
                                    DialogueOption(4, "I should go. Farewell.", "neutral")
                            )
                    "sage" ->
                            listOf(
                                    DialogueOption(1, "What herbs grow in this forest?", "curious"),
                                    DialogueOption(
                                            2,
                                            "Can you teach me about nature spirits?",
                                            "friendly"
                                    ),
                                    DialogueOption(3, "I need healing supplies.", "neutral"),
                                    DialogueOption(4, "I must leave now. Goodbye.", "neutral")
                            )
                    "kira" ->
                            listOf(
                                    DialogueOption(1, "What monsters lurk nearby?", "curious"),
                                    DialogueOption(2, "Teach me combat techniques.", "aggressive"),
                                    DialogueOption(3, "I need protection gear.", "neutral"),
                                    DialogueOption(4, "Stay vigilant. I'm leaving.", "neutral")
                            )
                    "thane" ->
                            listOf(
                                    DialogueOption(
                                            1,
                                            "Can you help me build something?",
                                            "friendly"
                                    ),
                                    DialogueOption(2, "Tell me about redstone.", "curious"),
                                    DialogueOption(3, "What resources do you need?", "neutral"),
                                    DialogueOption(4, "I'll be going now.", "neutral")
                            )
                    "lyra" ->
                            listOf(
                                    DialogueOption(1, "What do you see in my aura?", "curious"),
                                    DialogueOption(2, "Tell me about the stars.", "friendly"),
                                    DialogueOption(
                                            3,
                                            "Help me with a creative project.",
                                            "friendly"
                                    ),
                                    DialogueOption(4, "Until next time.", "neutral")
                            )
                    else ->
                            listOf(
                                    DialogueOption(1, "Tell me about yourself.", "friendly"),
                                    DialogueOption(2, "What can you help me with?", "curious"),
                                    DialogueOption(3, "What's happening around here?", "curious"),
                                    DialogueOption(4, "Goodbye.", "neutral")
                            )
                }

        state.options.addAll(fallbackOptions)
        // Add generic quest option
        state.options.add(DialogueOption(99, "Do you have any work for me?", "curious"))
        sendDialogueOptions(player, state)
    }

    /** Send dialogue options to player */
    fun sendDialogueOptions(player: ServerPlayerEntity, state: DialogueState) {
        player.sendMessage(Text.literal("§7Choose a response (type /npc select <number>):"), false)

        state.options.forEachIndexed { index, option ->
            val num = index + 1
            val color = getToneColor(option.tone)
            
            val text = Text.literal("  §8[§f$num§8] $color${option.text}")
            
            player.sendMessage(text, false)
        }

        player.sendMessage(Text.literal(""), false)
        player.sendMessage(Text.literal("§8Type §f/npc action §8for Follow/Roam/Stay"), false)
        player.sendMessage(Text.literal("§6═════════════════════════════════"), false)
    }

    /** Get color code for dialogue tone */
    private fun getToneColor(tone: String): String {
        return when (tone.lowercase()) {
            "friendly" -> "§a"
            "aggressive" -> "§c"
            "flirty" -> "§d"
            "curious" -> "§b"
            "intimidating" -> "§6"
            else -> "§f"
        }
    }

    /** Handle player selecting a dialogue option */
    fun handleDialogueSelection(player: ServerPlayerEntity, optionNum: Int) {
        val playerName = player.name.string
        val lock = getPlayerLock(playerName)
        lock.lock()
        try {
            LOGGER.info("handleDialogueSelection: player=$playerName, option=$optionNum")
            LOGGER.info("Active dialogues: ${playerDialogues.keys}")

            val state = playerDialogues[playerName]

            if (state == null) {
                LOGGER.info("No dialogue state found for $playerName")
                player.sendMessage(
                    Text.literal("§7You're not in a conversation. Right-click an NPC to talk."),
                    false
                )
                return
            }

            LOGGER.info("Found dialogue state: npc=${state.npcId}, options=${state.options.size}")

            val optionIndex = optionNum - 1
            if (optionIndex !in state.options.indices) {
                player.sendMessage(
                    Text.literal("§cInvalid option. Choose 1-${state.options.size}."),
                    false
                )
                return
            }

            val option = state.options[optionIndex]
            LOGGER.info("[DIALOGUE-STATE] Player: $playerName, Action: UPDATE, NPC: ${state.npcId}, Option: $optionNum")
            LOGGER.info("Player $playerName selected option $optionNum: ${option.text}")

            // Check if this option requires a skill check
            var rollSuccess: Boolean? = null
            var rollTotal: Int? = null

            if (option.rollCheck != null) {
                val (roll, success) = performSkillCheck(option.rollCheck)
                rollSuccess = success
                rollTotal = roll

                // Show roll result to player
                val rollText = when {
                    option.rollCheck.advantage -> "§7[Roll with Advantage]"
                    option.rollCheck.disadvantage -> "§7[Roll with Disadvantage]"
                    else -> "§7[Roll]"
                }

                val successColor = if (success) "§a" else "§c"
                val successText = if (success) "SUCCESS" else "FAILURE"

                player.sendMessage(Text.literal(""), false)
                player.sendMessage(
                    Text.literal("$rollText §e${option.rollCheck.skill.uppercase()} Check (DC ${option.rollCheck.difficulty})"),
                    false
                )
                player.sendMessage(
                    Text.literal("§7You rolled: §f$roll §7→ $successColor$successText"),
                    false
                )
                player.sendMessage(Text.literal(""), false)

                LOGGER.info("$playerName rolled ${option.rollCheck.skill} check: $roll vs DC ${option.rollCheck.difficulty} = $successText")
            }

            // Show player's choice
            player.sendMessage(Text.literal("§7You: §f${option.text}"), false)

            // Check if this ends the conversation
            if (option.text.contains("goodbye", ignoreCase = true) ||
                option.text.contains("farewell", ignoreCase = true) ||
                option.text.contains("leaving", ignoreCase = true) ||
                option.text.contains("I should go", ignoreCase = true)
            ) {

                val farewell = getNpcFarewell(state.npcId, state.npcName)
                player.sendMessage(Text.literal("§e[${state.npcName}]§r $farewell"), false)
                playerDialogues.remove(playerName)
                return
            }

            // Handle Quest Request (ID 99)
            if (option.id == 99 || option.text.contains("work", ignoreCase = true)) {
                LOGGER.warn("[QUEST] Quest generation not yet implemented (Phase 5)")
                player.sendMessage(Text.literal("§e[${state.npcName}]§r I don't have any work available right now. Check back later!"), false)
                return
            }

            // Get NPC response via LLM
            val server = player.entityWorld.server
            state.pendingRequest = CompletableFuture.runAsync {
                try {
                    val args = JsonObject().apply {
                        addProperty("conversation_id", state.conversationId ?: "")
                        addProperty("npc", state.npcId)  // MCP expects "npc" not "npc_id"
                        addProperty("player", playerName)  // MCP expects "player" not "player_name"
                        addProperty("option_text", option.text)

                        // Add roll check result if present
                        if (rollSuccess != null && rollTotal != null && option.rollCheck != null) {
                            add("roll_result", JsonObject().apply {
                                addProperty("skill", option.rollCheck.skill)
                                addProperty("difficulty", option.rollCheck.difficulty)
                                addProperty("roll", rollTotal)
                                addProperty("success", rollSuccess)
                            })
                        }
                    }

                    val response = sendMcpToolCall("minecraft_dialogue_respond", args)
                    val result = parseMcpResponse(response)

                    if (result != null) {
                        // Check if response is an error
                        if (result.has("error")) {
                            val errorMsg = result.get("error")?.asString ?: "Unknown error"
                            LOGGER.warn("MCP dialogue respond error: $errorMsg")

                            server.execute {
                                val fallbackResponse = getNpcResponse(state.npcId, option)
                                player.sendMessage(Text.literal("§e[${state.npcName}]§r $fallbackResponse"), false)
                                fetchDialogueOptions(player, state)
                            }
                            return@runAsync
                        }

                        val npcResponse = result.get("npc_response")?.asString ?: getNpcResponse(state.npcId, option)
                        val ended = result.get("conversation_ended")?.asBoolean ?: false
                        val newOptions = result.getAsJsonArray("new_options")

                        // NULL SAFETY: Check if options exist before iterating
                        if (newOptions != null && newOptions.size() > 0) {
                            server.execute {
                                player.sendMessage(Text.literal("§e[${state.npcName}]§r $npcResponse"), false)

                                if (ended) {
                                    playerDialogues.remove(playerName)
                                } else {
                                    state.options.clear()
                                    newOptions.forEach { opt ->
                                        val optObj = opt.asJsonObject
                                        state.options.add(
                                            DialogueOption(
                                                id = optObj.get("id").asInt,
                                                text = optObj.get("text").asString,
                                                tone = optObj.get("tone")?.asString ?: "neutral",
                                                rollCheck = parseRollCheck(optObj)
                                            )
                                        )
                                    }
                                    sendDialogueOptions(player, state)
                                }
                            }
                        } else {
                            // No new options - end conversation or use fallback
                            LOGGER.warn("MCP dialogue respond result missing options array")
                            server.execute {
                                player.sendMessage(Text.literal("§e[${state.npcName}]§r $npcResponse"), false)
                                if (ended) {
                                    playerDialogues.remove(playerName)
                                } else {
                                    fetchDialogueOptions(player, state)
                                }
                            }
                        }
                    } else {
                        // Fallback
                        server.execute {
                            val fallbackResponse = getNpcResponse(state.npcId, option)
                            player.sendMessage(Text.literal("§e[${state.npcName}]§r $fallbackResponse"), false)
                            fetchDialogueOptions(player, state)
                        }
                    }
                } catch (e: Exception) {
                    LOGGER.warn("Failed to get LLM response: ${e.message}")
                    server.execute {
                        val fallbackResponse = getNpcResponse(state.npcId, option)
                        player.sendMessage(Text.literal("§e[${state.npcName}]§r $fallbackResponse"), false)
                        fetchDialogueOptions(player, state)
                    }
                }
            }
        } finally {
            lock.unlock()
        }
    }

    /** Get NPC response to player choice (fallback) */
    private fun getNpcResponse(npcId: String, option: DialogueOption): String {
        return when {
            option.text.contains("fishing", ignoreCase = true) ->
                    "The cove to the east has the best catches at dawn. Watch for the shimmer on the water!"
            option.text.contains("herb", ignoreCase = true) ||
                    option.text.contains("plant", ignoreCase = true) ->
                    "Moonpetals bloom near the old oak. They're most potent under a full moon."
            option.text.contains("monster", ignoreCase = true) ->
                    "Creepers have been more active in the caves. Stay close to torchlight."
            option.text.contains("build", ignoreCase = true) ||
                    option.text.contains("redstone", ignoreCase = true) ->
                    "Bring me iron and redstone, and I'll show you mechanisms that'll make your jaw drop."
            option.text.contains("aura", ignoreCase = true) ||
                    option.text.contains("star", ignoreCase = true) ->
                    "Your aura shifts like aurora... You've been through much, haven't you?"
            else -> "Interesting... Tell me more about what brings you here."
        }
    }

    /** Get farewell message for NPC */
    private fun getNpcFarewell(npcId: String, npcName: String): String {
        return when (npcId) {
            "marina" -> "Fair winds and following seas!"
            "sage" -> "May the forest guide your path."
            "kira" -> "Stay sharp out there."
            "thane" -> "Build well, friend."
            "lyra" -> "May the stars light your way!"
            "grimm" -> "*nods nervously* Watch the shadows..."
            else -> "Until we meet again."
        }
    }

    /** Get greeting for NPC by ID */
    private fun getNpcGreeting(npcId: String, npcName: String): String {
        return when (npcId) {
            "marina" -> "Ahoy there! The tides brought you to me today..."
            "vex" -> "*looks through you* You walk between moments, I see..."
            "rowan" -> "A customer! Let's see what we can arrange..."
            "kira" -> "Stay alert. What brings you to me?"
            "sage" -> "*smiles gently* The forest whispered you'd come..."
            "thane" -> "Hm. Need something built right?"
            "lyra" -> "Oh! Your aura has such interesting colors today..."
            "grimm" -> "*glances around nervously* Quick, what do you need?"
            else -> "Greetings, traveler. What brings you here?"
        }
    }

    /** Send free-text talk request to NPC */
    fun sendNpcTalkRequest(player: ServerPlayerEntity, npcId: String, npcName: String, message: String) {
        val playerName = player.name.string
        val server = player.entityWorld.server

        // Show player's message
        player.sendMessage(Text.literal("§7You → §e$npcName§7: §f$message"), false)
        player.sendMessage(Text.literal("§6Waiting for response..."), false)

        Thread {
            try {
                val args = JsonObject().apply {
                    addProperty("player", playerName)
                    addProperty("npc", npcId)
                    addProperty("message", message)
                    addProperty("request_suggestions", true)
                }

                val response = sendMcpToolCall("minecraft_npc_talk_with_suggestions", args)
                val result = parseMcpResponse(response)

                if (result != null) {
                    val npcResponse = result.get("response")?.asString ?: "..."
                    val suggestions = if (result.has("suggestions")) result.getAsJsonArray("suggestions") else null

                    server.execute {
                        player.sendMessage(Text.literal("§e$npcName§7: §f$npcResponse"), false)

                        if (suggestions != null && suggestions.size() > 0) {
                            player.sendMessage(Text.literal(""), false)
                            player.sendMessage(Text.literal("§7Suggested responses:"), false)

                            val npcEntity = NpcManager.getNpc(npcName)

                            if (npcEntity != null) {
                                val state = DialogueState(
                                    npcId = npcEntity.npcId,
                                    npcName = npcEntity.npcName,
                                    npcEntity = npcEntity,
                                    conversationId = null
                                )

                                suggestions.forEachIndexed { index, suggestion ->
                                    val text = suggestion.asString
                                    val num = index + 1
                                    player.sendMessage(
                                        Text.literal("  §8[§f$num§8] §b$text"),
                                        false
                                    )
                                    state.options.add(DialogueOption(num, text, "neutral"))
                                }
                                playerDialogues[playerName] = state
                            }
                        }
                    }
                } else {
                    server.execute {
                        player.sendMessage(Text.literal("§cNPC is silent..."), false)
                    }
                }
            } catch (e: Exception) {
                LOGGER.warn("Failed to send talk request: ${e.message}")
                server.execute {
                    player.sendMessage(Text.literal("§cFailed to reach NPC."), false)
                }
            }
        }.start()
    }

    /** Check if player has an active dialogue */
    fun hasActiveDialogue(playerName: String): Boolean {
        return playerDialogues.containsKey(playerName)
    }

    /** Get the NPC ID the player is currently talking to */
    fun getActiveNpc(playerName: String): String? {
        return playerDialogues[playerName]?.npcId
    }
}
