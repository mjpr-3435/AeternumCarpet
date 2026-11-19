package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record PerimeterHudPayload(String json) implements CustomPayload {
    public static final CustomPayload.Id<PerimeterHudPayload> ID = new CustomPayload.Id<>(AeternumCarpet.id("perimeter_hud"));
    public static final PacketCodec<RegistryByteBuf, PerimeterHudPayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeString(payload.json, 262_144),
        buf -> new PerimeterHudPayload(buf.readString(262_144))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
