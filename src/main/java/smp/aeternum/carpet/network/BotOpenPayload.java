package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.bot.BotOpenData;

public record BotOpenPayload(int syncId, BotOpenData data) implements CustomPayload {
    public static final Id<BotOpenPayload> ID = new Id<>(AeternumCarpet.id("bot_open"));
    public static final PacketCodec<RegistryByteBuf, BotOpenPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.syncId);
            BotOpenData.CODEC.encode(buf, value.data);
        },
        buf -> new BotOpenPayload(buf.readVarInt(), BotOpenData.CODEC.decode(buf))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
