package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record DockStatePayload(String json) implements CustomPayload {
    public static final Id<DockStatePayload> ID = new Id<>(AeternumCarpet.id("dock_state"));
    public static final PacketCodec<RegistryByteBuf, DockStatePayload> CODEC = PacketCodec.of(
        (p, b) -> b.writeString(p.json, 128_000),
        b -> new DockStatePayload(b.readString(128_000))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
