package smp.aeternum.carpet.stats;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class AreaScanner {
    private static final int MAX_BORDER_BLOCKS = 1_000_000;
    private static final int PREVIEW_SIZE = 36;

    private AreaScanner() {
    }

    public record ScanResult(boolean ok, String error, AreaRegion region) {
    }

    public static ScanResult scanCustomContour(ServerWorld world, BlockPos seedPos) {
        if (world == null || seedPos == null) {
            return new ScanResult(false, "Mundo o posición inválida.", null);
        }
        BlockState seedState = world.getBlockState(seedPos);
        if (seedState.isAir()) {
            return new ScanResult(false, "El bloque en (" + seedPos.getX() + ", " + seedPos.getY() + ", " + seedPos.getZ() + ") es aire.", null);
        }
        Block targetBlock = seedState.getBlock();
        String blockId = Registries.BLOCK.getId(targetBlock).toString();

        // 1. 3D BFS (26-neighborhood) over connected blocks of the same type
        LongOpenHashSet visitedBlocks = new LongOpenHashSet();
        LongOpenHashSet borderCols = new LongOpenHashSet();
        List<BlockPos> queue = new ArrayList<>();
        queue.add(seedPos);
        visitedBlocks.add(seedPos.asLong());

        int minX = seedPos.getX(), maxX = seedPos.getX();
        int minZ = seedPos.getZ(), maxZ = seedPos.getZ();

        int head = 0;
        while (head < queue.size()) {
            BlockPos cur = queue.get(head++);
            int cx = cur.getX(), cy = cur.getY(), cz = cur.getZ();
            borderCols.add(AreaRegion.pack(cx, cz));
            if (cx < minX) minX = cx;
            if (cx > maxX) maxX = cx;
            if (cz < minZ) minZ = cz;
            if (cz > maxZ) maxZ = cz;

            if (visitedBlocks.size() >= MAX_BORDER_BLOCKS) {
                return new ScanResult(false, "Contorno demasiado grande (máx " + MAX_BORDER_BLOCKS + " bloques).", null);
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = new BlockPos(cx + dx, cy + dy, cz + dz);
                        long packed = next.asLong();
                        if (!visitedBlocks.contains(packed) && world.getBlockState(next).getBlock() == targetBlock) {
                            visitedBlocks.add(packed);
                            queue.add(next);
                        }
                    }
                }
            }
        }

        int width = maxX - minX + 3;
        int height = maxZ - minZ + 3;
        long cellCount = (long) width * (long) height;
        if (cellCount <= 0 || cellCount > Integer.MAX_VALUE - 8) {
            return new ScanResult(false, "Área demasiado grande para escaneo 2D.", null);
        }

        // 2. 2D exterior flood-fill on [minX-1 .. maxX+1] x [minZ-1 .. maxZ+1]
        int originX = minX - 1;
        int originZ = minZ - 1;
        BitSet exterior = new BitSet((int) cellCount);
        LongArrayFIFOQueue bfs = new LongArrayFIFOQueue();
        exterior.set(0);
        bfs.enqueue(0L);

        int[] dxs = { 1, -1, 0, 0 };
        int[] dzs = { 0, 0, 1, -1 };
        while (!bfs.isEmpty()) {
            long cell = bfs.dequeueLong();
            int gx = (int) (cell >> 32);
            int gz = (int) cell;
            for (int d = 0; d < 4; d++) {
                int nx = gx + dxs[d];
                int nz = gz + dzs[d];
                if (nx < 0 || nx >= width || nz < 0 || nz >= height) continue;
                int idx = nz * width + nx;
                if (exterior.get(idx)) continue;
                int wx = originX + nx;
                int wz = originZ + nz;
                if (borderCols.contains(AreaRegion.pack(wx, wz))) continue;
                exterior.set(idx);
                bfs.enqueue(((long) nx << 32) | (nz & 0xFFFFFFFFL));
            }
        }

        // 3. Collect interior + border columns into horizontal scanline spans
        int interiorCount = 0;
        int totalCols = 0;
        List<int[]> spans = new ArrayList<>();
        for (int gz = 1; gz < height - 1; gz++) {
            int wz = originZ + gz;
            int runStart = Integer.MIN_VALUE;
            for (int gx = 1; gx < width - 1; gx++) {
                int wx = originX + gx;
                boolean isBorder = borderCols.contains(AreaRegion.pack(wx, wz));
                boolean isInside = !isBorder && !exterior.get(gz * width + gx);
                if (isInside) interiorCount++;
                boolean included = isBorder || isInside;
                if (included) {
                    totalCols++;
                    if (runStart == Integer.MIN_VALUE) {
                        runStart = wx;
                    }
                } else if (runStart != Integer.MIN_VALUE) {
                    spans.add(new int[] { wz, runStart, wx - 1 });
                    runStart = Integer.MIN_VALUE;
                }
            }
            if (runStart != Integer.MIN_VALUE) {
                spans.add(new int[] { wz, runStart, originX + width - 2 });
            }
        }

        if (interiorCount == 0) {
            return new ScanResult(false, "El contorno de " + blockId + " no está cerrado o no encierra ningún interior.", null);
        }

        // 4. Build compact 36x36 preview grid ('.' = outside, '+' = interior, '#' = border)
        char[] grid = new char[PREVIEW_SIZE * PREVIEW_SIZE];
        Arrays.fill(grid, '.');
        int boxW = maxX - minX + 1;
        int boxH = maxZ - minZ + 1;
        int spanMax = Math.max(boxW, boxH);
        for (int gz = 1; gz < height - 1; gz++) {
            int wz = originZ + gz;
            int py = Math.min(PREVIEW_SIZE - 1, (int) (((long) (wz - minZ) * PREVIEW_SIZE) / Math.max(1, spanMax)));
            for (int gx = 1; gx < width - 1; gx++) {
                int wx = originX + gx;
                boolean isBorder = borderCols.contains(AreaRegion.pack(wx, wz));
                boolean isInside = !isBorder && !exterior.get(gz * width + gx);
                if (!isBorder && !isInside) continue;
                int px = Math.min(PREVIEW_SIZE - 1, (int) (((long) (wx - minX) * PREVIEW_SIZE) / Math.max(1, spanMax)));
                int pidx = py * PREVIEW_SIZE + px;
                if (isBorder || grid[pidx] == '.') {
                    grid[pidx] = isBorder ? '#' : '+';
                }
            }
        }

        AreaRegion region = new AreaRegion();
        region.mode = AreaRegion.Mode.CUSTOM;
        region.dimension = world.getRegistryKey().getValue().toString();
        region.x1 = minX;
        region.z1 = minZ;
        region.x2 = maxX;
        region.z2 = maxZ;
        region.spans = spans;
        region.columnCount = totalCols;
        region.previewSize = PREVIEW_SIZE;
        region.previewGrid = new String(grid);
        region.blockId = blockId;
        region.rebuildCache();

        return new ScanResult(true, "", region);
    }
}
