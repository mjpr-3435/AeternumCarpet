package smp.aeternum.carpet.guides;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Files are the source of truth: each guide is one markdown file in the world save. */
public final class GuideStore {
    private static final Pattern TITLE=Pattern.compile("[A-Za-z0-9 _-]{1,64}");
    private static Path directory; private static List<GuideDocument> guides=List.of();
    private GuideStore() { }
    public static void load(MinecraftServer server){directory=server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_guides");reload();}
    public static List<GuideDocument> all(){return List.copyOf(guides);}
    public static void reload(){List<GuideDocument> loaded=new ArrayList<>();try{Files.createDirectories(directory);try(var files=Files.list(directory)){files.filter(p->p.getFileName().toString().toLowerCase().endsWith(".md")).forEach(p->{try{String name=p.getFileName().toString();String title=name.substring(0,name.length()-3);if(TITLE.matcher(title).matches())loaded.add(new GuideDocument(title,Files.readString(p)));}catch(Exception e){AeternumCarpet.LOGGER.warn("Could not read guide {}",p,e);}});}}catch(Exception e){AeternumCarpet.LOGGER.error("Could not reload guides",e);}loaded.sort(Comparator.comparing(g->g.title,String.CASE_INSENSITIVE_ORDER));guides=List.copyOf(loaded);}
    public static boolean save(String title,String markdown){if(!valid(title)||markdown==null||markdown.length()>100_000)return false;try{Files.createDirectories(directory);Path target=path(title),temp=directory.resolve("."+title+".tmp");Files.writeString(temp,markdown);Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);reload();return true;}catch(Exception e){AeternumCarpet.LOGGER.error("Could not save guide {}",title,e);return false;}}
    public static boolean create(String title){if(!valid(title))return false;String base=title;int suffix=2;while(Files.exists(path(title)))title=base+" "+suffix++;return save(title,"# "+title+"\n\n");}
    public static boolean rename(String oldTitle,String newTitle){if(!valid(oldTitle)||!valid(newTitle)||Files.exists(path(newTitle)))return false;try{Files.move(path(oldTitle),path(newTitle));reload();return true;}catch(Exception e){return false;}}
    public static boolean delete(String title){if(!valid(title))return false;try{boolean ok=Files.deleteIfExists(path(title));if(ok)reload();return ok;}catch(Exception e){return false;}}
    private static boolean valid(String title){return title!=null&&TITLE.matcher(title).matches()&&!title.equals(".")&&!title.equals("..");}
    private static Path path(String title){return directory.resolve(title+".md");}
}
