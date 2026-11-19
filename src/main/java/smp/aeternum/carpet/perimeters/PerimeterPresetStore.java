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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;

/** World-persistent reusable block palette presets (World Eater, Quarry, custom). */
public final class PerimeterPresetStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, List<String>>>() {}.getType();
    private static final Map<String, List<String>> PRESETS = new LinkedHashMap<>();
    private static Path file;

    private PerimeterPresetStore() {}

    public static void load(MinecraftServer server) {
        PRESETS.clear();
        file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_perimeter_presets.json");
        if (!Files.exists(file)) {
            seedDefaults();
            save();
            return;
        }
        try {
            Map<String, List<String>> loaded = GSON.fromJson(Files.readString(file), MAP_TYPE);
            if (loaded != null) {
                for (var entry : loaded.entrySet()) {
                    if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                        PRESETS.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            AeternumCarpet.LOGGER.error("Could not load perimeter presets", e);
        }
    }

    private static void seedDefaults() {
        PRESETS.put("World Eater", new ArrayList<>(List.of(
            "minecraft:obsidian",
            "minecraft:crying_obsidian",
            "minecraft:ancient_debris",
            "minecraft:ender_chest",
            "minecraft:reinforced_deepslate",
            "minecraft:end_portal_frame",
            "minecraft:lava",
            "minecraft:water"
        )));
        PRESETS.put("Quarry", new ArrayList<>(List.of(
            "minecraft:obsidian",
            "minecraft:crying_obsidian",
            "minecraft:spawner",
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:reinforced_deepslate",
            "minecraft:lava",
            "minecraft:water"
        )));
    }

    public static Map<String, List<String>> all() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (var e : PRESETS.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return copy;
    }

    public static List<String> get(String name) {
        for (var e : PRESETS.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    public static void put(String name, List<String> palette) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) return;
        PRESETS.put(clean, new ArrayList<>(palette));
        save();
    }

    public static boolean remove(String name) {
        String target = null;
        for (String key : PRESETS.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                target = key;
                break;
            }
        }
        if (target == null) return false;
        PRESETS.remove(target);
        save();
        return true;
    }

    private static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(PRESETS), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AeternumCarpet.LOGGER.error("Could not save perimeter presets", e);
        }
    }
}
