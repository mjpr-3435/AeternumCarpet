package smp.aeternum.carpet.perimeters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;

/** World-persistent store for defined perimeters and per-player pinned HUD selection. */
public final class PerimeterStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LEGACY_BLOCKER_TYPE = new TypeToken<LinkedHashMap<String, LegacyScan>>() {}.getType();
    private static final Map<String, Perimeter> PERIMETERS = new LinkedHashMap<>();
    private static final Map<String, String> PINNED_HUD = new HashMap<>();
    private static Path file;

    private PerimeterStore() {}

    public static void load(MinecraftServer server) {
        PERIMETERS.clear();
        PINNED_HUD.clear();
        Path dataDir = server.getSavePath(WorldSavePath.ROOT).resolve("data");
        file = dataDir.resolve("aeternum_perimeters.json");
        if (Files.exists(file)) {
            try {
                PersistedState loaded = GSON.fromJson(Files.readString(file), PersistedState.class);
                if (loaded != null) {
                    if (loaded.perimeters != null) {
                        for (var e : loaded.perimeters.entrySet()) {
                            if (e.getValue() != null) {
                                if (e.getValue().palette == null) e.getValue().palette = new ArrayList<>();
                                if (e.getValue().clusters == null) e.getValue().clusters = new ArrayList<>();
                                PERIMETERS.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                    if (loaded.pinnedHud != null) {
                        PINNED_HUD.putAll(loaded.pinnedHud);
                    }
                }
            } catch (IOException | RuntimeException e) {
                AeternumCarpet.LOGGER.error("Could not load perimeters", e);
            }
            return;
        }

        // One-time migration from legacy aeternum_blockers.json if present.
        Path legacyFile = dataDir.resolve("aeternum_blockers.json");
        if (Files.exists(legacyFile)) {
            try {
                Map<String, LegacyScan> legacy = GSON.fromJson(Files.readString(legacyFile), LEGACY_BLOCKER_TYPE);
                if (legacy != null) {
                    for (LegacyScan s : legacy.values()) {
                        if (s == null || s.id == null) continue;
                        Perimeter p = new Perimeter();
                        p.id = s.id;
                        p.name = s.id;
                        p.dimension = s.dimension;
                        p.minX = s.minX;
                        p.minY = s.minY;
                        p.minZ = s.minZ;
                        p.maxX = s.maxX;
                        p.maxY = s.maxY;
                        p.maxZ = s.maxZ;
                        p.totalChunks = s.totalChunks;
                        p.scannedChunks = s.scannedChunks;
                        p.complete = s.complete;
                        p.totalMatchedBlocks = s.matchedEntries;
                        p.createdAt = s.createdAt;
                        p.requestedBy = s.requestedBy;
                        List<String> defaultWe = PerimeterPresetStore.get("World Eater");
                        if (defaultWe != null) p.palette.addAll(defaultWe);
                        PERIMETERS.put(p.id, p);
                    }
                    save();
                }
            } catch (IOException | RuntimeException e) {
                AeternumCarpet.LOGGER.warn("Could not migrate legacy blocker scans", e);
            }
        }
    }

    public static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            PersistedState state = new PersistedState();
            state.perimeters.putAll(PERIMETERS);
            state.pinnedHud.putAll(PINNED_HUD);
            Files.writeString(file, GSON.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AeternumCarpet.LOGGER.error("Could not save perimeters", e);
        }
    }

    public static Perimeter get(String id) {
        if (id == null) return null;
        Perimeter exact = PERIMETERS.get(id);
        if (exact != null) return exact;
        for (Perimeter p : PERIMETERS.values()) {
            if (p.id.equalsIgnoreCase(id) || (p.name != null && p.name.equalsIgnoreCase(id))) {
                return p;
            }
        }
        return null;
    }

    public static Collection<Perimeter> all() {
        return PERIMETERS.values();
    }

    public static void put(Perimeter perimeter) {
        PERIMETERS.put(perimeter.id, perimeter);
        save();
    }

    public static boolean remove(String id) {
        Perimeter p = get(id);
        if (p == null) return false;
        PERIMETERS.remove(p.id);
        PINNED_HUD.entrySet().removeIf(e -> p.id.equals(e.getValue()));
        save();
        return true;
    }

    public static String getPinnedHud(UUID playerUuid) {
        String id = PINNED_HUD.get(playerUuid.toString());
        return id != null && get(id) != null ? id : null;
    }

    public static void setPinnedHud(UUID playerUuid, String perimeterId) {
        if (perimeterId == null || perimeterId.isBlank()) {
            PINNED_HUD.remove(playerUuid.toString());
        } else {
            PINNED_HUD.put(playerUuid.toString(), perimeterId);
        }
        save();
    }

    private static final class PersistedState {
        Map<String, Perimeter> perimeters = new LinkedHashMap<>();
        Map<String, String> pinnedHud = new HashMap<>();
    }

    private static final class LegacyScan {
        String id;
        String dimension;
        int minX, minY, minZ, maxX, maxY, maxZ;
        int scannedChunks, totalChunks;
        boolean complete;
        int matchedEntries;
        long createdAt;
        String requestedBy;
    }
}
