package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smp.aeternum.carpet.blockers.BlockerScanStore;
import smp.aeternum.carpet.blockers.BlockerScanner;
import smp.aeternum.carpet.blockers.BlockerScan;
import smp.aeternum.carpet.blockers.BlockerEntry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BlockerNetworking {
    private static final Gson GSON = new GsonBuilder().create();
    /* Vanilla refuses custom payloads around 1 MiB. Leave margin for packet framing. */
    private static final int MAX_SNAPSHOT_BYTES = 850_000;

    private BlockerNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(BlockerDataPayload.ID, BlockerDataPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlockerDeletePayload.ID, BlockerDeletePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BlockerDeletePayload.ID, (payload, context) -> {
            if (!context.player().hasPermissionLevel(2)) return;
            if (BlockerScanStore.remove(payload.id())) {
                BlockerScanner.cancel(payload.id());
                Text notice = Text.empty()
                    .append(Text.literal("[Blockers] ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("Scan eliminado; se limpiaron sus selecciones: ").formatted(Formatting.GRAY))
                    .append(Text.literal(payload.id()).formatted(Formatting.AQUA));
                for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                    player.sendMessage(notice, false);
                }
                broadcastSnapshot(context.server());
            }
        });
    }

    public static void sendSnapshot(ServerPlayerEntity player, boolean openScreen) {
        if (ServerPlayNetworking.canSend(player, BlockerDataPayload.ID)) {
            ServerPlayNetworking.send(player, new BlockerDataPayload(snapshot(), openScreen, player.hasPermissionLevel(2)));
        }
    }

    public static void broadcastSnapshot(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) sendSnapshot(player, false);
    }

    private static String snapshot() {
        List<BlockerScan> originals = new ArrayList<>(BlockerScanStore.all());
        List<BlockerScan> visible = new ArrayList<>();
        for (BlockerScan original : originals) visible.add(copyWithoutEntries(original));

        int bytes = utf8Bytes(GSON.toJson(visible));
        for (int scanIndex = 0; scanIndex < visible.size(); scanIndex++) {
            BlockerScan original = originals.get(scanIndex);
            BlockerScan copy = visible.get(scanIndex);
            int totalMatches = original.matchedEntries > 0 ? original.matchedEntries : original.entries.size();
            copy.matchedEntries = totalMatches;
            for (BlockerEntry entry : original.entries) {
                int entryBytes = utf8Bytes(GSON.toJson(entry)) + 1;
                if (bytes + entryBytes > MAX_SNAPSHOT_BYTES) {
                    copy.entriesTruncated = true;
                    break;
                }
                copy.entries.add(entry);
                bytes += entryBytes;
            }
            if (copy.entries.size() < original.entries.size() || totalMatches > copy.entries.size()) copy.entriesTruncated = true;
        }
        return GSON.toJson(visible);
    }

    private static int utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static BlockerScan copyWithoutEntries(BlockerScan original) {
        BlockerScan copy = new BlockerScan();
        copy.id = original.id;
        copy.dimension = original.dimension;
        copy.minX = original.minX;
        copy.minY = original.minY;
        copy.minZ = original.minZ;
        copy.maxX = original.maxX;
        copy.maxY = original.maxY;
        copy.maxZ = original.maxZ;
        copy.scannedChunks = original.scannedChunks;
        copy.totalChunks = original.totalChunks;
        copy.complete = original.complete;
        copy.createdAt = original.createdAt;
        copy.requestedBy = original.requestedBy;
        copy.matchedEntries = original.matchedEntries > 0 ? original.matchedEntries : original.entries.size();
        copy.entriesTruncated = original.entriesTruncated;
        return copy;
    }
}
