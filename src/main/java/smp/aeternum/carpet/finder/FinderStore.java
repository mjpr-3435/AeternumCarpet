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
import java.util.regex.Pattern;

public final class FinderStore {
    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9]{1,48}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, FinderPlace>>(){}.getType();
    private static final Map<String, FinderPlace> PLACES = new LinkedHashMap<>();
    private static Path file;
    private FinderStore() { }
    public static boolean isValidName(String name) { return name != null && VALID_NAME.matcher(name).matches(); }
    public static void load(MinecraftServer server) {
        PLACES.clear(); file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_finder.json");
        if (!Files.exists(file)) return;
        try {
            Map<String, FinderPlace> loaded = GSON.fromJson(Files.readString(file), TYPE);
            if (loaded != null) {
                boolean dropped = false;
                for (FinderPlace place : loaded.values()) {
                    if (place == null || !isValidName(place.name)) { dropped = true; continue; }
                    place.containers = new java.util.ArrayList<>();
                    PLACES.put(place.name.toLowerCase(), place);
                }
                if (dropped) save();
            }
        }
        catch (IOException | RuntimeException e) { AeternumCarpet.LOGGER.error("Could not load Finder places", e); }
    }
    public static void save() { if (file == null) return; try { Files.createDirectories(file.getParent()); Files.writeString(file, GSON.toJson(PLACES), StandardCharsets.UTF_8); } catch (IOException e) { AeternumCarpet.LOGGER.error("Could not save Finder places", e); } }
    public static FinderPlace get(String name) { return PLACES.get(name.toLowerCase()); }
    public static Collection<FinderPlace> all() { return PLACES.values(); }
    /** Keys are case-insensitive, while the entered name remains visible to players. */
    public static void put(FinderPlace place) { PLACES.put(place.name.toLowerCase(), place); save(); }
    public static boolean remove(String name) { boolean removed = PLACES.remove(name.toLowerCase()) != null; if (removed) save(); return removed; }
}
