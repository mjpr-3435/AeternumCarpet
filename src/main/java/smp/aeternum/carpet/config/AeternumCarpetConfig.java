package smp.aeternum.carpet.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.exploration.ExplorationSelectionExporter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AeternumCarpetConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .registerTypeAdapter(AllowedGenerationArea.class, (JsonDeserializer<AllowedGenerationArea>)(json, type, context) -> {
            JsonObject obj = json.getAsJsonObject();
            String name = obj.get("name").getAsString();
            String dim = obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld";
            if (obj.has("minChunkX")) {
                return new AllowedGenerationArea(name, dim,
                    obj.get("minChunkX").getAsInt(), obj.get("minChunkZ").getAsInt(),
                    obj.get("maxChunkX").getAsInt(), obj.get("maxChunkZ").getAsInt());
            }
            int cx = obj.get("centerX").getAsInt(), cz = obj.get("centerZ").getAsInt(), s = obj.has("size") ? obj.get("size").getAsInt() : 0;
            return AllowedGenerationArea.fromCenter(name, dim, cx, cz, s);
        })
        .create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("aeternum-carpet.json");

    private static List<AllowedGenerationArea> allowedGenerationAreas = List.of();
    private static int mapMaxRequests = 2;

    private AeternumCarpetConfig() {
    }

    public static void load() {
        if (Files.notExists(CONFIG_PATH)) {
            allowedGenerationAreas = List.of();
            mapMaxRequests = 2;
            writeConfig(new ConfigData(allowedGenerationAreas, mapMaxRequests));
            exportSelections();
            return;
        }

        try {
            String raw = Files.readString(CONFIG_PATH);
            boolean migrated = raw.contains("\"centerX\"");
            ConfigData configData = GSON.fromJson(raw, ConfigData.class);
            allowedGenerationAreas = sanitize(configData == null ? null : configData.allowedGenerationAreas);
            mapMaxRequests = clampMapMaxRequests(configData == null ? 2 : configData.mapMaxRequests);
            exportSelections();
            if (migrated && !allowedGenerationAreas.isEmpty()) { save(); AeternumCarpet.LOGGER.info("Configuración de áreas migrada al nuevo formato (minChunk/maxChunk)."); }
        } catch (IOException | JsonParseException exception) {
            allowedGenerationAreas = List.of();
            AeternumCarpet.LOGGER.error("Failed to read config from {}", CONFIG_PATH, exception);
        }
    }

    public static List<AllowedGenerationArea> getAllowedGenerationAreas() {
        return List.copyOf(allowedGenerationAreas);
    }

    public static int getMapMaxRequests() { return mapMaxRequests; }

    public static void setMapMaxRequests(int value) {
        mapMaxRequests = clampMapMaxRequests(value);
        save();
    }

    public static boolean addAllowedGenerationArea(AllowedGenerationArea area) {
        if (area == null || !area.hasValidSize() || findAreaByName(area.name()).isPresent()) {
            return false;
        }

        List<AllowedGenerationArea> updatedAreas = new ArrayList<>(allowedGenerationAreas);
        updatedAreas.add(normalize(area));
        allowedGenerationAreas = List.copyOf(updatedAreas);
        save();
        return true;
    }

    public static AllowedGenerationArea removeAllowedGenerationAreaByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        List<AllowedGenerationArea> updatedAreas = new ArrayList<>(allowedGenerationAreas);
        for (int i = 0; i < updatedAreas.size(); i++) {
            AllowedGenerationArea area = updatedAreas.get(i);
            if (area.name().equals(name)) {
                updatedAreas.remove(i);
                allowedGenerationAreas = List.copyOf(updatedAreas);
                save();
                return area;
            }
        }

        return null;
    }

    /** Replaces one area in place, preserving its position (and therefore its map priority). */
    public static boolean replaceAllowedGenerationArea(String existingName, AllowedGenerationArea replacement) {
        if (existingName == null || existingName.isBlank() || replacement == null || !replacement.hasValidSize()) {
            return false;
        }

        List<AllowedGenerationArea> updatedAreas = new ArrayList<>(allowedGenerationAreas);
        for (int i = 0; i < updatedAreas.size(); i++) {
            AllowedGenerationArea current = updatedAreas.get(i);
            if (!current.name().equals(existingName)) {
                continue;
            }
            if (!existingName.equals(replacement.name()) && findAreaByName(replacement.name()).isPresent()) {
                return false;
            }
            updatedAreas.set(i, normalize(replacement));
            allowedGenerationAreas = List.copyOf(updatedAreas);
            save();
            return true;
        }
        return false;
    }

    public static List<AllowedGenerationArea> findAreasContainingChunk(String dimension, int chunkX, int chunkZ) {
        List<AllowedGenerationArea> matchingAreas = new ArrayList<>();
        for (AllowedGenerationArea area : allowedGenerationAreas) {
            if (area.matchesDimension(dimension) && area.containsChunk(chunkX, chunkZ)) {
                matchingAreas.add(area);
            }
        }
        return List.copyOf(matchingAreas);
    }

    public static boolean isChunkInsideAllowedGenerationArea(String dimension, int chunkX, int chunkZ) {
        return !findAreasContainingChunk(dimension, chunkX, chunkZ).isEmpty();
    }

    public static List<AllowedGenerationArea> findAreasByDimension(String dimension) {
        List<AllowedGenerationArea> matchingAreas = new ArrayList<>();
        for (AllowedGenerationArea area : allowedGenerationAreas) {
            if (area.matchesDimension(dimension)) {
                matchingAreas.add(area);
            }
        }
        return List.copyOf(matchingAreas);
    }

    public static void save() {
        writeConfig(new ConfigData(allowedGenerationAreas, mapMaxRequests));
        exportSelections();
    }

    private static void exportSelections() {
        try {
            ExplorationSelectionExporter.exportAll();
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to export exploration selections", exception);
        }
    }

    private static List<AllowedGenerationArea> sanitize(List<AllowedGenerationArea> areas) {
        if (areas == null || areas.isEmpty()) {
            return List.of();
        }

        List<AllowedGenerationArea> sanitizedAreas = new ArrayList<>();
        for (AllowedGenerationArea area : areas) {
            if (area != null && area.hasValidSize()) {
                sanitizedAreas.add(normalize(area));
            }
        }
        return List.copyOf(sanitizedAreas);
    }

    private static int clampMapMaxRequests(int value) { return value < 1 || value > 64 ? 2 : value; }

    private static AllowedGenerationArea normalize(AllowedGenerationArea area) {
        return new AllowedGenerationArea(area.name(), area.normalizedDimension(), area.minChunkX(), area.minChunkZ(), area.maxChunkX(), area.maxChunkZ());
    }

    private static Optional<AllowedGenerationArea> findAreaByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        for (AllowedGenerationArea area : allowedGenerationAreas) {
            if (area.name().equals(name)) {
                return Optional.of(area);
            }
        }

        return Optional.empty();
    }

    private static void writeConfig(ConfigData configData) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(configData, writer);
            }
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to write config to {}", CONFIG_PATH, exception);
        }
    }

    private static final class ConfigData {
        @SerializedName("allowedGenerationAreas")
        private final List<AllowedGenerationArea> allowedGenerationAreas;
        @SerializedName("mapMaxRequests")
        private final int mapMaxRequests;

        private ConfigData(List<AllowedGenerationArea> allowedGenerationAreas, int mapMaxRequests) {
            this.allowedGenerationAreas = allowedGenerationAreas == null ? List.of() : List.copyOf(allowedGenerationAreas);
            this.mapMaxRequests = clampMapMaxRequests(mapMaxRequests);
        }
    }
}
