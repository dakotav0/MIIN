package miinkt.listener.state

import miinkt.listener.entity.MIINNpcEntity

/** Dialogue state for a player's current NPC conversation */
data class DialogueState(
    val npcId: String,
    val npcName: String,
    val npcEntity: MIINNpcEntity,
    val options: MutableList<DialogueOption> = mutableListOf(),
    var showingActions: Boolean = false,
    var conversationId: String? = null
)

data class DialogueOption(
    val id: Int,
    val text: String,
    val tone: String = "neutral",
    val rollCheck: RollCheck? = null
)

/** Roll check metadata for skill checks */
data class RollCheck(
    val skill: String,       // e.g., "persuasion", "insight", "stealth"
    val difficulty: Int,     // DC (Difficulty Class) the player must meet
    val advantage: Boolean = false,  // Roll with advantage (roll twice, take higher)
    val disadvantage: Boolean = false  // Roll with disadvantage (roll twice, take lower)
)
