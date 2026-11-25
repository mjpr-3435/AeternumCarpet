package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.bot.BotInventoryOpener;
import smp.aeternum.carpet.bot.OfflinePlayerSession;
import smp.aeternum.carpet.motd.MotdMarkup;
import smp.aeternum.carpet.stats.AreaRegion;
import smp.aeternum.carpet.stats.AreaScanner;
import smp.aeternum.carpet.stats.PlayerRegistryStore;
import smp.aeternum.carpet.stats.PlayerRegistryStore.Entry;
import smp.aeternum.carpet.stats.PlayerRegistryStore.EntryType;
import smp.aeternum.carpet.stats.ScoreboardConfigStore;
import smp.aeternum.carpet.stats.ScoreboardConfigStore.ObjectiveConfig;

public final class StatsNetworking {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private static final Set<String> IN_FLIGHT_LOOKUPS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ACTIVE_VIEWERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, AreaRegion> PENDING_AREA_SCANS = new ConcurrentHashMap<>();

    private static final Deque<String> BATCH_QUEUE = new ArrayDeque<>();
    private static boolean batchRunning;
    private static boolean batchInFlight;
    private static int batchCooldownTicks;
    private static int batchDone;
    private static int batchTotal;
    private static int batchResolved;

    private StatsNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(StatsDataPayload.ID, StatsDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScoreboardDetailPayload.ID, ScoreboardDetailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AreaContourPreviewPayload.ID, AreaContourPreviewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerDetailPayload.ID, PlayerDetailPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StatsRegistryActionPayload.ID, StatsRegistryActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(StatsRegistryActionPayload.ID, (payload, context) ->
            context.server().execute(() -> handleAction(context.server(), context.player(), payload))
        );
    }

    public static void sendSnapshot(ServerPlayerEntity player, boolean openScreen) {
        sendSnapshot(player, openScreen, "", false);
    }

    private static void sendSnapshot(ServerPlayerEntity player, boolean openScreen, String status, boolean error) {
        if (!AeternumCarpetSettings.commandStats || !player.hasPermissionLevel(2)) {
            return;
        }
        if (!ProtocolHandshake.hasVersion(player, 2)) {
            if (openScreen) {
                player.sendMessage(
                    Text.literal("[Stats] Necesitas actualizar AeternumCarpet a 2.1.0 para abrir la interfaz de /stats (está en el canal de " + ProtocolHandshake.UPDATE_LOCATION + ").")
                        .formatted(Formatting.YELLOW),
                    false
                );
            }
            return;
        }
        if (openScreen) {
            ACTIVE_VIEWERS.add(player.getUuid());
            MinecraftServer server = player.getServer();
            PlayerRegistryStore.refreshSummariesAsync(server, () -> broadcastProgress(server, "Resúmenes de jugadores actualizados.", false));
        }
        StatsSnapshot snapshot = buildSnapshot(player.getServer());
        ServerPlayNetworking.send(player, new StatsDataPayload(GSON.toJson(snapshot), openScreen, status, error));
    }

    private static void broadcastProgress(MinecraftServer server, String status, boolean error) {
        if (server == null || ACTIVE_VIEWERS.isEmpty()) {
            return;
        }
        String json = GSON.toJson(buildSnapshot(server));
        for (UUID viewerId : List.copyOf(ACTIVE_VIEWERS)) {
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(viewerId);
            if (viewer == null || viewer.isDisconnected() || !AeternumCarpetSettings.commandStats || !viewer.hasPermissionLevel(2)) {
                ACTIVE_VIEWERS.remove(viewerId);
                continue;
            }
            ServerPlayNetworking.send(viewer, new StatsDataPayload(json, false, status, error));
        }
    }

    private static StatsSnapshot buildSnapshot(MinecraftServer server) {
        StatsSnapshot snapshot = new StatsSnapshot();
        Map<String, Entry> rawEntries = PlayerRegistryStore.getEntriesSnapshot();
        List<RegistryEntrySnapshot> list = new ArrayList<>(rawEntries.size());
        Map<String, EnumSet<EntryType>> typesByName = new HashMap<>();
        for (Entry entry : rawEntries.values()) {
            RegistryEntrySnapshot item = new RegistryEntrySnapshot();
            item.uuid = entry.uuid;
            item.name = entry.name != null ? entry.name : "";
            item.type = entry.type != null ? entry.type.name() : EntryType.UNKNOWN.name();
            item.uuidVersion = parseUuidVersion(entry.uuid);
            if (entry.files != null) {
                item.playerdata = entry.files.playerdata;
                item.stats = entry.files.stats;
                item.advancements = entry.files.advancements;
            }
            item.anomaly = entry.hasAnomaly();
            populateEntrySummary(server, entry, item);
            list.add(item);

            if (!item.name.isEmpty()) {
                typesByName.computeIfAbsent(item.name.toLowerCase(Locale.ROOT), k -> EnumSet.noneOf(EntryType.class))
                    .add(entry.type != null ? entry.type : EntryType.UNKNOWN);
            }
        }
        list.sort(
            Comparator.comparing((RegistryEntrySnapshot e) -> e.name.isEmpty())
                .thenComparing(e -> e.name.isEmpty() ? e.uuid : e.name, String.CASE_INSENSITIVE_ORDER)
        );
        snapshot.entries = list;
        snapshot.unexpectedFiles = PlayerRegistryStore.getUnexpectedFilesSnapshot();
        snapshot.unexpectedDetails = buildUnexpectedDetails(server, snapshot.unexpectedFiles, rawEntries);
        snapshot.scoreboards = buildScoreboardSnapshots(server, typesByName);
        snapshot.lookupRunning = batchRunning;
        snapshot.lookupDone = batchDone;
        snapshot.lookupTotal = batchTotal;
        snapshot.lookupResolved = batchResolved;
        return snapshot;
    }

    private static List<ScoreboardObjectiveSnapshot> buildScoreboardSnapshots(
        MinecraftServer server,
        Map<String, EnumSet<EntryType>> typesByName
    ) {
        if (server == null) {
            return List.of();
        }
        Scoreboard sb = server.getScoreboard();
        ScoreboardObjective sidebarObj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        ScoreboardObjective listObj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        ScoreboardObjective belowObj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);

        List<ScoreboardObjectiveSnapshot> out = new ArrayList<>();
        for (ScoreboardObjective obj : sb.getObjectives()) {
            ScoreboardObjectiveSnapshot snap = new ScoreboardObjectiveSnapshot();
            snap.name = obj.getName();
            snap.displayName = obj.getDisplayName() != null ? obj.getDisplayName().getString() : obj.getName();
            snap.criterion = obj.getCriterion() != null ? obj.getCriterion().getName() : "dummy";
            if (obj == sidebarObj) snap.slot = "SIDEBAR";
            else if (obj == listObj) snap.slot = "LIST";
            else if (obj == belowObj) snap.slot = "BELOW_NAME";
            snap.entryCount = sb.getScoreboardEntries(obj).size();
            ObjectiveConfig cfg = ScoreboardConfigStore.getOrDefault(obj.getName());
            if (cfg.tracker != null && !cfg.tracker.isEmpty()) {
                snap.criterion = cfg.tracker;
            }
            snap.chatAlias = cfg.chatAlias != null ? cfg.chatAlias : "";
            snap.displayNameMarkup = (cfg.displayNameMarkup != null && !cfg.displayNameMarkup.isBlank())
                ? cfg.displayNameMarkup
                : snap.displayName;
            snap.totalEnabled = cfg.totalEnabled;
            snap.totalHumans = cfg.totalHumans;
            snap.totalBots = cfg.totalBots;
            snap.totalUnknown = cfg.totalUnknown;
            AreaRegion area = cfg.area != null ? cfg.area : new AreaRegion();
            snap.areaMode = area.mode != null ? area.mode.name() : "NONE";
            snap.areaDimension = area.dimension != null ? area.dimension : "minecraft:overworld";
            snap.areaX1 = area.x1;
            snap.areaZ1 = area.z1;
            snap.areaX2 = area.x2;
            snap.areaZ2 = area.z2;
            snap.areaColumnCount = area.columnCount;
            snap.areaPreviewSize = area.previewSize;
            snap.areaPreviewGrid = area.previewGrid != null ? area.previewGrid : "";
            snap.areaBlockId = area.blockId != null ? area.blockId : "";
            out.add(snap);
        }
        out.sort(Comparator.comparing((ScoreboardObjectiveSnapshot o) -> o.name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static void sendObjectiveDetail(ServerPlayerEntity player, MinecraftServer server, String objectiveName) {
        if (!AeternumCarpetSettings.commandStats || !player.hasPermissionLevel(2) || objectiveName == null || objectiveName.isEmpty()) {
            return;
        }
        Scoreboard sb = server.getScoreboard();
        ScoreboardObjective target = null;
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (obj.getName().equals(objectiveName)) {
                target = obj;
                break;
            }
        }
        if (target == null) {
            ServerPlayNetworking.send(player, new ScoreboardDetailPayload(objectiveName, "[]"));
            return;
        }
        Map<String, EnumSet<EntryType>> typesByName = new HashMap<>();
        for (Entry entry : PlayerRegistryStore.getEntriesSnapshot().values()) {
            if (entry.name != null && !entry.name.isBlank()) {
                typesByName.computeIfAbsent(entry.name.toLowerCase(Locale.ROOT), k -> EnumSet.noneOf(EntryType.class))
                    .add(entry.type != null ? entry.type : EntryType.UNKNOWN);
            }
        }
        List<ScoreboardEntrySnapshot> entries = new ArrayList<>();
        for (ScoreboardEntry entry : sb.getScoreboardEntries(target)) {
            ScoreboardEntrySnapshot eSnap = new ScoreboardEntrySnapshot();
            eSnap.owner = entry.owner();
            eSnap.score = entry.value();
            eSnap.badge = resolveOwnerBadge(typesByName, entry.owner());
            entries.add(eSnap);
        }
        entries.sort(Comparator.comparingInt((ScoreboardEntrySnapshot e) -> e.score).reversed()
            .thenComparing(e -> e.owner, String.CASE_INSENSITIVE_ORDER));
        ServerPlayNetworking.send(player, new ScoreboardDetailPayload(objectiveName, GSON.toJson(entries)));
    }

    private static String resolveOwnerBadge(Map<String, EnumSet<EntryType>> typesByName, String owner) {
        if (owner == null || owner.isBlank()) {
            return "UNKNOWN";
        }
        EnumSet<EntryType> types = typesByName.get(owner.toLowerCase(Locale.ROOT));
        if (types == null || types.isEmpty()) {
            return "UNKNOWN";
        }
        boolean hasHuman = types.contains(EntryType.HUMAN);
        boolean hasBot = types.contains(EntryType.BOT);
        if (hasHuman && hasBot) return "AMBOS";
        if (hasHuman) return "HUMAN";
        if (hasBot) return "BOT";
        return "UNKNOWN";
    }

    private static int parseUuidVersion(String raw) {
        try {
            return UUID.fromString(raw).version();
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }

    private static void handleAction(MinecraftServer server, ServerPlayerEntity player, StatsRegistryActionPayload payload) {
        if (!AeternumCarpetSettings.commandStats || !player.hasPermissionLevel(2) || !ProtocolHandshake.hasVersion(player, 2)) {
            return;
        }
        ACTIVE_VIEWERS.add(player.getUuid());
        switch (payload.action()) {
            case "close_screen" -> ACTIVE_VIEWERS.remove(player.getUuid());
            case "rescan" -> {
                PlayerRegistryStore.scanFull(server);
                sendSnapshot(player, false, "Escaneo completado.", false);
                PlayerRegistryStore.refreshSummariesAsync(server, () -> broadcastProgress(server, "Resúmenes de jugadores actualizados.", false));
            }
            case "lookup_all_online" -> startBatchLookup(server, player);
            case "cancel_lookup_all" -> cancelBatchLookup(server, player);
            case "refresh_scoreboards" -> sendSnapshot(player, false, "Scoreboards actualizadas.", false);
            case "clear_ae_objectives" -> {
                Scoreboard sb = server.getScoreboard();
                List<ScoreboardObjective> toRemove = new ArrayList<>();
                for (ScoreboardObjective obj : sb.getObjectives()) {
                    if (obj.getName().toLowerCase(Locale.ROOT).startsWith("ae-")) {
                        toRemove.add(obj);
                    }
                }
                for (ScoreboardObjective obj : toRemove) {
                    sb.removeObjective(obj);
                    ScoreboardConfigStore.remove(obj.getName());
                }
                sendSnapshot(player, false,
                    toRemove.isEmpty() ? "No hay scoreboards ae-* que limpiar." : ("Limpiadas " + toRemove.size() + " scoreboards ae-*."),
                    false);
            }
            case "select_objective" -> sendObjectiveDetail(player, server, payload.value());
            case "delete_objective" -> {
                String objName = payload.value();
                Scoreboard sb = server.getScoreboard();
                ScoreboardObjective target = null;
                for (ScoreboardObjective obj : sb.getObjectives()) {
                    if (obj.getName().equals(objName)) {
                        target = obj;
                        break;
                    }
                }
                if (target != null) {
                    sb.removeObjective(target);
                    ScoreboardConfigStore.remove(objName);
                    sendSnapshot(player, false, "Scoreboard eliminada: " + objName, false);
                } else {
                    sendSnapshot(player, false, "Scoreboard no encontrada: " + objName, true);
                }
            }
            case "create_objective" -> handleCreateObjective(server, player, payload.value());
            case "edit_objective" -> handleEditObjective(server, player, payload.value());
            case "scan_area_contour" -> handleScanAreaContour(player, payload.value());
            case "set_score" -> handleSetScore(server, player, payload.value());
            case "delete_score_entry" -> handleDeleteScoreEntry(server, player, payload.value());
            case "request_player_detail" -> sendPlayerDetail(server, player, payload.uuid());
            case "open_player_inventory" -> openPlayerInventory(server, player, payload.uuid());
            case "tp_player_last_pos" -> teleportToLastPosition(server, player, payload.uuid());
            case "check_migration" -> handleCheckMigration(server, player, payload.uuid(), payload.value());
            case "execute_migration" -> handleExecuteMigration(server, player, payload.uuid(), payload.value());
            case "set_type" -> {
                EntryType nextType;
                try {
                    nextType = EntryType.valueOf(payload.value().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    sendSnapshot(player, false, "Tipo inválido: " + payload.value(), true);
                    return;
                }
                if (PlayerRegistryStore.setType(payload.uuid(), nextType)) {
                    syncPlayerAcrossAeObjectives(server, payload.uuid(), nextType);
                    sendSnapshot(player, false, "Tipo actualizado a " + nextType.name() + ".", false);
                } else {
                    sendSnapshot(player, false, "UUID no encontrada en el registro.", true);
                }
            }
            case "lookup_name" -> startMojangLookup(server, player, payload.uuid());
            case "delete_unexpected" -> {
                if (PlayerRegistryStore.deleteUnexpectedFile(server, payload.value())) {
                    sendSnapshot(player, false, "Archivo eliminado: " + payload.value(), false);
                } else {
                    sendSnapshot(player, false, "No se pudo eliminar: " + payload.value(), true);
                }
            }
            case "delete_all_unexpected" -> {
                int count = PlayerRegistryStore.deleteAllUnexpectedFiles(server);
                sendSnapshot(player, false, "Eliminados " + count + " archivos raros.", false);
            }
            case "open_unexpected_inventory" -> openUnexpectedInventory(server, player, payload.value());
            case "promote_unexpected" -> handlePromoteUnexpected(server, player, payload.value());
            case "heal_anomalies" -> {
                PlayerRegistryStore.HealResult res = PlayerRegistryStore.healAllAnomalies(server);
                StringBuilder msg = new StringBuilder();
                msg.append("Subsanados: ").append(res.healed());
                if (res.restoredDatOld() > 0) {
                    msg.append(" (").append(res.restoredDatOld()).append(" desde .dat_old)");
                }
                if (res.skippedMissingDat() > 0) {
                    msg.append("  ·  Sin .dat: ").append(res.skippedMissingDat());
                }
                if (res.skippedOnline() > 0) {
                    msg.append("  ·  Online omitidos: ").append(res.skippedOnline());
                }
                sendSnapshot(player, false, msg.toString(), false);
            }
            case "heal_entry" -> {
                switch (PlayerRegistryStore.healSingleAnomaly(server, payload.uuid())) {
                    case HEALED -> sendSnapshot(player, false, "Anomalía subsanada: " + payload.uuid(), false);
                    case RESTORED_DAT_OLD -> sendSnapshot(player, false, "Restaurado .dat_old y subsanado: " + payload.uuid(), false);
                    case MISSING_DAT -> sendSnapshot(player, false, "No se puede subsanar: falta .dat (ni .dat_old).", true);
                    case ONLINE -> sendSnapshot(player, false, "No se puede subsanar: el jugador/bot está conectado.", true);
                    case NOT_ANOMALOUS -> sendSnapshot(player, false, "Esa entrada ya no tiene anomalías.", false);
                    case NOT_FOUND -> sendSnapshot(player, false, "UUID no encontrada en el registro.", true);
                    case IO_ERROR -> sendSnapshot(player, false, "Error de disco al subsanar.", true);
                }
            }
            case "delete_entry" -> {
                switch (PlayerRegistryStore.deleteEntryAndFiles(server, payload.uuid())) {
                    case OK -> sendSnapshot(player, false, "Registro y archivos eliminados: " + payload.uuid(), false);
                    case ONLINE -> sendSnapshot(player, false, "No se puede borrar: el jugador/bot está conectado.", true);
                    case NOT_FOUND -> sendSnapshot(player, false, "UUID no encontrada en el registro.", true);
                    case IO_ERROR -> sendSnapshot(player, false, "Error de disco al borrar archivos.", true);
                }
            }
        }
    }

    private static void startMojangLookup(MinecraftServer server, ServerPlayerEntity requester, String uuidStr) {
        int version = parseUuidVersion(uuidStr);
        if (version != 4) {
            sendSnapshot(requester, false, "Solo se pueden consultar UUIDs online (v4).", true);
            return;
        }
        if (!IN_FLIGHT_LOOKUPS.add(uuidStr)) {
            sendSnapshot(requester, false, "Ya hay una consulta en curso para esa UUID.", false);
            return;
        }

        String compactUuid = uuidStr.replace("-", "");
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + compactUuid))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, throwable) -> server.execute(() -> {
                IN_FLIGHT_LOOKUPS.remove(uuidStr);
                if (!requester.isDisconnected()) {
                    if (throwable != null) {
                        sendSnapshot(requester, false, "Error consultando Mojang: " + throwable.getMessage(), true);
                        return;
                    }
                    int code = response.statusCode();
                    if (code == 204 || code == 404) {
                        sendSnapshot(requester, false, "Mojang no devolvió perfil para esa UUID.", true);
                        return;
                    }
                    if (code != 200) {
                        sendSnapshot(requester, false, "Mojang respondió HTTP " + code + ".", true);
                        return;
                    }
                    try {
                        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                        String name = json != null && json.has("name") ? json.get("name").getAsString() : "";
                        if (name == null || name.isBlank()) {
                            sendSnapshot(requester, false, "Respuesta de Mojang sin nombre.", true);
                            return;
                        }
                        PlayerRegistryStore.setName(uuidStr, name);
                        sendSnapshot(requester, false, "Nombre resuelto: " + name, false);
                    } catch (Exception ex) {
                        sendSnapshot(requester, false, "Error leyendo respuesta de Mojang.", true);
                    }
                }
            }));
    }

    public static void tick(MinecraftServer server) {
        if (!batchRunning || batchInFlight) {
            return;
        }
        if (batchCooldownTicks > 0) {
            batchCooldownTicks--;
            return;
        }
        String nextUuid = BATCH_QUEUE.pollFirst();
        if (nextUuid == null) {
            batchRunning = false;
            broadcastProgress(server, "Consulta masiva completada: " + batchResolved + "/" + batchTotal + " nombres resueltos.", false);
            return;
        }

        batchInFlight = true;
        String compactUuid = nextUuid.replace("-", "");
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + compactUuid))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, throwable) -> server.execute(() -> {
                batchInFlight = false;
                if (!batchRunning) {
                    return;
                }
                if (throwable != null) {
                    batchDone++;
                    batchCooldownTicks = 10;
                    broadcastProgress(server, "", false);
                    return;
                }
                int code = response.statusCode();
                if (code == 429) {
                    BATCH_QUEUE.addFirst(nextUuid);
                    batchCooldownTicks = 100;
                    broadcastProgress(server, "Mojang rate-limit (429), pausando 5s…", false);
                    return;
                }
                if (code == 200) {
                    try {
                        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                        String name = json != null && json.has("name") ? json.get("name").getAsString() : "";
                        if (name != null && !name.isBlank()) {
                            PlayerRegistryStore.setName(nextUuid, name);
                            batchResolved++;
                        }
                    } catch (Exception ignored) {
                    }
                }
                batchDone++;
                batchCooldownTicks = 6;
                if (BATCH_QUEUE.isEmpty()) {
                    batchRunning = false;
                    broadcastProgress(server, "Consulta masiva completada: " + batchResolved + "/" + batchTotal + " nombres resueltos.", false);
                } else {
                    broadcastProgress(server, "", false);
                }
            }));
    }

    private static void startBatchLookup(MinecraftServer server, ServerPlayerEntity requester) {
        if (batchRunning) {
            sendSnapshot(requester, false, "Ya hay una consulta masiva en curso.", false);
            return;
        }
        BATCH_QUEUE.clear();
        for (Entry entry : PlayerRegistryStore.getEntriesSnapshot().values()) {
            if (parseUuidVersion(entry.uuid) == 4 && (entry.name == null || entry.name.isBlank())) {
                BATCH_QUEUE.addLast(entry.uuid);
            }
        }
        if (BATCH_QUEUE.isEmpty()) {
            sendSnapshot(requester, false, "No hay UUIDs online sin nombre.", false);
            return;
        }
        batchTotal = BATCH_QUEUE.size();
        batchDone = 0;
        batchResolved = 0;
        batchCooldownTicks = 0;
        batchInFlight = false;
        batchRunning = true;
        broadcastProgress(server, "Consultando Mojang en cola (" + batchTotal + " UUIDs)…", false);
    }

    private static void cancelBatchLookup(MinecraftServer server, ServerPlayerEntity requester) {
        if (!batchRunning) {
            return;
        }
        BATCH_QUEUE.clear();
        batchRunning = false;
        batchInFlight = false;
        broadcastProgress(server, "Consulta masiva cancelada (" + batchDone + "/" + batchTotal + ", " + batchResolved + " resueltos).", false);
    }

    private static void handleCreateObjective(MinecraftServer server, ServerPlayerEntity player, String json) {
        CreateObjectiveRequest req;
        try {
            req = GSON.fromJson(json, CreateObjectiveRequest.class);
        } catch (Exception ex) {
            sendSnapshot(player, false, "Datos inválidos al crear scoreboard.", true);
            return;
        }
        if (req == null || req.name == null || req.name.isBlank()) {
            sendSnapshot(player, false, "El ID interno no puede estar vacío.", true);
            return;
        }
        String objName = req.name.trim();
        if (objName.contains(" ")) {
            sendSnapshot(player, false, "El ID interno no puede tener espacios.", true);
            return;
        }
        if (objName.toLowerCase(Locale.ROOT).startsWith("ae-")) {
            sendSnapshot(player, false, "El prefijo 'ae-' está reservado para /stats scoreboard.", true);
            return;
        }
        Scoreboard sb = server.getScoreboard();
        for (ScoreboardObjective existing : sb.getObjectives()) {
            if (existing.getName().equalsIgnoreCase(objName)) {
                sendSnapshot(player, false, "Ya existe una scoreboard con ID: " + objName, true);
                return;
            }
        }
        String criterionRaw = (req.criterion == null || req.criterion.isBlank()) ? "dummy" : req.criterion.trim();
        boolean isCustomTracker = ScoreboardConfigStore.TRACKER_DIG_ALL.equals(criterionRaw)
            || ScoreboardConfigStore.TRACKER_PLACED_BLOCKS.equals(criterionRaw);
        ScoreboardCriterion parsedCriterion = isCustomTracker
            ? ScoreboardCriterion.DUMMY
            : ScoreboardCriterion.getOrCreateStatCriterion(criterionRaw).orElse(null);
        if (parsedCriterion == null) {
            sendSnapshot(player, false, "Criterio inválido: " + criterionRaw, true);
            return;
        }

        AreaRegion area = resolveAreaFromRequest(player, req.areaMode, req.areaDimension, req.areaX1, req.areaZ1, req.areaX2, req.areaZ2, null);
        if (area == null) {
            sendSnapshot(player, false, "Primero escanea un contorno válido para el área Custom.", true);
            return;
        }

        boolean needsTracker = isCustomTracker || (area.isActive() && parsedCriterion instanceof Stat<?>);
        ScoreboardCriterion effectiveCriterion = needsTracker ? ScoreboardCriterion.DUMMY : parsedCriterion;

        String alias = ScoreboardConfigStore.normalizeAlias(req.chatAlias);
        String conflict = ScoreboardConfigStore.findAliasConflict(objName, alias);
        if (conflict != null) {
            sendSnapshot(player, false, "Alias !!" + alias + " ya está usado por: " + conflict, true);
            return;
        }
        String disp = (req.displayName == null || req.displayName.isBlank()) ? objName : req.displayName.trim();
        ScoreboardObjective created = sb.addObjective(
            objName,
            effectiveCriterion,
            MotdMarkup.parse(disp),
            effectiveCriterion.getDefaultRenderType(),
            false,
            null
        );
        applyObjectiveSlot(sb, created, req.slot);

        ObjectiveConfig cfg = new ObjectiveConfig();
        cfg.totalEnabled = req.totalEnabled;
        cfg.totalHumans = req.totalHumans;
        cfg.totalBots = req.totalBots;
        cfg.totalUnknown = req.totalUnknown;
        cfg.chatAlias = alias;
        cfg.displayNameMarkup = disp;
        cfg.tracker = needsTracker ? criterionRaw : "";
        cfg.area = area;
        ScoreboardConfigStore.put(objName, cfg);

        int filled = 0;
        if (req.fillFromStats) {
            if (isCustomTracker) {
                filled = populateTrackerFromStats(server, sb, created, criterionRaw);
            } else if (parsedCriterion instanceof Stat<?> stat) {
                filled = populateObjectiveFromStats(server, sb, created, stat, criterionRaw, false);
            }
        }
        String msg = filled > 0
            ? ("Scoreboard '" + objName + "' creada (" + filled + " entradas rellenadas).")
            : ("Scoreboard '" + objName + "' creada.");
        sendSnapshot(player, false, msg, false);
    }

    private static int populateTrackerFromStats(
        MinecraftServer server,
        Scoreboard sb,
        ScoreboardObjective objective,
        String trackerId
    ) {
        boolean digAll = ScoreboardConfigStore.TRACKER_DIG_ALL.equals(trackerId);
        List<Item> trackedItems = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (digAll) {
                var stack = item.getDefaultStack();
                if (stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS)
                    || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.HOES) || item == Items.SHEARS) {
                    trackedItems.add(item);
                }
            } else if (item instanceof BlockItem) {
                trackedItems.add(item);
            }
        }
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        int count = 0;

        for (Entry entry : PlayerRegistryStore.getEntriesSnapshot().values()) {
            if (entry.name == null || entry.name.isBlank()) {
                continue;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(entry.uuid);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            long total = 0L;
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
            if (online != null) {
                var handler = online.getStatHandler();
                for (Item item : trackedItems) {
                    total += handler.getStat(Stats.USED.getOrCreateStat(item));
                }
            } else {
                total = readOfflineUsedSum(statsDir.resolve(entry.uuid + ".json"), trackedItems);
            }

            if (total > 0) {
                int clamped = (int) Math.min(Integer.MAX_VALUE, total);
                sb.getOrCreateScore(safeScoreHolder(entry.name), objective).setScore(clamped);
                count++;
            }
        }
        return count;
    }

    private static long readOfflineUsedSum(Path statsFile, List<Item> trackedItems) {
        if (!Files.isRegularFile(statsFile)) {
            return 0L;
        }
        try (Reader reader = Files.newBufferedReader(statsFile)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("stats") || !root.get("stats").isJsonObject()) {
                return 0L;
            }
            JsonObject statsObj = root.getAsJsonObject("stats");
            if (!statsObj.has("minecraft:used") || !statsObj.get("minecraft:used").isJsonObject()) {
                return 0L;
            }
            JsonObject usedObj = statsObj.getAsJsonObject("minecraft:used");
            long sum = 0L;
            for (Item item : trackedItems) {
                Identifier id = Registries.ITEM.getId(item);
                if (usedObj.has(id.toString())) {
                    sum += usedObj.get(id.toString()).getAsLong();
                }
            }
            return sum;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static int populateObjectiveFromStats(
        MinecraftServer server,
        Scoreboard sb,
        ScoreboardObjective objective,
        Stat<?> stat,
        String criterionRaw,
        boolean humansOnly
    ) {
        String[] parts = parseStatCriterionKeys(criterionRaw);
        String catKey = parts != null ? parts[0] : null;
        String targetKey = parts != null ? parts[1] : null;
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        int count = 0;

        for (Entry entry : PlayerRegistryStore.getEntriesSnapshot().values()) {
            if (entry.name == null || entry.name.isBlank()) {
                continue;
            }
            if (humansOnly && entry.type != EntryType.HUMAN) {
                continue;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(entry.uuid);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            int val = 0;
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
            if (online != null) {
                val = online.getStatHandler().getStat(stat);
            } else if (catKey != null && targetKey != null) {
                val = readOfflineStatValue(statsDir.resolve(entry.uuid + ".json"), catKey, targetKey);
            }

            if (val > 0) {
                sb.getOrCreateScore(safeScoreHolder(entry.name), objective).setScore(val);
                count++;
            }
        }
        return count;
    }

    /** Returns a ScoreHolder whose getDisplayName() is non-null for mods like chronos-carpet-addons. */
    public static ScoreHolder safeScoreHolder(String name) {
        return new ScoreHolder() {
            @Override
            public String getNameForScoreboard() {
                return name;
            }

            @Override
            public Text getDisplayName() {
                return Text.literal(name);
            }
        };
    }

    public static void syncAeObjective(MinecraftServer server, Scoreboard sb, ScoreboardObjective target, String criterionRaw) {
        if (server == null || sb == null || target == null) {
            return;
        }
        Set<String> humanNames = new java.util.HashSet<>();
        for (Entry entry : PlayerRegistryStore.getEntriesSnapshot().values()) {
            if (entry.type == EntryType.HUMAN && entry.name != null && !entry.name.isBlank()) {
                humanNames.add(entry.name);
            }
        }
        List<String> staleOwners = new ArrayList<>();
        for (ScoreboardEntry e : sb.getScoreboardEntries(target)) {
            if (!humanNames.contains(e.owner())) {
                staleOwners.add(e.owner());
            }
        }
        for (String owner : staleOwners) {
            sb.removeScore(safeScoreHolder(owner), target);
        }
        if (target.getCriterion() instanceof Stat<?> stat) {
            String crit = (criterionRaw != null && !criterionRaw.isBlank())
                ? criterionRaw
                : target.getCriterion().getName();
            populateObjectiveFromStats(server, sb, target, stat, crit, true);
        }
    }

    private static void syncPlayerAcrossAeObjectives(MinecraftServer server, String uuidStr, EntryType nextType) {
        if (server == null || uuidStr == null) {
            return;
        }
        Entry entry = PlayerRegistryStore.getEntriesSnapshot().get(uuidStr);
        if (entry == null || entry.name == null || entry.name.isBlank()) {
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(entry.uuid);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        Scoreboard sb = server.getScoreboard();
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        ScoreHolder holder = safeScoreHolder(entry.name);

        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (!obj.getName().toLowerCase(Locale.ROOT).startsWith("ae-")) {
                continue;
            }
            if (nextType != EntryType.HUMAN) {
                sb.removeScore(holder, obj);
                continue;
            }
            if (obj.getCriterion() instanceof Stat<?> stat) {
                String criterionRaw = obj.getCriterion().getName();
                int val = 0;
                if (online != null) {
                    val = online.getStatHandler().getStat(stat);
                } else if (criterionRaw != null) {
                    String[] parts = parseStatCriterionKeys(criterionRaw);
                    if (parts != null) {
                        val = readOfflineStatValue(statsDir.resolve(entry.uuid + ".json"), parts[0], parts[1]);
                    }
                }
                if (val > 0) {
                    sb.getOrCreateScore(holder, obj).setScore(val);
                }
            }
        }
    }

    private static String[] parseStatCriterionKeys(String criterionRaw) {
        int colon = criterionRaw.indexOf(':');
        if (colon <= 0 || colon >= criterionRaw.length() - 1) {
            return null;
        }
        String cat = criterionRaw.substring(0, colon).replace('.', ':');
        String target = criterionRaw.substring(colon + 1).replace('.', ':');
        return new String[] { cat, target };
    }

    private static int readOfflineStatValue(Path statsFile, String catKey, String targetKey) {
        if (!Files.isRegularFile(statsFile)) {
            return 0;
        }
        try (Reader reader = Files.newBufferedReader(statsFile)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("stats") || !root.get("stats").isJsonObject()) {
                return 0;
            }
            JsonObject statsObj = root.getAsJsonObject("stats");
            if (!statsObj.has(catKey) || !statsObj.get(catKey).isJsonObject()) {
                return 0;
            }
            JsonObject catObj = statsObj.getAsJsonObject(catKey);
            if (!catObj.has(targetKey)) {
                return 0;
            }
            return catObj.get(targetKey).getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void handleEditObjective(MinecraftServer server, ServerPlayerEntity player, String json) {
        EditObjectiveRequest req;
        try {
            req = GSON.fromJson(json, EditObjectiveRequest.class);
        } catch (Exception ex) {
            sendSnapshot(player, false, "Datos inválidos al editar scoreboard.", true);
            return;
        }
        if (req == null) {
            sendSnapshot(player, false, "Scoreboard inválida.", true);
            return;
        }
        String lookupName = (req.originalName != null && !req.originalName.isBlank()) ? req.originalName.trim() : (req.name != null ? req.name.trim() : "");
        if (lookupName.isEmpty()) {
            sendSnapshot(player, false, "Scoreboard inválida.", true);
            return;
        }
        Scoreboard sb = server.getScoreboard();
        ScoreboardObjective target = null;
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (obj.getName().equals(lookupName)) {
                target = obj;
                break;
            }
        }
        if (target == null) {
            sendSnapshot(player, false, "Scoreboard no encontrada: " + lookupName, true);
            return;
        }

        String nextName = (req.name == null || req.name.isBlank()) ? target.getName() : req.name.trim();
        if (nextName.contains(" ")) {
            sendSnapshot(player, false, "El ID interno no puede tener espacios.", true);
            return;
        }
        if (!nextName.equalsIgnoreCase(target.getName()) && nextName.toLowerCase(Locale.ROOT).startsWith("ae-")) {
            sendSnapshot(player, false, "El prefijo 'ae-' está reservado para /stats scoreboard.", true);
            return;
        }
        if (!nextName.equals(target.getName())) {
            for (ScoreboardObjective existing : sb.getObjectives()) {
                if (existing != target && existing.getName().equalsIgnoreCase(nextName)) {
                    sendSnapshot(player, false, "Ya existe una scoreboard con ID: " + nextName, true);
                    return;
                }
            }
        }

        ObjectiveConfig prev = ScoreboardConfigStore.getOrDefault(target.getName());
        String currentEffectiveCriterion = (prev.tracker != null && !prev.tracker.isEmpty())
            ? prev.tracker
            : (target.getCriterion() != null ? target.getCriterion().getName() : "dummy");
        String nextCriterionRaw = (req.criterion == null || req.criterion.isBlank()) ? "dummy" : req.criterion.trim();
        boolean isCustomTracker = ScoreboardConfigStore.TRACKER_DIG_ALL.equals(nextCriterionRaw)
            || ScoreboardConfigStore.TRACKER_PLACED_BLOCKS.equals(nextCriterionRaw);
        ScoreboardCriterion parsedCriterion = isCustomTracker
            ? ScoreboardCriterion.DUMMY
            : ScoreboardCriterion.getOrCreateStatCriterion(nextCriterionRaw).orElse(null);
        if (parsedCriterion == null) {
            sendSnapshot(player, false, "Criterio inválido: " + nextCriterionRaw, true);
            return;
        }

        AreaRegion area = resolveAreaFromRequest(player, req.areaMode, req.areaDimension, req.areaX1, req.areaZ1, req.areaX2, req.areaZ2, prev.area);
        if (area == null) {
            sendSnapshot(player, false, "Primero escanea un contorno válido para el área Custom.", true);
            return;
        }

        boolean needsTracker = isCustomTracker || (area.isActive() && parsedCriterion instanceof Stat<?>);
        ScoreboardCriterion effectiveCriterion = needsTracker ? ScoreboardCriterion.DUMMY : parsedCriterion;

        String alias = ScoreboardConfigStore.normalizeAlias(req.chatAlias);
        String conflict = ScoreboardConfigStore.findAliasConflict(target.getName(), alias);
        if (conflict != null && !conflict.equals(nextName)) {
            sendSnapshot(player, false, "Alias !!" + alias + " ya está usado por: " + conflict, true);
            return;
        }

        String disp = (req.displayName == null || req.displayName.isBlank()) ? nextName : req.displayName.trim();
        String currentVanillaCriterion = target.getCriterion() != null ? target.getCriterion().getName() : "dummy";
        String targetVanillaCriterion = effectiveCriterion.getName();
        boolean needsRecreate = !nextName.equals(target.getName())
            || !nextCriterionRaw.equals(currentEffectiveCriterion)
            || !targetVanillaCriterion.equals(currentVanillaCriterion);

        ScoreboardObjective finalObj = target;
        if (needsRecreate) {
            List<ScoreboardEntry> oldEntries = List.copyOf(sb.getScoreboardEntries(target));
            for (ScoreboardDisplaySlot slot : ScoreboardDisplaySlot.values()) {
                if (sb.getObjectiveForSlot(slot) == target) {
                    sb.setObjectiveSlot(slot, null);
                }
            }
            sb.removeObjective(target);
            ScoreboardConfigStore.remove(target.getName());

            finalObj = sb.addObjective(
                nextName,
                effectiveCriterion,
                MotdMarkup.parse(disp),
                effectiveCriterion.getDefaultRenderType(),
                false,
                null
            );
            for (ScoreboardEntry entry : oldEntries) {
                sb.getOrCreateScore(safeScoreHolder(entry.owner()), finalObj).setScore(entry.value());
            }
        } else {
            finalObj.setDisplayName(MotdMarkup.parse(disp));
        }

        applyObjectiveSlot(sb, finalObj, req.slot);

        ObjectiveConfig cfg = new ObjectiveConfig();
        cfg.totalEnabled = req.totalEnabled;
        cfg.totalHumans = req.totalHumans;
        cfg.totalBots = req.totalBots;
        cfg.totalUnknown = req.totalUnknown;
        cfg.chatAlias = alias;
        cfg.displayNameMarkup = disp;
        cfg.tracker = needsTracker ? nextCriterionRaw : "";
        cfg.area = area;
        ScoreboardConfigStore.put(nextName, cfg);

        sendSnapshot(player, false, "Scoreboard '" + nextName + "' actualizada.", false);
    }

    private static void handleScanAreaContour(ServerPlayerEntity player, String json) {
        ScanContourRequest req;
        try {
            req = GSON.fromJson(json, ScanContourRequest.class);
        } catch (Exception ex) {
            ServerPlayNetworking.send(player, new AreaContourPreviewPayload(false, "Coordenadas inválidas.", "", 0, 0, "", ""));
            return;
        }
        if (req == null) {
            ServerPlayNetworking.send(player, new AreaContourPreviewPayload(false, "Coordenadas inválidas.", "", 0, 0, "", ""));
            return;
        }
        AreaScanner.ScanResult res = AreaScanner.scanCustomContour(player.getWorld(), new BlockPos(req.x, req.y, req.z));
        if (!res.ok()) {
            ServerPlayNetworking.send(player, new AreaContourPreviewPayload(false, res.error(), "", 0, 0, "", ""));
            return;
        }
        AreaRegion region = res.region();
        PENDING_AREA_SCANS.put(player.getUuid(), region);
        ServerPlayNetworking.send(player, new AreaContourPreviewPayload(
            true,
            "Contorno escaneado: " + region.columnCount + " columnas (" + region.blockId + ")",
            region.dimension,
            region.columnCount,
            region.previewSize,
            region.previewGrid,
            region.blockId
        ));
    }

    private static AreaRegion resolveAreaFromRequest(
        ServerPlayerEntity player,
        String areaMode,
        String dimension,
        int x1,
        int z1,
        int x2,
        int z2,
        AreaRegion existingArea
    ) {
        if (areaMode == null || "NONE".equalsIgnoreCase(areaMode)) {
            PENDING_AREA_SCANS.remove(player.getUuid());
            return new AreaRegion();
        }
        if ("RECTANGLE".equalsIgnoreCase(areaMode)) {
            PENDING_AREA_SCANS.remove(player.getUuid());
            AreaRegion r = new AreaRegion();
            r.mode = AreaRegion.Mode.RECTANGLE;
            r.dimension = (dimension != null && !dimension.isBlank()) ? dimension : "minecraft:overworld";
            r.x1 = x1;
            r.z1 = z1;
            r.x2 = x2;
            r.z2 = z2;
            return r;
        }
        if ("CUSTOM".equalsIgnoreCase(areaMode)) {
            AreaRegion scanned = PENDING_AREA_SCANS.remove(player.getUuid());
            if (scanned != null) {
                return scanned;
            }
            if (existingArea != null && existingArea.mode == AreaRegion.Mode.CUSTOM) {
                return existingArea.copy();
            }
            return null;
        }
        return new AreaRegion();
    }

    private static void applyObjectiveSlot(Scoreboard sb, ScoreboardObjective target, String slotName) {
        for (ScoreboardDisplaySlot slot : ScoreboardDisplaySlot.values()) {
            if (sb.getObjectiveForSlot(slot) == target) {
                sb.setObjectiveSlot(slot, null);
            }
        }
        if (slotName != null && !slotName.isEmpty()) {
            ScoreboardDisplaySlot nextSlot = switch (slotName) {
                case "SIDEBAR" -> ScoreboardDisplaySlot.SIDEBAR;
                case "LIST" -> ScoreboardDisplaySlot.LIST;
                case "BELOW_NAME" -> ScoreboardDisplaySlot.BELOW_NAME;
                default -> null;
            };
            if (nextSlot != null) {
                sb.setObjectiveSlot(nextSlot, target);
            }
        }
    }

    public static final class ScanContourRequest {
        public int x;
        public int y;
        public int z;
    }

    public static final class CreateObjectiveRequest {
        public String name = "";
        public String displayName = "";
        public String criterion = "dummy";
        public boolean fillFromStats;
        public String slot = "";
        public String chatAlias = "";
        public boolean totalEnabled;
        public boolean totalHumans = true;
        public boolean totalBots;
        public boolean totalUnknown;
        public String areaMode = "NONE";
        public String areaDimension = "minecraft:overworld";
        public int areaX1;
        public int areaZ1;
        public int areaX2;
        public int areaZ2;
    }

    public static final class EditObjectiveRequest {
        public String originalName = "";
        public String name = "";
        public String criterion = "dummy";
        public String displayName = "";
        public String slot = "";
        public String chatAlias = "";
        public boolean totalEnabled;
        public boolean totalHumans = true;
        public boolean totalBots;
        public boolean totalUnknown;
        public String areaMode = "NONE";
        public String areaDimension = "minecraft:overworld";
        public int areaX1;
        public int areaZ1;
        public int areaX2;
        public int areaZ2;
    }

    public static final class StatsSnapshot {
        public List<RegistryEntrySnapshot> entries = List.of();
        public List<String> unexpectedFiles = List.of();
        public List<UnexpectedFileSnapshot> unexpectedDetails = List.of();
        public List<ScoreboardObjectiveSnapshot> scoreboards = List.of();
        public boolean lookupRunning;
        public int lookupDone;
        public int lookupTotal;
        public int lookupResolved;
    }

    public static final class UnexpectedFileSnapshot {
        public String path = "";
        public long sizeBytes = -1L;
        public long lastModifiedMs;
        public boolean validNbt;
        public String linkedUuid = "";
        public String linkedName = "";
        public String linkedType = "";
        public boolean hasOfficialDat;
        public boolean hasOfficialDatOld;
        public int sameUuidCount;
    }

    public static final class ScoreboardObjectiveSnapshot {
        public String name = "";
        public String displayName = "";
        public String displayNameMarkup = "";
        public String criterion = "dummy";
        public String slot = "";
        public int entryCount;
        public String chatAlias = "";
        public boolean totalEnabled;
        public boolean totalHumans = true;
        public boolean totalBots;
        public boolean totalUnknown;
        public String areaMode = "NONE";
        public String areaDimension = "minecraft:overworld";
        public int areaX1;
        public int areaZ1;
        public int areaX2;
        public int areaZ2;
        public int areaColumnCount;
        public int areaPreviewSize;
        public String areaPreviewGrid = "";
        public String areaBlockId = "";
    }

    private static void handleSetScore(MinecraftServer server, ServerPlayerEntity player, String json) {
        SetScoreRequest req;
        try {
            req = GSON.fromJson(json, SetScoreRequest.class);
        } catch (Exception ex) {
            sendSnapshot(player, false, "Datos inválidos al actualizar score.", true);
            return;
        }
        if (req == null || req.objective == null || req.objective.isBlank() || req.owner == null || req.owner.isBlank()) {
            sendSnapshot(player, false, "Entrada o scoreboard inválida.", true);
            return;
        }
        Scoreboard sb = server.getScoreboard();
        ScoreboardObjective target = null;
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (obj.getName().equals(req.objective.trim())) {
                target = obj;
                break;
            }
        }
        if (target == null) {
            sendSnapshot(player, false, "Scoreboard no encontrada: " + req.objective, true);
            return;
        }
        sb.getOrCreateScore(safeScoreHolder(req.owner), target).setScore(req.score);
        sendObjectiveDetail(player, server, target.getName());
        sendSnapshot(player, false, "Score de " + req.owner + " actualizado a " + String.format(Locale.US, "%,d", req.score) + ".", false);
    }

    private static void handleDeleteScoreEntry(MinecraftServer server, ServerPlayerEntity player, String json) {
        DeleteScoreEntryRequest req;
        try {
            req = GSON.fromJson(json, DeleteScoreEntryRequest.class);
        } catch (Exception ex) {
            sendSnapshot(player, false, "Datos inválidos al borrar registro.", true);
            return;
        }
        if (req == null || req.objective == null || req.objective.isBlank() || req.owner == null || req.owner.isBlank()) {
            sendSnapshot(player, false, "Entrada o scoreboard inválida.", true);
            return;
        }
        Scoreboard sb = server.getScoreboard();
        ScoreboardObjective target = null;
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (obj.getName().equals(req.objective.trim())) {
                target = obj;
                break;
            }
        }
        if (target == null) {
            sendSnapshot(player, false, "Scoreboard no encontrada: " + req.objective, true);
            return;
        }
        sb.removeScore(safeScoreHolder(req.owner), target);
        sendObjectiveDetail(player, server, target.getName());
        sendSnapshot(player, false, "Registro eliminado de " + target.getName() + ": " + req.owner, false);
    }

    public static final class ScoreboardEntrySnapshot {
        public String owner = "";
        public int score;
        public String badge = "UNKNOWN";
    }

    private static void populateEntrySummary(MinecraftServer server, Entry entry, RegistryEntrySnapshot item) {
        ServerPlayerEntity online = null;
        if (server != null && entry.uuid != null) {
            try {
                online = server.getPlayerManager().getPlayer(UUID.fromString(entry.uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (online != null) {
            item.invSlots = countNonEmpty(online.getInventory());
            item.enderSlots = countNonEmpty(online.getEnderChestInventory());
            item.playTimeTicks = Math.max(0L, online.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME)));
            item.mined = sumPlayerStat(online, Stats.MINED, Registries.BLOCK);
            item.crafted = sumPlayerStat(online, Stats.CRAFTED, Registries.ITEM);
            return;
        }
        if (entry.summary != null) {
            item.enderSlots = entry.summary.enderSlots;
            item.invSlots = entry.summary.invSlots;
            item.playTimeTicks = entry.summary.playTimeTicks;
            item.mined = entry.summary.mined;
            item.crafted = entry.summary.crafted;
        }
    }

    private static int countNonEmpty(Inventory inv) {
        if (inv == null) return -1;
        int count = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (!inv.getStack(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static <T> long sumPlayerStat(ServerPlayerEntity player, StatType<T> statType, Iterable<T> registry) {
        long total = 0L;
        for (T key : registry) {
            int val = player.getStatHandler().getStat(statType.getOrCreateStat(key));
            if (val > 0) {
                total += val;
            }
        }
        return total;
    }

    public static final class RegistryEntrySnapshot {
        public String uuid = "";
        public String name = "";
        public String type = "UNKNOWN";
        public int uuidVersion;
        public boolean playerdata;
        public boolean stats;
        public boolean advancements;
        public boolean anomaly;
        public int enderSlots = -1;
        public int invSlots = -1;
        public long playTimeTicks = -1L;
        public long mined = -1L;
        public long crafted = -1L;
    }

    public static final class SetScoreRequest {
        public String objective = "";
        public String owner = "";
        public int score;
    }

    public static final class DeleteScoreEntryRequest {
        public String objective = "";
        public String owner = "";
    }

    public static final class PlayerStatSnapshot {
        public String category = "";
        public String key = "";
        public int value;
    }

    public static final class ItemSlotSnapshot {
        public int slot;
        public String itemId = "minecraft:air";
        public int count;
        public String customName = "";
        public int damage;
        public int maxDamage;
        public List<String> tooltip = List.of();
    }

    public static final class PlayerDetailSnapshot {
        public String uuid = "";
        public String name = "";
        public String type = "UNKNOWN";
        public boolean online;
        public boolean hasPlayerdata;
        public boolean hasStats;
        public boolean hasLastPos;
        public String lastDimension = "";
        public double lastX;
        public double lastY;
        public double lastZ;
        public List<PlayerStatSnapshot> stats = new ArrayList<>();
        public List<ItemSlotSnapshot> inventory = new ArrayList<>();
        public List<ItemSlotSnapshot> enderChest = new ArrayList<>();
    }

    private record LastPositionData(String dimension, double x, double y, double z, float yaw, float pitch) {}

    private static LastPositionData readOfflineLastPosition(MinecraftServer server, String uuidStr) {
        Path datPath = server.getSavePath(WorldSavePath.PLAYERDATA).resolve(uuidStr + ".dat");
        if (!Files.isRegularFile(datPath)) return null;
        try {
            NbtCompound root = NbtIo.readCompressed(datPath, NbtSizeTracker.ofUnlimitedBytes());
            NbtList posList = root.getList("Pos").orElse(null);
            if (posList == null || posList.size() < 3) return null;
            double x = posList.getDouble(0).orElse(0.0);
            double y = posList.getDouble(1).orElse(0.0);
            double z = posList.getDouble(2).orElse(0.0);
            String dim = root.getString("Dimension").orElse("minecraft:overworld");
            float yaw = 0f;
            float pitch = 0f;
            NbtList rotList = root.getList("Rotation").orElse(null);
            if (rotList != null && rotList.size() >= 2) {
                yaw = rotList.getFloat(0).orElse(0f);
                pitch = rotList.getFloat(1).orElse(0f);
            }
            return new LastPositionData(dim, x, y, z, yaw, pitch);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void teleportToLastPosition(MinecraftServer server, ServerPlayerEntity viewer, String uuidStr) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ignored) {
            sendSnapshot(viewer, false, "UUID inválida.", true);
            return;
        }
        Entry entry = PlayerRegistryStore.getEntriesSnapshot().get(uuidStr);
        String label = (entry != null && entry.name != null && !entry.name.isBlank()) ? entry.name : uuidStr;

        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        ServerWorld targetWorld;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        String dimLabel;

        if (online != null) {
            targetWorld = online.getWorld();
            x = online.getX();
            y = online.getY();
            z = online.getZ();
            yaw = online.getYaw();
            pitch = online.getPitch();
            dimLabel = targetWorld.getRegistryKey().getValue().toString();
            label = online.getName().getString();
        } else {
            LastPositionData pos = readOfflineLastPosition(server, uuidStr);
            if (pos == null) {
                sendSnapshot(viewer, false, "No existe última posición en playerdata para " + label + ".", true);
                return;
            }
            Identifier dimId = Identifier.tryParse(pos.dimension());
            targetWorld = dimId != null ? server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId)) : null;
            if (targetWorld == null) {
                sendSnapshot(viewer, false, "Dimensión no disponible: " + pos.dimension(), true);
                return;
            }
            x = pos.x();
            y = pos.y();
            z = pos.z();
            yaw = pos.yaw();
            pitch = pos.pitch();
            dimLabel = pos.dimension();
        }

        viewer.teleport(targetWorld, x, y, z, Set.<PositionFlag>of(), yaw, pitch, true);
        viewer.sendMessage(
            Text.literal(String.format(Locale.ROOT, "[Stats] TP a última posición de %s (%s: %.1f, %.1f, %.1f).", label, dimLabel, x, y, z))
                .formatted(Formatting.GREEN),
            false
        );
    }

    public static final class AccountSideSnapshot {
        public String uuid = "";
        public String name = "";
        public int uuidVersion;
        public String type = "UNKNOWN";
        public boolean online;
        public boolean hasData;
        public boolean hasStats;
        public boolean hasAdv;
        public int invSlots;
        public int enderSlots;
        public long mined;
        public long playTicks;
        public int advDoneCount;
    }

    public static final class MigrationCheckResult {
        public boolean ok;
        public String error = "";
        public String mode = "PREMIUM";
        public String targetName = "";
        public boolean hasCollision;
        public AccountSideSnapshot source;
        public AccountSideSnapshot target;
    }

    private static final class MigrationCheckRequest {
        String mode = "PREMIUM";
        String targetName = "";
    }

    private static final class MigrationExecuteRequest {
        String mode = "PREMIUM";
        String targetName = "";
        String targetUuid = "";
        String dataChoice = "KEEP_A";
        String statsChoice = "KEEP_A";
        String advChoice = "KEEP_A";
    }

    private static void handleCheckMigration(MinecraftServer server, ServerPlayerEntity requester, String srcUuidStr, String json) {
        MigrationCheckRequest req;
        try {
            req = GSON.fromJson(json, MigrationCheckRequest.class);
        } catch (Exception e) {
            sendMigrationCheckError(requester, srcUuidStr, "Solicitud inválida.");
            return;
        }
        String mode = req != null && "NON_PREMIUM".equalsIgnoreCase(req.mode) ? "NON_PREMIUM" : "PREMIUM";
        String targetName = req != null && req.targetName != null ? req.targetName.trim() : "";
        if (targetName.isEmpty() || targetName.length() > 16) {
            sendMigrationCheckError(requester, srcUuidStr, "Ingresa un nombre válido (1-16 caracteres).");
            return;
        }
        if ("NON_PREMIUM".equals(mode)) {
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes(StandardCharsets.UTF_8));
            buildAndSendMigrationCheck(server, requester, srcUuidStr, mode, targetName, offlineUuid.toString());
            return;
        }

        String encoded = URLEncoder.encode(targetName, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encoded))
            .timeout(Duration.ofSeconds(6))
            .GET()
            .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, throwable) -> server.execute(() -> {
                if (requester.isDisconnected()) return;
                if (throwable != null) {
                    sendMigrationCheckError(requester, srcUuidStr, "Error consultando Mojang: " + throwable.getMessage());
                    return;
                }
                int code = response.statusCode();
                if (code == 204 || code == 404) {
                    sendMigrationCheckError(requester, srcUuidStr, "No existe ninguna cuenta Mojang (Premium) con el nombre '" + targetName + "'.");
                    return;
                }
                if (code != 200) {
                    sendMigrationCheckError(requester, srcUuidStr, "Mojang respondió HTTP " + code + ".");
                    return;
                }
                try {
                    JsonObject obj = GSON.fromJson(response.body(), JsonObject.class);
                    String rawId = obj != null && obj.has("id") ? obj.get("id").getAsString() : "";
                    String canonicalName = obj != null && obj.has("name") ? obj.get("name").getAsString() : targetName;
                    String formattedUuid = formatCompactUuid(rawId);
                    if (formattedUuid == null) {
                        sendMigrationCheckError(requester, srcUuidStr, "Mojang devolvió una UUID inválida.");
                        return;
                    }
                    buildAndSendMigrationCheck(server, requester, srcUuidStr, mode, canonicalName, formattedUuid);
                } catch (Exception ex) {
                    sendMigrationCheckError(requester, srcUuidStr, "Error leyendo respuesta de Mojang.");
                }
            }));
    }

    private static String formatCompactUuid(String raw) {
        if (raw == null) return null;
        String clean = raw.replace("-", "").trim();
        if (clean.length() != 32) return null;
        return clean.substring(0, 8) + "-" + clean.substring(8, 12) + "-" + clean.substring(12, 16) + "-"
            + clean.substring(16, 20) + "-" + clean.substring(20, 32);
    }

    private static void buildAndSendMigrationCheck(
        MinecraftServer server,
        ServerPlayerEntity requester,
        String srcUuidStr,
        String mode,
        String targetName,
        String dstUuidStr
    ) {
        MigrationCheckResult result = new MigrationCheckResult();
        result.mode = mode;
        result.targetName = targetName;
        result.source = inspectAccountSide(server, srcUuidStr, "");
        result.target = inspectAccountSide(server, dstUuidStr, targetName);

        if (srcUuidStr.equalsIgnoreCase(dstUuidStr)) {
            result.ok = false;
            result.error = "El origen y el destino tienen exactamente la misma UUID (" + dstUuidStr + ").";
        } else if (result.source.online || result.target.online) {
            result.ok = false;
            result.error = "Tanto el origen como el destino deben estar desconectados antes de migrar.";
        } else if (!result.source.hasData && !result.source.hasStats && !result.source.hasAdv) {
            result.ok = false;
            result.error = "La cuenta origen no tiene archivos (data, stats ni adv) para migrar.";
        } else {
            result.ok = true;
            result.hasCollision = result.target.hasData || result.target.hasStats || result.target.hasAdv;
        }
        ServerPlayNetworking.send(requester, new PlayerDetailPayload("__migrate_check__:" + srcUuidStr, GSON.toJson(result)));
    }

    private static void sendMigrationCheckError(ServerPlayerEntity requester, String srcUuidStr, String error) {
        MigrationCheckResult result = new MigrationCheckResult();
        result.ok = false;
        result.error = error;
        ServerPlayNetworking.send(requester, new PlayerDetailPayload("__migrate_check__:" + srcUuidStr, GSON.toJson(result)));
    }

    private static AccountSideSnapshot inspectAccountSide(MinecraftServer server, String uuidStr, String fallbackName) {
        AccountSideSnapshot side = new AccountSideSnapshot();
        side.uuid = uuidStr;
        side.uuidVersion = parseUuidVersion(uuidStr);

        Entry entry = PlayerRegistryStore.getEntriesSnapshot().get(uuidStr);
        if (entry != null) {
            side.name = entry.name != null && !entry.name.isBlank() ? entry.name : fallbackName;
            side.type = entry.type != null ? entry.type.name() : EntryType.UNKNOWN.name();
        } else {
            side.name = fallbackName;
        }

        try {
            UUID uuid = UUID.fromString(uuidStr);
            ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(uuid);
            side.online = onlinePlayer != null;
            if (onlinePlayer != null && side.name.isBlank()) {
                side.name = onlinePlayer.getName().getString();
            }
        } catch (IllegalArgumentException ignored) {
        }

        Path datPath = server.getSavePath(WorldSavePath.PLAYERDATA).resolve(uuidStr + ".dat");
        Path statsPath = server.getSavePath(WorldSavePath.STATS).resolve(uuidStr + ".json");
        Path advPath = server.getSavePath(WorldSavePath.ADVANCEMENTS).resolve(uuidStr + ".json");
        side.hasData = Files.isRegularFile(datPath);
        side.hasStats = Files.isRegularFile(statsPath);
        side.hasAdv = Files.isRegularFile(advPath);

        if (entry != null && entry.summary != null) {
            side.invSlots = entry.summary.invSlots;
            side.enderSlots = entry.summary.enderSlots;
            side.mined = entry.summary.mined;
            side.playTicks = entry.summary.playTimeTicks;
        }
        if (side.hasStats && side.mined == 0 && side.playTicks == 0) {
            try (Reader reader = Files.newBufferedReader(statsPath)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root != null && root.has("stats") && root.get("stats").isJsonObject()) {
                    JsonObject statsObj = root.getAsJsonObject("stats");
                    if (statsObj.has("minecraft:mined") && statsObj.get("minecraft:mined").isJsonObject()) {
                        long sum = 0;
                        for (var e : statsObj.getAsJsonObject("minecraft:mined").entrySet()) {
                            if (e.getValue().isJsonPrimitive()) sum += e.getValue().getAsLong();
                        }
                        side.mined = sum;
                    }
                    if (statsObj.has("minecraft:custom") && statsObj.get("minecraft:custom").isJsonObject()) {
                        JsonObject custom = statsObj.getAsJsonObject("minecraft:custom");
                        if (custom.has("minecraft:play_time")) {
                            side.playTicks = custom.get("minecraft:play_time").getAsLong();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (side.hasData && side.invSlots == 0 && side.enderSlots == 0) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                OfflinePlayerSession s = OfflinePlayerSession.load(server, new GameProfile(uuid, side.name.isBlank() ? uuidStr : side.name));
                if (s != null) {
                    int inv = 0;
                    for (int i = 0; i < s.inventory.size(); i++) if (!s.inventory.getStack(i).isEmpty()) inv++;
                    int end = 0;
                    for (int i = 0; i < s.enderChest.size(); i++) if (!s.enderChest.getStack(i).isEmpty()) end++;
                    side.invSlots = inv;
                    side.enderSlots = end;
                }
            } catch (Exception ignored) {
            }
        }
        if (side.hasAdv) {
            side.advDoneCount = countDoneAdvancements(advPath);
        }
        return side;
    }

    private static int countDoneAdvancements(Path advPath) {
        try (Reader reader = Files.newBufferedReader(advPath)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (!el.isJsonObject()) return 0;
            int count = 0;
            for (var entry : el.getAsJsonObject().entrySet()) {
                if ("DataVersion".equals(entry.getKey()) || !entry.getValue().isJsonObject()) continue;
                JsonObject obj = entry.getValue().getAsJsonObject();
                if (obj.has("done") && obj.get("done").getAsBoolean()) {
                    count++;
                }
            }
            return count;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void handleExecuteMigration(MinecraftServer server, ServerPlayerEntity requester, String srcUuidStr, String json) {
        MigrationExecuteRequest req;
        try {
            req = GSON.fromJson(json, MigrationExecuteRequest.class);
        } catch (Exception e) {
            sendSnapshot(requester, false, "Solicitud de migración inválida.", true);
            return;
        }
        if (req == null || req.targetUuid == null || req.targetUuid.isBlank()) {
            sendSnapshot(requester, false, "Falta la UUID de destino.", true);
            return;
        }
        String dstUuidStr = req.targetUuid.trim();
        if (srcUuidStr.equalsIgnoreCase(dstUuidStr)) {
            sendSnapshot(requester, false, "Origen y destino tienen la misma UUID.", true);
            return;
        }

        UUID srcUuid;
        UUID dstUuid;
        try {
            srcUuid = UUID.fromString(srcUuidStr);
            dstUuid = UUID.fromString(dstUuidStr);
        } catch (IllegalArgumentException e) {
            sendSnapshot(requester, false, "UUID inválida.", true);
            return;
        }
        if (server.getPlayerManager().getPlayer(srcUuid) != null || server.getPlayerManager().getPlayer(dstUuid) != null) {
            sendSnapshot(requester, false, "Desconecta tanto al origen como al destino antes de migrar.", true);
            return;
        }

        Entry srcEntry = PlayerRegistryStore.getEntriesSnapshot().get(srcUuidStr);
        EntryType srcType = (srcEntry != null && srcEntry.type != null) ? srcEntry.type : EntryType.UNKNOWN;

        Path playerdataDir = server.getSavePath(WorldSavePath.PLAYERDATA);
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        Path advDir = server.getSavePath(WorldSavePath.ADVANCEMENTS);

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backupRoot = server.getSavePath(WorldSavePath.ROOT).resolve("migrationbackup").resolve(stamp);

        try {
            backupIfExists(playerdataDir.resolve(srcUuidStr + ".dat"), backupRoot.resolve("playerdata").resolve(srcUuidStr + ".dat"));
            backupIfExists(playerdataDir.resolve(srcUuidStr + ".dat_old"), backupRoot.resolve("playerdata").resolve(srcUuidStr + ".dat_old"));
            backupIfExists(playerdataDir.resolve(dstUuidStr + ".dat"), backupRoot.resolve("playerdata").resolve(dstUuidStr + ".dat"));
            backupIfExists(playerdataDir.resolve(dstUuidStr + ".dat_old"), backupRoot.resolve("playerdata").resolve(dstUuidStr + ".dat_old"));

            backupIfExists(statsDir.resolve(srcUuidStr + ".json"), backupRoot.resolve("stats").resolve(srcUuidStr + ".json"));
            backupIfExists(statsDir.resolve(dstUuidStr + ".json"), backupRoot.resolve("stats").resolve(dstUuidStr + ".json"));

            backupIfExists(advDir.resolve(srcUuidStr + ".json"), backupRoot.resolve("advancements").resolve(srcUuidStr + ".json"));
            backupIfExists(advDir.resolve(dstUuidStr + ".json"), backupRoot.resolve("advancements").resolve(dstUuidStr + ".json"));

            // 1. Playerdata
            Path srcDat = playerdataDir.resolve(srcUuidStr + ".dat");
            Path dstDat = playerdataDir.resolve(dstUuidStr + ".dat");
            if ("KEEP_A".equalsIgnoreCase(req.dataChoice) && Files.isRegularFile(srcDat)) {
                Files.createDirectories(playerdataDir);
                Files.copy(srcDat, dstDat, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.deleteIfExists(srcDat);
            Files.deleteIfExists(playerdataDir.resolve(srcUuidStr + ".dat_old"));

            // 2. Stats
            Path srcStats = statsDir.resolve(srcUuidStr + ".json");
            Path dstStats = statsDir.resolve(dstUuidStr + ".json");
            if ("MERGE".equalsIgnoreCase(req.statsChoice)) {
                mergeStatsFiles(srcStats, dstStats);
            } else if ("KEEP_A".equalsIgnoreCase(req.statsChoice) && Files.isRegularFile(srcStats)) {
                Files.createDirectories(statsDir);
                Files.copy(srcStats, dstStats, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.deleteIfExists(srcStats);

            // 3. Advancements
            Path srcAdv = advDir.resolve(srcUuidStr + ".json");
            Path dstAdv = advDir.resolve(dstUuidStr + ".json");
            if ("MERGE".equalsIgnoreCase(req.advChoice)) {
                mergeAdvancementFiles(srcAdv, dstAdv);
            } else if ("KEEP_A".equalsIgnoreCase(req.advChoice) && Files.isRegularFile(srcAdv)) {
                Files.createDirectories(advDir);
                Files.copy(srcAdv, dstAdv, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.deleteIfExists(srcAdv);

            PlayerRegistryStore.deleteEntryAndFiles(server, srcUuidStr);
            PlayerRegistryStore.scanFull(server);
            if (req.targetName != null && !req.targetName.isBlank()) {
                PlayerRegistryStore.setName(dstUuidStr, req.targetName.trim());
            }
            if (srcType != EntryType.UNKNOWN) {
                Entry dstEntry = PlayerRegistryStore.getEntriesSnapshot().get(dstUuidStr);
                if (dstEntry == null || dstEntry.type == null || dstEntry.type == EntryType.UNKNOWN) {
                    PlayerRegistryStore.setType(dstUuidStr, srcType);
                }
            }
            Entry finalDst = PlayerRegistryStore.getEntriesSnapshot().get(dstUuidStr);
            if (finalDst != null && finalDst.type != null) {
                syncPlayerAcrossAeObjectives(server, dstUuidStr, finalDst.type);
            }
            sendSnapshot(requester, false, "Migración completada hacia " + req.targetName + " (respaldo en migrationbackup/" + stamp + ").", false);
            PlayerRegistryStore.refreshSummariesAsync(server, () -> server.execute(() -> {
                if (!requester.isDisconnected()) {
                    sendSnapshot(requester, false, "", false);
                }
            }));
        } catch (Exception e) {
            AeternumCarpet.LOGGER.error("Migration failed from {} to {}", srcUuidStr, dstUuidStr, e);
            sendSnapshot(requester, false, "Error durante la migración: " + e.getMessage(), true);
        }
    }

    private static void backupIfExists(Path src, Path dst) throws java.io.IOException {
        if (!Files.isRegularFile(src)) return;
        Files.createDirectories(dst.getParent());
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void mergeStatsFiles(Path srcPath, Path dstPath) throws java.io.IOException {
        boolean hasSrc = Files.isRegularFile(srcPath);
        boolean hasDst = Files.isRegularFile(dstPath);
        if (!hasSrc && !hasDst) return;
        if (hasSrc && !hasDst) {
            Files.createDirectories(dstPath.getParent());
            Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (!hasSrc) return;

        JsonObject srcRoot = readJsonObject(srcPath);
        JsonObject dstRoot = readJsonObject(dstPath);
        int dataVer = Math.max(
            srcRoot.has("DataVersion") ? srcRoot.get("DataVersion").getAsInt() : 0,
            dstRoot.has("DataVersion") ? dstRoot.get("DataVersion").getAsInt() : 0
        );
        JsonObject mergedStats = new JsonObject();
        JsonObject srcStats = srcRoot.has("stats") && srcRoot.get("stats").isJsonObject() ? srcRoot.getAsJsonObject("stats") : new JsonObject();
        JsonObject dstStats = dstRoot.has("stats") && dstRoot.get("stats").isJsonObject() ? dstRoot.getAsJsonObject("stats") : new JsonObject();

        for (var cat : dstStats.entrySet()) {
            if (cat.getValue().isJsonObject()) {
                mergedStats.add(cat.getKey(), cat.getValue().deepCopy());
            }
        }
        for (var cat : srcStats.entrySet()) {
            if (!cat.getValue().isJsonObject()) continue;
            JsonObject targetCat = mergedStats.has(cat.getKey()) && mergedStats.get(cat.getKey()).isJsonObject()
                ? mergedStats.getAsJsonObject(cat.getKey())
                : new JsonObject();
            for (var stat : cat.getValue().getAsJsonObject().entrySet()) {
                if (!stat.getValue().isJsonPrimitive()) continue;
                long addVal = stat.getValue().getAsLong();
                long curVal = targetCat.has(stat.getKey()) && targetCat.get(stat.getKey()).isJsonPrimitive()
                    ? targetCat.get(stat.getKey()).getAsLong()
                    : 0L;
                targetCat.addProperty(stat.getKey(), curVal + addVal);
            }
            mergedStats.add(cat.getKey(), targetCat);
        }

        JsonObject out = new JsonObject();
        out.add("stats", mergedStats);
        if (dataVer > 0) out.addProperty("DataVersion", dataVer);
        Files.createDirectories(dstPath.getParent());
        Files.writeString(dstPath, GSON.toJson(out), StandardCharsets.UTF_8);
    }

    private static void mergeAdvancementFiles(Path srcPath, Path dstPath) throws java.io.IOException {
        boolean hasSrc = Files.isRegularFile(srcPath);
        boolean hasDst = Files.isRegularFile(dstPath);
        if (!hasSrc && !hasDst) return;
        if (hasSrc && !hasDst) {
            Files.createDirectories(dstPath.getParent());
            Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (!hasSrc) return;

        JsonObject srcRoot = readJsonObject(srcPath);
        JsonObject dstRoot = readJsonObject(dstPath);
        JsonObject out = dstRoot.deepCopy();

        int dataVer = Math.max(
            srcRoot.has("DataVersion") ? srcRoot.get("DataVersion").getAsInt() : 0,
            dstRoot.has("DataVersion") ? dstRoot.get("DataVersion").getAsInt() : 0
        );

        for (var entry : srcRoot.entrySet()) {
            String key = entry.getKey();
            if ("DataVersion".equals(key) || !entry.getValue().isJsonObject()) continue;
            JsonObject srcAdv = entry.getValue().getAsJsonObject();
            if (!out.has(key) || !out.get(key).isJsonObject()) {
                out.add(key, srcAdv.deepCopy());
                continue;
            }
            JsonObject dstAdv = out.getAsJsonObject(key);
            boolean srcDone = srcAdv.has("done") && srcAdv.get("done").getAsBoolean();
            boolean dstDone = dstAdv.has("done") && dstAdv.get("done").getAsBoolean();
            if (srcDone && !dstDone) {
                out.add(key, srcAdv.deepCopy());
            } else {
                JsonObject dstCrit = dstAdv.has("criteria") && dstAdv.get("criteria").isJsonObject()
                    ? dstAdv.getAsJsonObject("criteria")
                    : new JsonObject();
                JsonObject srcCrit = srcAdv.has("criteria") && srcAdv.get("criteria").isJsonObject()
                    ? srcAdv.getAsJsonObject("criteria")
                    : new JsonObject();
                for (var c : srcCrit.entrySet()) {
                    if (!dstCrit.has(c.getKey())) {
                        dstCrit.add(c.getKey(), c.getValue().deepCopy());
                    }
                }
                dstAdv.add("criteria", dstCrit);
            }
        }
        if (dataVer > 0) out.addProperty("DataVersion", dataVer);
        Files.createDirectories(dstPath.getParent());
        Files.writeString(dstPath, GSON.toJson(out), StandardCharsets.UTF_8);
    }

    private static JsonObject readJsonObject(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static List<UnexpectedFileSnapshot> buildUnexpectedDetails(
        MinecraftServer server,
        List<String> unexpectedFiles,
        Map<String, Entry> rawEntries
    ) {
        if (server == null || unexpectedFiles == null || unexpectedFiles.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> uuidCounts = new HashMap<>();
        for (String path : unexpectedFiles) {
            String u = PlayerRegistryStore.extractUuidPrefix(path);
            if (!u.isEmpty()) {
                uuidCounts.merge(u, 1, Integer::sum);
            }
        }
        Path playerdataDir = server.getSavePath(WorldSavePath.PLAYERDATA).toAbsolutePath().normalize();
        List<UnexpectedFileSnapshot> out = new ArrayList<>(unexpectedFiles.size());
        for (String path : unexpectedFiles) {
            UnexpectedFileSnapshot snap = new UnexpectedFileSnapshot();
            snap.path = path;
            snap.linkedUuid = PlayerRegistryStore.extractUuidPrefix(path);
            if (!snap.linkedUuid.isEmpty()) {
                snap.sameUuidCount = uuidCounts.getOrDefault(snap.linkedUuid, 1);
                Entry entry = rawEntries.get(snap.linkedUuid);
                if (entry != null) {
                    snap.linkedName = entry.name != null ? entry.name : "";
                    snap.linkedType = entry.type != null ? entry.type.name() : EntryType.UNKNOWN.name();
                }
                snap.hasOfficialDat = Files.isRegularFile(playerdataDir.resolve(snap.linkedUuid + ".dat"));
                snap.hasOfficialDatOld = Files.isRegularFile(playerdataDir.resolve(snap.linkedUuid + ".dat_old"));
            }
            Path target = PlayerRegistryStore.resolveUnexpectedTarget(server, path);
            if (target != null && Files.exists(target)) {
                try {
                    snap.lastModifiedMs = Files.getLastModifiedTime(target).toMillis();
                    if (Files.isRegularFile(target)) {
                        snap.sizeBytes = Files.size(target);
                        snap.validNbt = path.startsWith("playerdata/") && path.endsWith(".dat")
                            && PlayerRegistryStore.isValidPlayerdataNbt(target);
                    }
                } catch (IOException ignored) {
                }
            }
            out.add(snap);
        }
        return out;
    }

    private static void openUnexpectedInventory(MinecraftServer server, ServerPlayerEntity viewer, String relativePath) {
        if (!PlayerRegistryStore.getUnexpectedFilesSnapshot().contains(relativePath)
            || !relativePath.startsWith("playerdata/")
            || !relativePath.endsWith(".dat")) {
            sendSnapshot(viewer, false, "Archivo no válido para previsualizar inventario.", true);
            return;
        }
        Path target = PlayerRegistryStore.resolveUnexpectedTarget(server, relativePath);
        String uuidStr = PlayerRegistryStore.extractUuidPrefix(relativePath);
        UUID uuid;
        try {
            uuid = !uuidStr.isEmpty() ? UUID.fromString(uuidStr) : UUID.nameUUIDFromBytes(relativePath.getBytes());
        } catch (IllegalArgumentException ignored) {
            uuid = UUID.nameUUIDFromBytes(relativePath.getBytes());
        }
        Entry entry = !uuidStr.isEmpty() ? PlayerRegistryStore.getEntriesSnapshot().get(uuidStr) : null;
        String baseName = (entry != null && entry.name != null && !entry.name.isBlank())
            ? entry.name
            : (!uuidStr.isEmpty() ? uuidStr.substring(0, 8) : "temp");
        String fileName = target != null ? target.getFileName().toString() : relativePath;
        String label = baseName + " [" + (fileName.length() > 18 ? fileName.substring(fileName.length() - 18) : fileName) + "]";
        OfflinePlayerSession session = OfflinePlayerSession.loadFromPath(server, uuid, target, label);
        if (session == null) {
            sendSnapshot(viewer, false, "El archivo está vacío o corrupto (no se pudo leer NBT).", true);
            return;
        }
        BotInventoryOpener.openOffline(viewer, label, session);
    }

    private static void handlePromoteUnexpected(MinecraftServer server, ServerPlayerEntity viewer, String relativePath) {
        switch (PlayerRegistryStore.promoteUnexpectedToOfficial(server, relativePath)) {
            case PROMOTED -> sendSnapshot(viewer, false, "Convertido en .dat oficial: " + relativePath, false);
            case OFFICIAL_EXISTS -> sendSnapshot(viewer, false, "Ya existe un <uuid>.dat oficial para esa UUID.", true);
            case ONLINE -> sendSnapshot(viewer, false, "El jugador está conectado; desconéctalo antes de restaurar su .dat.", true);
            case CORRUPT_OR_EMPTY -> sendSnapshot(viewer, false, "Archivo vacío o NBT corrupto; no se puede convertir en oficial.", true);
            case INVALID -> sendSnapshot(viewer, false, "Archivo inválido o fuera de playerdata/.", true);
            case IO_ERROR -> sendSnapshot(viewer, false, "Error de disco al mover el archivo.", true);
        }
    }

    private static void openPlayerInventory(MinecraftServer server, ServerPlayerEntity viewer, String uuidStr) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ignored) {
            sendSnapshot(viewer, false, "UUID inválida.", true);
            return;
        }
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        if (online != null) {
            BotInventoryOpener.openOnline(viewer, online);
            return;
        }
        Entry entry = PlayerRegistryStore.getEntriesSnapshot().get(uuidStr);
        String label = (entry != null && entry.name != null && !entry.name.isBlank()) ? entry.name : uuidStr;
        OfflinePlayerSession session = OfflinePlayerSession.load(server, new GameProfile(uuid, label));
        if (session == null) {
            sendSnapshot(viewer, false, "No existe playerdata (.dat) para " + label + ".", true);
            return;
        }
        BotInventoryOpener.openOffline(viewer, label, session);
    }

    private static void sendPlayerDetail(MinecraftServer server, ServerPlayerEntity viewer, String uuidStr) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        Entry entry = PlayerRegistryStore.getEntriesSnapshot().get(uuidStr);
        PlayerDetailSnapshot detail = new PlayerDetailSnapshot();
        detail.uuid = uuidStr;
        detail.name = (entry != null && entry.name != null) ? entry.name : "";
        detail.type = (entry != null && entry.type != null) ? entry.type.name() : EntryType.UNKNOWN.name();

        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        detail.online = online != null;
        if (online != null) {
            if (detail.name.isEmpty()) detail.name = online.getName().getString();
            detail.hasPlayerdata = true;
            detail.hasStats = true;
            detail.hasLastPos = true;
            detail.lastDimension = online.getWorld().getRegistryKey().getValue().toString();
            detail.lastX = online.getX();
            detail.lastY = online.getY();
            detail.lastZ = online.getZ();
            collectOnlineStats(online, detail.stats);
            collectInventorySlots(server, viewer, online.getInventory(), 41, detail.inventory);
            collectInventorySlots(server, viewer, online.getEnderChestInventory(), 27, detail.enderChest);
        } else {
            detail.hasPlayerdata = entry != null && entry.files != null && entry.files.playerdata;
            detail.hasStats = entry != null && entry.files != null && entry.files.stats;
            if (detail.hasStats) {
                collectOfflineStats(server.getSavePath(WorldSavePath.STATS).resolve(uuidStr + ".json"), detail.stats);
            }
            if (detail.hasPlayerdata) {
                String label = !detail.name.isEmpty() ? detail.name : uuidStr;
                OfflinePlayerSession session = OfflinePlayerSession.load(server, new GameProfile(uuid, label));
                if (session != null) {
                    collectInventorySlots(server, viewer, session.inventory, 41, detail.inventory);
                    collectInventorySlots(server, viewer, session.enderChest, 27, detail.enderChest);
                }
                LastPositionData lastPos = readOfflineLastPosition(server, uuidStr);
                if (lastPos != null) {
                    detail.hasLastPos = true;
                    detail.lastDimension = lastPos.dimension();
                    detail.lastX = lastPos.x();
                    detail.lastY = lastPos.y();
                    detail.lastZ = lastPos.z();
                }
            }
        }
        detail.stats.sort(Comparator.comparingInt((PlayerStatSnapshot s) -> s.value).reversed()
            .thenComparing(s -> s.key, String.CASE_INSENSITIVE_ORDER));
        ServerPlayNetworking.send(viewer, new PlayerDetailPayload(uuidStr, GSON.toJson(detail)));
    }

    private static void collectOnlineStats(ServerPlayerEntity player, List<PlayerStatSnapshot> out) {
        collectStatCategory(player, "custom", Stats.CUSTOM, Registries.CUSTOM_STAT, out, id -> id.toString());
        collectStatCategory(player, "mined", Stats.MINED, Registries.BLOCK, out, b -> Registries.BLOCK.getId(b).toString());
        collectStatCategory(player, "used", Stats.USED, Registries.ITEM, out, i -> Registries.ITEM.getId(i).toString());
        collectStatCategory(player, "crafted", Stats.CRAFTED, Registries.ITEM, out, i -> Registries.ITEM.getId(i).toString());
        collectStatCategory(player, "broken", Stats.BROKEN, Registries.ITEM, out, i -> Registries.ITEM.getId(i).toString());
        collectStatCategory(player, "picked_up", Stats.PICKED_UP, Registries.ITEM, out, i -> Registries.ITEM.getId(i).toString());
        collectStatCategory(player, "dropped", Stats.DROPPED, Registries.ITEM, out, i -> Registries.ITEM.getId(i).toString());
        collectStatCategory(player, "killed", Stats.KILLED, Registries.ENTITY_TYPE, out, e -> Registries.ENTITY_TYPE.getId(e).toString());
        collectStatCategory(player, "killed_by", Stats.KILLED_BY, Registries.ENTITY_TYPE, out, e -> Registries.ENTITY_TYPE.getId(e).toString());
    }

    private static <T> void collectStatCategory(
        ServerPlayerEntity player,
        String category,
        StatType<T> type,
        Iterable<T> keys,
        List<PlayerStatSnapshot> out,
        java.util.function.Function<T, String> idFn
    ) {
        for (T key : keys) {
            int val = player.getStatHandler().getStat(type.getOrCreateStat(key));
            if (val > 0) {
                PlayerStatSnapshot s = new PlayerStatSnapshot();
                s.category = category;
                s.key = idFn.apply(key);
                s.value = val;
                out.add(s);
            }
        }
    }

    private static void collectOfflineStats(Path statsPath, List<PlayerStatSnapshot> out) {
        if (!Files.isRegularFile(statsPath)) return;
        try (Reader reader = Files.newBufferedReader(statsPath)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("stats") || !root.get("stats").isJsonObject()) return;
            JsonObject statsObj = root.getAsJsonObject("stats");
            for (String cat : List.of("custom", "mined", "used", "crafted", "broken", "picked_up", "dropped", "killed", "killed_by")) {
                String mcCat = "minecraft:" + cat;
                if (!statsObj.has(mcCat) || !statsObj.get(mcCat).isJsonObject()) continue;
                JsonObject sec = statsObj.getAsJsonObject(mcCat);
                for (Map.Entry<String, JsonElement> e : sec.entrySet()) {
                    if (e.getValue() == null || !e.getValue().isJsonPrimitive()) continue;
                    int val = e.getValue().getAsInt();
                    if (val > 0) {
                        PlayerStatSnapshot s = new PlayerStatSnapshot();
                        s.category = cat;
                        s.key = e.getKey();
                        s.value = val;
                        out.add(s);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void collectInventorySlots(
        MinecraftServer server,
        ServerPlayerEntity viewer,
        Inventory inv,
        int maxSlots,
        List<ItemSlotSnapshot> out
    ) {
        if (inv == null) return;
        Item.TooltipContext ctx = Item.TooltipContext.create(server.getRegistryManager());
        int limit = Math.min(maxSlots, inv.size());
        for (int i = 0; i < limit; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            ItemSlotSnapshot snap = new ItemSlotSnapshot();
            snap.slot = i;
            snap.itemId = Registries.ITEM.getId(stack.getItem()).toString();
            snap.count = stack.getCount();
            snap.customName = stack.contains(DataComponentTypes.CUSTOM_NAME) ? stack.getName().getString() : "";
            snap.damage = stack.isDamageable() ? stack.getDamage() : 0;
            snap.maxDamage = stack.isDamageable() ? stack.getMaxDamage() : 0;
            List<String> tips = new ArrayList<>();
            try {
                for (Text line : stack.getTooltip(ctx, viewer, TooltipType.BASIC)) {
                    String s = line.getString();
                    if (!s.isBlank()) tips.add(s);
                }
            } catch (Exception ignored) {
            }
            snap.tooltip = tips;
            out.add(snap);
        }
    }
}
