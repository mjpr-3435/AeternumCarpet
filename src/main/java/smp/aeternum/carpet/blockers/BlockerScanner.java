package smp.aeternum.carpet.blockers;

import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
import net.minecraft.world.chunk.WorldChunk;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.network.BlockerNetworking;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import java.util.UUID;

/** Spreads expensive block checks across server ticks. */
public final class BlockerScanner {
    private static final int BLOCKS_PER_TICK = 4096;
    /** A result list larger than this is not useful to render and can exceed Minecraft's packet limit. */
    public static final int MAX_ENTRIES_PER_SCAN = 5_000;
    private static final Queue<Job> JOBS = new ArrayDeque<>();

    private BlockerScanner() {
    }

    public static boolean start(ServerWorld world, BlockerScan scan) {
        if (JOBS.stream().anyMatch(job -> job.scan.id.equals(scan.id))) return false;
        JOBS.add(new Job(world, scan));
        return true;
    }

    /** Deleting a scan also stops an unfinished job from recreating it in memory. */
    public static void cancel(String scanId) {
        JOBS.removeIf(job -> job.scan.id.equals(scanId));
    }

    public static void tick(MinecraftServer server) {
        Job job = JOBS.peek();
        if (job == null) return;
        try {
            if (job.tick()) {
                job.scan.complete = true;
                BlockerScanStore.save();
                BlockerNetworking.broadcastSnapshot(server);
                JOBS.remove();
                for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    player.sendMessage(Text.empty()
                        .append(Text.literal("[Blockers] ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal("Análisis terminado: ").formatted(Formatting.GRAY))
                        .append(Text.literal(job.scan.id).formatted(Formatting.AQUA))
                        .append(Text.literal(" → ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal(job.scan.matchedEntries + " bloqueadores").formatted(Formatting.YELLOW))
                        .append(Text.literal(". Usa ").formatted(Formatting.GRAY))
                        .append(Text.literal("/blockers").setStyle(Style.EMPTY.withColor(Formatting.WHITE)
                            .withClickEvent(new ClickEvent.RunCommand("/blockers"))
                            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Abrir la lista de scans")))))
                        .append(Text.literal(" para verlo.").formatted(Formatting.GRAY)), false);
                }
            } else {
                job.sendProgress(server);
            }
        } catch (RuntimeException exception) {
            JOBS.remove();
            AeternumCarpet.LOGGER.error("Blocker scan '{}' failed", job.scan.id, exception);
        }
    }

    public static void onPlayerBlockBroken(ServerWorld world, BlockPos pos) {
        boolean changed = false;
        for (BlockerScan scan : BlockerScanStore.all()) {
            if (!scan.belongsTo(world)) continue;
            Iterator<BlockerEntry> iterator = scan.entries.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().pos().equals(pos)) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        if (changed) {
            BlockerScanStore.save();
            BlockerNetworking.broadcastSnapshot(world.getServer());
        }
    }

    private static final class Job {
        private final ServerWorld world;
        private final BlockerScan scan;
        private final int minChunkX;
        private final int maxChunkX;
        private final int minChunkZ;
        private final int maxChunkZ;
        private int chunkX;
        private int chunkZ;
        private int x;
        private int y;
        private int z;
        private boolean chunkStarted;
        private int lastProgressChunks = -1;
        private long lastProgressAt;

        private Job(ServerWorld world, BlockerScan scan) {
            this.world = world;
            this.scan = scan;
            minChunkX = Math.floorDiv(scan.minX, 16);
            maxChunkX = Math.floorDiv(scan.maxX, 16);
            minChunkZ = Math.floorDiv(scan.minZ, 16);
            maxChunkZ = Math.floorDiv(scan.maxZ, 16);
            chunkX = minChunkX;
            chunkZ = minChunkZ;
        }

        private boolean tick() {
            int budget = BLOCKS_PER_TICK;
            while (budget-- > 0) {
                if (chunkZ > maxChunkZ) return true;
                if (!chunkStarted) beginChunk();
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = world.getBlockState(pos);
                if (isBlocker(state, pos)) {
                    String id = Registries.BLOCK.getId(state.getBlock()).toString();
                    String reason = isWaterlogged(state) ? "waterlogged blocker" : "configured explosion blocker";
                    scan.matchedEntries++;
                    if (scan.entries.size() < MAX_ENTRIES_PER_SCAN) {
                        scan.entries.add(new BlockerEntry(x, y, z, id, reason));
                    } else {
                        scan.entriesTruncated = true;
                    }
                }
                advance();
            }
            return false;
        }

        private void beginChunk() {
            // This deliberately requests one FULL chunk at a time; it is never force-loaded permanently.
            WorldChunk ignored = world.getChunkManager().getWorldChunk(chunkX, chunkZ, true);
            x = Math.max(scan.minX, chunkX << 4);
            z = Math.max(scan.minZ, chunkZ << 4);
            y = scan.minY;
            chunkStarted = true;
        }

        private void advance() {
            if (++x <= Math.min(scan.maxX, (chunkX << 4) + 15)) return;
            x = Math.max(scan.minX, chunkX << 4);
            if (++z <= Math.min(scan.maxZ, (chunkZ << 4) + 15)) return;
            z = Math.max(scan.minZ, chunkZ << 4);
            if (++y <= scan.maxY) return;
            scan.scannedChunks++;
            chunkStarted = false;
            if (++chunkX <= maxChunkX) return;
            chunkX = minChunkX;
            chunkZ++;
        }

        private boolean isBlocker(BlockState state, BlockPos pos) {
            if (state.isAir()) return false;
            if (isWaterlogged(state)) return true;
            return BlockerBlockList.contains(Registries.BLOCK.getId(state.getBlock()).toString());
        }

        private boolean isWaterlogged(BlockState state) {
            return state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED);
        }

        private void sendProgress(MinecraftServer server) {
            if (scan.requestedBy == null || scan.totalChunks <= 0) return;
            long now = System.currentTimeMillis();
            if (scan.scannedChunks == lastProgressChunks || now - lastProgressAt < 2_000) return;
            net.minecraft.server.network.ServerPlayerEntity player;
            try { player = server.getPlayerManager().getPlayer(UUID.fromString(scan.requestedBy)); }
            catch (IllegalArgumentException ignored) { return; }
            if (player == null) return;
            lastProgressChunks = scan.scannedChunks;
            lastProgressAt = now;
            int percent = (int) ((scan.scannedChunks * 100L) / scan.totalChunks);
            player.sendMessage(Text.empty()
                .append(Text.literal("[Blockers] ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal("Progreso: ").formatted(Formatting.GRAY))
                .append(Text.literal(scan.scannedChunks + "/" + scan.totalChunks).formatted(Formatting.WHITE))
                .append(Text.literal(" (" + percent + "%)").formatted(Formatting.DARK_GRAY)), false);
        }
    }
}
