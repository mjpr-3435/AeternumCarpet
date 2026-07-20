package smp.aeternum.carpet.blockers;

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

/** Persistent scan data kept next to the world data, independent of client state. */
public final class BlockerScanStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, BlockerScan>>() {}.getType();
    private static final Map<String, BlockerScan> SCANS = new LinkedHashMap<>();
    private static Path file;

    private BlockerScanStore() {
    }

    public static void load(MinecraftServer server) {
        SCANS.clear();
        file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_blockers.json");
        if (!Files.exists(file)) return;
        try {
            Map<String, BlockerScan> loaded = GSON.fromJson(Files.readString(file), MAP_TYPE);
            if (loaded != null) SCANS.putAll(loaded);
        } catch (IOException | RuntimeException exception) {
            AeternumCarpet.LOGGER.error("Could not load blocker scans", exception);
        }
    }

    public static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(SCANS), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Could not save blocker scans", exception);
        }
    }

    public static BlockerScan get(String id) { return SCANS.get(id); }
    public static Collection<BlockerScan> all() { return SCANS.values(); }
    public static void put(BlockerScan scan) { SCANS.put(scan.id, scan); save(); }
    public static boolean remove(String id) {
        if (SCANS.remove(id) == null) return false;
        save();
        return true;
    }
}
