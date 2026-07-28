package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record MaterialsActionPayload(String listName, String itemId, String player, boolean claim) implements CustomPayload {
	public static final Id<MaterialsActionPayload> ID = new Id<>(AeternumCarpet.id("materials_action"));
	public static final PacketCodec<RegistryByteBuf, MaterialsActionPayload> CODEC = PacketCodec.of(
		(p, b) -> { b.writeString(p.listName, 80); b.writeString(p.itemId, 128); b.writeString(p.player, 32); b.writeBoolean(p.claim); },
		b -> new MaterialsActionPayload(b.readString(80), b.readString(128), b.readString(32), b.readBoolean()));
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
