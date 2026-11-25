package smp.aeternum.carpet.stats;

import carpet.patches.EntityPlayerMPFake;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
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
    private static final AtomicBoolean SUMMARY_REFRESH_RUNNING = new AtomicBoolean(false);

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

    public static final class EntrySummary {
        public long datMtime;
        public int enderSlots = -1;
        public int invSlots = -1;
        public long statsMtime;
        public long playTimeTicks = -1L;
        public long mined = -1L;
        public long crafted = -1L;

        public EntrySummary copy() {
            EntrySummary c = new EntrySummary();
            c.datMtime = datMtime;
            c.enderSlots = enderSlots;
            c.invSlots = invSlots;
            c.statsMtime = statsMtime;
            c.playTimeTicks = playTimeTicks;
            c.mined = mined;
            c.crafted = crafted;
            return c;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EntrySummary that)) return false;
            return datMtime == that.datMtime
                && enderSlots == that.enderSlots
                && invSlots == that.invSlots
                && statsMtime == that.statsMtime
                && playTimeTicks == that.playTimeTicks
                && mined == that.mined
                && crafted == that.crafted;
        }

        @Override
        public int hashCode() {
            return Objects.hash(datMtime, enderSlots, invSlots, statsMtime, playTimeTicks, mined, crafted);
        }
    }

    public static final class Entry {
        public String uuid;
        public String name;
        public EntryType type = EntryType.UNKNOWN;
        public FilePresence files = new FilePresence();
        public EntrySummary summary;

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

    public record HealResult(int healed, int restoredDatOld, int skippedMissingDat, int skippedOnline) {
    }

    public enum SingleHealStatus {
        HEALED,
        RESTORED_DAT_OLD,
        MISSING_DAT,
        ONLINE,
        NOT_ANOMALOUS,
        NOT_FOUND,
        IO_ERROR
    }

    public enum DeleteResult {
        OK,
        NOT_FOUND,
        ONLINE,
        IO_ERROR
    }

    public static synchronized SingleHealStatus healSingleAnomaly(MinecraftServer server, String uuidStr) {
        if (server == null || uuidStr == null) {
            return SingleHealStatus.NOT_FOUND;
        }
        Entry entry = entries.get(uuidStr);
        if (entry == null) {
            return SingleHealStatus.NOT_FOUND;
        }
        if (!entry.hasAnomaly()) {
            return SingleHealStatus.NOT_ANOMALOUS;
        }
        Path playerdataDir = server.getSavePath(WorldSavePath.PLAYERDATA);
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        Path advancementsDir = server.getSavePath(WorldSavePath.ADVANCEMENTS);
        int dataVersion = SharedConstants.getGameVersion().dataVersion().id();

        SingleHealStatus status = healEntryInternal(server, entry, playerdataDir, statsDir, advancementsDir, dataVersion);
        if (status == SingleHealStatus.HEALED || status == SingleHealStatus.RESTORED_DAT_OLD) {
            scanFull(server);
        }
        return status;
    }

    public static synchronized HealResult healAllAnomalies(MinecraftServer server) {
        if (server == null) {
            return new HealResult(0, 0, 0, 0);
        }

        Path playerdataDir = server.getSavePath(WorldSavePath.PLAYERDATA);
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        Path advancementsDir = server.getSavePath(WorldSavePath.ADVANCEMENTS);
        int dataVersion = SharedConstants.getGameVersion().dataVersion().id();

        int healed = 0;
        int restoredDatOld = 0;
        int skippedMissingDat = 0;
        int skippedOnline = 0;

        for (Entry entry : List.copyOf(entries.values())) {
            if (!entry.hasAnomaly()) {
                continue;
            }
            switch (healEntryInternal(server, entry, playerdataDir, statsDir, advancementsDir, dataVersion)) {
                case RESTORED_DAT_OLD -> {
                    restoredDatOld++;
                    healed++;
                }
                case HEALED -> healed++;
                case MISSING_DAT -> skippedMissingDat++;
                case ONLINE -> skippedOnline++;
                default -> { }
            }
        }

        scanFull(server);
        return new HealResult(healed, restoredDatOld, skippedMissingDat, skippedOnline);
    }

    private static SingleHealStatus healEntryInternal(
        MinecraftServer server,
        Entry entry,
        Path playerdataDir,
        Path statsDir,
        Path advancementsDir,
        int dataVersion
    ) {
        UUID uuid = tryParseCanonicalUuid(entry.uuid);
        if (uuid == null) {
            return SingleHealStatus.NOT_FOUND;
        }
        if (server.getPlayerManager().getPlayer(uuid) != null) {
            return SingleHealStatus.ONLINE;
        }

        String key = uuid.toString();
        Path datPath = playerdataDir.resolve(key + ".dat");
        Path datOldPath = playerdataDir.resolve(key + ".dat_old");
        Path statsPath = statsDir.resolve(key + ".json");
        Path advPath = advancementsDir.resolve(key + ".json");

        boolean restored = false;
        boolean modified = false;
        try {
            if (Files.notExists(datPath) && Files.isRegularFile(datOldPath)) {
                Files.createDirectories(playerdataDir);
                Files.copy(datOldPath, datPath);
                restored = true;
                modified = true;
            }
            if (Files.notExists(datPath)) {
                return SingleHealStatus.MISSING_DAT;
            }
            if (Files.notExists(statsPath)) {
                Files.createDirectories(statsDir);
                Files.writeString(statsPath, "{\"stats\":{},\"DataVersion\":" + dataVersion + "}");
                modified = true;
            }
            if (Files.notExists(advPath)) {
                Files.createDirectories(advancementsDir);
                Files.writeString(advPath, "{\"DataVersion\":" + dataVersion + "}");
                modified = true;
            }
            if (restored) {
                return SingleHealStatus.RESTORED_DAT_OLD;
            }
            return modified ? SingleHealStatus.HEALED : SingleHealStatus.NOT_ANOMALOUS;
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to heal anomalies for {}", key, exception);
            return SingleHealStatus.IO_ERROR;
        }
    }

    public static synchronized DeleteResult deleteEntryAndFiles(MinecraftServer server, String uuidStr) {
        if (server == null || uuidStr == null) {
            return DeleteResult.NOT_FOUND;
        }
        Entry existing = entries.get(uuidStr);
        if (existing == null) {
            return DeleteResult.NOT_FOUND;
        }
        UUID uuid = tryParseCanonicalUuid(uuidStr);
        if (uuid != null && server.getPlayerManager().getPlayer(uuid) != null) {
            return DeleteResult.ONLINE;
        }

        Path playerdataDir = server.getSavePath(WorldSavePath.PLAYERDATA);
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        Path advancementsDir = server.getSavePath(WorldSavePath.ADVANCEMENTS);

        try {
            Files.deleteIfExists(playerdataDir.resolve(uuidStr + ".dat"));
            Files.deleteIfExists(playerdataDir.resolve(uuidStr + ".dat_old"));
            Files.deleteIfExists(statsDir.resolve(uuidStr + ".json"));
            Files.deleteIfExists(advancementsDir.resolve(uuidStr + ".json"));
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to delete player files for {}", uuidStr, exception);
            return DeleteResult.IO_ERROR;
        }

        entries.remove(uuidStr);
        save();
        return DeleteResult.OK;
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

    public enum PromoteUnexpectedStatus {
        PROMOTED,
        OFFICIAL_EXISTS,
        ONLINE,
        CORRUPT_OR_EMPTY,
        INVALID,
        IO_ERROR
    }

    public static String extractUuidPrefix(String relativePath) {
        if (relativePath == null) {
            return "";
        }
        int slash = relativePath.indexOf('/');
        String fileName = slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
        if (fileName.endsWith("/")) {
            fileName = fileName.substring(0, fileName.length() - 1);
        }
        if (fileName.length() < 37) {
            return "";
        }
        char sep = fileName.charAt(36);
        if (sep != '-' && sep != '.') {
            return "";
        }
        UUID parsed = tryParseCanonicalUuid(fileName.substring(0, 36));
        return parsed != null ? parsed.toString() : "";
    }

    public static Path resolveUnexpectedTarget(MinecraftServer server, String relativePath) {
        if (server == null || relativePath == null) {
            return null;
        }
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
            return null;
        }

        if (subPath.endsWith("/")) {
            subPath = subPath.substring(0, subPath.length() - 1);
        }
        if (subPath.isEmpty() || subPath.contains("/") || subPath.contains("\\") || subPath.equals("..") || subPath.equals(".")) {
            return null;
        }

        Path target = baseDir.resolve(subPath).normalize();
        if (!target.startsWith(baseDir) || target.equals(baseDir)) {
            return null;
        }
        return target;
    }

    public static boolean isValidPlayerdataNbt(Path target) {
        if (target == null || !Files.isRegularFile(target)) {
            return false;
        }
        try {
            if (Files.size(target) <= 0L) {
                return false;
            }
            return NbtIo.readCompressed(target, NbtSizeTracker.ofUnlimitedBytes()) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static synchronized PromoteUnexpectedStatus promoteUnexpectedToOfficial(MinecraftServer server, String relativePath) {
        if (server == null || relativePath == null || !unexpectedFiles.contains(relativePath)) {
            return PromoteUnexpectedStatus.INVALID;
        }
        if (!relativePath.startsWith("playerdata/") || !relativePath.endsWith(".dat")) {
            return PromoteUnexpectedStatus.INVALID;
        }
        Path source = resolveUnexpectedTarget(server, relativePath);
        String uuidStr = extractUuidPrefix(relativePath);
        UUID uuid = tryParseCanonicalUuid(uuidStr);
        if (source == null || uuid == null || !Files.isRegularFile(source)) {
            return PromoteUnexpectedStatus.INVALID;
        }
        if (server.getPlayerManager().getPlayer(uuid) != null) {
            return PromoteUnexpectedStatus.ONLINE;
        }
        Path officialDat = server.getSavePath(WorldSavePath.PLAYERDATA).toAbsolutePath().normalize().resolve(uuidStr + ".dat");
        if (Files.exists(officialDat)) {
            return PromoteUnexpectedStatus.OFFICIAL_EXISTS;
        }
        if (!isValidPlayerdataNbt(source)) {
            return PromoteUnexpectedStatus.CORRUPT_OR_EMPTY;
        }
        try {
            Files.move(source, officialDat);
            scanFull(server);
            return PromoteUnexpectedStatus.PROMOTED;
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Failed to promote unexpected playerdata {} -> {}", source, officialDat, exception);
            return PromoteUnexpectedStatus.IO_ERROR;
        }
    }

    private static boolean deleteUnexpectedTarget(MinecraftServer server, String relativePath) {
        Path target = resolveUnexpectedTarget(server, relativePath);
        if (target == null) {
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
        var pm = server != null ? server.getPlayerManager() : null;
        ServerPlayerEntity online = pm != null ? pm.getPlayer(uuid) : null;
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
        copy.summary = source.summary != null ? source.summary.copy() : null;
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
        clean.summary = raw.summary != null ? raw.summary.copy() : null;
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
                || !Objects.equals(cur.files, other.files)
                || !Objects.equals(cur.summary, other.summary)) {
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

    public static void refreshSummariesAsync(MinecraftServer server, Runnable onUpdated) {
        if (server == null || !SUMMARY_REFRESH_RUNNING.compareAndSet(false, true)) {
            return;
        }
        Path playerdataDir = server.getSavePath(WorldSavePath.PLAYERDATA);
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        List<Entry> snapshot;
        synchronized (PlayerRegistryStore.class) {
            snapshot = new ArrayList<>(entries.size());
            for (Entry e : entries.values()) {
                snapshot.add(copyEntry(e));
            }
        }
        Thread worker = new Thread(() -> {
            try {
                Map<String, EntrySummary> updates = new LinkedHashMap<>();
                for (Entry e : snapshot) {
                    if (e.uuid == null) continue;
                    boolean hasDat = e.files != null && e.files.playerdata;
                    boolean hasStats = e.files != null && e.files.stats;
                    Path datPath = playerdataDir.resolve(e.uuid + ".dat");
                    Path statsPath = statsDir.resolve(e.uuid + ".json");
                    long datMtime = (hasDat && Files.isRegularFile(datPath)) ? safeMtime(datPath) : 0L;
                    long statsMtime = (hasStats && Files.isRegularFile(statsPath)) ? safeMtime(statsPath) : 0L;

                    EntrySummary prev = e.summary;
                    boolean needDat = (prev == null && datMtime > 0L) || (prev != null && prev.datMtime != datMtime);
                    boolean needStats = (prev == null && statsMtime > 0L) || (prev != null && prev.statsMtime != statsMtime);
                    if (!needDat && !needStats) {
                        continue;
                    }

                    EntrySummary next = prev != null ? prev.copy() : new EntrySummary();
                    if (needDat) {
                        next.datMtime = datMtime;
                        if (datMtime > 0L) {
                            readDatSummary(datPath, next);
                        } else {
                            next.enderSlots = -1;
                            next.invSlots = -1;
                        }
                    }
                    if (needStats) {
                        next.statsMtime = statsMtime;
                        if (statsMtime > 0L) {
                            readStatsSummary(statsPath, next);
                        } else {
                            next.playTimeTicks = -1L;
                            next.mined = -1L;
                            next.crafted = -1L;
                        }
                    }
                    updates.put(e.uuid, next);
                }

                if (!updates.isEmpty()) {
                    synchronized (PlayerRegistryStore.class) {
                        for (Map.Entry<String, EntrySummary> u : updates.entrySet()) {
                            Entry target = entries.get(u.getKey());
                            if (target != null) {
                                target.summary = u.getValue();
                            }
                        }
                        save();
                    }
                    if (onUpdated != null) {
                        server.execute(onUpdated);
                    }
                }
            } catch (Exception ex) {
                AeternumCarpet.LOGGER.warn("Failed refreshing player registry summaries", ex);
            } finally {
                SUMMARY_REFRESH_RUNNING.set(false);
            }
        }, "aeternum-registry-summary");
        worker.setDaemon(true);
        worker.start();
    }

    private static long safeMtime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static void readDatSummary(Path datPath, EntrySummary out) {
        try {
            NbtCompound root = NbtIo.readCompressed(datPath, NbtSizeTracker.ofUnlimitedBytes());
            if (root == null) return;
            NbtList inv = root.getListOrEmpty("Inventory");
            NbtList ender = root.getListOrEmpty("EnderItems");
            out.invSlots = inv.size();
            out.enderSlots = ender.size();
        } catch (Exception ignored) {
            out.invSlots = -1;
            out.enderSlots = -1;
        }
    }

    private static void readStatsSummary(Path statsPath, EntrySummary out) {
        try (Reader reader = Files.newBufferedReader(statsPath)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();
            JsonObject statsObj = root.has("stats") && root.get("stats").isJsonObject()
                ? root.getAsJsonObject("stats")
                : null;
            if (statsObj == null) {
                out.playTimeTicks = 0L;
                out.mined = 0L;
                out.crafted = 0L;
                return;
            }
            long playTime = 0L;
            if (statsObj.has("minecraft:custom") && statsObj.get("minecraft:custom").isJsonObject()) {
                JsonObject custom = statsObj.getAsJsonObject("minecraft:custom");
                if (custom.has("minecraft:play_time") && custom.get("minecraft:play_time").isJsonPrimitive()) {
                    playTime = Math.max(0L, custom.get("minecraft:play_time").getAsLong());
                }
            }
            out.playTimeTicks = playTime;
            out.mined = sumJsonSection(statsObj, "minecraft:mined");
            out.crafted = sumJsonSection(statsObj, "minecraft:crafted");
        } catch (Exception ignored) {
            out.playTimeTicks = -1L;
            out.mined = -1L;
            out.crafted = -1L;
        }
    }

    private static long sumJsonSection(JsonObject statsObj, String section) {
        if (!statsObj.has(section) || !statsObj.get(section).isJsonObject()) {
            return 0L;
        }
        long sum = 0L;
        JsonObject sec = statsObj.getAsJsonObject(section);
        for (Map.Entry<String, JsonElement> item : sec.entrySet()) {
            if (item.getValue() != null && item.getValue().isJsonPrimitive()) {
                try {
                    sum += Math.max(0L, item.getValue().getAsLong());
                } catch (Exception ignored) {
                }
            }
        }
        return sum;
    }
}
