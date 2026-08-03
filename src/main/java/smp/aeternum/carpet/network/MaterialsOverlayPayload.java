package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record MaterialsOverlayPayload(String json) implements CustomPayload {
	public static final Id<MaterialsOverlayPayload> ID = new Id<>(AeternumCarpet.id("materials_overlay"));
	public static final PacketCodec<RegistryByteBuf, MaterialsOverlayPayload> CODEC = PacketCodec.of(
		(p, b) -> b.writeString(p.json, 100_000),
		b -> new MaterialsOverlayPayload(b.readString(100_000)));
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
