package smp.aeternum.carpet.perimeters;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.network.PerimeterNetworking;

/** Distributed tick-based perimeter scanner with 3D BFS cluster grouping. */
public final class PerimeterScanner {
    private static final int BLOCKS_PER_TICK = 4096;
    private static final int MAX_RAW_POSITIONS = 200_000;
    private static final int MAX_CLUSTERS = 1500;
    private static final Queue<Job> JOBS = new ArrayDeque<>();

    private PerimeterScanner() {}

    public static boolean start(MinecraftServer server, Perimeter perimeter, String requesterUuid) {
        if (perimeter == null || perimeter.palette == null || perimeter.palette.isEmpty()) {
            return false;
        }
        Identifier dimId = Identifier.tryParse(perimeter.dimension);
        if (dimId == null) return false;
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
        if (world == null) return false;

        cancel(perimeter.id);
        perimeter.scannedChunks = 0;
        perimeter.complete = false;
        perimeter.totalMatchedBlocks = 0;
        perimeter.resultsTruncated = false;
        perimeter.clusters.clear();
        perimeter.recomputeChunks();
        if (requesterUuid != null) {
            perimeter.requestedBy = requesterUuid;
        }
        PerimeterStore.save();
        JOBS.add(new Job(world, perimeter));
        PerimeterNetworking.broadcastAll(server);
        return true;
    }

    public static void cancel(String perimeterId) {
        if (perimeterId == null) return;
        JOBS.removeIf(job -> job.perimeter.id.equalsIgnoreCase(perimeterId));
    }

    public static boolean isScanning(String perimeterId) {
        return JOBS.stream().anyMatch(job -> job.perimeter.id.equalsIgnoreCase(perimeterId));
    }

    public static int pendingCount() {
        return JOBS.size();
    }

    public static void tick(MinecraftServer server) {
        Job job = JOBS.peek();
        if (job == null) return;
        try {
            if (job.tick()) {
                job.finish();
                JOBS.remove();
                PerimeterStore.save();
                PerimeterNetworking.broadcastAll(server);

                Text done = Text.empty()
                    .append(Text.literal("[Perimeters] ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("Análisis terminado: ").formatted(Formatting.GRAY))
                    .append(Text.literal(job.perimeter.name).formatted(Formatting.AQUA))
                    .append(Text.literal(" → ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(job.perimeter.clusters.size() + " zona(s) / " + job.perimeter.totalMatchedBlocks + " bloq.").formatted(Formatting.YELLOW))
                    .append(Text.literal(". Usa ").formatted(Formatting.GRAY))
                    .append(Text.literal("/perimeter").setStyle(Style.EMPTY.withColor(Formatting.WHITE)
                        .withClickEvent(new ClickEvent.RunCommand("/perimeter"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Abrir Perimeters")))))
                    .append(Text.literal(".").formatted(Formatting.GRAY));
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    player.sendMessage(done, false);
                }
            } else {
                job.sendProgress(server);
            }
        } catch (RuntimeException e) {
            JOBS.remove();
            AeternumCarpet.LOGGER.error("Perimeter scan '{}' failed", job.perimeter.id, e);
        }
    }

    public static void onPlayerBlockBroken(ServerWorld world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) return;
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        long packed = pos.asLong();
        boolean changed = false;
        for (Perimeter p : PerimeterStore.all()) {
            if (!p.complete || !p.belongsTo(world)) continue;
            if (pos.getX() < p.minX || pos.getX() > p.maxX
                || pos.getY() < p.minY || pos.getY() > p.maxY
                || pos.getZ() < p.minZ || pos.getZ() > p.maxZ) {
                continue;
            }

            // 1) Exact coordinate match across member positions (prevents nested/diagonal AABB ambiguity)
            BlockCluster matched = null;
            for (BlockCluster c : p.clusters) {
                if (blockId.equals(c.blockId) && c.contains(pos) && c.removeExact(packed)) {
                    matched = c;
                    break;
                }
            }
            if (matched != null) {
                p.totalMatchedBlocks = Math.max(0, p.totalMatchedBlocks - 1);
                if (matched.count <= 0) {
                    p.clusters.remove(matched);
                }
                changed = true;
                continue;
            }

            // 2) Fallback for legacy clusters without stored positions: choose smallest enclosing bounding box
            BlockCluster smallest = null;
            for (BlockCluster c : p.clusters) {
                if (c.positions == null && blockId.equals(c.blockId) && c.contains(pos)) {
                    if (smallest == null || c.volume() < smallest.volume()) {
                        smallest = c;
                    }
                }
            }
            if (smallest != null) {
                smallest.count--;
                p.totalMatchedBlocks = Math.max(0, p.totalMatchedBlocks - 1);
                if (smallest.count <= 0) {
                    p.clusters.remove(smallest);
                }
                changed = true;
            }
        }
        if (changed) {
            PerimeterStore.save();
            PerimeterNetworking.broadcastAll(world.getServer());
        }
    }

    private static final class PaletteMatcher {
        private final Set<Block> exactBlocks = new HashSet<>();
        private final List<TagKey<Block>> tags = new ArrayList<>();

        PaletteMatcher(List<String> entries) {
            for (String raw : entries) {
                if (raw == null || raw.isBlank()) continue;
                String s = raw.trim();
                if (s.startsWith("#")) {
                    Identifier id = Identifier.tryParse(s.substring(1));
                    if (id != null) tags.add(TagKey.of(RegistryKeys.BLOCK, id));
                } else {
                    Identifier id = Identifier.tryParse(s.contains(":") ? s : "minecraft:" + s);
                    if (id != null) {
                        Registries.BLOCK.getOptionalValue(id).ifPresent(exactBlocks::add);
                    }
                }
            }
        }

        boolean matches(BlockState state) {
            if (state.isAir()) return false;
            if (exactBlocks.contains(state.getBlock())) return true;
            for (TagKey<Block> tag : tags) {
                if (state.isIn(tag)) return true;
            }
            return false;
        }
    }

    private static final class Job {
        private final ServerWorld world;
        private final Perimeter perimeter;
        private final PaletteMatcher matcher;
        private final Map<String, LongOpenHashSet> matchedByBlock = new HashMap<>();
        private final BlockPos.Mutable cursor = new BlockPos.Mutable();
        private final int minChunkX, maxChunkX, minChunkZ, maxChunkZ;
        private int chunkX, chunkZ, x, y, z;
        private boolean chunkStarted;
        private int rawCaptured;
        private int lastProgressChunks = -1;
        private long lastProgressAt;

        private Job(ServerWorld world, Perimeter perimeter) {
            this.world = world;
            this.perimeter = perimeter;
            this.matcher = new PaletteMatcher(perimeter.palette);
            this.minChunkX = Math.floorDiv(perimeter.minX, 16);
            this.maxChunkX = Math.floorDiv(perimeter.maxX, 16);
            this.minChunkZ = Math.floorDiv(perimeter.minZ, 16);
            this.maxChunkZ = Math.floorDiv(perimeter.maxZ, 16);
            this.chunkX = minChunkX;
            this.chunkZ = minChunkZ;
        }

        private boolean tick() {
            int budget = BLOCKS_PER_TICK;
            while (budget-- > 0) {
                if (chunkZ > maxChunkZ) return true;
                if (!chunkStarted) beginChunk();
                cursor.set(x, y, z);
                BlockState state = world.getBlockState(cursor);
                if (matcher.matches(state)) {
                    perimeter.totalMatchedBlocks++;
                    if (rawCaptured < MAX_RAW_POSITIONS) {
                        String id = Registries.BLOCK.getId(state.getBlock()).toString();
                        matchedByBlock.computeIfAbsent(id, k -> new LongOpenHashSet()).add(BlockPos.asLong(x, y, z));
                        rawCaptured++;
                    } else {
                        perimeter.resultsTruncated = true;
                    }
                }
                advance();
            }
            return false;
        }

        private void beginChunk() {
            WorldChunk ignored = world.getChunkManager().getWorldChunk(chunkX, chunkZ, true);
            x = Math.max(perimeter.minX, chunkX << 4);
            z = Math.max(perimeter.minZ, chunkZ << 4);
            y = perimeter.minY;
            chunkStarted = true;
        }

        private void advance() {
            if (++x <= Math.min(perimeter.maxX, (chunkX << 4) + 15)) return;
            x = Math.max(perimeter.minX, chunkX << 4);
            if (++z <= Math.min(perimeter.maxZ, (chunkZ << 4) + 15)) return;
            z = Math.max(perimeter.minZ, chunkZ << 4);
            if (++y <= perimeter.maxY) return;
            perimeter.scannedChunks++;
            chunkStarted = false;
            if (++chunkX <= maxChunkX) return;
            chunkX = minChunkX;
            chunkZ++;
        }

        private void finish() {
            List<BlockCluster> clusters = new ArrayList<>();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            it.unimi.dsi.fastutil.longs.LongArrayList memberBuf = new it.unimi.dsi.fastutil.longs.LongArrayList();

            for (var entry : matchedByBlock.entrySet()) {
                String blockId = entry.getKey();
                LongOpenHashSet remaining = entry.getValue();
                while (!remaining.isEmpty()) {
                    LongIterator it = remaining.iterator();
                    long seed = it.nextLong();
                    remaining.remove(seed);
                    queue.clear();
                    memberBuf.clear();
                    queue.enqueue(seed);

                    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                    long sumX = 0, sumY = 0, sumZ = 0;

                    while (!queue.isEmpty()) {
                        long packed = queue.dequeueLong();
                        memberBuf.add(packed);
                        int bx = BlockPos.unpackLongX(packed);
                        int by = BlockPos.unpackLongY(packed);
                        int bz = BlockPos.unpackLongZ(packed);
                        if (bx < minX) minX = bx;
                        if (by < minY) minY = by;
                        if (bz < minZ) minZ = bz;
                        if (bx > maxX) maxX = bx;
                        if (by > maxY) maxY = by;
                        if (bz > maxZ) maxZ = bz;
                        sumX += bx;
                        sumY += by;
                        sumZ += bz;

                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    if (dx == 0 && dy == 0 && dz == 0) continue;
                                    long nb = BlockPos.asLong(bx + dx, by + dy, bz + dz);
                                    if (remaining.remove(nb)) {
                                        queue.enqueue(nb);
                                    }
                                }
                            }
                        }
                    }

                    int count = memberBuf.size();
                    String uid = blockId + "@" + seed;
                    clusters.add(new BlockCluster(
                        uid, blockId, minX, minY, minZ, maxX, maxY, maxZ,
                        (int) (sumX / count), (int) (sumY / count), (int) (sumZ / count),
                        count, memberBuf.toLongArray()
                    ));
                }
            }

            clusters.sort(Comparator.comparingInt((BlockCluster c) -> c.count).reversed());
            if (clusters.size() > MAX_CLUSTERS) {
                clusters = new ArrayList<>(clusters.subList(0, MAX_CLUSTERS));
                perimeter.resultsTruncated = true;
            }
            perimeter.clusters = clusters;
            perimeter.complete = true;
            perimeter.lastScanAt = System.currentTimeMillis();
        }

        private void sendProgress(MinecraftServer server) {
            if (perimeter.totalChunks <= 0) return;
            long now = System.currentTimeMillis();
            if (perimeter.scannedChunks == lastProgressChunks || now - lastProgressAt < 2_500) return;
            lastProgressChunks = perimeter.scannedChunks;
            lastProgressAt = now;
            PerimeterNetworking.broadcastHud(server);
            if (perimeter.requestedBy == null) return;
            ServerPlayerEntity player;
            try { player = server.getPlayerManager().getPlayer(UUID.fromString(perimeter.requestedBy)); }
            catch (IllegalArgumentException ignored) { return; }
            if (player == null) return;
            int pct = (int) ((perimeter.scannedChunks * 100L) / perimeter.totalChunks);
            player.sendMessage(Text.empty()
                .append(Text.literal("[Perimeters] ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(perimeter.name + ": ").formatted(Formatting.AQUA))
                .append(Text.literal(perimeter.scannedChunks + "/" + perimeter.totalChunks).formatted(Formatting.WHITE))
                .append(Text.literal(" (" + pct + "%)").formatted(Formatting.DARK_GRAY)), true);
        }
    }
}
