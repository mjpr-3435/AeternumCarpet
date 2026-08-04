package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ScreenOpenRequestPayload(String screen) implements CustomPayload {
    public static final Id<ScreenOpenRequestPayload> ID = new Id<>(AeternumCarpet.id("screen_open_request"));
    public static final PacketCodec<RegistryByteBuf, ScreenOpenRequestPayload> CODEC = PacketCodec.of(
        (payload, buffer) -> buffer.writeString(payload.screen, 32),
        buffer -> new ScreenOpenRequestPayload(buffer.readString(32)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
