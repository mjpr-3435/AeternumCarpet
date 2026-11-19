package smp.aeternum.carpet.carefulbreak;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;

/** Per-player persistent preference for carefulBreak: off, sneak, or always. */
public final class CarefulBreakStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static Path file;
    private static Map<String, String> modes = new LinkedHashMap<>();

    private CarefulBreakStore() {}

    public static synchronized void load(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_careful_break.json");
        modes = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                Map<String, String> loaded = GSON.fromJson(Files.readString(file), TYPE);
                if (loaded != null) {
                    for (var e : loaded.entrySet()) {
                        String norm = normalize(e.getValue());
                        if (norm != null && !"off".equals(norm)) {
                            modes.put(e.getKey(), norm);
                        }
                    }
                }
            } catch (Exception e) {
                AeternumCarpet.LOGGER.error("Could not load carefulBreak preferences", e);
            }
        }
    }

    public static String normalize(String raw) {
        if (raw == null) return null;
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "off" -> "off";
            case "sneak" -> "sneak";
            case "always", "allways" -> "always";
            default -> null;
        };
    }

    public static synchronized String getMode(UUID uuid) {
        return modes.getOrDefault(uuid.toString(), "off");
    }

    public static synchronized void setMode(UUID uuid, String mode) {
        String norm = normalize(mode);
        if (norm == null) return;
        if ("off".equals(norm)) {
            modes.remove(uuid.toString());
        } else {
            modes.put(uuid.toString(), norm);
        }
        persist();
    }

    public static boolean shouldCollect(ServerPlayerEntity player) {
        if (!AeternumCarpetSettings.carefulBreak || player == null) return false;
        String mode = getMode(player.getUuid());
        return switch (mode) {
            case "always" -> true;
            case "sneak" -> player.isSneaking();
            default -> false;
        };
    }

    private static void persist() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(modes));
        } catch (IOException e) {
            AeternumCarpet.LOGGER.error("Could not save carefulBreak preferences", e);
        }
    }
}
