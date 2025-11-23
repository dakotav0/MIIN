package miinkt.listener.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import miinkt.listener.entity.MIINNpcEntity
import miinkt.listener.state.DialogueState
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class MIINCommandRegistry(
    private val playerDialogues: ConcurrentHashMap<String, DialogueState>,
    private val sendDialogueOptions: (ServerPlayerEntity, DialogueState) -> Unit,
    private val listAllNpcs: (CommandContext<ServerCommandSource>) -> Unit,
    private val loreService: miinkt.listener.service.MIINLoreService,
    private val onOptionSelected: (ServerPlayerEntity, Int) -> Unit,
    private val onTalkRequest: (ServerPlayerEntity, String) -> Unit,
    private val onActionSelected: (ServerPlayerEntity, String) -> Unit,
    private val onBackSelected: (ServerPlayerEntity) -> Unit,
    private val spawnNpc: (ServerPlayerEntity, String, String?) -> Unit
) {
    private val logger = LoggerFactory.getLogger("MIIN-command-registry")

    fun registerCommands() {
        registerLoreCommand()
        registerNpcCommand()
        registerSpawnCommand()
    }

    private fun registerLoreCommand() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            // /lore command with subcommands
            dispatcher.register(
                literal("lore")
                    .executes { context ->
                        val player = context.source.player
                        if (player != null) {
                            loreService.showPlayerLore(player)
                        }
                        1
                    }
                    .then(
                        // /lore write <title> <content>
                        literal("write")
                            .then(
                                argument("title", StringArgumentType.string())
                                    .then(
                                        argument("content", StringArgumentType.greedyString())
                                            .executes { context ->
                                                val player = context.source.player
                                                if (player != null) {
                                                    val title = StringArgumentType.getString(context, "title")
                                                    val content = StringArgumentType.getString(context, "content")
                                                    loreService.createLoreBook(player, title, content)
                                                }
                                                1
                                            }
                                    )
                            )
                    )
                    .then(
                        // /lore rename <new_name> (for held item)
                        literal("rename")
                            .then(
                                argument("new_name", StringArgumentType.greedyString())
                                    .executes { context ->
                                        val player = context.source.player
                                        if (player != null) {
                                            val newName = StringArgumentType.getString(context, "new_name")
                                            loreService.renameHeldItem(player, newName)
                                        }
                                        1
                                    }
                            )
                    )
                    .then(
                        // /lore savemap
                        literal("savemap")
                            .executes { context ->
                                val player = context.source.player
                                if (player != null) {
                                    loreService.saveMapToSatchel(player)
                                }
                                1
                            }
                    )
                    .then(
                        // /lore give (satchel)
                        literal("give")
                            .executes { context ->
                                val player = context.source.player
                                if (player != null) {
                                    loreService.giveSatchel(player)
                                }
                                1
                            }
                    )
            )
        }
    }

    private fun registerNpcCommand() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal("npc")
                    .executes { context ->
                        val player = context.source.player
                        if (player != null) {
                            if (playerDialogues.containsKey(player.name.string)) {
                                // In conversation - show current options
                                val state = playerDialogues[player.name.string]!!
                                sendDialogueOptions(player, state)
                            } else {
                                listAllNpcs(context)
                            }
                        }
                        1
                    }
                    .then(
                        argument("option", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .executes { context ->
                                val player = context.source.player
                                if (player != null) {
                                    val option = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "option")
                                    onOptionSelected(player, option)
                                }
                                1
                            }
                    )
                    .then(
                        // /npc select <number> (Alternative for clickable chat)
                        literal("select")
                            .then(
                                argument("option", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val player = context.source.player
                                        if (player != null) {
                                            val option = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "option")
                                            onOptionSelected(player, option)
                                        }
                                        1
                                    }
                            )
                    )
                    .then(
                        // /npc talk <message>
                        literal("talk")
                            .then(
                                argument("message", StringArgumentType.greedyString())
                                    .executes { context ->
                                        val player = context.source.player
                                        if (player != null) {
                                            val message = StringArgumentType.getString(context, "message")
                                            onTalkRequest(player, message)
                                        }
                                        1
                                    }
                            )
                    )
                    .then(
                        // /npc action <type>
                        literal("action")
                            .then(
                                argument("type", StringArgumentType.word())
                                    .executes { context ->
                                        val player = context.source.player
                                        if (player != null) {
                                            val type = StringArgumentType.getString(context, "type")
                                            onActionSelected(player, type)
                                        }
                                        1
                                    }
                            )
                    )
                    .then(
                        // /npc back
                        literal("back")
                            .executes { context ->
                                val player = context.source.player
                                if (player != null) {
                                    onBackSelected(player)
                                }
                                1
                            }
                    )
                    .then(
                        // /npc setid <id> - Set ID for NPC you're looking at
                        literal("setid")
                            .requires { source -> source.hasPermissionLevel(2) } // Op level 2
                            .then(
                                argument("id", StringArgumentType.word())
                                    .executes { context ->
                                        setNpcId(context)
                                        1
                                    }
                            )
                    )
                    .then(
                        // /npc cleanup - Remove duplicate NPCs
                        literal("cleanup")
                            .requires { source -> source.hasPermissionLevel(2) } // Op level 2
                            .executes { context ->
                                cleanupDuplicateNpcs(context)
                                1
                            }
                    )
            )
        }
    }

    private fun setNpcId(context: CommandContext<ServerCommandSource>) {
        val source = context.source
        val player = source.player

        if (player == null) {
            source.sendFeedback({ Text.literal("§cMust be a player to use this command") }, false)
            return
        }

        val newId = StringArgumentType.getString(context, "id")

        // Find closest NPC within 10 blocks
        val targetEntity: MIINNpcEntity? = source.world.iterateEntities()
            .filterIsInstance<MIINNpcEntity>()
            .filter { it.squaredDistanceTo(player) < 100.0 } // Within 10 blocks
            .minByOrNull { it.squaredDistanceTo(player) }

        if (targetEntity != null) {
            val oldId = targetEntity.npcId
            targetEntity.npcId = newId
            source.sendFeedback({
                Text.literal("§aSet NPC ID: §e$oldId §a→ §b$newId")
            }, false)
            logger.info("Player ${player.name.string} set NPC ID from '$oldId' to '$newId'")
        } else {
            source.sendFeedback({ Text.literal("§cNo NPC found nearby. Look at an NPC!") }, false)
        }
    }

    private fun cleanupDuplicateNpcs(context: CommandContext<ServerCommandSource>) {
        val source = context.source
        val server = source.server

        source.sendFeedback({ Text.literal("§6Scanning for duplicate NPCs...") }, false)

        val found = mutableMapOf<String, MutableList<MIINNpcEntity>>()
        var totalEntities = 0

        // Scan all worlds for NPC entities
        for (world in server.worlds) {
            for (entity in world.iterateEntities()) {
                if (entity is MIINNpcEntity) {
                    totalEntities++
                    found.computeIfAbsent(entity.npcId) { mutableListOf() }.add(entity)
                }
            }
        }

        source.sendFeedback({ Text.literal("§7Found $totalEntities total NPC entities") }, false)

        // Remove duplicates (keep first one, remove rest)
        var removedCount = 0
        found.forEach { (id, entities) ->
            if (entities.size > 1) {
                logger.warn("Found ${entities.size} duplicates of NPC '$id', removing ${entities.size - 1}")
                source.sendFeedback({
                    Text.literal("§cFound ${entities.size} copies of §e$id§c, removing ${entities.size - 1}")
                }, false)

                // Remove all except the first one
                entities.drop(1).forEach {
                    it.discard()
                    removedCount++
                }
            }
        }

        if (removedCount > 0) {
            source.sendFeedback({ Text.literal("§aRemoved $removedCount duplicate NPC(s)") }, false)
            logger.info("Cleanup removed $removedCount duplicate NPCs")
        } else {
            source.sendFeedback({ Text.literal("§aNo duplicates found!") }, false)
        }
    }

    private fun registerSpawnCommand() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal("MIIN")
                    .then(
                        literal("spawn")
                            .then(
                                argument("template_id", StringArgumentType.word())
                                    .executes { context ->
                                        val player = context.source.player
                                        if (player != null) {
                                            val templateId = StringArgumentType.getString(context, "template_id")
                                            spawnNpc(player, templateId, null)
                                        }
                                        1
                                    }
                                    .then(
                                        argument("name", StringArgumentType.greedyString())
                                            .executes { context ->
                                                val player = context.source.player
                                                if (player != null) {
                                                    val templateId = StringArgumentType.getString(context, "template_id")
                                                    val name = StringArgumentType.getString(context, "name")
                                                    spawnNpc(player, templateId, name)
                                                }
                                                1
                                            }
                                    )
                            )
                    )
            )
        }
    }
}
