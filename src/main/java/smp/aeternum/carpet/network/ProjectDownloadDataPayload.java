package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ProjectDownloadDataPayload(
	String projectName,
	byte[] bytes,
	boolean hasPlacement,
	String dimension,
	int x,
	int y,
	int z,
	String rotation,
	String mirror,
	boolean loadInLitematica
) implements CustomPayload {
	public static final Id<ProjectDownloadDataPayload> ID = new Id<>(AeternumCarpet.id("project_download_data"));
	public static final PacketCodec<RegistryByteBuf, ProjectDownloadDataPayload> CODEC = PacketCodec.of(
		(p, b) -> {
			b.writeString(p.projectName, 64);
			b.writeByteArray(p.bytes);
			b.writeBoolean(p.hasPlacement);
			b.writeString(p.dimension, 128);
			b.writeInt(p.x);
			b.writeInt(p.y);
			b.writeInt(p.z);
			b.writeString(p.rotation, 32);
			b.writeString(p.mirror, 32);
			b.writeBoolean(p.loadInLitematica);
		},
		b -> new ProjectDownloadDataPayload(
			b.readString(64),
			b.readByteArray(1_048_576),
			b.readBoolean(),
			b.readString(128),
			b.readInt(),
			b.readInt(),
			b.readInt(),
			b.readString(32),
			b.readString(32),
			b.readBoolean()
		)
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
