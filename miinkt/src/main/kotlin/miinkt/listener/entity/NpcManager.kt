/**
 * NPC Manager - Spawns and manages MIIN NPCs
 */

package miinkt.listener.entity

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import java.io.File

object NpcManager {
    private val LOGGER = LoggerFactory.getLogger("MIIN-npc-manager")
    private val gson = Gson()
    private val spawnedNpcs = mutableMapOf<String, MIINNpcEntity>()
    private var currentWorldName: String = ""

    /**
     * Clear the spawned NPCs map - call when server stops or world changes
     */
    fun clearSpawnedNpcs() {
        spawnedNpcs.clear()
        currentWorldName = ""
        LOGGER.info("Cleared spawned NPCs map")
    }

    /**
     * Get the spawn tracking file path for a world
     */
    private fun getSpawnTrackingFile(server: MinecraftServer): File {
        val worldDir = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toFile()
        return File(worldDir, "MIIN_spawned_npcs.json")
    }

    /**
     * Load list of NPCs already spawned in this world
     */
    private fun loadSpawnedNpcIds(server: MinecraftServer): Set<String> {
        val file = getSpawnTrackingFile(server)
        if (!file.exists()) {
            return emptySet()
        }

        return try {
            val content = file.readText()
            val array = JsonParser.parseString(content).asJsonArray
            array.map { it.asString }.toSet()
        } catch (e: Exception) {
            LOGGER.warn("Error reading spawn tracking file: ${e.message}")
            emptySet()
        }
    }

    /**
     * Save the list of spawned NPC IDs for this world
     */
    private fun saveSpawnedNpcIds(server: MinecraftServer) {
        val file = getSpawnTrackingFile(server)
        try {
            val array = JsonArray()
            spawnedNpcs.keys.forEach { array.add(it) }
            file.writeText(gson.toJson(array))
            LOGGER.debug("Saved ${spawnedNpcs.size} NPC IDs to tracking file")
        } catch (e: Exception) {
            LOGGER.error("Error saving spawn tracking file: ${e.message}")
        }
    }

    /**
     * Load NPC config and spawn NPCs
     */
    fun loadAndSpawnNpcs(server: MinecraftServer, configPath: String) {
        try {
            val configFile = File(configPath)
            if (!configFile.exists()) {
                LOGGER.warn("NPC config not found at $configPath")
                return
            }

            val config = gson.fromJson(configFile.readText(), JsonObject::class.java)
            val npcs = config.getAsJsonArray("npcs")

            // Get world spawn point for relative positioning
            val spawnPos = server.overworld.spawnPoint.pos
            LOGGER.info("World spawn point: ${spawnPos.x}, ${spawnPos.y}, ${spawnPos.z}")

            // Load NPCs already spawned in this world from tracking file
            val alreadySpawned = loadSpawnedNpcIds(server)
            LOGGER.info("Found ${alreadySpawned.size} previously spawned NPCs in tracking file")

            // Note: We rely solely on tracking file to prevent duplication issues
            // when entities load from disk after SERVER_STARTED event fires.
            // Use /npc cleanup command if duplicates occur.

            for (npcElement in npcs) {
                val npc = npcElement.asJsonObject
                
                // Handle missing ID by generating one from name
                val name = npc.get("name").asString
                val npcId = if (npc.has("id")) {
                    npc.get("id").asString
                } else {
                    name.lowercase().replace(" ", "_")
                }

                // Handle location or spawn_location
                val location = if (npc.has("spawn_location")) {
                    npc.getAsJsonObject("spawn_location")
                } else {
                    npc.getAsJsonObject("location")
                }

                // Parse new context fields
                val archetype = if (npc.has("archetype")) npc.get("archetype").asString else ""
                val race = if (npc.has("race")) npc.get("race").asString else ""
                val personality = if (npc.has("personality")) npc.get("personality").asString else ""
                val profession = if (npc.has("profession")) npc.get("profession").asString else ""

                // Coordinates are relative to spawn unless "absolute" is true
                val isAbsolute = location.has("absolute") && location.get("absolute").asBoolean
                val offsetX = location.get("x").asDouble
                val offsetY = location.get("y").asDouble
                val offsetZ = location.get("z").asDouble
                val dimension = location.get("dimension").asString

                // Calculate actual position
                val x = if (isAbsolute) offsetX else spawnPos.x + offsetX
                val y = if (isAbsolute) offsetY else spawnPos.y + offsetY
                val z = if (isAbsolute) offsetZ else spawnPos.z + offsetZ

                // Get skin path if specified
                val skinPath = if (npc.has("skin")) {
                    npc.get("skin").asString
                } else {
                    "${npcId}.png"  // Default to npc_id.png
                }

                // Check if this NPC already exists (either in tracking file or found as entity)
                if (alreadySpawned.contains(npcId)) {
                    LOGGER.info("NPC $name ($npcId) already exists in world, skipping spawn")
                    continue
                }

                // Get the appropriate world
                val world = getWorldForDimension(server, dimension)
                if (world != null) {
                    spawnNpc(world, npcId, name, x, y, z, skinPath, archetype, race, personality, profession)
                    LOGGER.info("NPC $name spawned at relative offset ($offsetX, $offsetY, $offsetZ) from spawn")
                } else {
                    LOGGER.warn("Could not find world for dimension: $dimension")
                }
            }

            // Save tracking file with all spawned NPC IDs
            saveSpawnedNpcIds(server)
            LOGGER.info("Loaded and spawned ${spawnedNpcs.size} NPCs")

        } catch (e: Exception) {
            LOGGER.error("Error loading NPC config", e)
        }
    }

    /**
     * Spawn a single NPC with ground-level detection
     */
    private fun spawnNpc(
        world: ServerWorld,
        npcId: String,
        name: String,
        x: Double,
        y: Double,
        z: Double,
        skinPath: String,
        archetype: String = "",
        race: String = "",
        personality: String = "",
        profession: String = ""
    ) {
        // Check if NPC already spawned
        if (spawnedNpcs.containsKey(npcId)) {
            LOGGER.debug("NPC $npcId already spawned")
            return
        }

        // Find safe ground level at this X/Z position
        val safeY = findSafeSpawnY(world, x.toInt(), y.toInt(), z.toInt())

        val entity = MIINNpcEntity(NpcRegistry.MIIN_NPC, world)
        entity.setNpcIdentity(npcId, name, skinPath, archetype, race, personality, profession)
        entity.setPosition(x, safeY.toDouble(), z)

        // Set default behavior to roaming
        entity.setBehavior(MIINNpcEntity.BehaviorMode.ROAMING, 8.0)

        world.spawnEntity(entity)
        spawnedNpcs[npcId] = entity

        LOGGER.info("Spawned NPC: $name ($npcId) at $x, $safeY, $z with ROAMING behavior")
    }

    /**
     * Find safe Y coordinate for spawning (on solid ground with air above)
     */
    private fun findSafeSpawnY(world: ServerWorld, x: Int, startY: Int, z: Int): Int {
        // Start from the suggested Y and search up/down for valid ground
        val pos = BlockPos(x, startY, z)

        // First try: search upward from suggested Y
        for (y in startY..world.topYInclusive) {
            val groundPos = BlockPos(x, y, z)
            val abovePos = BlockPos(x, y + 1, z)
            val above2Pos = BlockPos(x, y + 2, z)

            val groundState = world.getBlockState(groundPos)
            val aboveState = world.getBlockState(abovePos)
            val above2State = world.getBlockState(above2Pos)

            // Need solid ground with 2 blocks of air above
            if (groundState.isSolidBlock(world, groundPos) &&
                aboveState.isAir && above2State.isAir) {
                return y + 1
            }
        }

        // Fallback: search downward
        for (y in startY downTo world.bottomY) {
            val groundPos = BlockPos(x, y, z)
            val abovePos = BlockPos(x, y + 1, z)
            val above2Pos = BlockPos(x, y + 2, z)

            val groundState = world.getBlockState(groundPos)
            val aboveState = world.getBlockState(abovePos)
            val above2State = world.getBlockState(above2Pos)

            if (groundState.isSolidBlock(world, groundPos) &&
                aboveState.isAir && above2State.isAir) {
                return y + 1
            }
        }

        // Last resort: use world spawn Y + 1
        return world.spawnPoint.pos.y + 1
    }

    /**
     * Find existing MIIN NPCs in all worlds and register them
     */
    private fun findExistingNpcs(server: MinecraftServer): Set<String> {
        val foundIds = mutableSetOf<String>()

        for (world in server.worlds) {
            val entities = world.iterateEntities()
            for (entity in entities) {
                if (entity is MIINNpcEntity) {
                    val npcId = entity.npcId
                    if (npcId != "unknown") {
                        foundIds.add(npcId)
                        spawnedNpcs[npcId] = entity
                        LOGGER.info("Found existing NPC: ${entity.npcName} ($npcId) at ${entity.blockPos}")
                    }
                }
            }
        }

        return foundIds
    }

    /**
     * Get world for dimension identifier
     */
    private fun getWorldForDimension(server: MinecraftServer, dimension: String): ServerWorld? {
        return when (dimension) {
            "minecraft:overworld" -> server.overworld
            "minecraft:the_nether" -> server.getWorld(net.minecraft.world.World.NETHER)
            "minecraft:the_end" -> server.getWorld(net.minecraft.world.World.END)
            else -> server.overworld
        }
    }

    /**
     * Remove all spawned NPCs
     */
    fun removeAllNpcs() {
        spawnedNpcs.values.forEach { it.discard() }
        spawnedNpcs.clear()
        LOGGER.info("Removed all spawned NPCs")
    }

    /**
     * Get NPC by ID
     */
    fun getNpc(npcId: String): MIINNpcEntity? {
        return spawnedNpcs[npcId]
    }

    /**
     * List all spawned NPC IDs
     */
    fun getSpawnedNpcIds(): List<String> {
        return spawnedNpcs.keys.toList()
    }
}
