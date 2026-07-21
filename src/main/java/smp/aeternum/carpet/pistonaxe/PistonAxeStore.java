package smp.aeternum.carpet.pistonaxe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-player subscription and block list: no global authority is granted. */
public final class PistonAxeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, Data>>(){}.getType();
    private static final Map<String, Data> PLAYERS = new LinkedHashMap<>();
    private static Path file;
    private PistonAxeStore() { }
    public static void load(MinecraftServer server) { PLAYERS.clear(); file=server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_piston_axe.json"); if(!Files.exists(file))return; try {Map<String,Data> read=GSON.fromJson(Files.readString(file),TYPE);if(read!=null)PLAYERS.putAll(read);}catch(IOException|RuntimeException e){AeternumCarpet.LOGGER.error("Could not load Piston Axe data",e);} }
    private static Data data(UUID id){return PLAYERS.computeIfAbsent(id.toString(),ignored->new Data());}
    public static boolean toggle(UUID id){Data d=data(id);d.enabled=!d.enabled;save();return d.enabled;}
    public static boolean enabled(UUID id){Data d=PLAYERS.get(id.toString());return d!=null&&d.enabled;}
    public static boolean allows(UUID id,String block){Data d=PLAYERS.get(id.toString());return d!=null&&d.blocks.contains(block);}
    public static boolean add(UUID id,String block){boolean changed=data(id).blocks.add(block);if(changed)save();return changed;}
    public static boolean remove(UUID id,String block){Data d=data(id);boolean changed=d.blocks.remove(block);if(changed)save();return changed;}
    public static Set<String> list(UUID id){return Set.copyOf(data(id).blocks);}
    private static void save(){if(file==null)return;try{Files.createDirectories(file.getParent());Files.writeString(file,GSON.toJson(PLAYERS),StandardCharsets.UTF_8);}catch(IOException e){AeternumCarpet.LOGGER.error("Could not save Piston Axe data",e);}}
    private static final class Data { boolean enabled; Set<String> blocks=new LinkedHashSet<>(); }
}
