# PHASE 0: CRITICAL INFRASTRUCTURE FIXES

**Priority**: URGENT - Must complete before continuing feature development
**Total Estimated Time**: 9-13 hours
**Status**: Not Started

---

## Overview

Critical infrastructure issues were identified in `miinkt/runtimebuglog2.txt` showing significant system degradation compared to baseline `runtimebuglog.txt`. These issues must be resolved before continuing with Phase 1+ feature development.

### Key Problems Identified

1. **Dialogue State Race Condition**: Player clicks NPC "Rowan" but receives dialogue data for "Kira"
2. **MCP Request Timeouts**: 60+ second timeouts causing dialogue failures
3. **Quest Generation Hangs**: 3+ minute hangs with no response or error handling
4. **Cross-NPC Chat Bleeding**: Marina's idle chatter appears during other NPC conversations
5. **Performance Degradation**: 51s average response time (vs 14-17s baseline)
6. **NPC ID Returns "unknown" on First Interaction**: DataTracker synchronization delay causes first NPC click to fail
7. **Dynamic NPC Generation Missing**: No system to procedurally generate NPCs during gameplay

---

## 0.1: Thread Safety for Dialogue State Manager

**Time Estimate**: 2-3 hours
**Priority**: CRITICAL

### Problem

From `runtimebuglog2.txt` lines 117-149:
- Player clicks Rowan NPC at 06:26:30 (Thread-56)
- Thread-53 (still processing Kira dialogue from 06:25:12) completes at 06:26:36
- Thread-53's Kira data overwrites dialogue state, causing:
  - Player sees Kira's dialogue options instead of Rowan's
  - "Invalid option. Choose 1-0" errors when player tries to respond

### Root Cause

Async dialogue state management without proper locking/synchronization. Multiple threads can:
- Read and write dialogue state simultaneously
- Overwrite active dialogue sessions
- Create orphaned conversation IDs

### Implementation Tasks

#### Task 0.1.1: Add Thread-Safe Dialogue State Manager (1.5h)

**File**: `miinkt/src/main/kotlin/miinkt/listener/dialogue/DialogueManager.kt`

1. **Replace HashMap with ConcurrentHashMap**:
   ```kotlin
   // Before:
   private val activeDialogues: MutableMap<UUID, DialogueState> = mutableMapOf()

   // After:
   private val activeDialogues: ConcurrentHashMap<UUID, DialogueState> = ConcurrentHashMap()
   ```

2. **Add Mutex/Lock per Player**:
   ```kotlin
   private val playerLocks: ConcurrentHashMap<UUID, ReentrantLock> = ConcurrentHashMap()

   private fun getPlayerLock(playerId: UUID): ReentrantLock {
       return playerLocks.computeIfAbsent(playerId) { ReentrantLock() }
   }
   ```

3. **Wrap Critical Sections**:
   ```kotlin
   fun startDialogue(playerId: UUID, npcId: String) {
       val lock = getPlayerLock(playerId)
       lock.lock()
       try {
           // Cancel any existing dialogue for this player
           activeDialogues[playerId]?.let { existing ->
               logger.warn("Cancelling existing dialogue with ${existing.npcId} for player $playerId")
               // Cancel pending async requests
           }

           // Start new dialogue
           val state = DialogueState(npcId, conversationId, timestamp)
           activeDialogues[playerId] = state

           // Make async MCP call
       } finally {
           lock.unlock()
       }
   }
   ```

4. **Add Dialogue State Validation**:
   ```kotlin
   fun updateDialogueState(playerId: UUID, npcId: String, data: DialogueData) {
       val lock = getPlayerLock(playerId)
       lock.lock()
       try {
           val current = activeDialogues[playerId]

           // Validate this update is for the current dialogue
           if (current == null || current.npcId != npcId) {
               logger.warn("Stale dialogue update for $npcId ignored (current: ${current?.npcId})")
               return
           }

           current.updateData(data)
       } finally {
           lock.unlock()
       }
   }
   ```

#### Task 0.1.2: Add Request Cancellation (1h)

1. **Track Pending Async Requests**:
   ```kotlin
   data class DialogueState(
       val npcId: String,
       val conversationId: String,
       val timestamp: Long,
       var pendingRequest: CompletableFuture<*>? = null,
       var options: List<DialogueOption> = emptyList()
   )
   ```

2. **Cancel on New Interaction**:
   ```kotlin
   fun startDialogue(playerId: UUID, npcId: String) {
       lock.lock()
       try {
           activeDialogues[playerId]?.pendingRequest?.cancel(true)

           val future = mcpClient.callToolAsync(...)
           val state = DialogueState(npcId, conversationId, timestamp, future)
           activeDialogues[playerId] = state
       } finally {
           lock.unlock()
       }
   }
   ```

#### Task 0.1.3: Add Logging and Monitoring (30m)

```kotlin
logger.info("[DIALOGUE-STATE] Player: $playerId, Action: START, NPC: $npcId, Thread: ${Thread.currentThread().name}")
logger.info("[DIALOGUE-STATE] Player: $playerId, Action: UPDATE, NPC: $npcId, Valid: $isValid")
logger.info("[DIALOGUE-STATE] Player: $playerId, Action: CANCEL, Reason: $reason")
```

### Testing Checklist

- [ ] Rapidly click between multiple NPCs - should cancel previous dialogue
- [ ] Verify logs show cancellation messages
- [ ] Confirm no "Invalid option" errors
- [ ] Test with 2+ players interacting with NPCs simultaneously
- [ ] Monitor thread count and ensure no leaks

---

## 0.2: AI Idle Chatter System Fix

**Time Estimate**: 1 hour
**Priority**: HIGH

### Problem

From `runtimebuglog2.txt` lines 34-35, 76-77, 108-109:
- Marina sends idle chatter messages while player is in dialogue with Kira/Sage
- Messages appear in chat: `§6[MIINkt AI]§r [Marina Tidecaller] Busy day? I can see you've been active.`
- Disrupts immersion and confuses dialogue flow

### Root Cause

AI idle chatter system (`send_chat` command) doesn't check if player is currently in dialogue before sending messages.

### Implementation Tasks

#### Task 0.2.1: Add Dialogue State Check to Idle Chatter (45m)

**File**: `miinkt/src/main/kotlin/miinkt/listener/MIINListener.kt` (or wherever idle chatter is handled)

1. **Check Active Dialogue Before Sending**:
   ```kotlin
   fun handleIdleChatter(npcId: String, message: String, targetPlayer: ServerPlayer?) {
       if (targetPlayer != null) {
           // Check if player is in dialogue
           val inDialogue = DialogueManager.hasActiveDialogue(targetPlayer.uuid)
           if (inDialogue) {
               logger.debug("[IDLE-CHATTER] Skipping message from $npcId - player in dialogue")
               return
           }
       }

       // Send message
       sendChatMessage(npcId, message, targetPlayer)
   }
   ```

2. **Add Dialogue State Query Method**:
   ```kotlin
   // In DialogueManager.kt
   fun hasActiveDialogue(playerId: UUID): Boolean {
       return activeDialogues.containsKey(playerId)
   }

   fun getActiveNpc(playerId: UUID): String? {
       return activeDialogues[playerId]?.npcId
   }
   ```

#### Task 0.2.2: Add Configuration for Idle Chatter (15m)

```kotlin
// Allow disabling idle chatter during dialogue globally
object IdleChatterConfig {
    var enableDuringDialogue: Boolean = false // Default: disabled
    var minTimeBetweenMessages: Long = 60000 // 60 seconds
}
```

### Testing Checklist

- [ ] Start dialogue with Kira - Marina should NOT send messages
- [ ] Exit dialogue - Marina's idle chatter should resume after cooldown
- [ ] Verify logs show skipped messages during dialogue
- [ ] Test with multiple NPCs and multiple players

---

## 0.3: MCP Timeout Handling

**Time Estimate**: 1-2 hours
**Priority**: HIGH

### Problem

From `runtimebuglog2.txt` lines 78-80, 88-89:
- MCP requests timeout after 60 seconds
- No retry logic or graceful degradation
- User sees "NPC is silent..." message
- Backend may be slow or unresponsive

### Root Cause

1. No timeout configuration (hardcoded 60s?)
2. No retry mechanism for failed requests
3. No fallback dialogue when LLM is unavailable
4. Potential Ollama backend performance issues

### Implementation Tasks

#### Task 0.3.1: Add Configurable Timeouts (30m)

**File**: `npc/scripts/mcp_client.py` (or Kotlin equivalent)

```kotlin
object MCPConfig {
    var requestTimeout: Long = 30000 // 30 seconds (down from 60s)
    var maxRetries: Int = 2
    var retryDelay: Long = 2000 // 2 seconds between retries
}
```

#### Task 0.3.2: Implement Retry Logic (1h)

```kotlin
suspend fun callMCPWithRetry(
    tool: String,
    params: Map<String, Any>,
    maxRetries: Int = MCPConfig.maxRetries
): Result<JsonObject> {
    var lastException: Exception? = null

    repeat(maxRetries + 1) { attempt ->
        try {
            logger.info("[MCP] Attempt ${attempt + 1}/${maxRetries + 1} for tool: $tool")

            val result = withTimeout(MCPConfig.requestTimeout) {
                mcpClient.callTool(tool, params)
            }

            logger.info("[MCP] Success on attempt ${attempt + 1}")
            return Result.success(result)

        } catch (e: TimeoutCancellationException) {
            logger.warn("[MCP] Timeout on attempt ${attempt + 1}: ${e.message}")
            lastException = e

            if (attempt < maxRetries) {
                delay(MCPConfig.retryDelay)
            }

        } catch (e: Exception) {
            logger.error("[MCP] Error on attempt ${attempt + 1}: ${e.message}")
            return Result.failure(e)
        }
    }

    return Result.failure(lastException ?: Exception("MCP call failed"))
}
```

#### Task 0.3.3: Add Fallback Dialogue (30m)

```kotlin
fun getFallbackDialogue(npcId: String): DialogueData {
    return DialogueData(
        npcId = npcId,
        greeting = "I seem to be at a loss for words right now. Perhaps we can talk later?",
        options = listOf(
            DialogueOption(1, "No problem, see you later.", "friendly"),
            DialogueOption(2, "Sure, take your time.", "neutral")
        )
    )
}

// Use when MCP fails:
val result = callMCPWithRetry(tool, params)
val dialogueData = result.getOrElse {
    logger.error("[MCP] All retries failed, using fallback dialogue")
    getFallbackDialogue(npcId)
}
```

### Backend Investigation Tasks

- [ ] Check Ollama server logs for errors
- [ ] Monitor Ollama response times: `curl -w "@curl-format.txt" http://localhost:11434/api/chat`
- [ ] Verify model is loaded and not being evicted: `ollama ps`
- [ ] Check system resources (RAM, GPU VRAM, CPU)
- [ ] Test direct Ollama calls outside of MCP to isolate bottleneck

### Testing Checklist

- [ ] Force timeout by blocking backend - should retry 2 times
- [ ] Verify fallback dialogue appears after retries exhausted
- [ ] Confirm timeout reduced to 30s
- [ ] Test multiple concurrent MCP calls
- [ ] Verify logs show retry attempts

---

## 0.4: Quest Generation Stub

**Time Estimate**: 30 minutes
**Priority**: MEDIUM

### Problem

From `runtimebuglog2.txt` lines 105-107:
- Player selects "Do you have any work for me?" option
- `generate_quest` command received but never responds
- NPC says "Let me think..." but hangs for 3+ minutes
- No error handling or timeout

### Root Cause

Quest generation system (Phase 5) not implemented, but dialogue options reference it. Need proper stub with error handling.

### Implementation Tasks

#### Task 0.4.1: Add Quest Generation Stub (30m)

**File**: Command handler (likely in `MIINListener.kt` or `CommandHandler.kt`)

```kotlin
fun handleGenerateQuest(playerId: UUID, npcId: String) {
    logger.warn("[QUEST] Quest generation not yet implemented (Phase 5)")

    // Send immediate response instead of hanging
    val player = server.getPlayerByUUID(playerId) ?: return

    player.sendSystemMessage(
        Component.literal("I don't have any work available right now. Check back later!")
            .withStyle(ChatFormatting.YELLOW)
    )

    // Optionally: Remove quest-related options from dialogue until Phase 5
    // Or: Return generic fetch quests as placeholders
}
```

#### Task 0.4.2: Remove Quest Options from Dialogue (Alternative)

```kotlin
// In dialogue generation prompt or filtering:
fun filterQuestOptions(options: List<DialogueOption>): List<DialogueOption> {
    return options.filter { option ->
        !option.text.contains("quest", ignoreCase = true) &&
        !option.text.contains("work for me", ignoreCase = true) &&
        option.leadsTo != "quest"
    }
}
```

### Testing Checklist

- [ ] Select quest option - should get immediate response
- [ ] Verify no 3+ minute hangs
- [ ] Check logs show "not yet implemented" message
- [ ] Confirm dialogue flow continues normally

---

## 0.5: Performance Investigation

**Time Estimate**: 1 hour
**Priority**: MEDIUM (after above fixes)

### Problem

Response times degraded from 14-17s baseline to 51s average (3x slower).

### Investigation Tasks

1. **Profile MCP Response Times**:
   - Add detailed timing logs to each MCP call
   - Separate network time vs LLM generation time
   - Identify bottleneck (network, Ollama, parsing, Kotlin processing)

2. **Check for Resource Leaks**:
   - Monitor thread count over time
   - Check for unclosed HTTP connections
   - Verify CompletableFuture cleanup

3. **Ollama Backend Health**:
   - Test direct Ollama performance: `time curl -X POST http://localhost:11434/api/chat -d '...'`
   - Check if model is being evicted/reloaded between calls
   - Verify context window size isn't causing slowdown

4. **Network/MCP Overhead**:
   - Measure JSON serialization/deserialization time
   - Check MCP server response time separately from Ollama

### Logging Additions

```kotlin
val startTime = System.currentTimeMillis()
logger.info("[PERF] MCP call started: $tool")

// ... make call ...

val endTime = System.currentTimeMillis()
logger.info("[PERF] MCP call completed: $tool, Time: ${endTime - startTime}ms")
```

---

## 0.6: NPC ID Synchronization Fix

**Time Estimate**: 30 minutes
**Priority**: HIGH

### Problem

From latest `runtimebuglog2.txt` lines 1-27:
- First NPC interaction after server start returns `NPC: unknown` instead of actual NPC ID
- MCP call fails with error: `"NPC 'unknown' not found"`
- Subsequent interactions work correctly (lines 123, 178 show proper IDs)
- Causes poor first-impression UX and dialogue failures

### Root Cause

**DataTracker Synchronization Race Condition** in `MIINNpcEntity.kt`:

1. Entity is created with default `NPC_ID = "unknown"` in `initDataTracker()` (line 100)
2. `NpcManager.spawnNpc()` calls `entity.setNpcIdentity()` to set real ID (line 194 in NpcManager.kt)
3. The `npcId` property setter calls `dataTracker.set(NPC_ID, value)` (line 75)
4. Entity is spawned into world with `world.spawnEntity(entity)` (line 200 in NpcManager.kt)
5. Player clicks NPC immediately after spawn
6. The `npcId` property getter reads from `dataTracker.get(NPC_ID)` but sync hasn't completed yet
7. Returns `"unknown"` → MCP fails with "NPC 'unknown' not found"

By the second interaction, DataTracker has synchronized, so it works correctly.

### Implementation Tasks

#### Task 0.6.1: Add Server-Side ID Field (20m)

**File**: `miinkt/src/main/kotlin/miinkt/listener/entity/MIINNpcEntity.kt`

**Around line 72, add server-side storage:**
```kotlin
// Server-authoritative NPC ID (avoids DataTracker sync delay)
private var serverNpcId: String = "unknown"

var npcId: String
    get() = if (entityWorld.isClient) {
        dataTracker.get(NPC_ID)  // Client reads from synced data
    } else {
        serverNpcId  // Server uses immediate field
    }
    set(value) {
        serverNpcId = value  // Set server field immediately
        dataTracker.set(NPC_ID, value)  // Async sync to client
    }
```

This ensures the server-side ID is available immediately after `setNpcIdentity()` is called, while still syncing to the client for rendering.

#### Task 0.6.2: Add Interaction Validation (10m)

**File**: `miinkt/src/main/kotlin/miinkt/listener/entity/MIINNpcEntity.kt`

**Around line 199 in `interactMob()`, add validation:**
```kotlin
override fun interactMob(player: PlayerEntity, hand: Hand): ActionResult {
    if (hand == Hand.MAIN_HAND && dialogueEnabled) {
        if (!entityWorld.isClient) {
            // Prevent interaction before ID is set
            if (npcId == "unknown") {
                LOGGER.warn("NPC interaction attempted before ID sync complete")
                player.sendMessage(Text.literal("§cNPC is still initializing..."), false)
                return ActionResult.FAIL
            }

            LOGGER.info("SERVER: Player ${player.name.string} interacted with NPC $npcId ($npcName)")
            triggerDialogue(player)
        }
        return ActionResult.SUCCESS
    }
    return ActionResult.PASS
}
```

This provides a safety check as a fallback, though the server-side field should eliminate the race condition.

### Testing Checklist

- [ ] Restart server and immediately click first NPC - should show correct ID
- [ ] Verify logs show correct NPC ID (not "unknown")
- [ ] Verify MCP receives correct NPC ID in dialogue_start call
- [ ] Confirm no "NPC 'unknown' not found" errors
- [ ] Test rapid clicking between multiple NPCs after server start
- [ ] Verify client still renders NPC name correctly after sync

---

## 0.7: Dynamic NPC Generation System

**Time Estimate**: 4-6 hours
**Priority**: MEDIUM (after critical fixes)

### Problem

No dynamic NPC generation system exists. All NPCs are static preset entities from `npcs.json`:
- 8 preset NPCs: marina, vex, rowan, kira, sage, thane, lyra, grimm
- No NPCs spawn based on exploration, biomes, or events
- No procedurally generated NPC personalities
- World feels static and unpopulated beyond preset NPCs

The existing `SpawnListener.kt` only enhances **vanilla mob spawns** (zombies, skeletons) with custom equipment/names. It does NOT create `MIINNpcEntity` instances.

### Root Cause

**Missing Feature**: No system exists to:
1. Monitor player exploration and spawn NPCs in new areas
2. Generate NPC personalities/backstories procedurally via LLM
3. Create biome-appropriate NPCs (fishers near ocean, miners in caves)
4. Spawn NPCs dynamically during gameplay
5. Persist generated NPCs to avoid duplicates

### Implementation Tasks

#### Task 0.7.1: Create DynamicNpcGenerator Class (2h)

**New File**: `miinkt/src/main/kotlin/miinkt/listener/entity/DynamicNpcGenerator.kt`

```kotlin
package miinkt.listener.entity

import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.biome.Biome
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

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

    fun tick(server: MinecraftServer) {
        // Called every server tick - throttle processing
        // Check for spawn opportunities every 100 ticks (5 seconds)
    }

    private fun checkSpawnOpportunity(world: ServerWorld, pos: BlockPos) {
        val chunkKey = "${world.registryKey.value}:${pos.x shr 4}:${pos.z shr 4}"

        // Skip if chunk already processed recently
        if (processedChunks.containsKey(chunkKey)) return

        // Roll spawn chance
        if (Random.nextDouble() > SPAWN_CHANCE_PER_CHUNK) {
            processedChunks[chunkKey] = System.currentTimeMillis()
            return
        }

        // Check spawn conditions
        if (!isValidSpawnLocation(world, pos)) return
        if (tooManyNearbyNpcs(world, pos)) return

        // Generate NPC asynchronously via MCP
        generateAndSpawnNpc(world, pos)
    }

    private fun isValidSpawnLocation(world: ServerWorld, pos: BlockPos): Boolean {
        // Check distance from spawn
        val spawnPos = world.spawnPos
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

        // Call MCP to generate NPC personality
        // This would be an async call to a new MCP tool: "minecraft_generate_dynamic_npc"
        // Tool would return: name, archetype, personality, backstory, skin reference

        // For now, stub with placeholder
        val generatedId = "dynamic_${UUID.randomUUID().toString().substring(0, 8)}"
        val archetype = getBiomeAppropriateArchetype(biomeKey)

        // TODO: Call MCP for full generation
        // val mcpResult = MCPClient.generateDynamicNpc(biomeKey, archetype)

        // Spawn the NPC
        spawnDynamicNpc(world, pos, generatedId, archetype)
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

    private fun spawnDynamicNpc(
        world: ServerWorld,
        pos: BlockPos,
        npcId: String,
        archetype: String
    ) {
        // Integration with NpcManager
        // This would call NpcManager.spawnNpc() with generated data
        LOGGER.info("Spawned dynamic NPC: $npcId ($archetype) at $pos")

        // TODO: Persist to dynamic_npcs.json to avoid duplicates on reload
    }
}
```

#### Task 0.7.2: Add MCP Tool for NPC Generation (1h)

**File**: `npc/scripts/service.py`

Add new MCP tool `minecraft_generate_dynamic_npc`:
```python
@mcp.tool()
async def minecraft_generate_dynamic_npc(biome: str, archetype: str) -> dict:
    """
    Generate a unique NPC personality for dynamic spawning.

    Args:
        biome: Minecraft biome identifier (e.g., "plains", "ocean", "mountain")
        archetype: Base archetype (e.g., "merchant", "fisher", "miner")

    Returns:
        Dictionary with NPC data: name, personality, backstory, profession, race
    """
    prompt = f"""Generate a unique Minecraft NPC with the following constraints:

Biome: {biome}
Archetype: {archetype}

Return JSON with:
- name: Full NPC name (first + last, fantasy-appropriate)
- race: Fantasy race (human, elf, dwarf, etc.)
- personality: 3-5 personality traits
- profession: Specific profession related to archetype
- backstory: 2-3 sentence backstory explaining why they're in this biome
- dialogue_style: How they speak (formal, casual, gruff, etc.)

Make them interesting and memorable!"""

    # Call LLM to generate
    response = await call_llm(prompt, temperature=0.9)  # High temp for variety

    # Parse and return
    return parse_npc_data(response)
```

#### Task 0.7.3: Integrate with Server Lifecycle (30m)

**File**: `miinkt/src/main/kotlin/miinkt/listener/MIINListener.kt`

**Around line 110, add initialization:**
```kotlin
ServerLifecycleEvents.SERVER_STARTED.register { server ->
    // Existing NPC loading code...
    NpcManager.loadAndSpawnNpcs(server)

    // Initialize dynamic generation
    DynamicNpcGenerator.initialize(server)
}

// Add tick event for dynamic spawning
ServerTickEvents.END_SERVER_TICK.register { server ->
    DynamicNpcGenerator.tick(server)
}
```

#### Task 0.7.4: Add Persistence for Dynamic NPCs (1h)

**New File**: `dynamic_npcs.json` (in config directory)

Track dynamically generated NPCs to avoid duplicates:
```json
{
  "dynamic_npcs": [
    {
      "id": "dynamic_a3f8e21c",
      "name": "Bjorn Saltbeard",
      "archetype": "fisher",
      "biome": "ocean",
      "spawn_location": {"x": 450, "y": 64, "z": -230},
      "generated_timestamp": 1732377600,
      "personality": {...},
      "backstory": "..."
    }
  ]
}
```

Load and spawn these NPCs on server start, similar to preset NPCs.

#### Task 0.7.5: Add Player Exploration Tracking (30m)

Track which chunks players have explored to prioritize spawn locations:

```kotlin
object ExplorationTracker {
    private val exploredChunks = ConcurrentHashMap<String, MutableSet<String>>()

    fun trackPlayerMovement(playerId: UUID, chunkPos: ChunkPos) {
        val playerChunks = exploredChunks.computeIfAbsent(playerId.toString()) {
            mutableSetOf()
        }
        val chunkKey = "${chunkPos.x}:${chunkPos.z}"

        if (playerChunks.add(chunkKey)) {
            // New chunk explored - trigger spawn check
            DynamicNpcGenerator.onNewChunkExplored(chunkPos)
        }
    }
}
```

### Testing Checklist

- [ ] Explore new chunks - NPCs should spawn with ~5% chance per chunk
- [ ] Verify NPCs spawn with biome-appropriate archetypes
- [ ] Check NPCs spawn at valid locations (solid ground, away from spawn)
- [ ] Confirm max 3 NPCs per 128-block radius
- [ ] Verify dynamic NPCs persist across server restarts
- [ ] Test dialogue with dynamically generated NPCs
- [ ] Check no duplicate IDs for dynamic NPCs
- [ ] Verify MCP generates unique personalities

### Future Enhancements

- **Event-Based Spawning**: Spawn NPCs during specific events (lightning, player achievements)
- **NPC Migration**: NPCs move between biomes over time
- **Relationship System**: Dynamic NPCs know about preset NPCs
- **Quest Integration**: Dynamic NPCs can offer procedurally generated quests (Phase 5)

---

## Implementation Order

**Recommended sequence**:

1. **0.1: Thread Safety** (CRITICAL) - Prevents dialogue corruption
2. **0.6: NPC ID Synchronization** (HIGH) - Fixes first-interaction bug
3. **0.2: Idle Chatter Fix** (HIGH) - Quick win, improves UX
4. **0.3: MCP Timeout Handling** (HIGH) - Prevents user-facing hangs
5. **0.4: Quest Generation Stub** (MEDIUM) - Removes broken feature until Phase 5
6. **0.5: Performance Investigation** (MEDIUM) - May reveal deeper issues
7. **0.7: Dynamic NPC Generation** (MEDIUM) - New feature, can be deferred

**Parallel Work Possible**:
- 0.2 and 0.4 can be done in parallel (independent systems)
- 0.6 can be done in parallel with 0.2 (different files)
- 0.5 can run during testing of 0.1-0.4
- 0.7 can be developed independently after critical fixes

---

## Success Criteria

Phase 0 is complete when:

- [ ] No dialogue state race conditions (Rowan/Kira bug fixed)
- [ ] No cross-NPC chat bleeding during dialogue
- [ ] MCP timeouts handled gracefully with retries
- [ ] Quest generation returns immediate stub response
- [ ] Response times return to 14-17s baseline (or bottleneck identified)
- [ ] **First NPC interaction after server start shows correct ID (not "unknown")**
- [ ] **Dynamic NPCs spawn in explored chunks (optional - can defer to post-Phase 0)**
- [ ] All Phase 0 tests pass
- [ ] Runtime logs show no WARN/ERROR messages for 5+ minute test session
- [ ] Multiple concurrent players can interact with NPCs without issues

---

## Files to Modify

| File | Tasks | Priority |
|------|-------|----------|
| `miinkt/src/main/kotlin/miinkt/listener/dialogue/DialogueManager.kt` | 0.1.1, 0.1.2, 0.1.3, 0.2.1 | CRITICAL |
| `miinkt/src/main/kotlin/miinkt/listener/entity/MIINNpcEntity.kt` | 0.6.1, 0.6.2 | HIGH |
| `miinkt/src/main/kotlin/miinkt/listener/MIINListener.kt` | 0.2.1, 0.4.1, 0.7.3 | HIGH |
| `npc/scripts/mcp_client.py` or Kotlin MCP wrapper | 0.3.1, 0.3.2, 0.3.3 | HIGH |
| Command handler (TBD location) | 0.4.1 | MEDIUM |
| Various for performance profiling | 0.5 | MEDIUM |
| **`miinkt/src/main/kotlin/miinkt/listener/entity/DynamicNpcGenerator.kt` (NEW)** | 0.7.1 | MEDIUM |
| **`npc/scripts/service.py`** | 0.7.2 | MEDIUM |
| **Config: `dynamic_npcs.json` (NEW)** | 0.7.4 | MEDIUM |

---

## Notes

- **Phase 1+ Blocked**: Do not proceed with Phase 1 (Anti-Meta Filter, etc.) until Phase 0 is complete
- **Testing Environment**: Run all tests in creative mode with multiple NPCs spawned
- **Backup Runtime Logs**: Keep `runtimebuglog.txt` as baseline, continue logging to `runtimebuglog2.txt`
- **Backend Health**: Monitor Ollama server health throughout testing

---

## Next Steps After Phase 0

Once Phase 0 is complete and verified:
1. Return to **Phase 1: Dialogue Optimization & Meta-Awareness** in `SYSTEMS_ROADMAP.md`
2. Continue with Phase 1.1 (Anti-Meta Filter) if not already implemented
3. Verify Phase 1.2 and 1.3 implementations
4. Proceed to Phase 2+

---

**Document Created**: 2025-11-23
**Last Updated**: 2025-11-23 (Added 0.6 and 0.7)
**Status**: Ready for Implementation

---

## Version History

- **v1.0** (2025-11-23): Initial document with sections 0.1-0.5
- **v1.1** (2025-11-23): Added 0.6 (NPC ID Synchronization) and 0.7 (Dynamic NPC Generation) based on latest runtime bug analysis
