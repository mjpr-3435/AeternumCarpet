package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record StatsDataPayload(String json, boolean openScreen, String statusMessage, boolean statusError) implements CustomPayload {
    public static final Id<StatsDataPayload> ID = new Id<>(AeternumCarpet.id("stats_data"));
    public static final PacketCodec<RegistryByteBuf, StatsDataPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.string(1_048_576), StatsDataPayload::json,
        PacketCodecs.BOOLEAN, StatsDataPayload::openScreen,
        PacketCodecs.STRING, StatsDataPayload::statusMessage,
        PacketCodecs.BOOLEAN, StatsDataPayload::statusError,
        StatsDataPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
