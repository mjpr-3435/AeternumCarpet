package smp.aeternum.carpet.network;

import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

/** Initial map state; the terrain itself streams in region-sized tiles. */
public record ExplorationMapOpenPayload(String dimension, int centerChunkX, int centerChunkZ, int maxRequests, List<Area> areas, float mspt) implements CustomPayload {
    public static final Id<ExplorationMapOpenPayload> ID = new Id<>(AeternumCarpet.id("exploration_map_open"));
    public static final PacketCodec<RegistryByteBuf, ExplorationMapOpenPayload> CODEC = PacketCodec.of(
        (payload, buffer) -> { buffer.writeString(payload.dimension, 96); buffer.writeInt(payload.centerChunkX); buffer.writeInt(payload.centerChunkZ); buffer.writeVarInt(payload.maxRequests); buffer.writeFloat(payload.mspt); buffer.writeVarInt(payload.areas.size()); for (Area area : payload.areas) area.write(buffer); },
        buffer -> { String dimension=buffer.readString(96); int x=buffer.readInt(),z=buffer.readInt(),maxRequests=Math.clamp(buffer.readVarInt(),1,64); float mspt=buffer.readFloat(); int count=buffer.readVarInt(); java.util.ArrayList<Area> areas=new java.util.ArrayList<>(); for(int i=0;i<count&&i<512;i++) areas.add(Area.read(buffer)); return new ExplorationMapOpenPayload(dimension,x,z,maxRequests,List.copyOf(areas),mspt); });
    @Override public Id<? extends CustomPayload> getId() { return ID; }
    public record Area(String name, String dimension, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        void write(RegistryByteBuf b){b.writeString(name,80);b.writeString(dimension,96);b.writeInt(minChunkX);b.writeInt(minChunkZ);b.writeInt(maxChunkX);b.writeInt(maxChunkZ);}
        static Area read(RegistryByteBuf b){return new Area(b.readString(80),b.readString(96),b.readInt(),b.readInt(),b.readInt(),b.readInt());}
    }
}
