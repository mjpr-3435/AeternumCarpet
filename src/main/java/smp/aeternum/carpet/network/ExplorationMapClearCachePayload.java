package smp.aeternum.carpet.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ExplorationMapClearCachePayload(String dimension, boolean visibleOnly, List<TileCoord> tiles) implements CustomPayload {
	public static final Id<ExplorationMapClearCachePayload> ID = new Id<>(AeternumCarpet.id("exploration_map_clear_cache"));
	public static final PacketCodec<RegistryByteBuf, ExplorationMapClearCachePayload> CODEC = PacketCodec.of(
		(payload, buffer) -> { buffer.writeString(payload.dimension, 96); buffer.writeBoolean(payload.visibleOnly); buffer.writeVarInt(payload.tiles.size()); for (TileCoord t : payload.tiles) { buffer.writeInt(t.x); buffer.writeInt(t.z); } },
		buffer -> { String d = buffer.readString(96); boolean v = buffer.readBoolean(); int c = buffer.readVarInt(); List<TileCoord> tiles = new ArrayList<>(c); for (int i = 0; i < c; i++) tiles.add(new TileCoord(buffer.readInt(), buffer.readInt())); return new ExplorationMapClearCachePayload(d, v, List.copyOf(tiles)); });
	@Override public Id<? extends CustomPayload> getId() { return ID; }
	public record TileCoord(int x, int z) {}
}
