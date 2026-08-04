package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ExplorationMapStatusRequestPayload() implements CustomPayload {
    public static final Id<ExplorationMapStatusRequestPayload> ID = new Id<>(AeternumCarpet.id("exploration_map_status_request"));
    public static final PacketCodec<RegistryByteBuf, ExplorationMapStatusRequestPayload> CODEC = PacketCodec.of(
        (payload, buffer) -> {},
        buffer -> new ExplorationMapStatusRequestPayload());
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
