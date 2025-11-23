package miinkt.listener.state

/** Build Session - tracks a player's building activity */
data class BuildSession(
    val playerId: String,
    val playerName: String,
    var startTime: Long = System.currentTimeMillis(),
    val blockCounts: MutableMap<String, Int> = mutableMapOf(),
    var blockCount: Int = 0,
    var lastActivity: Long = System.currentTimeMillis()
) {
    fun addBlock(blockName: String) {
        blockCounts[blockName] = blockCounts.getOrDefault(blockName, 0) + 1
        blockCount++
        lastActivity = System.currentTimeMillis()
    }

    fun getUniqueBlocks(): List<String> {
        return blockCounts.keys.toList()
    }

    fun getDuration(): Long {
        return (System.currentTimeMillis() - startTime) / 1000 // seconds
    }

    fun shouldFinalize(): Boolean {
        // Finalize if inactive for 30 seconds
        val inactiveTime = System.currentTimeMillis() - lastActivity
        return inactiveTime > 30_000 && blockCount > 0
    }

    fun reset() {
        blockCounts.clear()
        blockCount = 0
        startTime = System.currentTimeMillis()
        lastActivity = System.currentTimeMillis()
    }
}
