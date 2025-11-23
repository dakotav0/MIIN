/**
 * NPC Registry - Registers NPC entity types
 */

package miinkt.listener.entity

import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.world.World
import org.slf4j.LoggerFactory

object NpcRegistry {
    private val LOGGER = LoggerFactory.getLogger("MIIN-npc-registry")

    private val NPC_ID = Identifier.of("miin-listener", "miin_npc")
    private val NPC_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, NPC_ID)
    // Entity type for MIIN NPCs
    val MIIN_NPC: EntityType<MIINNpcEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        NPC_ID,
        FabricEntityTypeBuilder.create(SpawnGroup.MISC) { entityType: EntityType<MIINNpcEntity>, world: World ->
            MIINNpcEntity(entityType, world)
        }
            .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
            .trackRangeBlocks(48)
            .build(NPC_KEY)
    )

    fun register() {
        LOGGER.info("Registering MIIN NPC entity types...")

        // Register entity attributes
        FabricDefaultAttributeRegistry.register(MIIN_NPC, MIINNpcEntity.createMobAttributes())

        LOGGER.info("MIIN NPC entity types registered!")
    }
}
