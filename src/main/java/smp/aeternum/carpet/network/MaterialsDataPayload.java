package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record MaterialsDataPayload(String json) implements CustomPayload {
	public static final Id<MaterialsDataPayload> ID = new Id<>(AeternumCarpet.id("materials_data"));
	public static final PacketCodec<RegistryByteBuf, MaterialsDataPayload> CODEC = PacketCodec.of(
		(p, b) -> b.writeString(p.json, 500_000),
		b -> new MaterialsDataPayload(b.readString(500_000)));
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
