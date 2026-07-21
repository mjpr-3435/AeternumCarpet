package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record CommandPacksUpdatePayload(String json) implements CustomPayload {
    public static final Id<CommandPacksUpdatePayload> ID = new Id<>(AeternumCarpet.id("update_command_packs"));
    public static final PacketCodec<RegistryByteBuf, CommandPacksUpdatePayload> CODEC = PacketCodec.of(
        (p, b) -> b.writeString(p.json, 1_000_000), b -> new CommandPacksUpdatePayload(b.readString(1_000_000)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
