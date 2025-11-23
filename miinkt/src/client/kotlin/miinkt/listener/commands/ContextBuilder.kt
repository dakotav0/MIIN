package miinkt.listener.commands

import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos

class ContextBuilder {
    fun buildGameContext(): String {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return "Player not available"
        val world = client.world ?: return "World not available"
        
        val context = StringBuilder()
        context.append("Player: ${player.name.string}\n")
        context.append("Health: ${player.health}/${player.maxHealth}\n")
        context.append("Position: ${player.blockPos}\n")
        context.append("Dimension: ${world.registryKey.value}\n")
        
        // Add nearby blocks/entities if needed
        return context.toString()
    }
}