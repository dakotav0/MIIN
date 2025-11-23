/**
 * MIIN NPC Renderer - Renders NPCs with player models
 *
 * This renderer uses the PlayerEntityModel to give NPCs a humanoid appearance with custom skins.
 */
package miinkt.listener.entity

import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.util.Identifier

class MIINNpcRenderState : net.minecraft.client.render.entity.state.PlayerEntityRenderState() {
    var skinIdentifier: Identifier? = null
}

class MIINNpcRenderer(context: EntityRendererFactory.Context) :
        net.minecraft.client.render.entity.MobEntityRenderer<MIINNpcEntity, MIINNpcRenderState, PlayerEntityModel>(
                context,
                PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false),
                0.5f // Shadow radius
        ) {

    companion object {
        // Default skin for NPCs without custom skin
        private val STEVE_TEXTURE =
                Identifier.of("minecraft", "textures/entity/player/wide/steve.png")
    }

    override fun getTexture(state: MIINNpcRenderState): Identifier {
        // Use custom skin if available, otherwise Steve
        return state.skinIdentifier ?: STEVE_TEXTURE
    }

    override fun createRenderState(): MIINNpcRenderState {
        return MIINNpcRenderState()
    }

    override fun updateRenderState(
        entity: MIINNpcEntity,
        state: MIINNpcRenderState,
        tickDelta: Float
    ) {
        super.updateRenderState(entity, state, tickDelta)
        // Store entity's skin in the state
        state.skinIdentifier = entity.skinIdentifier
    }
}
