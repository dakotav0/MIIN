package miinkt.listener.state

import net.minecraft.server.network.ServerPlayerEntity

/** Player State - tracks player state for change detection */
data class PlayerState(
    val playerId: String,
    var lastX: Int = 0,
    var lastY: Int = 0,
    var lastZ: Int = 0,
    var lastDimension: String = "",
    var lastBiome: String = "",
    var lastHealth: Float = 0f,
    var changedSinceLastSend: Boolean = false
) {
    fun update(player: ServerPlayerEntity) {
        val x = player.x.toInt()
        val y = player.y.toInt()
        val z = player.z.toInt()
        val dimension = player.entityWorld.registryKey.value.toString()
        val biome =
            player.entityWorld.getBiome(player.blockPos).key.orElse(null)?.value?.path
                ?: "unknown"
        val health = player.health

        // Detect significant changes (moved >10 blocks, dimension change, biome change, health
        // change >2)
        val moved =
            Math.abs(x - lastX) > 10 || Math.abs(y - lastY) > 10 || Math.abs(z - lastZ) > 10
        val dimensionChanged = dimension != lastDimension
        val biomeChanged = biome != lastBiome
        val healthChanged = Math.abs(health - lastHealth) > 2.0f

        if (moved || dimensionChanged || biomeChanged || healthChanged) {
            lastX = x
            lastY = y
            lastZ = z
            lastDimension = dimension
            lastBiome = biome
            lastHealth = health
            changedSinceLastSend = true
        }
    }

    fun hasSignificantChange(): Boolean = changedSinceLastSend

    fun markSent() {
        changedSinceLastSend = false
    }
}
