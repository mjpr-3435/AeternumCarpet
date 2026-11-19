package smp.aeternum.carpet.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;

/** Persists per-player return positions and gamemode while using /cs. */
public final class CameraModeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, SavedState>>() {}.getType();

    private static Path file;
    private static Map<String, SavedState> saved = new LinkedHashMap<>();

    private CameraModeStore() {}

    public static synchronized void load(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_camera.json");
        saved = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                Map<String, SavedState> loaded = GSON.fromJson(Files.readString(file), TYPE);
                if (loaded != null) saved.putAll(loaded);
            } catch (Exception e) {
                AeternumCarpet.LOGGER.error("Could not load camera mode store", e);
            }
        }
    }

    public static synchronized void save(UUID uuid, SavedState state) {
        saved.put(uuid.toString(), state);
        persist();
    }

    public static synchronized SavedState pop(UUID uuid) {
        SavedState state = saved.remove(uuid.toString());
        if (state != null) persist();
        return state;
    }

    private static void persist() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(saved));
        } catch (IOException e) {
            AeternumCarpet.LOGGER.error("Could not save camera mode store", e);
        }
    }

    public static final class SavedState {
        public String dimension;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public String gameMode;

        public SavedState() {}

        public SavedState(String dimension, double x, double y, double z, float yaw, float pitch, String gameMode) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.gameMode = gameMode;
        }
    }
}
