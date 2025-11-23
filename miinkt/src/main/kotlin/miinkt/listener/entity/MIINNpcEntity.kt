/**
 * MIIN NPC Entity - Custom NPC with player model
 *
 * This entity uses a player model and supports custom skins for NPCs.
 * Each NPC can have its own appearance based on config.
 */

package miinkt.listener.entity

import net.minecraft.entity.EntityType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.PathAwareEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.ai.goal.WanderAroundFarGoal
import net.minecraft.entity.ai.goal.LookAtEntityGoal
import net.minecraft.entity.ai.goal.LookAroundGoal
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import com.google.gson.Gson
import com.google.gson.JsonObject

class MIINNpcEntity(
    entityType: EntityType<out MIINNpcEntity>,
    world: World
) : net.minecraft.entity.mob.PathAwareEntity(entityType, world) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("MIINkt-npc")
        private val gson = Gson()
        private val httpClient = HttpClient.newBuilder().build()
        private const val HTTP_BRIDGE_URL = "http://localhost:5558/command"

        // Tracked data for client sync
        private val NPC_ID: TrackedData<String> = DataTracker.registerData(
            MIINNpcEntity::class.java,
            TrackedDataHandlerRegistry.STRING
        )
        private val NPC_NAME: TrackedData<String> = DataTracker.registerData(
            MIINNpcEntity::class.java,
            TrackedDataHandlerRegistry.STRING
        )
        private val SKIN_PATH: TrackedData<String> = DataTracker.registerData(
            MIINNpcEntity::class.java,
            TrackedDataHandlerRegistry.STRING
        )

        // Ambient bark cooldown per player (in ticks, 20 ticks = 1 second)
        private val playerAmbientCooldowns = mutableMapOf<String, Long>()
        private const val AMBIENT_COOLDOWN_TICKS = 6000L // 5 minutes

        fun createMobAttributes(): DefaultAttributeContainer.Builder {
            return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.FOLLOW_RANGE, 48.0)
        }
    }

    // NPC properties (synced to client via DataTracker)
    var npcId: String
        get() = dataTracker.get(NPC_ID)
        set(value) = dataTracker.set(NPC_ID, value)

    var npcName: String
        get() = dataTracker.get(NPC_NAME)
        set(value) = dataTracker.set(NPC_NAME, value)

    var skinPath: String
        get() = dataTracker.get(SKIN_PATH)
        set(value) = dataTracker.set(SKIN_PATH, value)

    // Compute skin identifier from synced skin path
    val skinIdentifier: Identifier?
        get() {
            val path = skinPath
            return if (path.isNotEmpty()) {
                Identifier.of("miin-listener", "textures/entity/npc/$path")
            } else {
                null
            }
        }

    var dialogueEnabled: Boolean = true

    override fun initDataTracker(builder: DataTracker.Builder) {
        super.initDataTracker(builder)
        builder.add(NPC_ID, "unknown")
        builder.add(NPC_NAME, "NPC")
        builder.add(SKIN_PATH, "")
    }

    // Behavior settings
    var behaviorMode: BehaviorMode = BehaviorMode.STATIONARY
    private var homePos: BlockPos? = null
    private var roamRadius: Double = 10.0

    // Following behavior
    private var followTarget: PlayerEntity? = null
    private var followDistance: Double = 3.0

    /**
     * Set home position for roaming behavior
     */
    fun setHomePosition(pos: BlockPos) {
        this.homePos = pos
    }

    // Ambient bark tracking
    private var ticksSinceLastBark = 0
    private val ambientBarkChance = 0.001f // Small chance per tick when player nearby

    enum class BehaviorMode {
        STATIONARY,  // Stays in place, looks around
        ROAMING,     // Wanders within radius of home
        FOLLOWING    // Follows a specific player
    }

    init {
        // NPCs are invulnerable by default
        isInvulnerable = true

        setPersistent()
    }

    override fun initGoals() {
        // Priority 1: Look at nearby players (most important)
        goalSelector.add(1, LookAtEntityGoal(this, PlayerEntity::class.java, 8.0f))

        // Priority 2: Wander around home position (if roaming)
        goalSelector.add(2, WanderAroundFarGoal(this, 0.6, 0.001f))

        // Priority 3: Look around randomly when idle
        goalSelector.add(3, LookAroundGoal(this))
    }

    /**
     * Set behavior mode and home position
     */
    fun setBehavior(mode: BehaviorMode, radius: Double = 10.0) {
        this.behaviorMode = mode
        this.roamRadius = radius
        this.homePos = blockPos

        // Clear follow target if not following
        if (mode != BehaviorMode.FOLLOWING) {
            this.followTarget = null
        }

        LOGGER.debug("$npcName behavior set to $mode with radius $radius")
    }

    /**
     * Set NPC to follow a specific player
     */
    fun setFollowTarget(player: PlayerEntity, distance: Double = 3.0) {
        this.behaviorMode = BehaviorMode.FOLLOWING
        this.followTarget = player
        this.followDistance = distance

        LOGGER.info("$npcName now following ${player.name.string}")
    }

    /**
     * Stop following and return to stationary
     */
    fun stopFollowing() {
        this.behaviorMode = BehaviorMode.STATIONARY
        this.followTarget = null
        this.homePos = blockPos

        LOGGER.info("$npcName stopped following, now stationary at $blockPos")
    }

    /**
     * Get the current follow target
     */
    fun getFollowTarget(): PlayerEntity? = followTarget

    /**
     * Handle player interaction with NPC
     */
    override fun interactMob(player: PlayerEntity, hand: Hand): ActionResult {
        // Log on both client and server to see what's happening
        LOGGER.info("=== NPC INTERACT CALLED ===")
        LOGGER.info("Hand: $hand, isClient: ${entityWorld.isClient}, dialogueEnabled: $dialogueEnabled")

        if (hand == Hand.MAIN_HAND && dialogueEnabled) {
            if (!entityWorld.isClient) {
                LOGGER.info("SERVER: Player ${player.name.string} interacted with NPC $npcId ($npcName)")
                triggerDialogue(player)
            }
            return ActionResult.SUCCESS
        }
        return ActionResult.PASS
    }

    // Extended NPC properties (server-side only for LLM context)
    var archetype: String = ""
    var race: String = ""
    var personality: String = ""
    var profession: String = ""

    /**
     * Trigger dialogue with NPC via HTTP bridge
     */
    private fun triggerDialogue(player: PlayerEntity) {
        val playerName = player.name.string

        // Send immediate greeting while waiting for AI response
        val greeting = getImmediateGreeting()

        LOGGER.info("Attempting to send greeting: $greeting")

        // Cast player safely and send message
        val serverPlayer = player as? net.minecraft.server.network.ServerPlayerEntity
        if (serverPlayer != null) {
            serverPlayer.sendMessage(Text.literal("§e[$npcName]§r $greeting"), false)
            LOGGER.info("SUCCESS: Sent greeting to $playerName")
        } else {
            LOGGER.warn("FAILED: Player is ${player.javaClass.simpleName}, not ServerPlayerEntity")
        }

        val world = entityWorld

        // Request AI-generated dialogue in background (with timeout to prevent hanging)
        Thread {
            try {
                val payload = JsonObject().apply {
                    addProperty("type", "npc_dialogue")
                    add("data", JsonObject().apply {
                        addProperty("npc_id", npcId)
                        addProperty("npc_name", npcName)
                        addProperty("player", playerName)
                        addProperty("action", "greet")
                        
                        // Add context fields
                        if (archetype.isNotEmpty()) addProperty("archetype", archetype)
                        if (race.isNotEmpty()) addProperty("race", race)
                        if (personality.isNotEmpty()) addProperty("personality", personality)
                        if (profession.isNotEmpty()) addProperty("profession", profession)
                    })
                }

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(HTTP_BRIDGE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() == 200) {
                    val result = gson.fromJson(response.body(), JsonObject::class.java)
                    if (result.has("message")) {
                        val aiMessage = result.get("message").asString
                        // Send to player in game via server
                        world.server?.execute {
                            serverPlayer?.sendMessage(Text.literal("§e[$npcName]§r $aiMessage"), false)
                        }
                    }
                }
            } catch (e: java.net.ConnectException) {
                // Service not running - this is fine, we already sent the greeting
                LOGGER.debug("Dialogue service not running")
            } catch (e: Exception) {
                LOGGER.debug("Dialogue request failed: ${e.message}")
            }
        }.start()
    }

    /**
     * Get immediate greeting based on NPC personality
     */
    private fun getImmediateGreeting(): String {
        val greetings = mapOf(
            "marina" to "Ahoy there! The tides brought you to me today...",
            "vex" to "*looks through you* You walk between moments, I see...",
            "rowan" to "A customer! Let's see what we can arrange...",
            "kira" to "Stay alert. What brings you to me?",
            "sage" to "*smiles gently* The forest whispered you'd come...",
            "thane" to "Hm. Need something built right?",
            "lyra" to "Oh! Your aura has such interesting colors today...",
            "grimm" to "*glances around nervously* Quick, what do you need?"
        )
        return greetings[npcId] ?: "Greetings, traveler. What brings you here?"
    }

    override fun isPushable(): Boolean = false

    override fun cannotDespawn(): Boolean = true

    override fun isAffectedBySplashPotions(): Boolean = false

    /**
     * Set NPC identity from config
     */
    fun setNpcIdentity(
        id: String, 
        name: String, 
        skin: String?, 
        archetype: String = "",
        race: String = "",
        personality: String = "",
        profession: String = ""
    ) {
        this.npcId = id
        this.npcName = name
        this.customName = Text.literal(name)
        this.isCustomNameVisible = true
        
        this.archetype = archetype
        this.race = race
        this.personality = personality
        this.profession = profession

        // Set skin path via DataTracker (will sync to client)
        if (skin != null) {
            this.skinPath = skin
        }

        LOGGER.info("Set NPC identity: id=$id, name=$name, skin=$skin, archetype=$archetype")
    }


    /**
     * Make NPC look at player
     */
    fun lookAtPlayer(player: PlayerEntity) {
        this.lookControl.lookAt(player, 30.0f, 30.0f)
    }

    /**
     * Tick behavior - handle roaming bounds, following, and ambient barks
     */
    override fun tick() {
        super.tick()

        // Only run on server side
        if (entityWorld.isClient) return

        // Handle FOLLOWING mode
        if (behaviorMode == BehaviorMode.FOLLOWING && followTarget != null) {
            val target = followTarget!!

            // Check if target is still valid (online and in same world)
            if (target.isRemoved || target.entityWorld != entityWorld) {
                LOGGER.info("$npcName lost follow target, stopping")
                stopFollowing()
                return
            }

            val distToTarget = squaredDistanceTo(target)

            // If too far, teleport (lost them)
            if (distToTarget > 400) { // 20 blocks
                // Teleport near player
                val newPos = target.blockPos.add(
                    random.nextInt(3) - 1,
                    0,
                    random.nextInt(3) - 1
                )
                teleport(newPos.x.toDouble(), newPos.y.toDouble(), newPos.z.toDouble(), true)
                LOGGER.debug("$npcName teleported to catch up with ${target.name.string}")
            }
            // If farther than follow distance, move toward them
            else if (distToTarget > followDistance * followDistance) {
                navigation.startMovingTo(target, 1.0)
            }
            // Close enough, just look at them
            else {
                navigation.stop()
                lookAtPlayer(target)
            }
        }

        // Check if NPC has wandered too far from home (for ROAMING mode)
        if (behaviorMode == BehaviorMode.ROAMING && homePos != null) {
            val distFromHome = blockPos.getSquaredDistance(homePos)
            if (distFromHome > roamRadius * roamRadius) {
                // Return home - clear current path and move back
                navigation.startMovingTo(
                    homePos!!.x.toDouble(),
                    homePos!!.y.toDouble(),
                    homePos!!.z.toDouble(),
                    1.0
                )
            }
        }

        // Check for ambient bark opportunity
        val nearestPlayer = entityWorld.getClosestPlayer(this, 10.0)
        if (nearestPlayer != null) {
            ticksSinceLastBark++
            if (ticksSinceLastBark > 200) { // At least 10 seconds between attempts
                if (shouldAmbientBark(nearestPlayer)) {
                    triggerAmbientBark(nearestPlayer)
                    ticksSinceLastBark = 0
                }
            }
        }
    }

    /**
     * Check if NPC should trigger an ambient bark
     */
    private fun shouldAmbientBark(player: PlayerEntity): Boolean {
        val playerName = player.name.string
        val currentTick = entityWorld.time
        val lastBark = playerAmbientCooldowns[playerName] ?: 0L

        // Check cooldown
        if (currentTick - lastBark < AMBIENT_COOLDOWN_TICKS) {
            return false
        }

        // Random chance
        return random.nextFloat() < ambientBarkChance
    }

    /**
     * Trigger an ambient bark to nearby player
     */
    private fun triggerAmbientBark(player: PlayerEntity) {
        val playerName = player.name.string
        playerAmbientCooldowns[playerName] = entityWorld.time

        // Generate contextual bark based on time of day
        val bark = generateAmbientBark()

        // Send via HTTP bridge
        sendAmbientMessage(playerName, bark)

        LOGGER.debug("$npcName barked at $playerName: $bark")
    }

    /**
     * Generate ambient bark based on context
     */
    private fun generateAmbientBark(): String {
        val timeOfDay = entityWorld.timeOfDay % 24000

        val barks = when {
            timeOfDay < 450 -> listOf(
                "The dawn brings new opportunities...",
                "Another day begins. What will you build?",
                "The morning light reveals much."
            )
            timeOfDay < 11616 -> listOf(
                "Busy day? I can see you've been active.",
                "The sun is high. Good time for adventures.",
                "Greetings, traveler.",
                "Care for a quest? I might have something for you."
            )
            timeOfDay < 13800 -> listOf(
                "The evening approaches. Mind the shadows.",
                "Dusk falls... dangerous creatures stir.",
                "Best to find shelter soon."
            )
            else -> listOf(
                "The night holds many secrets.",
                "Stay vigilant in the darkness.",
                "Monsters roam freely now. Be careful.",
                "A brave soul, wandering at night."
            )
        }

        return barks[random.nextInt(barks.size)]
    }

    /**
     * Send ambient message to player via HTTP bridge
     */
    private fun sendAmbientMessage(playerName: String, message: String) {
        try {
            val payload = JsonObject().apply {
                addProperty("type", "send_chat")
                add("data", JsonObject().apply {
                    addProperty("player", playerName)
                    addProperty("message", "[$npcName] $message")
                })
            }

            val request = HttpRequest.newBuilder()
                .uri(URI.create(HTTP_BRIDGE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build()

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept { response ->
                    if (response.statusCode() != 200) {
                        LOGGER.warn("Failed to send ambient bark: ${response.statusCode()}")
                    }
                }

        } catch (e: Exception) {
            LOGGER.error("Error sending ambient bark", e)
        }
    }
}
