package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record CommandActionRunPayload(String packId) implements CustomPayload {
    public static final Id<CommandActionRunPayload> ID = new Id<>(AeternumCarpet.id("run_command_action"));
    public static final PacketCodec<RegistryByteBuf, CommandActionRunPayload> CODEC = PacketCodec.of(
        (p, b) -> b.writeString(p.packId, 36), b -> new CommandActionRunPayload(b.readString(36)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
