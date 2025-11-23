package miinkt.listener.component

import java.util.function.UnaryOperator
import net.minecraft.component.ComponentType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object MIINDataComponents {
    // Component to store list of items (like a bundle)
    val SATCHEL_CONTENTS: ComponentType<SatchelContentsComponent> =
            register("satchel_contents") { builder ->
                builder.codec(SatchelContentsComponent.CODEC)
                        .packetCodec(SatchelContentsComponent.PACKET_CODEC)
            }

    // Component to store lore metadata (book ID, author, etc.)
    val LORE_DATA: ComponentType<LoreDataComponent> =
            register("lore_data") { builder ->
                builder.codec(LoreDataComponent.CODEC).packetCodec(LoreDataComponent.PACKET_CODEC)
            }

    fun register() {
        // Static initialization triggers registration
    }

    private fun <T> register(
            name: String,
            builderOperator: UnaryOperator<ComponentType.Builder<T>>
    ): ComponentType<T> {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("miin-listener", name),
                (builderOperator.apply(ComponentType.builder())).build()
        )
    }
}
