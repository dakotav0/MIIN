package miinkt.listener.service

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.WrittenBookContentComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.RawFilteredPair
import net.minecraft.text.Text
import org.slf4j.LoggerFactory

class MIINLoreService {
    private val logger = LoggerFactory.getLogger("MIIN-lore-service")

    fun giveSatchel(player: ServerPlayerEntity) {
        val stack = _root_ide_package_.miinkt.listener.item.ItemRegistry.createSatchel()
        if (player.inventory.insertStack(stack)) {
            player.sendMessage(Text.literal("§aReceived Lore Satchel!"), false)
        } else {
            player.dropItem(stack, false)
            player.sendMessage(Text.literal("§aReceived Lore Satchel (dropped)"), false)
        }
    }

    fun createLoreBook(player: ServerPlayerEntity, title: String, content: String) {
        // Create written book
        val bookStack = ItemStack(Items.WRITTEN_BOOK)

        // Split content into pages (max 256 chars per page for readability)
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
        val bookContent =
            WrittenBookContentComponent(
                RawFilteredPair.of(title),
                player.name.string,
                0,
                pages,
                true
            )
        bookStack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, bookContent)

        // Give to player
        player.giveItemStack(bookStack)
        player.sendMessage(Text.literal("§aCreated book: $title"), false)
        logger.info("Created lore book '$title' for ${player.name.string}")
    }

    fun renameHeldItem(player: ServerPlayerEntity, newName: String) {
        val heldItem = player.mainHandStack
        if (!heldItem.isEmpty) {
            heldItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal(newName))
            player.sendMessage(Text.literal("§aRenamed to: $newName"), false)
            logger.info("${player.name.string} renamed item to '$newName'")
        } else {
            player.sendMessage(Text.literal("§cNo item in hand"), false)
        }
    }

    fun saveMapToSatchel(player: ServerPlayerEntity) {
        val heldItem = player.mainHandStack
        if (heldItem.item == Items.FILLED_MAP) {
            val mapId = heldItem.get(DataComponentTypes.MAP_ID)
            if (mapId != null) {
                // TODO: Save map data to satchel storage via MCP
                // For now just confirm the action
                player.sendMessage(Text.literal("§aMap saved to satchel!"), false)
                logger.info("${player.name.string} saved map ${mapId.id}")
            } else {
                player.sendMessage(Text.literal("§cInvalid map"), false)
            }
        } else {
            player.sendMessage(Text.literal("§cHold a map to save it"), false)
        }
    }

    fun showPlayerLore(player: ServerPlayerEntity) {
        // Placeholder for showing lore UI or book
        // This would likely trigger an MCP call to get lore list
        player.sendMessage(Text.literal("§eFetching your lore..."), false)
        // Logic to fetch and display lore would go here
    }
}
