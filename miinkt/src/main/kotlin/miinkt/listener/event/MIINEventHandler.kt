package miinkt.listener.event

import com.google.gson.Gson
import com.google.gson.JsonObject
import miinkt.listener.state.PlayerState
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap

class MIINEventHandler(
    private val mcpEndpoint: String,
    private val playerStates: ConcurrentHashMap<String, PlayerState>
) {
    private val logger = LoggerFactory.getLogger("MIINkt-event-handler")
    private val gson = Gson()
    private val httpClient = HttpClient.newBuilder().build()
    
    // Rate limiting
    private var lastEventTime = 0L
    companion object {
        private const val MIN_EVENT_INTERVAL_MS = 50L // Max 20 events/second
    }

    fun registerEvents() {
        registerChatListener()
        registerConnectionListener()
        registerPlayerStateMonitor()
    }

    /** Chat event forwarding - send player chat messages to MCP */
    private fun registerChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
            val chatMessage = message.content.string

            // Forward chat to MCP for context awareness
            sendMCPEvent(
                    "player_chat",
                    JsonObject().apply {
                        addProperty("playerName", sender.name.string)
                        addProperty("playerId", sender.uuidAsString)
                        addProperty("message", chatMessage)
                        addProperty("timestamp", System.currentTimeMillis())
                    }
            )

            logger.debug("Chat from ${sender.name.string}: $chatMessage")
        }
    }

    /** Connection event forwarding */
    private fun registerConnectionListener() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            sendMCPEvent(
                    "session_start",
                    JsonObject().apply {
                        addProperty("playerName", player.name.string)
                        addProperty("playerId", player.uuidAsString)
                        addProperty("timestamp", System.currentTimeMillis())
                    }
            )
            logger.info("Player joined: ${player.name.string}")
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            sendMCPEvent(
                    "session_end",
                    JsonObject().apply {
                        addProperty("playerName", player.name.string)
                        addProperty("playerId", player.uuidAsString)
                        addProperty("timestamp", System.currentTimeMillis())
                    }
            )
            logger.info("Player left: ${player.name.string}")
        }
    }

    /** Monitor player state changes (position, biome, etc.) */
    private fun registerPlayerStateMonitor() {
        var tickCounter = 0

        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickCounter++

            // Every second (20 ticks), check for state changes
            if (tickCounter >= 20) {
                tickCounter = 0

                server.playerManager.playerList.forEach { player ->
                    val state = getOrCreatePlayerState(player)
                    state.update(player)

                    if (state.hasSignificantChange()) {
                        sendPlayerStateEvent(player, state)
                        state.markSent()
                    }
                }
            }
        }
    }

    private fun sendPlayerStateEvent(player: ServerPlayerEntity, state: PlayerState) {
        val data =
                JsonObject().apply {
                    addProperty("playerName", player.name.string)
                    addProperty("playerId", player.uuidAsString)
                    addProperty("x", state.lastX)
                    addProperty("y", state.lastY)
                    addProperty("z", state.lastZ)
                    addProperty("dimension", state.lastDimension)
                    addProperty("biome", state.lastBiome)
                    addProperty("health", state.lastHealth)
                    addProperty("timeOfDay", getTimeOfDay(player))
                    addProperty("weather", getWeatherState(player))
                    addProperty("timestamp", System.currentTimeMillis())
                }

        sendMCPEvent("player_state", data)
    }

    /** Get or create player state tracker */
    private fun getOrCreatePlayerState(player: ServerPlayerEntity): PlayerState {
        val playerId = player.uuidAsString
        return playerStates.computeIfAbsent(playerId) { PlayerState(playerId) }
    }

    /** Get weather state */
    private fun getWeatherState(player: ServerPlayerEntity): String {
        return when {
            player.entityWorld.isThundering -> "thundering"
            player.entityWorld.isRaining -> "raining"
            else -> "clear"
        }
    }

    /** Get time of day (dawn, day, dusk, night) */
    private fun getTimeOfDay(player: ServerPlayerEntity): String {
        val time = player.entityWorld.timeOfDay % 24000
        return when {
            time < 450 -> "dawn"
            time < 11616 -> "day"
            time < 13800 -> "dusk"
            else -> "night"
        }
    }

    /** Generic MCP event sender with rate limiting */
    fun sendMCPEvent(eventType: String, data: JsonObject) {
        // Rate limiting
        val now = System.currentTimeMillis()
        if (now - lastEventTime < MIN_EVENT_INTERVAL_MS) {
            return // Skip event to prevent spam
        }
        lastEventTime = now

        try {
            val toolCall =
                    JsonObject().apply {
                        addProperty("tool", "minecraft_track_event")
                        add(
                                "arguments",
                                JsonObject().apply {
                                    addProperty("eventType", eventType)
                                    add("data", data)
                                }
                        )
                    }

            val request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(mcpEndpoint))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(toolCall)))
                            .build()

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            logger.error("Error sending MCP event: $eventType", e)
        }
    }
}
