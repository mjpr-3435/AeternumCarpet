package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record MaterialsScanPayload(String listName) implements CustomPayload {
	public static final Id<MaterialsScanPayload> ID = new Id<>(AeternumCarpet.id("materials_scan"));
	public static final PacketCodec<RegistryByteBuf, MaterialsScanPayload> CODEC = PacketCodec.of(
		(p, b) -> b.writeString(p.listName, 80),
		b -> new MaterialsScanPayload(b.readString(80)));
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
