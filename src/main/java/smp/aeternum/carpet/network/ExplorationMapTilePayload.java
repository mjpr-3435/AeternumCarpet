package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ExplorationMapTilePayload(String dimension, int regionX, int regionZ, int[] colors) implements CustomPayload {
    public static final Id<ExplorationMapTilePayload> ID = new Id<>(AeternumCarpet.id("exploration_map_tile"));
    public static final PacketCodec<RegistryByteBuf, ExplorationMapTilePayload> CODEC = PacketCodec.of(
        (payload, buffer) -> { buffer.writeString(payload.dimension,96); buffer.writeInt(payload.regionX); buffer.writeInt(payload.regionZ); buffer.writeIntArray(payload.colors); },
        buffer -> new ExplorationMapTilePayload(buffer.readString(96),buffer.readInt(),buffer.readInt(),buffer.readIntArray(16_384)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
