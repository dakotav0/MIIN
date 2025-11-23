package miinkt.listener.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

data class LoreDataComponent(val loreId: String, val category: String, val discovered: Boolean) {
        companion object {
                val DEFAULT = LoreDataComponent("", "unknown", false)

                val CODEC: Codec<LoreDataComponent> =
                        RecordCodecBuilder.create { instance ->
                                instance.group(
                                                Codec.STRING
                                                        .fieldOf("lore_id")
                                                        .forGetter(LoreDataComponent::loreId),
                                                Codec.STRING
                                                        .fieldOf("category")
                                                        .forGetter(LoreDataComponent::category),
                                                Codec.BOOL
                                                        .fieldOf("discovered")
                                                        .forGetter(LoreDataComponent::discovered)
                                        )
                                        .apply(instance, ::LoreDataComponent)
                        }

                val PACKET_CODEC: PacketCodec<RegistryByteBuf, LoreDataComponent> =
                        PacketCodec.tuple(
                                PacketCodecs.STRING,
                                LoreDataComponent::loreId,
                                PacketCodecs.STRING,
                                LoreDataComponent::category,
                                PacketCodecs.BOOLEAN,
                                LoreDataComponent::discovered,
                                ::LoreDataComponent
                        )
        }
}
