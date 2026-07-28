package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record MaterialsDeletePayload(String listName) implements CustomPayload {
	public static final Id<MaterialsDeletePayload> ID = new Id<>(AeternumCarpet.id("materials_delete"));
	public static final PacketCodec<RegistryByteBuf, MaterialsDeletePayload> CODEC = PacketCodec.of(
		(p, b) -> b.writeString(p.listName, 80),
		b -> new MaterialsDeletePayload(b.readString(80)));
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
