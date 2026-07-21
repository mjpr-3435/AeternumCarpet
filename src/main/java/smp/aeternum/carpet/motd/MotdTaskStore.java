package smp.aeternum.carpet.motd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** World-persistent public tasks shown beneath the join MOTD. */
public final class MotdTaskStore {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE=new TypeToken<List<String>>(){}.getType();
    private static List<String> tasks=new ArrayList<>(); private static Path file;
    private MotdTaskStore(){}
    public static void load(MinecraftServer server){file=server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_motd_tasks.json");tasks=new ArrayList<>();try{if(Files.exists(file)){List<String> loaded=GSON.fromJson(Files.readString(file),TYPE);if(loaded!=null)for(String task:loaded)if(valid(task))tasks.add(task.strip());}}catch(Exception e){AeternumCarpet.LOGGER.error("Could not load MOTD tasks",e);}}
    public static List<String> all(){return List.copyOf(tasks);}
    public static boolean add(String task){if(!valid(task)||tasks.size()>=64)return false;tasks.add(task.strip());save();return true;}
    public static boolean remove(int index){if(index<0||index>=tasks.size())return false;tasks.remove(index);save();return true;}
    private static boolean valid(String task){return task!=null&&!task.strip().isEmpty()&&task.strip().length()<=180&&task.indexOf('\u0000')<0;}
    private static void save(){try{Files.createDirectories(file.getParent());Files.writeString(file,GSON.toJson(tasks));}catch(Exception e){AeternumCarpet.LOGGER.error("Could not save MOTD tasks",e);}}
}
