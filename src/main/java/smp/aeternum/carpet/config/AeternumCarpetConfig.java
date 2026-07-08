package smp.aeternum.carpet.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import smp.aeternum.carpet.AeternumCarpet;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AeternumCarpetConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("aeternum-carpet.json");

    private static List<AllowedGenerationArea> allowedGenerationAreas = List.of();

    private AeternumCarpetConfig() {
    }

    public static void load() {
        if (Files.notExists(CONFIG_PATH)) {
            allowedGenerationAreas = List.of();
            writeConfig(new ConfigData(allowedGenerationAreas));
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ConfigData configData = GSON.fromJson(reader, ConfigData.class);
            allowedGenerationAreas = sanitize(configData == null ? null : configData.allowedGenerationAreas);
        } catch (IOException | JsonParseException exception) {
            allowedGenerationAreas = List.of();
            AeternumCarpet.LOGGER.error("Failed to read config from {}", CONFIG_PATH, exception);
        }
    }

    public static List<AllowedGenerationArea> getAllowedGenerationAreas() {
        return List.copyOf(allowedGenerationAreas);
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
        writeConfig(new ConfigData(allowedGenerationAreas));
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

    private static AllowedGenerationArea normalize(AllowedGenerationArea area) {
        return new AllowedGenerationArea(
            area.name(),
            area.normalizedDimension(),
            area.centerX(),
            area.centerZ(),
            area.size()
        );
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

        private ConfigData(List<AllowedGenerationArea> allowedGenerationAreas) {
            this.allowedGenerationAreas = allowedGenerationAreas == null ? List.of() : List.copyOf(allowedGenerationAreas);
        }
    }
}
