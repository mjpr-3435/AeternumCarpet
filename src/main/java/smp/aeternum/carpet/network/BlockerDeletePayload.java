package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record BlockerDeletePayload(String id) implements CustomPayload {
    public static final CustomPayload.Id<BlockerDeletePayload> ID = new CustomPayload.Id<>(AeternumCarpet.id("delete_blocker_scan"));
    public static final PacketCodec<RegistryByteBuf, BlockerDeletePayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeString(payload.id, 48), buf -> new BlockerDeletePayload(buf.readString(48)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
