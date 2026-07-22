package smp.aeternum.carpet.finder;

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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FinderStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, FinderPlace>>(){}.getType();
    private static final Map<String, FinderPlace> PLACES = new LinkedHashMap<>();
    private static Path file;
    private static Path defaultFile;
    private static String defaultPlace = "MainStorage";
    private FinderStore() { }
    public static void load(MinecraftServer server) {
        PLACES.clear(); file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_finder.json");
        defaultFile = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_finder_default.txt");
        try { if (Files.exists(defaultFile)) defaultPlace = Files.readString(defaultFile, StandardCharsets.UTF_8).trim(); } catch (IOException ignored) { }
        if (!Files.exists(file)) return;
        try { Map<String, FinderPlace> loaded = GSON.fromJson(Files.readString(file), TYPE); if (loaded != null) { for (FinderPlace place : loaded.values()) place.containers = new java.util.ArrayList<>(); PLACES.putAll(loaded); } }
        catch (IOException | RuntimeException e) { AeternumCarpet.LOGGER.error("Could not load Finder places", e); }
    }
    public static void save() { if (file == null) return; try { Files.createDirectories(file.getParent()); Files.writeString(file, GSON.toJson(PLACES), StandardCharsets.UTF_8); } catch (IOException e) { AeternumCarpet.LOGGER.error("Could not save Finder places", e); } }
    public static FinderPlace get(String name) { return PLACES.get(name.toLowerCase()); }
    public static Collection<FinderPlace> all() { return PLACES.values(); }
    /** Keys are case-insensitive, while the entered name remains visible to players. */
    public static void put(FinderPlace place) { PLACES.put(place.name.toLowerCase(), place); save(); }
    public static boolean remove(String name) { boolean removed = PLACES.remove(name.toLowerCase()) != null; if (removed) save(); return removed; }
    public static String defaultName() { return defaultPlace; }
    public static void setDefault(String name) { defaultPlace = name; try { Files.createDirectories(defaultFile.getParent()); Files.writeString(defaultFile, name, StandardCharsets.UTF_8); } catch (IOException e) { AeternumCarpet.LOGGER.error("Could not save Finder default", e); } }
}
