package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ProjectUploadV2Payload(
	String name,
	byte[] bytes,
	boolean hasPlacement,
	String dimension,
	int x,
	int y,
	int z,
	String rotation,
	String mirror,
	String subregionsJson
) implements CustomPayload {
	public static final Id<ProjectUploadV2Payload> ID = new Id<>(AeternumCarpet.id("project_upload_v2"));
	public static final PacketCodec<RegistryByteBuf, ProjectUploadV2Payload> CODEC = PacketCodec.of(
		(p, b) -> {
			b.writeString(p.name, 64);
			b.writeByteArray(p.bytes);
			b.writeBoolean(p.hasPlacement);
			b.writeString(p.dimension, 128);
			b.writeInt(p.x);
			b.writeInt(p.y);
			b.writeInt(p.z);
			b.writeString(p.rotation, 32);
			b.writeString(p.mirror, 32);
			b.writeString(p.subregionsJson != null ? p.subregionsJson : "[]", 262144);
		},
		b -> new ProjectUploadV2Payload(
			b.readString(64),
			b.readByteArray(1_048_576),
			b.readBoolean(),
			b.readString(128),
			b.readInt(),
			b.readInt(),
			b.readInt(),
			b.readString(32),
			b.readString(32),
			b.readString(262144)
		)
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
