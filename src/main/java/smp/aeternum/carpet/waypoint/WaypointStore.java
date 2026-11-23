package smp.aeternum.carpet.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;

public final class WaypointStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path filePath;
    private static Map<String, Waypoint> waypoints = new LinkedHashMap<>();

    private WaypointStore() {
    }

    public static final class Waypoint {
        public String name = "";
        public String dimension = "minecraft:overworld";
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public String createdBy = "";
    }

    private static final class Data {
        Map<String, Waypoint> waypoints = new LinkedHashMap<>();
    }

    public static synchronized void load(MinecraftServer server) {
        filePath = server.getSavePath(WorldSavePath.ROOT)
            .resolve("data")
            .resolve(AeternumCarpet.MOD_ID)
            .resolve("waypoints.json");
        waypoints = new LinkedHashMap<>();
        if (Files.isRegularFile(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                Data data = GSON.fromJson(reader, Data.class);
                if (data != null && data.waypoints != null) {
                    for (Map.Entry<String, Waypoint> e : data.waypoints.entrySet()) {
                        Waypoint wp = e.getValue();
                        if (wp == null) continue;
                        String key = (wp.name != null && !wp.name.isBlank()) ? wp.name.trim() : e.getKey();
                        if (!isValidName(key)) continue;
                        wp.name = key;
                        waypoints.put(key.toLowerCase(Locale.ROOT), wp);
                    }
                }
            } catch (Exception ex) {
                AeternumCarpet.LOGGER.error("Failed to load waypoints from {}", filePath, ex);
            }
        }
    }

    public static synchronized boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    public static synchronized Waypoint get(String name) {
        if (name == null) return null;
        return waypoints.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public static synchronized void put(Waypoint wp) {
        if (wp == null || !isValidName(wp.name)) return;
        waypoints.put(wp.name.trim().toLowerCase(Locale.ROOT), wp);
        save();
    }

    public static synchronized boolean remove(String name) {
        if (name == null) return false;
        Waypoint removed = waypoints.remove(name.trim().toLowerCase(Locale.ROOT));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public static synchronized List<Waypoint> all() {
        List<Waypoint> out = new ArrayList<>(waypoints.values());
        out.sort(Comparator.comparing(w -> w.name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            Data data = new Data();
            for (Waypoint wp : all()) {
                data.waypoints.put(wp.name, wp);
            }
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ex) {
            AeternumCarpet.LOGGER.error("Failed to save waypoints to {}", filePath, ex);
        }
    }
}
