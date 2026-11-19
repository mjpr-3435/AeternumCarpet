package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ProjectDownloadRequestPayload(String projectName, boolean loadInLitematica) implements CustomPayload {
	public static final Id<ProjectDownloadRequestPayload> ID = new Id<>(AeternumCarpet.id("project_download_req"));
	public static final PacketCodec<RegistryByteBuf, ProjectDownloadRequestPayload> CODEC = PacketCodec.of(
		(p, b) -> {
			b.writeString(p.projectName, 64);
			b.writeBoolean(p.loadInLitematica);
		},
		b -> new ProjectDownloadRequestPayload(b.readString(64), b.readBoolean())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
