/**
 * Lore Satchel Item - Portable collection of discovered lore
 *
 * Features:
 * - Shows all discovered lore books
 * - Feeds RAG context for NPC knowledge
 * - Tracks completion progress
 */
package miinkt.listener.item

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import miinkt.listener.component.MIINDataComponents
import miinkt.listener.component.SatchelContentsComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World
import org.slf4j.LoggerFactory

class LoreSatchelItem(settings: Settings) : net.minecraft.item.Item(settings) {
    private val logger = LoggerFactory.getLogger("MIIN-lore-satchel")
    private val gson = Gson()
    private val httpClient = HttpClient.newBuilder().build()

    // MCP Server endpoint
    private val MCP_ENDPOINT = "http://localhost:5557/mcp/call"

    override fun use(world: World, user: PlayerEntity, hand: Hand): ActionResult {
        val stack = user.getStackInHand(hand)

        // Client side: just play animation
        if (world.isClient) {
            return ActionResult.SUCCESS
        }

        // Server side logic
        if (!world.isClient) {
            val otherHand = if (hand == Hand.MAIN_HAND) Hand.OFF_HAND else Hand.MAIN_HAND
            val otherStack = user.getStackInHand(otherHand)

            // Check if we are trying to insert an item (holding a Lore Book in other hand)
            if (LoreBookItem.isLoreBook(otherStack)) {
                insertItem(stack, otherStack, user)
            } else {
                // Otherwise, show contents
                showContents(stack, user)
            }
        }

        return ActionResult.SUCCESS
    }

    private fun insertItem(satchel: ItemStack, itemToInsert: ItemStack, user: PlayerEntity) {
        val currentContents =
                satchel.get(MIINDataComponents.SATCHEL_CONTENTS) ?: SatchelContentsComponent.DEFAULT
        val newStacks = ArrayList(currentContents.stacks)

        // Add the item (copy it)
        newStacks.add(itemToInsert.copy())

        // Update satchel component
        satchel.set(MIINDataComponents.SATCHEL_CONTENTS, SatchelContentsComponent(newStacks))

        // Consume the item from player's hand
        itemToInsert.decrement(itemToInsert.count)

        user.sendMessage(Text.literal("§aStored ${newStacks.last().name.string} in satchel"), true)
    }

    private fun showContents(stack: ItemStack, user: PlayerEntity) {
        val contents = stack.get(MIINDataComponents.SATCHEL_CONTENTS)

        if (contents != null && contents.stacks.isNotEmpty()) {
            // Show stored items
            user.sendMessage(Text.literal(""), false)
            user.sendMessage(Text.literal("§6§l=== Satchel Contents ==="), false)
            contents.stacks.forEach { itemStack ->
                user.sendMessage(
                        Text.literal("  §7- ${itemStack.name.string} x${itemStack.count}"),
                        false
                )
            }
            user.sendMessage(Text.literal(""), false)
        } else {
            // Empty satchel - fetch global progress from MCP
            fetchAndShowProgress(user)
        }
    }

    private fun fetchAndShowProgress(user: PlayerEntity) {
        val playerName = user.name.string
        user.sendMessage(Text.literal("§6Checking lore progress..."), false)

        // Fetch lore progress from service
        try {
            val toolCall =
                    com.google.gson.JsonObject().apply {
                        addProperty("tool", "minecraft_lore_progress")
                        add(
                                "arguments",
                                com.google.gson.JsonObject().apply {
                                    addProperty("player", playerName)
                                }
                        )
                    }

            val request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(MCP_ENDPOINT))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(toolCall)))
                            .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val mcpResponse = JsonParser.parseString(response.body()).asJsonObject
                val result = mcpResponse.getAsJsonObject("result")
                val content = result?.getAsJsonArray("content")
                val textContent = content?.get(0)?.asJsonObject?.get("text")?.asString

                val progress =
                        if (textContent != null) {
                            JsonParser.parseString(textContent).asJsonObject
                        } else null

                if (progress != null) {
                    val discovered = progress.get("discovered")?.asInt ?: 0
                    val total = progress.get("total")?.asInt ?: 0
                    val completion = progress.get("completion")?.asFloat ?: 0f
                    val percent = (completion * 100).toInt()

                    // Display satchel summary
                    user.sendMessage(Text.literal(""), false)
                    user.sendMessage(Text.literal("§6§l=== Lore Satchel (Empty) ==="), false)
                    user.sendMessage(
                            Text.literal("§fDiscovered: §e$discovered§f / §e$total §7($percent%)"),
                            false
                    )
                    user.sendMessage(Text.literal(""), false)
                    user.sendMessage(
                            Text.literal(
                                    "§7Tip: Hold satchel and right-click with a Lore Book in offhand to store it!"
                            ),
                            false
                    )
                } else {
                    user.sendMessage(Text.literal("§cFailed to load satchel contents"), false)
                }
            } else {
                user.sendMessage(Text.literal("§cCould not connect to lore service"), false)
            }
        } catch (e: Exception) {
            logger.error("Error fetching lore progress", e)
            user.sendMessage(Text.literal("§cError: ${e.message}"), false)
        }
    }

    companion object {
        /** Create a satchel with initial data */
        fun createSatchel(): ItemStack {
            return ItemRegistry.createSatchel()
        }
    }
}
