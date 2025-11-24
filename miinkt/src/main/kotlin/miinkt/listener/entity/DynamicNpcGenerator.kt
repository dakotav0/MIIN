package miinkt.listener.entity

import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.biome.Biome
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import miinkt.listener.MIINListener
import com.google.gson.JsonObject
import java.util.concurrent.CompletableFuture

object DynamicNpcGenerator {
    private val LOGGER = LoggerFactory.getLogger("DynamicNpcGenerator")

    // Track chunks that have been processed for NPC spawns
    private val processedChunks = ConcurrentHashMap<String, Long>()

    // Track dynamically spawned NPCs
    private val dynamicNpcs = ConcurrentHashMap<String, MIINNpcEntity>()

    // Configuration
    private const val SPAWN_CHANCE_PER_CHUNK = 0.05  // 5% chance per new chunk
    private const val MIN_DISTANCE_FROM_SPAWN = 100.0
    private const val MAX_NPCS_PER_AREA = 3  // Max NPCs in 128 block radius

    fun initialize(server: MinecraftServer) {
        LOGGER.info("Dynamic NPC Generator initialized")
    }

    private var tickCounter = 0

    fun tick(server: MinecraftServer) {
        tickCounter++
        if (tickCounter < 20) return // Check every second (20 ticks)
        tickCounter = 0

        for (player in server.playerManager.playerList) {
            val world = player.entityWorld as ServerWorld
            val chunkPos = player.chunkPos
            
            // Check current chunk and surrounding chunks (3x3 area)
            for (x in -1..1) {
                for (z in -1..1) {
                    val nearbyChunk = ChunkPos(chunkPos.x + x, chunkPos.z + z)
                    onNewChunkExplored(world, nearbyChunk)
                }
            }
        }
    }

    fun onNewChunkExplored(world: ServerWorld, chunkPos: ChunkPos) {
        val chunkKey = "${world.registryKey.value}:${chunkPos.x}:${chunkPos.z}"

        // Skip if chunk already processed
        if (processedChunks.containsKey(chunkKey)) return

        processedChunks[chunkKey] = System.currentTimeMillis()

        // Roll spawn chance
        if (Random.nextDouble() > SPAWN_CHANCE_PER_CHUNK) {
            return
        }

        // Try to find a valid spawn position in this chunk
        val centerPos = chunkPos.getCenterAtY(world.seaLevel)
        // Simple check: surface spawn
        val surfacePos = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, centerPos)
        
        checkSpawnOpportunity(world, surfacePos)
    }

    private fun checkSpawnOpportunity(world: ServerWorld, pos: BlockPos) {
        // Check spawn conditions
        if (!isValidSpawnLocation(world, pos)) return
        if (tooManyNearbyNpcs(world, pos)) return

        // Generate NPC asynchronously via MCP
        generateAndSpawnNpc(world, pos)
    }

    private fun isValidSpawnLocation(world: ServerWorld, pos: BlockPos): Boolean {
        // Check distance from spawn
        val spawnPos = world.spawnPoint.pos
        val distance = pos.getSquaredDistance(spawnPos)
        if (distance < MIN_DISTANCE_FROM_SPAWN * MIN_DISTANCE_FROM_SPAWN) return false

        // Check valid ground block
        val groundBlock = world.getBlockState(pos.down())
        if (!groundBlock.isSolidBlock(world, pos.down())) return false

        // Check air above
        val aboveBlock = world.getBlockState(pos)
        if (!aboveBlock.isAir) return false

        return true
    }

    private fun tooManyNearbyNpcs(world: ServerWorld, pos: BlockPos): Boolean {
        val nearby = world.getEntitiesByClass(
            MIINNpcEntity::class.java,
            Box.of(pos.toCenterPos(), 128.0, 128.0, 128.0)
        ) { true }

        return nearby.size >= MAX_NPCS_PER_AREA
    }

    private fun generateAndSpawnNpc(world: ServerWorld, pos: BlockPos) {
        LOGGER.info("Generating dynamic NPC at ${pos.x}, ${pos.y}, ${pos.z}")

        // Get biome context
        val biome = world.getBiome(pos)
        val biomeKey = biome.key.get().value.path
        val archetype = getBiomeAppropriateArchetype(biomeKey)
        val dimension = world.registryKey.value.toString()

        // Run async to avoid blocking server thread
        CompletableFuture.runAsync {
            try {
                val args = JsonObject().apply {
                    addProperty("template_id", archetype)
                    addProperty("x", pos.x)
                    addProperty("y", pos.y)
                    addProperty("z", pos.z)
                    addProperty("dimension", dimension)
                    addProperty("biome", biomeKey)
                }

                val response = MIINListener.getDialogueManager().sendMcpToolCall("minecraft_generate_dynamic_npc", args)

                if (response != null && !response.has("error")) {
                    // Parse nested MCP response: response -> result -> content[0] -> text -> JSON
                    var npcData: JsonObject? = null
                    
                    try {
                        if (response.has("result")) {
                            val resultObj = response.getAsJsonObject("result")
                            if (resultObj.has("content")) {
                                val contentArray = resultObj.getAsJsonArray("content")
                                if (contentArray.size() > 0) {
                                    val text = contentArray.get(0).asJsonObject.get("text").asString
                                    LOGGER.info("[DynamicNpcGenerator] Raw NPC Data from Python: $text")
                                    npcData = com.google.gson.JsonParser.parseString(text).asJsonObject
                                }
                            }
                        }
                    } catch (e: Exception) {
                        LOGGER.warn("Failed to parse MCP response structure: ${e.message}")
                    }

                    if (npcData != null) {
                        if (npcData.has("error")) {
                            LOGGER.error("[DynamicNpcGenerator] Python script returned error: ${npcData.get("error").asString}")
                            return@runAsync
                        }

                        // Parse response
                        val npcId = if (npcData.has("id")) npcData.get("id").asString else "unknown_${System.currentTimeMillis()}"
                        val name = if (npcData.has("name")) npcData.get("name").asString else "Wanderer"
                        val skin = if (npcData.has("skin")) npcData.get("skin").asString else "$archetype.png"
                        val race = if (npcData.has("race")) npcData.get("race").asString else "human"
                        val personality = if (npcData.has("personality")) npcData.get("personality").asString else ""
                        val profession = if (npcData.has("profession")) npcData.get("profession").asString else archetype

                        LOGGER.info("[DynamicNpcGenerator] Spawning NPC: $name ($npcId)")

                        // Schedule spawn on server thread
                        world.server.execute {
                            NpcManager.spawnNpc(world, npcId, name, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), skin, archetype, race, personality, profession)
                            NpcManager.saveSpawnedNpcIds(world.server)
                        }
                    } else {
                        LOGGER.warn("MCP response did not contain valid NPC data")
                    }
                } else {
                    LOGGER.warn("Failed to generate NPC: ${response?.get("error")?.asString ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                LOGGER.error("Error generating dynamic NPC", e)
            }
        }
    }

    private fun getBiomeAppropriateArchetype(biome: String): String {
        return when {
            biome.contains("ocean") || biome.contains("beach") -> "fisher"
            biome.contains("mountain") || biome.contains("hill") -> "miner"
            biome.contains("forest") -> "hunter"
            biome.contains("desert") -> "nomad"
            biome.contains("plains") -> "farmer"
            biome.contains("village") -> "merchant"
            else -> "wanderer"
        }
    }


}
