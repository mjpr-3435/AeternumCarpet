package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ProjectSubregionActionPayload(String projectName, String subregionName, String action) implements CustomPayload {
	public static final Id<ProjectSubregionActionPayload> ID = new Id<>(AeternumCarpet.id("project_subregion_action"));
	public static final PacketCodec<RegistryByteBuf, ProjectSubregionActionPayload> CODEC = PacketCodec.of(
		(p, b) -> {
			b.writeString(p.projectName, 64);
			b.writeString(p.subregionName, 128);
			b.writeString(p.action, 32);
		},
		b -> new ProjectSubregionActionPayload(b.readString(64), b.readString(128), b.readString(32))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
