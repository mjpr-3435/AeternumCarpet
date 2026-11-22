package smp.aeternum.carpet.stats;

import carpet.patches.EntityPlayerMPFake;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UserCache;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;

public final class PlayerRegistryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private static Path filePath;
    private static Path unexpectedFilePath;
    private static Map<String, Entry> entries = new LinkedHashMap<>();
    private static List<String> unexpectedFiles = new ArrayList<>();

    private PlayerRegistryStore() {
    }

    public enum EntryType {
        UNKNOWN,
        HUMAN,
        BOT
    }

    public static final class FilePresence {
        public boolean playerdata;
        public boolean stats;
        public boolean advancements;

        public FilePresence() {
        }

        public FilePresence(boolean playerdata, boolean stats, boolean advancements) {
            this.playerdata = playerdata;
            this.stats = stats;
            this.advancements = advancements;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FilePresence that)) return false;
            return playerdata == that.playerdata && stats == that.stats && advancements == that.advancements;
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerdata, stats, advancements);
        }
    }

    public static final class Entry {
        public String uuid;
        public String name;
        public EntryType type = EntryType.UNKNOWN;
        public FilePresence files = new FilePresence();

        public boolean hasAnomaly() {
            return files == null || !files.playerdata || !files.stats || !files.advancements;
        }
    }

    private static final class Data {
        Map<String, Entry> entries = new LinkedHashMap<>();
    }

    private static final class UnexpectedData {
        List<String> unexpectedFiles = new ArrayList<>();
    }

    public static synchronized void load(MinecraftServer server) {
        Path baseDir = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(AeternumCarpet.MOD_ID);
        filePath = baseDir.resolve("player-registry.json");
        unexpectedFilePath = baseDir.resolve("unexpected-files.json");
        entries = new LinkedHashMap<>();
        unexpectedFiles = new ArrayList<>();

        if (Files.isRegularFile(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                Data data = GSON.fromJson(reader, Data.class);
                if (data != null && data.entries != null) {
                    for (Map.Entry<String, Entry> item : data.entries.entrySet()) {
                        Entry e = sanitizeEntry(item.getKey(), item.getValue());
                        if (e != null) {
                            entries.put(e.uuid, e);
                        }
                    }
                }
            } catch (IOException exception) {
                AeternumCarpet.LOGGER.error("Failed to load player registry from {}", filePath, exception);
            }
        }

        if (Files.isRegularFile(unexpectedFilePath)) {
            try (Reader reader = Files.newBufferedReader(unexpectedFilePath)) {
                UnexpectedData data = GSON.fromJson(reader, UnexpectedData.class);
                if (data != null && data.unexpectedFiles != null) {
                    unexpectedFiles = new ArrayList<>(data.unexpectedFiles);
                }
            } catch (IOException exception) {
                AeternumCarpet.LOGGER.error("Failed to load unexpected files report from {}", unexpectedFilePath, exception);
            }
        }

        scanFull(server);
    }

    public static synchronized void scanFull(MinecraftServer server) {
        if (server == null || filePath == null) {
            return;
        }

        Path playerdataDir = server.getSavePath(WorldSavePath.PLAYERDATA);
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        Path advancementsDir = server.getSavePath(WorldSavePath.ADVANCEMENTS);

        Set<UUID> discoveredUuids = new LinkedHashSet<>();
        List<String> nextUnexpected = new ArrayList<>();

        scanDirectory(playerdataDir, "playerdata", ".dat", true, discoveredUuids, nextUnexpected);
        scanDirectory(statsDir, "stats", ".json", false, discoveredUuids, nextUnexpected);
        scanDirectory(advancementsDir, "advancements", ".json", false, discoveredUuids, nextUnexpected);
        Collections.sort(nextUnexpected);

        Map<String, Entry> nextEntries = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> existingItem : entries.entrySet()) {
            Entry copy = copyEntry(existingItem.getValue());
            UUID existingUuid = tryParseCanonicalUuid(copy.uuid);
            if (existingUuid != null) {
                String resolvedName = resolveKnownName(server, existingUuid);
                if (resolvedName != null && !resolvedName.isEmpty()) {
                    copy.name = resolvedName;
                }
                copy.files = checkFiles(playerdataDir, statsDir, advancementsDir, copy.uuid);
            }
            nextEntries.put(copy.uuid, copy);
        }

        for (UUID uuid : discoveredUuids) {
            String key = uuid.toString();
            if (nextEntries.containsKey(key)) {
                continue;
            }
            Entry entry = new Entry();
            entry.uuid = key;
            entry.type = EntryType.UNKNOWN;
            entry.name = resolveKnownName(server, uuid);
            entry.files = checkFiles(playerdataDir, statsDir, advancementsDir, key);
            nextEntries.put(key, entry);
        }

        boolean entriesChanged = !entriesMatch(entries, nextEntries) || Files.notExists(filePath);
        boolean unexpectedChanged = !nextUnexpected.equals(unexpectedFiles) || Files.notExists(unexpectedFilePath);
        entries = nextEntries;
        unexpectedFiles = nextUnexpected;

        if (entriesChanged) {
            save();
        }
        if (unexpectedChanged) {
            saveUnexpected();
        }
    }

    public static synchronized void onPlayerJoin(MinecraftServer server, ServerPlayerEntity player) {
        if (server == null || player == null || filePath == null) {
            return;
        }

        UUID uuid = player.getUuid();
        String key = uuid.toString();
        boolean isBot = player instanceof EntityPlayerMPFake;
        String currentName = player.getName().getString();
        FilePresence currentFiles = checkFiles(
            server.getSavePath(WorldSavePath.PLAYERDATA),
            server.getSavePath(WorldSavePath.STATS),
            server.getSavePath(WorldSavePath.ADVANCEMENTS),
            key
        );

        Entry existing = entries.get(key);
        if (existing == null) {
            Entry created = new Entry();
            created.uuid = key;
            created.name = currentName;
            created.type = isBot ? EntryType.BOT : EntryType.HUMAN;
            created.files = currentFiles;
            entries.put(key, created);
            save();
            return;
        }

        boolean changed = false;
        if (!Objects.equals(existing.name, currentName)) {
            existing.name = currentName;
            changed = true;
        }
        if (!Objects.equals(existing.files, currentFiles)) {
            existing.files = currentFiles;
            changed = true;
        }

        if (!isBot) {
            if (existing.type != EntryType.HUMAN) {
                existing.type = EntryType.HUMAN;
                changed = true;
            }
        } else {
            if (existing.type == EntryType.UNKNOWN) {
                existing.type = EntryType.BOT;
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }

    public static synchronized void refreshPlayer(MinecraftServer server, ServerPlayerEntity player) {
        if (server == null || player == null || filePath == null) {
            return;
        }

        UUID uuid = player.getUuid();
        String key = uuid.toString();
        Path playerdataDir = server.getSavePath(WorldSavePath.PLAYERDATA);
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        Path advancementsDir = server.getSavePath(WorldSavePath.ADVANCEMENTS);

        FilePresence nextFiles = checkFiles(playerdataDir, statsDir, advancementsDir, key);
        if (!nextFiles.playerdata && !nextFiles.stats && !nextFiles.advancements) {
            return;
        }

        Entry existing = entries.get(key);
        String currentName = player.getName().getString();
        if (existing == null) {
            Entry created = new Entry();
            created.uuid = key;
            created.name = currentName;
            created.type = (player instanceof EntityPlayerMPFake) ? EntryType.BOT : EntryType.HUMAN;
            created.files = nextFiles;
            entries.put(key, created);
            save();
            return;
        }

        boolean changed = false;
        if (!Objects.equals(existing.name, currentName)) {
            existing.name = currentName;
            changed = true;
        }
        if (!Objects.equals(existing.files, nextFiles)) {
            existing.files = nextFiles;
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public static synchronized Map<String, Entry> getEntriesSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static synchronized List<String> getUnexpectedFilesSnapshot() {
        return List.copyOf(unexpectedFiles);
    }

    public static synchronized boolean deleteUnexpectedFile(MinecraftServer server, String relativePath) {
        if (server == null || relativePath == null || !unexpectedFiles.contains(relativePath)) {
            return false;
        }
        boolean deleted = deleteUnexpectedTarget(server, relativePath);
        scanFull(server);
        return deleted;
    }

    public static synchronized int deleteAllUnexpectedFiles(MinecraftServer server) {
        if (server == null || unexpectedFiles.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String relativePath : List.copyOf(unexpectedFiles)) {
            if (deleteUnexpectedTarget(server, relativePath)) {
                count++;
            }
        }
        scanFull(server);
        return count;
    }

    private static boolean deleteUnexpectedTarget(MinecraftServer server, String relativePath) {
        Path baseDir;
        String subPath;
        if (relativePath.startsWith("playerdata/")) {
            baseDir = server.getSavePath(WorldSavePath.PLAYERDATA).toAbsolutePath().normalize();
            subPath = relativePath.substring("playerdata/".length());
        } else if (relativePath.startsWith("stats/")) {
            baseDir = server.getSavePath(WorldSavePath.STATS).toAbsolutePath().normalize();
            subPath = relativePath.substring("stats/".length());
        } else if (relativePath.startsWith("advancements/")) {
            baseDir = server.getSavePath(WorldSavePath.ADVANCEMENTS).toAbsolutePath().normalize();
            subPath = relativePath.substring("advancements/".length());
        } else {
            return false;
        }

        if (subPath.endsWith("/")) {
            subPath = subPath.substring(0, subPath.length() - 1);
        }
        if (subPath.isEmpty() || subPath.contains("/") || subPath.contains("\\") || subPath.equals("..") || subPath.equals(".")) {
            return false;
        }

        Path target = baseDir.resolve(subPath).normalize();
        if (!target.startsWith(baseDir) || target.equals(baseDir)) {
            return false;
        }

        try {
            if (Files.isDirectory(target)) {
                try (Stream<Path> walk = Files.walk(target)) {
                    for (Path p : walk.sorted(Collections.reverseOrder()).toList()) {
                        Files.deleteIfExists(p);
                    }
                }
                return true;
            }
            return Files.deleteIfExists(target);
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to delete unexpected file {}", target, exception);
            return false;
        }
    }

    public static synchronized boolean setType(String uuid, EntryType nextType) {
        if (uuid == null || nextType == null) {
            return false;
        }
        Entry entry = entries.get(uuid);
        if (entry == null) {
            return false;
        }
        if (entry.type != nextType) {
            entry.type = nextType;
            save();
        }
        return true;
    }

    public static synchronized boolean setName(String uuid, String nextName) {
        if (uuid == null || nextName == null || nextName.isBlank()) {
            return false;
        }
        Entry entry = entries.get(uuid);
        if (entry == null) {
            return false;
        }
        if (!Objects.equals(entry.name, nextName)) {
            entry.name = nextName;
            save();
        }
        return true;
    }

    private static void scanDirectory(
        Path dir,
        String folderLabel,
        String expectedExt,
        boolean allowDatOld,
        Set<UUID> discoveredUuids,
        List<String> unexpected
    ) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(path -> {
                String fileName = path.getFileName().toString();
                if (!Files.isRegularFile(path)) {
                    unexpected.add(folderLabel + "/" + fileName + "/");
                    return;
                }
                if (fileName.endsWith(expectedExt)) {
                    String rawUuid = fileName.substring(0, fileName.length() - expectedExt.length());
                    UUID parsed = tryParseCanonicalUuid(rawUuid);
                    if (parsed != null) {
                        discoveredUuids.add(parsed);
                        return;
                    }
                } else if (allowDatOld && fileName.endsWith(".dat_old")) {
                    String rawUuid = fileName.substring(0, fileName.length() - ".dat_old".length());
                    if (tryParseCanonicalUuid(rawUuid) != null) {
                        return;
                    }
                }
                unexpected.add(folderLabel + "/" + fileName);
            });
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to scan directory {} for player registry", dir, exception);
        }
    }

    private static UUID tryParseCanonicalUuid(String raw) {
        if (raw == null || raw.length() != 36) {
            return null;
        }
        try {
            UUID parsed = UUID.fromString(raw);
            return parsed.toString().equals(raw.toLowerCase(Locale.ROOT)) ? parsed : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static FilePresence checkFiles(Path playerdataDir, Path statsDir, Path advancementsDir, String uuidStr) {
        return new FilePresence(
            Files.isRegularFile(playerdataDir.resolve(uuidStr + ".dat")),
            Files.isRegularFile(statsDir.resolve(uuidStr + ".json")),
            Files.isRegularFile(advancementsDir.resolve(uuidStr + ".json"))
        );
    }

    private static String resolveKnownName(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        if (online != null) {
            return online.getName().getString();
        }
        UserCache cache = server.getUserCache();
        if (cache != null) {
            Optional<GameProfile> profile = cache.getByUuid(uuid);
            if (profile.isPresent() && profile.get().getName() != null && !profile.get().getName().isBlank()) {
                return profile.get().getName();
            }
        }
        return null;
    }

    private static Entry copyEntry(Entry source) {
        Entry copy = new Entry();
        copy.uuid = source.uuid;
        copy.name = source.name;
        copy.type = source.type != null ? source.type : EntryType.UNKNOWN;
        copy.files = source.files != null
            ? new FilePresence(source.files.playerdata, source.files.stats, source.files.advancements)
            : new FilePresence();
        return copy;
    }

    private static Entry sanitizeEntry(String key, Entry raw) {
        if (raw == null) {
            return null;
        }
        UUID parsed = tryParseCanonicalUuid(raw.uuid != null ? raw.uuid : key);
        if (parsed == null) {
            return null;
        }
        Entry clean = new Entry();
        clean.uuid = parsed.toString();
        clean.name = raw.name;
        clean.type = raw.type != null ? raw.type : EntryType.UNKNOWN;
        clean.files = raw.files != null ? raw.files : new FilePresence();
        return clean;
    }

    private static boolean entriesMatch(Map<String, Entry> a, Map<String, Entry> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<String, Entry> item : a.entrySet()) {
            Entry other = b.get(item.getKey());
            if (other == null) {
                return false;
            }
            Entry cur = item.getValue();
            if (!Objects.equals(cur.name, other.name)
                || cur.type != other.type
                || !Objects.equals(cur.files, other.files)) {
                return false;
            }
        }
        return true;
    }

    private static void save() {
        if (filePath == null) {
            return;
        }
        try {
            Files.createDirectories(filePath.getParent());
            Data data = new Data();
            data.entries = entries;
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to save player registry to {}", filePath, exception);
        }
    }

    private static void saveUnexpected() {
        if (unexpectedFilePath == null) {
            return;
        }
        try {
            Files.createDirectories(unexpectedFilePath.getParent());
            UnexpectedData data = new UnexpectedData();
            data.unexpectedFiles = unexpectedFiles;
            try (Writer writer = Files.newBufferedWriter(unexpectedFilePath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to save unexpected files report to {}", unexpectedFilePath, exception);
        }
    }
}
