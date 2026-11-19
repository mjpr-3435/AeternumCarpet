package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ProjectPlacementPayload(
	String projectName,
	String dimension,
	int x,
	int y,
	int z,
	String rotation,
	String mirror
) implements CustomPayload {
	public static final Id<ProjectPlacementPayload> ID = new Id<>(AeternumCarpet.id("project_placement"));
	public static final PacketCodec<RegistryByteBuf, ProjectPlacementPayload> CODEC = PacketCodec.of(
		(p, b) -> {
			b.writeString(p.projectName, 64);
			b.writeString(p.dimension, 128);
			b.writeInt(p.x);
			b.writeInt(p.y);
			b.writeInt(p.z);
			b.writeString(p.rotation, 32);
			b.writeString(p.mirror, 32);
		},
		b -> new ProjectPlacementPayload(
			b.readString(64),
			b.readString(128),
			b.readInt(),
			b.readInt(),
			b.readInt(),
			b.readString(32),
			b.readString(32)
		)
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
