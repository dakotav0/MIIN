/**
 * Item Registry - Registers all MIIN custom items
 */

package miinkt.listener.item

import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

object ItemRegistry {
    private val LOGGER = LoggerFactory.getLogger("MIIN-items")

    // Item instances (lateinit to avoid null pointer)
    lateinit var LORE_SATCHEL: LoreSatchelItem
    lateinit var LORE_BOOK: LoreBookItem

    /**
     * Register all items with the game registry
     */
    fun register() {
        val satchelId = Identifier.of("miin-listener", "lore_satchel")
        val satchelKey = RegistryKey.of(RegistryKeys.ITEM, satchelId)
        LORE_SATCHEL = Registry.register(
            Registries.ITEM,
            satchelKey,
            LoreSatchelItem(Item.Settings().maxCount(1).registryKey(satchelKey))
        )
        LOGGER.info("Registered lore_satchel item")

        val bookId = Identifier.of("miin-listener", "lore_book")
        val bookKey = RegistryKey.of(RegistryKeys.ITEM, bookId)
        LORE_BOOK = Registry.register(
            Registries.ITEM,
            bookKey,
            LoreBookItem(Item.Settings().maxCount(1).registryKey(bookKey))
        )
        LOGGER.info("Registered lore_book item")

        LOGGER.info("MIIN items registered successfully!")
    }

    /**
     * Create a new lore satchel item stack
     */
    fun createSatchel(): net.minecraft.item.ItemStack {
        return net.minecraft.item.ItemStack(LORE_SATCHEL)
    }

    /**
     * Create a new lore book item stack
     */
    fun createLoreBook(): net.minecraft.item.ItemStack {
        return net.minecraft.item.ItemStack(LORE_BOOK)
    }
}
