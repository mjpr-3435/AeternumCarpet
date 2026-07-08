package smp.aeternum.carpet.quickaccess;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class QuickAccessSubscriptionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path filePath;
    private static Set<String> quickShulkerPlayers = new LinkedHashSet<>();
    private static Set<String> quickEnderChestPlayers = new LinkedHashSet<>();

    private QuickAccessSubscriptionStore() {
    }

    public static void load(MinecraftServer server) {
        filePath = server.getSavePath(WorldSavePath.ROOT)
            .resolve("data")
            .resolve(AeternumCarpet.MOD_ID)
            .resolve("quick-access-subscriptions.json");

        quickShulkerPlayers = new LinkedHashSet<>();
        quickEnderChestPlayers = new LinkedHashSet<>();

        if (Files.notExists(filePath)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            Data data = GSON.fromJson(reader, Data.class);
            quickShulkerPlayers = sanitize(data == null ? null : data.quickShulkerPlayers);
            quickEnderChestPlayers = sanitize(data == null ? null : data.quickEnderChestPlayers);
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to load quick access subscriptions from {}", filePath, exception);
            quickShulkerPlayers = new LinkedHashSet<>();
            quickEnderChestPlayers = new LinkedHashSet<>();
        }
    }

    public static boolean isQuickShulkerEnabled(UUID playerUuid) {
        return quickShulkerPlayers.contains(playerUuid.toString());
    }

    public static boolean isQuickEnderChestEnabled(UUID playerUuid) {
        return quickEnderChestPlayers.contains(playerUuid.toString());
    }

    public static boolean toggleQuickShulker(UUID playerUuid) {
        boolean enabled = toggle(quickShulkerPlayers, playerUuid);
        save();
        return enabled;
    }

    public static boolean toggleQuickEnderChest(UUID playerUuid) {
        boolean enabled = toggle(quickEnderChestPlayers, playerUuid);
        save();
        return enabled;
    }

    private static boolean toggle(Set<String> players, UUID playerUuid) {
        String key = playerUuid.toString();
        if (players.contains(key)) {
            players.remove(key);
            return false;
        }

        players.add(key);
        return true;
    }

    private static void save() {
        if (filePath == null) {
            return;
        }

        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(new Data(quickShulkerPlayers, quickEnderChestPlayers), writer);
            }
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to save quick access subscriptions to {}", filePath, exception);
        }
    }

    private static Set<String> sanitize(Set<String> source) {
        Set<String> sanitized = new LinkedHashSet<>();
        if (source == null) {
            return sanitized;
        }

        for (String playerUuid : source) {
            if (playerUuid != null && !playerUuid.isBlank()) {
                sanitized.add(playerUuid);
            }
        }
        return sanitized;
    }

    private static final class Data {
        private final Set<String> quickShulkerPlayers;
        private final Set<String> quickEnderChestPlayers;

        private Data(Set<String> quickShulkerPlayers, Set<String> quickEnderChestPlayers) {
            this.quickShulkerPlayers = quickShulkerPlayers == null ? Set.of() : new LinkedHashSet<>(quickShulkerPlayers);
            this.quickEnderChestPlayers = quickEnderChestPlayers == null ? Set.of() : new LinkedHashSet<>(quickEnderChestPlayers);
        }
    }
}
