package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record CommandPacksDataPayload(String json, boolean openScreen, boolean canEdit) implements CustomPayload {
    public static final Id<CommandPacksDataPayload> ID = new Id<>(AeternumCarpet.id("command_packs_data"));
    public static final PacketCodec<RegistryByteBuf, CommandPacksDataPayload> CODEC = PacketCodec.of(
        (p, b) -> { b.writeString(p.json, 1_000_000); b.writeBoolean(p.openScreen); b.writeBoolean(p.canEdit); },
        b -> new CommandPacksDataPayload(b.readString(1_000_000), b.readBoolean(), b.readBoolean()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
