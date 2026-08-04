package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ExplorationMapStatusPayload(float mspt) implements CustomPayload {
    public static final Id<ExplorationMapStatusPayload> ID = new Id<>(AeternumCarpet.id("exploration_map_status"));
    public static final PacketCodec<RegistryByteBuf, ExplorationMapStatusPayload> CODEC = PacketCodec.of(
        (payload, buffer) -> buffer.writeFloat(payload.mspt),
        buffer -> new ExplorationMapStatusPayload(buffer.readFloat()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
