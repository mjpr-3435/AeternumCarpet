package smp.aeternum.carpet.bot;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

public record BotOpenData(int entityId, int selectedSlot) {
    public static final PacketCodec<RegistryByteBuf, BotOpenData> CODEC = PacketCodec.of(
        (d, b) -> {
            b.writeVarInt(d.entityId);
            b.writeVarInt(d.selectedSlot);
        },
        b -> new BotOpenData(b.readVarInt(), b.readVarInt())
    );
}
