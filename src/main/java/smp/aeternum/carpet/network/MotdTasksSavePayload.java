package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record MotdTasksSavePayload(String json) implements CustomPayload {
    public static final Id<MotdTasksSavePayload> ID = new Id<>(AeternumCarpet.id("motd_tasks_save"));
    public static final PacketCodec<RegistryByteBuf, MotdTasksSavePayload> CODEC = PacketCodec.of(
        (p, b) -> b.writeString(p.json, 32_000),
        b -> new MotdTasksSavePayload(b.readString(32_000))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
