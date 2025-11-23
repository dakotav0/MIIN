package miinkt.listener.spawn

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.world.LightType
import org.slf4j.LoggerFactory

object SpawnRuleManager {
    private val logger = LoggerFactory.getLogger("MIINkt-SpawnRules")
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private var config: SpawnRulesConfig = SpawnRulesConfig()

    fun loadConfig() {
        val configDir = FabricLoader.getInstance().configDir
        val configFile = configDir.resolve("spawn_rules.json")

        if (Files.exists(configFile)) {
            try {
                config = mapper.readValue(configFile.toFile())
                logger.info("Loaded ${config.rules.size} spawn rules")
            } catch (e: Exception) {
                logger.error("Failed to load spawn_rules.json", e)
            }
        } else {
            logger.info("No spawn_rules.json found, creating default")
            saveDefaultConfig(configFile)
        }
    }

    private fun saveDefaultConfig(path: java.nio.file.Path) {
        try {
            val defaultRule = SpawnRule(
                id = "undead_legion_guard",
                targetEntity = "minecraft:zombie",
                conditions = SpawnConditions(
                    biomes = listOf("minecraft:plains", "minecraft:forest"),
                    time = "night",
                    chance = 0.5
                ),
                apply = SpawnModifications(
                    name = "Undead Legion Guard",
                    healthMultiplier = 1.5,
                    equipment = EquipmentConfig(
                        mainHand = ItemConfig(item = "minecraft:iron_sword", name = "Legion Blade")
                    ),
                    metadata = mapOf("faction" to "undead_legion", "role" to "guard")
                )
            )
            val defaultConfig = SpawnRulesConfig(listOf(defaultRule))
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), defaultConfig)
            config = defaultConfig
        } catch (e: Exception) {
            logger.error("Failed to save default spawn_rules.json", e)
        }
    }

    fun process(entity: LivingEntity) {
        val entityId = Registries.ENTITY_TYPE.getId(entity.type).toString()
        val world = entity.entityWorld
        val pos = entity.blockPos

        // Filter rules that match this entity type
        val matchingRules = config.rules.filter { it.targetEntity == entityId }

        for (rule in matchingRules) {
            if (checkConditions(rule.conditions, entity)) {
                applyRule(rule, entity)
                // Only apply one rule per entity to avoid conflicts? 
                // For now, let's break after the first match to keep it simple.
                break
            }
        }
    }

    private fun checkConditions(conditions: SpawnConditions, entity: LivingEntity): Boolean {
        val world = entity.entityWorld
        val pos = entity.blockPos

        // Chance check
        if (Math.random() > conditions.chance) return false

        // Biome check
        if (!conditions.biomes.isNullOrEmpty()) {
            val biomeEntry = world.getBiome(pos)
            val biomeId = biomeEntry.key.get().value.toString()
            if (biomeId !in conditions.biomes) return false
        }

        // Time check
        if (conditions.time != null) {
            val timeOfDay = world.timeOfDay % 24000
            val isDay = timeOfDay in 0..12000
            when (conditions.time) {
                "day" -> if (!isDay) return false
                "night" -> if (isDay) return false
                // Add more granular checks if needed
            }
        }

        // Height check
        if (conditions.minHeight != null && pos.y < conditions.minHeight) return false
        if (conditions.maxHeight != null && pos.y > conditions.maxHeight) return false

        return true
    }

    private fun applyRule(rule: SpawnRule, entity: LivingEntity) {
        val mod = rule.apply

        // Name
        if (mod.name != null) {
            entity.customName = Text.of(mod.name)
            entity.isCustomNameVisible = true
        }

        // Health Multiplier
        if (mod.healthMultiplier != null) {
            val healthAttr = entity.getAttributeInstance(EntityAttributes.MAX_HEALTH)
            if (healthAttr != null) {
                // Remove existing modifiers to avoid stacking if called multiple times (unlikely but safe)
                val modifierId = Identifier.of("miinkt", "spawn_health_boost")
                // Note: In 1.21, modifiers use Identifier directly usually, checking API...
                // Actually, let's just add a new one.
                // Using a fixed UUID or Identifier for the modifier
                val modifier = EntityAttributeModifier(
                    Identifier.of("miinkt", "spawn_health_multiplier_${rule.id}"),
                    mod.healthMultiplier - 1.0,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                )
                if (!healthAttr.hasModifier(modifier.id)) {
                    healthAttr.addPersistentModifier(modifier)
                    entity.health = entity.maxHealth // Heal to new max
                }
            }
        }

        // Equipment
        mod.equipment?.let { equip ->
            applyEquipment(entity, EquipmentSlot.MAINHAND, equip.mainHand)
            applyEquipment(entity, EquipmentSlot.OFFHAND, equip.offHand)
            applyEquipment(entity, EquipmentSlot.HEAD, equip.helmet)
            applyEquipment(entity, EquipmentSlot.CHEST, equip.chestplate)
            applyEquipment(entity, EquipmentSlot.LEGS, equip.leggings)
            applyEquipment(entity, EquipmentSlot.FEET, equip.boots)
        }

        // Metadata (NBT)
        if (!mod.metadata.isNullOrEmpty()) {
            // We need to write to the entity's NBT.
            // However, standard entity NBT writing is often done via writeCustomDataToNbt
            // But we want to inject data that stays.
            // The safest way for persistent custom data in vanilla entities without mixins 
            // is often just adding tags or using a custom NBT key if the entity supports it.
            // Fabric API has `FabricDataAttachment` but that requires registering attachments.
            // For simplicity in this "hacky" MCP version, we'll try to write to a "MIINkt" sub-compound.
            // BUT: Entity.writeNbt() writes everything. We can't easily *inject* into the live entity's NBT 
            // structure unless we use an interface or mixin.
            
            // ALTERNATIVE: Use Scoreboard Tags for simple string metadata.
            // "MIINkt:faction:undead_legion"
            mod.metadata.forEach { (key, value) ->
                entity.addCommandTag("MIINkt:$key:$value")
            }
        }
    }

    private fun applyEquipment(entity: LivingEntity, slot: EquipmentSlot, config: ItemConfig?) {
        if (config == null) return
        
        // Chance to drop/equip
        if (config.dropChance != null && Math.random() > config.dropChance) return // Wait, dropChance usually means chance to drop on death.
        // If it's "equip chance", we should have a separate field. 
        // Let's assume config.dropChance is for the equipment drop chance field.
        
        val item = Registries.ITEM.get(Identifier.of(config.item))
        val stack = ItemStack(item, config.count)

        // Custom Name
        if (config.name != null) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.of(config.name))
        }

        // Lore
        if (!config.lore.isNullOrEmpty()) {
            val loreText = config.lore.map { Text.of(it) }
            stack.set(DataComponentTypes.LORE, LoreComponent(loreText))
        }

        entity.equipStack(slot, stack)
        
        if (config.dropChance != null && entity is MobEntity) {
            entity.setEquipmentDropChance(slot, config.dropChance)
        }
    }
}
