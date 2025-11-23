/**
 * Lore Book Item - Custom books containing F-EIGHT canon lore
 *
 * When picked up and read, the lore is sent to the RAG system so NPCs can reference it in
 * conversations.
 */
package miinkt.listener.item

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import miinkt.listener.component.LoreDataComponent
import miinkt.listener.component.MIINDataComponents
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.WrittenBookContentComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.text.RawFilteredPair
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World
import org.slf4j.LoggerFactory

class LoreBookItem(settings: Settings) : net.minecraft.item.Item(settings) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("MIIN-lore-book")
        private val gson = Gson()
        private val httpClient = HttpClient.newBuilder().build()

        // MCP endpoint for sending lore to RAG
        private const val MCP_ENDPOINT = "http://localhost:5558/command"

        /** Create a lore book with the given content */
        fun createLoreBook(
                title: String,
                author: String,
                pages: List<String>,
                loreCategory: String,
                loreId: String
        ): ItemStack {
            val stack = ItemStack(Items.WRITTEN_BOOK)

            // Create book content using new component system
            val bookPages =
                    pages
                            .map { page -> RawFilteredPair.of<Text>(Text.literal(page)) }
                            .toMutableList()

            val bookContent =
                    WrittenBookContentComponent(
                            RawFilteredPair.of(title),
                            author,
                            0, // generation
                            bookPages,
                            true // resolved
                    )

            stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, bookContent)

            // Add custom lore metadata using custom data component
            val loreData = LoreDataComponent(loreId, loreCategory, false)
            stack.set(MIINDataComponents.LORE_DATA, loreData)

            return stack
        }

        /** Check if this is a MIIN lore book */
        fun isLoreBook(stack: ItemStack): Boolean {
            return stack.contains(MIINDataComponents.LORE_DATA)
        }

        /** Get lore content from a book stack */
        fun getLoreContent(stack: ItemStack): LoreContent? {
            val loreData = stack.get(MIINDataComponents.LORE_DATA) ?: return null
            val bookContent = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT) ?: return null

            val pages = bookContent.pages().map { it.raw().string }

            return LoreContent(
                    title = bookContent.title().raw(),
                    author = bookContent.author(),
                    category = loreData.category,
                    loreId = loreData.loreId,
                    pages = pages
            )
        }
    }

    data class LoreContent(
            val title: String,
            val author: String,
            val category: String,
            val loreId: String,
            val pages: List<String>
    )

    override fun use(world: World, player: PlayerEntity, hand: Hand): ActionResult {
        val stack = player.getStackInHand(hand)

        if (!world.isClient && isLoreBook(stack)) {
            val content = getLoreContent(stack)
            if (content != null) {
                // Send to RAG system
                sendLoreToRAG(content, player.name.string)

                // Mark as discovered in the item component
                val loreData = stack.get(MIINDataComponents.LORE_DATA)
                if (loreData != null && !loreData.discovered) {
                    stack.set(MIINDataComponents.LORE_DATA, loreData.copy(discovered = true))

                    // Notify player
                    player.sendMessage(
                            Text.literal("§6[Lore Discovered]§r ${content.title}"),
                            false
                    )
                    player.sendMessage(
                            Text.literal("§7This knowledge has been added to your chronicles."),
                            false
                    )
                }
            }
        }

        // Return parent behavior (opens book UI)
        return ActionResult.PASS
    }

    /** Send discovered lore to the RAG system */
    private fun sendLoreToRAG(content: LoreContent, playerName: String) {
        try {
            val command =
                    JsonObject().apply {
                        addProperty("type", "lore_discovered")
                        add(
                                "data",
                                JsonObject().apply {
                                    addProperty("player", playerName)
                                    addProperty("lore_id", content.loreId)
                                    addProperty("title", content.title)
                                    addProperty("author", content.author)
                                    addProperty("category", content.category)
                                    addProperty("content", content.pages.joinToString("\n\n"))
                                    addProperty("timestamp", System.currentTimeMillis())
                                }
                        )
                    }

            val request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(MCP_ENDPOINT))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(command)))
                            .build()

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept {
                    response ->
                if (response.statusCode() == 200) {
                    LOGGER.info("Lore '${content.title}' sent to RAG successfully")
                } else {
                    LOGGER.warn("Failed to send lore to RAG: ${response.statusCode()}")
                }
            }
        } catch (e: Exception) {
            LOGGER.error("Error sending lore to RAG", e)
        }
    }
}
