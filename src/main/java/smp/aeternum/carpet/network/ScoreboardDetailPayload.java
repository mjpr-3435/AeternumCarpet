package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ScoreboardDetailPayload(String objectiveName, String json) implements CustomPayload {
    public static final Id<ScoreboardDetailPayload> ID = new Id<>(AeternumCarpet.id("scoreboard_detail"));
    public static final PacketCodec<RegistryByteBuf, ScoreboardDetailPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.string(128), ScoreboardDetailPayload::objectiveName,
        PacketCodecs.string(524_288), ScoreboardDetailPayload::json,
        ScoreboardDetailPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
