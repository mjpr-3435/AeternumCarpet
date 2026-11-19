package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.perimeters.BlockCluster;
import smp.aeternum.carpet.perimeters.BotGridManager;
import smp.aeternum.carpet.perimeters.Perimeter;
import smp.aeternum.carpet.perimeters.PerimeterPresetStore;
import smp.aeternum.carpet.perimeters.PerimeterScanner;
import smp.aeternum.carpet.perimeters.PerimeterStore;
import smp.aeternum.carpet.perimeters.WorldEaterMonitor;

public final class PerimeterNetworking {
    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_SNAPSHOT_BYTES = 850_000;

    private PerimeterNetworking() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(PerimeterDataPayload.ID, PerimeterDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PerimeterHudPayload.ID, PerimeterHudPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PerimeterActionPayload.ID, PerimeterActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PerimeterActionPayload.ID, (payload, context) ->
            context.server().execute(() -> handleAction(context.player(), context.server(), payload)));
    }

    private static void handleAction(ServerPlayerEntity player, MinecraftServer server, PerimeterActionPayload payload) {
        if (!AeternumCarpetSettings.commandPerimeters) return;
        Perimeter p = PerimeterStore.get(payload.perimeterId());
        switch (payload.action()) {
            case "update" -> {
                if (p == null) return;
                if (p.palette == null || p.palette.isEmpty()) {
                    player.sendMessage(Text.literal("[Perimeters] Añade al menos un bloque o #tag a la paleta antes de escanear.").formatted(Formatting.YELLOW), false);
                    return;
                }
                if (!PerimeterScanner.start(server, p, player.getUuidAsString())) {
                    player.sendMessage(Text.literal("[Perimeters] No se pudo iniciar el escaneo (dimensión inválida o paleta vacía).").formatted(Formatting.RED), false);
                }
            }
            case "add_palette" -> {
                if (p == null || payload.arg1().isBlank()) return;
                String item = payload.arg1().trim();
                if (!p.palette.contains(item)) {
                    p.palette.add(item);
                    PerimeterStore.save();
                    broadcastSnapshot(server);
                }
            }
            case "remove_palette" -> {
                if (p == null || payload.arg1().isBlank()) return;
                if (p.palette.remove(payload.arg1())) {
                    PerimeterStore.save();
                    broadcastSnapshot(server);
                }
            }
            case "clear_palette" -> {
                if (p == null) return;
                p.palette.clear();
                PerimeterStore.save();
                broadcastSnapshot(server);
            }
            case "apply_preset" -> {
                if (p == null) return;
                List<String> preset = PerimeterPresetStore.get(payload.arg1());
                if (preset == null) return;
                p.palette = new ArrayList<>(preset);
                PerimeterStore.save();
                broadcastSnapshot(server);
            }
            case "save_preset" -> {
                if (p == null || payload.arg1().isBlank()) return;
                PerimeterPresetStore.put(payload.arg1(), p.palette);
                broadcastSnapshot(server);
            }
            case "delete_preset" -> {
                if (payload.arg1().isBlank()) return;
                if (PerimeterPresetStore.remove(payload.arg1())) {
                    broadcastSnapshot(server);
                }
            }
            case "pin_hud" -> {
                String targetId = (payload.perimeterId() == null || payload.perimeterId().isBlank()) ? null : payload.perimeterId();
                if (targetId != null && PerimeterStore.get(targetId) == null) targetId = null;
                PerimeterStore.setPinnedHud(player.getUuid(), targetId);
                sendSnapshot(player, false);
                sendHud(player);
            }
            case "delete" -> {
                if (p == null) return;
                boolean isOwner = p.requestedBy != null && p.requestedBy.equals(player.getUuidAsString());
                if (!player.hasPermissionLevel(2) && !isOwner) return;
                if (PerimeterStore.remove(p.id)) {
                    PerimeterScanner.cancel(p.id);
                    broadcastAll(server);
                }
            }
            case "we_toggle" -> {
                if (p == null) return;
                WorldEaterMonitor.onToggle(server, p, !p.weEnabled);
            }
            case "we_spawn_bots" -> {
                if (p == null) return;
                int count = BotGridManager.spawnGrid(server, p);
                p.weActiveBots = BotGridManager.countActiveGridBots(server, p);
                player.sendMessage(Text.literal("[Perimeters] Bots de grilla solicitados: " + count + ".").formatted(Formatting.AQUA), false);
                broadcastAll(server);
            }
            case "we_kill_bots" -> {
                if (p == null) return;
                int killed = BotGridManager.killGrid(server, p);
                p.weActiveBots = 0;
                player.sendMessage(Text.literal("[Perimeters] Bots de grilla retirados: " + killed + ".").formatted(Formatting.GRAY), false);
                broadcastAll(server);
            }
            case "we_config" -> {
                if (p == null) return;
                try {
                    if (!payload.arg1().isBlank()) {
                        p.weTargetLowY = Math.max(-64, Math.min(320, Integer.parseInt(payload.arg1().trim())));
                    }
                    if (!payload.arg2().isBlank()) {
                        p.weStallSeconds = Math.max(5, Math.min(600, Integer.parseInt(payload.arg2().trim())));
                    }
                    WorldEaterMonitor.onConfigUpdated(p);
                    PerimeterStore.save();
                    broadcastAll(server);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public static void sendSnapshot(ServerPlayerEntity player, boolean openScreen) {
        if (!ServerPlayNetworking.canSend(player, PerimeterDataPayload.ID)) return;
        String pinned = PerimeterStore.getPinnedHud(player.getUuid());
        String json = buildDataSnapshot(player.getServer(), pinned);
        ServerPlayNetworking.send(player, new PerimeterDataPayload(json, openScreen, player.hasPermissionLevel(2)));
    }

    public static void sendHud(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, PerimeterHudPayload.ID)) return;
        if (!AeternumCarpetSettings.commandPerimeters) {
            ServerPlayNetworking.send(player, new PerimeterHudPayload(""));
            return;
        }
        String pinnedId = PerimeterStore.getPinnedHud(player.getUuid());
        Perimeter p = pinnedId != null ? PerimeterStore.get(pinnedId) : null;
        if (p == null) {
            ServerPlayNetworking.send(player, new PerimeterHudPayload(""));
            return;
        }
        HudSnapshot hud = new HudSnapshot();
        hud.id = p.id;
        hud.name = p.name;
        hud.dimension = p.dimension;
        hud.complete = p.complete;
        hud.scanning = PerimeterScanner.isScanning(p.id);
        hud.scannedChunks = p.scannedChunks;
        hud.totalChunks = p.totalChunks;
        hud.totalMatchedBlocks = p.totalMatchedBlocks;
        hud.totalClusters = p.clusters.size();
        hud.weEnabled = p.weEnabled;
        hud.weStatus = p.weStatus;
        hud.weCurrentTntY = p.weCurrentTntY;
        hud.weTargetLowY = p.effectiveTargetLowY();
        hud.weLoadedPct = p.weLoadedPct;
        hud.weActiveBots = p.weActiveBots;
        hud.weHint = p.weHint;
        int limit = Math.min(8, p.clusters.size());
        for (int i = 0; i < limit; i++) {
            hud.clusters.add(p.clusters.get(i).copyForNetwork());
        }
        ServerPlayNetworking.send(player, new PerimeterHudPayload(GSON.toJson(hud)));
    }

    public static void broadcastSnapshot(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendSnapshot(player, false);
        }
    }

    public static void broadcastHud(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendHud(player);
        }
    }

    public static void broadcastAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendSnapshot(player, false);
            sendHud(player);
        }
    }

    private static String buildDataSnapshot(MinecraftServer server, String pinnedId) {
        DataSnapshot data = new DataSnapshot();
        data.pinnedId = pinnedId;
        data.presets = PerimeterPresetStore.all();

        List<Perimeter> originals = new ArrayList<>(PerimeterStore.all());
        List<Perimeter> visible = new ArrayList<>();
        for (Perimeter orig : originals) {
            visible.add(copyHeader(server, orig));
        }
        data.perimeters = visible;

        int bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8).length;
        for (int i = 0; i < visible.size(); i++) {
            Perimeter orig = originals.get(i);
            Perimeter copy = visible.get(i);
            for (BlockCluster c : orig.clusters) {
                BlockCluster netCluster = c.copyForNetwork();
                int cBytes = GSON.toJson(netCluster).getBytes(StandardCharsets.UTF_8).length + 1;
                if (bytes + cBytes > MAX_SNAPSHOT_BYTES) {
                    copy.resultsTruncated = true;
                    break;
                }
                copy.clusters.add(netCluster);
                bytes += cBytes;
            }
        }
        return GSON.toJson(data);
    }

    private static Perimeter copyHeader(MinecraftServer server, Perimeter o) {
        Perimeter c = new Perimeter();
        c.id = o.id;
        c.name = o.name;
        c.dimension = o.dimension;
        c.minX = o.minX;
        c.minY = o.minY;
        c.minZ = o.minZ;
        c.maxX = o.maxX;
        c.maxY = o.maxY;
        c.maxZ = o.maxZ;
        c.palette = new ArrayList<>(o.palette);
        c.scannedChunks = o.scannedChunks;
        c.totalChunks = o.totalChunks;
        c.complete = o.complete;
        c.totalMatchedBlocks = o.totalMatchedBlocks;
        c.resultsTruncated = o.resultsTruncated;
        c.createdAt = o.createdAt;
        c.lastScanAt = o.lastScanAt;
        c.requestedBy = o.requestedBy;
        c.weEnabled = o.weEnabled;
        c.weTargetLowY = o.effectiveTargetLowY();
        c.weStallSeconds = o.weStallSeconds;
        c.weStatus = o.weStatus;
        c.weCurrentTntY = o.weCurrentTntY;
        c.weLoadedPct = o.weLoadedPct;
        c.weActiveBots = server != null ? BotGridManager.countActiveGridBots(server, o) : o.weActiveBots;
        c.wePreviewBots = server != null ? BotGridManager.previewCount(server, o) : o.wePreviewBots;
        c.weHint = o.weHint;
        return c;
    }

    public static final class DataSnapshot {
        public String pinnedId;
        public List<Perimeter> perimeters = new ArrayList<>();
        public Map<String, List<String>> presets = Map.of();
    }

    public static final class HudSnapshot {
        public String id;
        public String name;
        public String dimension;
        public boolean complete;
        public boolean scanning;
        public int scannedChunks;
        public int totalChunks;
        public int totalMatchedBlocks;
        public int totalClusters;
        public boolean weEnabled;
        public String weStatus;
        public int weCurrentTntY = Integer.MIN_VALUE;
        public int weTargetLowY;
        public int weLoadedPct;
        public int weActiveBots;
        public String weHint;
        public List<BlockCluster> clusters = new ArrayList<>();
    }
}
