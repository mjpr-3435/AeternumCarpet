package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record MaterialsRefreshPayload() implements CustomPayload {
	public static final Id<MaterialsRefreshPayload> ID = new Id<>(AeternumCarpet.id("materials_refresh"));
	public static final PacketCodec<RegistryByteBuf, MaterialsRefreshPayload> CODEC = PacketCodec.of(
		(p, b) -> {},
		b -> new MaterialsRefreshPayload());
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
