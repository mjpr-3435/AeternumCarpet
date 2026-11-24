package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ClientHelloPayload(int protocolVersion) implements CustomPayload {
    public static final int CURRENT_PROTOCOL_VERSION = 2;
    public static final Id<ClientHelloPayload> ID = new Id<>(AeternumCarpet.id("client_hello"));
    public static final PacketCodec<RegistryByteBuf, ClientHelloPayload> CODEC = PacketCodec.of(
        (p, b) -> b.writeVarInt(p.protocolVersion),
        b -> new ClientHelloPayload(b.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
