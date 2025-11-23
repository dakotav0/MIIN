package miinkt.listener.handler

import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import org.slf4j.LoggerFactory

class ResponseHandler {
    private val logger = LoggerFactory.getLogger("response-handler")
    
    fun displayResponse(response: String) {
        val client = MinecraftClient.getInstance()
        client.execute {
            client.player?.sendMessage(Text.literal("§a[MIIN]: §f$response"), false)
        }
    }
    
    fun displayError(error: String) {
        val client = MinecraftClient.getInstance()
        client.execute {
            client.player?.sendMessage(Text.literal("§c[MIIN Error]: §f$error"), false)
        }
    }
}