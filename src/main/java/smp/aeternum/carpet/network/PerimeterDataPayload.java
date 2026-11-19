package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record PerimeterDataPayload(String json, boolean openScreen, boolean canDelete) implements CustomPayload {
    public static final CustomPayload.Id<PerimeterDataPayload> ID = new CustomPayload.Id<>(AeternumCarpet.id("perimeter_data"));
    public static final PacketCodec<RegistryByteBuf, PerimeterDataPayload> CODEC = PacketCodec.of(
        (payload, buf) -> {
            buf.writeString(payload.json, 1_000_000);
            buf.writeBoolean(payload.openScreen);
            buf.writeBoolean(payload.canDelete);
        },
        buf -> new PerimeterDataPayload(buf.readString(1_000_000), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
