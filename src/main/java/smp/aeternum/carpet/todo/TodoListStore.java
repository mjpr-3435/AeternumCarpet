package smp.aeternum.carpet.todo;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TodoListStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path filePath;
    private static Map<String, List<String>> players = new LinkedHashMap<>();

    private TodoListStore() {
    }

    public static void load(MinecraftServer server) {
        filePath = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(AeternumCarpet.MOD_ID).resolve("todo-list.json");
        players = new LinkedHashMap<>();

        if (Files.notExists(filePath)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            Data data = GSON.fromJson(reader, Data.class);
            players = sanitize(data == null ? null : data.players);
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to load todo list data from {}", filePath, exception);
            players = new LinkedHashMap<>();
        }
    }

    public static List<String> getNotes(UUID playerUuid) {
        return List.copyOf(players.getOrDefault(playerUuid.toString(), List.of()));
    }

    public static void addNote(UUID playerUuid, String note) {
        List<String> notes = new ArrayList<>(players.getOrDefault(playerUuid.toString(), List.of()));
        notes.add(note);
        players.put(playerUuid.toString(), notes);
        save();
    }

    public static boolean removeNote(UUID playerUuid, int index) {
        List<String> notes = new ArrayList<>(players.getOrDefault(playerUuid.toString(), List.of()));
        if (index < 0 || index >= notes.size()) {
            return false;
        }

        notes.remove(index);
        if (notes.isEmpty()) {
            players.remove(playerUuid.toString());
        } else {
            players.put(playerUuid.toString(), notes);
        }
        save();
        return true;
    }

    public static void clearNotes(UUID playerUuid) {
        players.remove(playerUuid.toString());
        save();
    }

    private static void save() {
        if (filePath == null) {
            return;
        }

        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(new Data(players), writer);
            }
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to save todo list data to {}", filePath, exception);
        }
    }

    private static Map<String, List<String>> sanitize(Map<String, List<String>> source) {
        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        if (source == null) {
            return sanitized;
        }

        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }

            List<String> notes = new ArrayList<>();
            for (String note : entry.getValue()) {
                if (note != null) {
                    notes.add(note);
                }
            }

            if (!notes.isEmpty()) {
                sanitized.put(entry.getKey(), notes);
            }
        }
        return sanitized;
    }

    private static final class Data {
        private final Map<String, List<String>> players;

        private Data(Map<String, List<String>> players) {
            this.players = players == null ? new LinkedHashMap<>() : new LinkedHashMap<>(players);
        }
    }
}
