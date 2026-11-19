package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record PerimeterActionPayload(String action, String perimeterId, String arg1, String arg2) implements CustomPayload {
    public static final CustomPayload.Id<PerimeterActionPayload> ID = new CustomPayload.Id<>(AeternumCarpet.id("perimeter_action"));
    public static final PacketCodec<RegistryByteBuf, PerimeterActionPayload> CODEC = PacketCodec.of(
        (payload, buf) -> {
            buf.writeString(payload.action, 64);
            buf.writeString(payload.perimeterId, 64);
            buf.writeString(payload.arg1, 256);
            buf.writeString(payload.arg2, 256);
        },
        buf -> new PerimeterActionPayload(
            buf.readString(64),
            buf.readString(64),
            buf.readString(256),
            buf.readString(256)
        )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
