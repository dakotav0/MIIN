package miinkt.listener.spawn

import com.fasterxml.jackson.annotation.JsonProperty

data class SpawnRulesConfig(
    val rules: List<SpawnRule> = emptyList()
)

data class SpawnRule(
    val id: String,
    @JsonProperty("target_entity")
    val targetEntity: String,
    val conditions: SpawnConditions,
    val apply: SpawnModifications
)

data class SpawnConditions(
    val biomes: List<String>? = null,
    val time: String? = null, // "day", "night", "midnight", etc.
    val chance: Double = 1.0,
    @JsonProperty("min_height")
    val minHeight: Int? = null,
    @JsonProperty("max_height")
    val maxHeight: Int? = null
)

data class SpawnModifications(
    val name: String? = null,
    @JsonProperty("health_multiplier")
    val healthMultiplier: Double? = null,
    val equipment: EquipmentConfig? = null,
    val metadata: Map<String, String>? = null
)

data class EquipmentConfig(
    @JsonProperty("main_hand")
    val mainHand: ItemConfig? = null,
    @JsonProperty("off_hand")
    val offHand: ItemConfig? = null,
    val helmet: ItemConfig? = null,
    val chestplate: ItemConfig? = null,
    val leggings: ItemConfig? = null,
    val boots: ItemConfig? = null
)

data class ItemConfig(
    val item: String,
    val name: String? = null,
    val lore: List<String>? = null,
    val count: Int = 1,
    @JsonProperty("drop_chance")
    val dropChance: Float? = null
)
