package miinkt.listener.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.item.ItemStack
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

data class SatchelContentsComponent(val stacks: List<ItemStack>) {
    companion object {
        val DEFAULT = SatchelContentsComponent(emptyList())

        val CODEC: Codec<SatchelContentsComponent> =
                RecordCodecBuilder.create { instance ->
                    instance.group(
                                    ItemStack.CODEC
                                            .listOf()
                                            .fieldOf("stacks")
                                            .forGetter(SatchelContentsComponent::stacks)
                            )
                            .apply(instance, ::SatchelContentsComponent)
                }

        val PACKET_CODEC: PacketCodec<RegistryByteBuf, SatchelContentsComponent> =
                PacketCodec.tuple(
                        ItemStack.PACKET_CODEC.collect(PacketCodecs.toList()),
                        SatchelContentsComponent::stacks,
                        ::SatchelContentsComponent
                )
    }
}
