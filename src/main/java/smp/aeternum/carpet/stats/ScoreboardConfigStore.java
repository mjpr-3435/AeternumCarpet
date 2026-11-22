package smp.aeternum.carpet.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;

public final class ScoreboardConfigStore {
    public static final String TRACKER_DIG_ALL = "aeternum:dig_all";
    public static final String TRACKER_PLACED_BLOCKS = "aeternum:placed_blocks";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path filePath;
    private static Map<String, ObjectiveConfig> objectives = new LinkedHashMap<>();
    private static volatile boolean anyTrackerConfigured;

    private ScoreboardConfigStore() {
    }

    public static final class ObjectiveConfig {
        public boolean totalEnabled;
        public boolean totalHumans = true;
        public boolean totalBots;
        public boolean totalUnknown;
        public String chatAlias = "";
        public String displayNameMarkup = "";
        public String tracker = "";
    }

    private static final class Data {
        Map<String, ObjectiveConfig> objectives = new LinkedHashMap<>();
    }

    public static synchronized void load(MinecraftServer server) {
        filePath = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(AeternumCarpet.MOD_ID).resolve("scoreboard-config.json");
        objectives = new LinkedHashMap<>();
        if (Files.notExists(filePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(filePath)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null && data.objectives != null) {
                for (Map.Entry<String, ObjectiveConfig> entry : data.objectives.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        ObjectiveConfig cfg = entry.getValue();
                        cfg.chatAlias = normalizeAlias(cfg.chatAlias);
                        cfg.tracker = cfg.tracker != null ? cfg.tracker.trim() : "";
                        objectives.put(entry.getKey(), cfg);
                    }
                }
            }
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to load scoreboard config from {}", filePath, exception);
        }
        recomputeTrackerFlag();
    }

    public static synchronized ObjectiveConfig getOrDefault(String objectiveName) {
        ObjectiveConfig cfg = objectives.get(objectiveName);
        if (cfg == null) {
            return new ObjectiveConfig();
        }
        ObjectiveConfig copy = new ObjectiveConfig();
        copy.totalEnabled = cfg.totalEnabled;
        copy.totalHumans = cfg.totalHumans;
        copy.totalBots = cfg.totalBots;
        copy.totalUnknown = cfg.totalUnknown;
        copy.chatAlias = cfg.chatAlias != null ? cfg.chatAlias : "";
        copy.displayNameMarkup = cfg.displayNameMarkup != null ? cfg.displayNameMarkup : "";
        copy.tracker = cfg.tracker != null ? cfg.tracker : "";
        return copy;
    }

    public static synchronized String findAliasConflict(String objectiveName, String alias) {
        String norm = normalizeAlias(alias);
        if (norm.isEmpty()) {
            return null;
        }
        if ("clear".equals(norm)) {
            return "clear (reservado)";
        }
        for (Map.Entry<String, ObjectiveConfig> entry : objectives.entrySet()) {
            if (entry.getKey().equals(objectiveName)) continue;
            if (norm.equals(normalizeAlias(entry.getValue().chatAlias))) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static synchronized void put(String objectiveName, ObjectiveConfig config) {
        if (objectiveName == null || objectiveName.isBlank() || config == null) {
            return;
        }
        config.chatAlias = normalizeAlias(config.chatAlias);
        config.tracker = config.tracker != null ? config.tracker.trim() : "";
        objectives.put(objectiveName, config);
        recomputeTrackerFlag();
        save();
    }

    public static synchronized void remove(String objectiveName) {
        if (objectiveName != null && objectives.remove(objectiveName) != null) {
            recomputeTrackerFlag();
            save();
        }
    }

    public static boolean hasAnyTracker() {
        return anyTrackerConfigured;
    }

    private static void recomputeTrackerFlag() {
        boolean found = false;
        for (ObjectiveConfig cfg : objectives.values()) {
            if (cfg.tracker != null && !cfg.tracker.isEmpty()) {
                found = true;
                break;
            }
        }
        anyTrackerConfigured = found;
    }

    public static void addTrackerScore(ServerPlayerEntity player, String trackerId, int amount) {
        MinecraftServer server = player.getServer();
        if (server == null || amount <= 0) {
            return;
        }
        List<String> targetNames = getObjectiveNamesForTracker(trackerId);
        if (targetNames.isEmpty()) {
            return;
        }
        Scoreboard sb = server.getScoreboard();
        ScoreHolder holder = ScoreHolder.fromName(player.getName().getString());
        for (String name : targetNames) {
            for (ScoreboardObjective obj : sb.getObjectives()) {
                if (obj.getName().equals(name)) {
                    sb.getOrCreateScore(holder, obj).incrementScore(amount);
                    break;
                }
            }
        }
    }

    private static synchronized List<String> getObjectiveNamesForTracker(String trackerId) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, ObjectiveConfig> entry : objectives.entrySet()) {
            if (trackerId.equals(entry.getValue().tracker)) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    public static boolean handleChat(ServerPlayerEntity player, String rawMessage) {
        if (!AeternumCarpetSettings.commandStats || player == null || rawMessage == null) {
            return false;
        }
        String trimmed = rawMessage.trim();
        if (!trimmed.startsWith("!!") || trimmed.length() <= 2) {
            return false;
        }
        String alias = normalizeAlias(trimmed.substring(2));
        if (alias.isEmpty()) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        Scoreboard sb = server.getScoreboard();
        if ("clear".equals(alias)) {
            sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
            player.sendMessage(Text.literal("[Stats] Sidebar limpiada.").formatted(Formatting.GRAY), true);
            return true;
        }

        String targetObjective = findObjectiveByAlias(alias);
        if (targetObjective == null) {
            return false;
        }
        ScoreboardObjective obj = null;
        for (ScoreboardObjective candidate : sb.getObjectives()) {
            if (candidate.getName().equals(targetObjective)) {
                obj = candidate;
                break;
            }
        }
        if (obj == null) {
            return false;
        }
        sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);
        player.sendMessage(Text.literal("[Stats] Mostrando: " + obj.getName()).formatted(Formatting.GRAY), true);
        return true;
    }

    private static synchronized String findObjectiveByAlias(String alias) {
        for (Map.Entry<String, ObjectiveConfig> entry : objectives.entrySet()) {
            if (alias.equals(normalizeAlias(entry.getValue().chatAlias))) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static String normalizeAlias(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        while (s.startsWith("!")) {
            s = s.substring(1);
        }
        return s.replaceAll("\\s+", "");
    }

    private static void save() {
        if (filePath == null) {
            return;
        }
        try {
            Files.createDirectories(filePath.getParent());
            Data data = new Data();
            data.objectives = objectives;
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to save scoreboard config to {}", filePath, exception);
        }
    }
}
