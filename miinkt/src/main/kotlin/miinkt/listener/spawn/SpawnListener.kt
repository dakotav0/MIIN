package miinkt.listener.spawn

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld

object SpawnListener {
    fun register() {
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity is LivingEntity && world is ServerWorld) {
                // Check if already processed to avoid re-applying rules on chunk load
                if (!entity.commandTags.contains("MIINkt:processed")) {
                    SpawnRuleManager.process(entity)
                    entity.addCommandTag("MIINkt:processed")
                }
            }
        }
    }
}
