package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ExplorationMapUpdateRegionPayload(String targetName, String name, String dimension, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, boolean delete) implements CustomPayload {
	public static final Id<ExplorationMapUpdateRegionPayload> ID = new Id<>(AeternumCarpet.id("exploration_map_update_region"));
	public static final PacketCodec<RegistryByteBuf, ExplorationMapUpdateRegionPayload> CODEC = PacketCodec.of(
		(p, b) -> { b.writeString(p.targetName, 80); b.writeString(p.name, 80); b.writeString(p.dimension, 96); b.writeInt(p.minChunkX); b.writeInt(p.minChunkZ); b.writeInt(p.maxChunkX); b.writeInt(p.maxChunkZ); b.writeBoolean(p.delete); },
		b -> new ExplorationMapUpdateRegionPayload(b.readString(80), b.readString(80), b.readString(96), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readBoolean())
	);
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
