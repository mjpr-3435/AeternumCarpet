package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.stats.PlayerRegistryStore;
import smp.aeternum.carpet.stats.PlayerRegistryStore.Entry;
import smp.aeternum.carpet.stats.PlayerRegistryStore.EntryType;

public final class StatsNetworking {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private static final Set<String> IN_FLIGHT_LOOKUPS = ConcurrentHashMap.newKeySet();

    private StatsNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(StatsDataPayload.ID, StatsDataPayload.CODEC);
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
        StatsSnapshot snapshot = buildSnapshot();
        ServerPlayNetworking.send(player, new StatsDataPayload(GSON.toJson(snapshot), openScreen, status, error));
    }

    private static StatsSnapshot buildSnapshot() {
        StatsSnapshot snapshot = new StatsSnapshot();
        Map<String, Entry> rawEntries = PlayerRegistryStore.getEntriesSnapshot();
        List<RegistryEntrySnapshot> list = new ArrayList<>(rawEntries.size());
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
            list.add(item);
        }
        list.sort(
            Comparator.comparing((RegistryEntrySnapshot e) -> e.name.isEmpty())
                .thenComparing(e -> e.name.isEmpty() ? e.uuid : e.name, String.CASE_INSENSITIVE_ORDER)
        );
        snapshot.entries = list;
        snapshot.unexpectedFiles = PlayerRegistryStore.getUnexpectedFilesSnapshot();
        return snapshot;
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
        switch (payload.action()) {
            case "rescan" -> {
                PlayerRegistryStore.scanFull(server);
                sendSnapshot(player, false, "Escaneo completado.", false);
            }
            case "set_type" -> {
                EntryType nextType;
                try {
                    nextType = EntryType.valueOf(payload.value().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    sendSnapshot(player, false, "Tipo inválido: " + payload.value(), true);
                    return;
                }
                if (PlayerRegistryStore.setType(payload.uuid(), nextType)) {
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

    public static final class StatsSnapshot {
        public List<RegistryEntrySnapshot> entries = List.of();
        public List<String> unexpectedFiles = List.of();
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
    }
}
