package smp.aeternum.carpet.exploration;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.network.ExplorationMapTilePayload;

/** Reads saved chunk NBT on one background thread. It never asks the world to load or generate a chunk. */
public final class ExplorationMapService {
    private static final int TILE_BLOCKS=128, TILE_CHUNKS=8;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Aeternum map tiles");t.setDaemon(true);return t;});
    private static final Map<TileKey,int[]> CACHE=new ConcurrentHashMap<>();
    private static final Map<TileKey,Boolean> IN_FLIGHT=new ConcurrentHashMap<>();
    private ExplorationMapService() {}
    /** Clears only one dimension's map cache, both RAM and the world's persistent cache directory. */
    public static int clearCache(MinecraftServer server,String requestedDimension){
        String dimension=normalizeDimension(requestedDimension);if(dimension==null)return -1;
        CACHE.keySet().removeIf(key->key.dimension.equals(dimension));
        Path directory=server.getSavePath(WorldSavePath.ROOT).resolve("data/aeternum_map_cache").resolve(dimension.replace(':','_'));
        if(!Files.isDirectory(directory))return 0;
        try(Stream<Path> paths=Files.walk(directory)){List<Path> all=paths.sorted(Comparator.reverseOrder()).toList();for(Path path:all)Files.deleteIfExists(path);return all.size();}catch(IOException ignored){return -1;}
    }
    public static void request(MinecraftServer server,ServerPlayerEntity player,String dimension,int rx,int rz){
        if(!validDimension(dimension)||Math.abs(rx)>1_000_000||Math.abs(rz)>1_000_000)return;
        TileKey key=new TileKey(dimension,rx,rz);int[] cached=CACHE.get(key);if(cached!=null){send(player,key,cached);return;}
        if(IN_FLIGHT.putIfAbsent(key,Boolean.TRUE)!=null)return;
        CompletableFuture.supplyAsync(()->readTile(server,key),WORKER).whenComplete((colors,error)->server.execute(()->{IN_FLIGHT.remove(key);if(error!=null)return;CACHE.put(key,colors);send(player,key,colors);}));
    }
    private static void send(ServerPlayerEntity player,TileKey key,int[] colors){if(player.networkHandler!=null&&ServerPlayNetworking.canSend(player,ExplorationMapTilePayload.ID))ServerPlayNetworking.send(player,new ExplorationMapTilePayload(key.dimension,key.x,key.z,colors));}
    private static boolean validDimension(String d){return "minecraft:overworld".equals(d)||"minecraft:the_nether".equals(d)||"minecraft:the_end".equals(d);}
    private static String normalizeDimension(String dimension){return switch(dimension){case "overworld","minecraft:overworld"->"minecraft:overworld";case "nether","minecraft:the_nether"->"minecraft:the_nether";case "end","minecraft:the_end"->"minecraft:the_end";default->null;};}
    private static int[] readTile(MinecraftServer server,TileKey key){
        int[] colors=new int[TILE_BLOCKS*TILE_BLOCKS];Path root=server.getSavePath(WorldSavePath.ROOT);Path dir=switch(key.dimension){case "minecraft:the_nether"->root.resolve("DIM-1/region");case "minecraft:the_end"->root.resolve("DIM1/region");default->root.resolve("region");};
        int startChunkX=key.x*TILE_CHUNKS,startChunkZ=key.z*TILE_CHUNKS,regionX=Math.floorDiv(startChunkX,32),regionZ=Math.floorDiv(startChunkZ,32);Path region=dir.resolve("r."+regionX+"."+regionZ+".mca");if(!Files.isRegularFile(region))return colors;long stamp=sourceStamp(region);Path cache=root.resolve("data/aeternum_map_cache").resolve(key.dimension.replace(':','_')).resolve(key.x+"_"+key.z+".bin");int[] cached=readCache(cache,stamp);if(cached!=null)return cached;
        try(RandomAccessFile file=new RandomAccessFile(region.toFile(),"r")){for(int z=0;z<TILE_CHUNKS;z++)for(int x=0;x<TILE_CHUNKS;x++){NbtCompound nbt=readChunk(file,region,startChunkX+x,startChunkZ+z);if(nbt!=null)renderChunk(nbt,key.dimension,colors,x*16,z*16);}}catch(IOException ignored){}if(stamp==sourceStamp(region))writeCache(cache,stamp,colors);return colors;
    }
    private static long sourceStamp(Path source){try{return Files.getLastModifiedTime(source).toMillis()^Files.size(source)^0x4D415035L;}catch(IOException ignored){return Long.MIN_VALUE;}}
    private static int[] readCache(Path cache,long stamp){try(DataInputStream input=new DataInputStream(Files.newInputStream(cache))){if(input.readLong()!=stamp)return null;int[] colors=new int[TILE_BLOCKS*TILE_BLOCKS];for(int i=0;i<colors.length;i++)colors[i]=input.readInt();return colors;}catch(IOException ignored){return null;}}
    private static void writeCache(Path cache,long stamp,int[] colors){try{Files.createDirectories(cache.getParent());try(DataOutputStream output=new DataOutputStream(Files.newOutputStream(cache))){output.writeLong(stamp);for(int color:colors)output.writeInt(color);}}catch(IOException ignored){}}
    private static NbtCompound readChunk(RandomAccessFile file,Path region,int chunkX,int chunkZ)throws IOException{
        int localX=Math.floorMod(chunkX,32),localZ=Math.floorMod(chunkZ,32);file.seek((localX+localZ*32L)*4);int location=file.readInt();int sector=location>>>8;if(sector<2)return null;file.seek(sector*4096L);int length=file.readInt();if(length<2||length>1_048_576)return null;int version=file.readUnsignedByte();byte[] data=new byte[length-1];file.readFully(data);int compression=version&0x7F;InputStream raw;
        if((version&0x80)!=0){Path external=region.resolveSibling("c."+chunkX+"."+chunkZ+".mcc");if(!Files.isRegularFile(external))return null;raw=Files.newInputStream(external);}else raw=new ByteArrayInputStream(data);
        try(raw;InputStream decoded=switch(compression){case 1->new GZIPInputStream(raw);case 2->new InflaterInputStream(raw);case 3->raw;default->null;}){return decoded==null?null:NbtIo.readCompound(new DataInputStream(decoded));}
    }
    private static int color(NbtCompound chunk,String dimension){
        try{long[] heights=chunk.getCompound("Heightmaps").flatMap(h->h.getLongArray("WORLD_SURFACE")).orElse(null);if(heights==null||heights.length==0)return 0xFF606060;int y=height(heights,8+8*16)-64;NbtList sections=chunk.getList("sections").orElse(null);if(sections==null)return 0xFF606060;String block=topBlock(sections,y,8,8);return shade(block,y,dimension);}catch(Exception ignored){return 0xFF606060;}
    }
    private static void renderChunk(NbtCompound chunk,String dimension,int[] target,int offsetX,int offsetZ){try{long[] heights=chunk.getCompound("Heightmaps").flatMap(h->h.getLongArray("WORLD_SURFACE")).orElse(null);NbtList sections=chunk.getList("sections").orElse(null);if(heights==null||sections==null)return;boolean nether="minecraft:the_nether".equals(dimension);for(int z=0;z<16;z++)for(int x=0;x<16;x++){Surface surface=nether?highestSurface(sections,x,z):new Surface(surfaceBlock(sections,height(heights,x+z*16)-64,x,z,false),height(heights,x+z*16)-64);target[(offsetZ+z)*TILE_BLOCKS+offsetX+x]=shade(surface.block,surface.y,dimension);}}catch(Exception ignored){}}
    /** Nether can contain builds above the roof; scan section data instead of trusting a heightmap. */
    private static Surface highestSurface(NbtList sections,int x,int z){List<Section> ordered=new ArrayList<>();for(int i=0;i<sections.size();i++){NbtCompound section=sections.getCompound(i).orElse(null);if(section!=null)ordered.add(new Section(section,section.getByte("Y").orElse((byte)0)));}ordered.sort(Comparator.comparingInt(Section::y).reversed());for(Section section:ordered)for(int localY=15;localY>=0;localY--){String block=blockAt(section.data,localY,x,z);if(!air(block))return new Surface(block,section.y*16+localY);}return new Surface("minecraft:air",0);}
    private static String surfaceBlock(NbtList sections,int y,int x,int z,boolean highestOnly){int minimum=highestOnly?y-1:y-24;for(int current=y;current>=minimum;current--){String block=topBlock(sections,current,x,z);if(!air(block))return block;}return "minecraft:stone";}
    private static boolean air(String block){return block.endsWith(":air")||block.endsWith(":cave_air")||block.endsWith(":void_air");}
    private static int height(long[] values,int index){int bits=Math.max(1,(values.length*64)/256),per=64/bits,slot=index/per;return slot>=values.length?63:(int)((values[slot]>>>((index%per)*bits))&((1L<<bits)-1))-1;}
    private static String topBlock(NbtList sections,int y,int x,int z){for(int i=0;i<sections.size();i++){NbtCompound section=sections.getCompound(i).orElse(null);if(section==null||section.getByte("Y").orElse((byte)0)!=(byte)Math.floorDiv(y,16))continue;return blockAt(section,Math.floorMod(y,16),x,z);}return "minecraft:air";}
    private static String blockAt(NbtCompound section,int localY,int x,int z){NbtCompound states=section.getCompound("block_states").orElse(null);if(states==null)return "minecraft:air";NbtList palette=states.getList("palette").orElse(null);if(palette==null||palette.isEmpty())return "minecraft:air";int index=localY*256+z*16+x,pi=0;if(palette.size()>1){long[] data=states.getLongArray("data").orElse(new long[0]);int bits=Math.max(4,32-Integer.numberOfLeadingZeros(palette.size()-1)),per=64/bits,slot=index/per;if(slot>=data.length)return "minecraft:air";pi=(int)((data[slot]>>>((index%per)*bits))&((1L<<bits)-1));}return pi>=palette.size()?"minecraft:air":palette.getCompound(pi).flatMap(p->p.getString("Name")).orElse("minecraft:air");}
    private static int shade(String block,int y,String dimension){int rgb;try{rgb=Registries.BLOCK.get(Identifier.of(block)).getDefaultMapColor().getRenderColor(MapColor.Brightness.NORMAL)&0xFFFFFF;}catch(Exception ignored){rgb=0x7E7E7E;}int light=Math.max(-22,Math.min(22,(y-64)/3));int r=Math.max(0,Math.min(255,(rgb>>16&255)+light)),g=Math.max(0,Math.min(255,(rgb>>8&255)+light)),b=Math.max(0,Math.min(255,(rgb&255)+light));return 0xFF000000|(r<<16)|(g<<8)|b;}
    private record Surface(String block,int y){}
    private record Section(NbtCompound data,int y){}
    private record TileKey(String dimension,int x,int z){}
}
