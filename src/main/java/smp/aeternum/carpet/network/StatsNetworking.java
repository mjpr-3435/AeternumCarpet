package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpetSettings;
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
        if (!AeternumCarpetSettings.commandStats || !player.hasPermissionLevel(2)) {
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
                sb.getOrCreateScore(ScoreHolder.fromName(entry.name), objective).setScore(clamped);
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
                sb.getOrCreateScore(ScoreHolder.fromName(entry.name), objective).setScore(val);
                count++;
            }
        }
        return count;
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
            sb.removeScore(ScoreHolder.fromName(owner), target);
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
        ScoreHolder holder = ScoreHolder.fromName(entry.name);

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
                sb.getOrCreateScore(ScoreHolder.fromName(entry.owner()), finalObj).setScore(entry.value());
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
        public List<ScoreboardObjectiveSnapshot> scoreboards = List.of();
        public boolean lookupRunning;
        public int lookupDone;
        public int lookupTotal;
        public int lookupResolved;
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
        sb.getOrCreateScore(ScoreHolder.fromName(req.owner), target).setScore(req.score);
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
        sb.removeScore(ScoreHolder.fromName(req.owner), target);
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
}
