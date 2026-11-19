package smp.aeternum.carpet.bot;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

public record BotOpenData(int entityId, int selectedSlot, int gameModeCode, boolean canSetCreative, String statusLabel) {
    public static final PacketCodec<RegistryByteBuf, BotOpenData> CODEC = PacketCodec.of(
        (d, b) -> {
            b.writeVarInt(d.entityId);
            b.writeVarInt(d.selectedSlot);
            b.writeVarInt(d.gameModeCode);
            b.writeBoolean(d.canSetCreative);
            b.writeString(d.statusLabel);
        },
        b -> new BotOpenData(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readString())
    );
}
