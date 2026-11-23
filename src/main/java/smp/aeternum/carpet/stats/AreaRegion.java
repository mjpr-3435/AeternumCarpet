package smp.aeternum.carpet.stats;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public final class AreaRegion {
    public enum Mode {
        NONE,
        RECTANGLE,
        CUSTOM
    }

    public Mode mode = Mode.NONE;
    public String dimension = "minecraft:overworld";
    public int x1;
    public int z1;
    public int x2;
    public int z2;

    // For CUSTOM mode: compressed horizontal scanline spans [z, xStart, xEnd]
    public List<int[]> spans = new ArrayList<>();
    public int columnCount;
    public int previewSize;
    public String previewGrid = "";
    public String blockId = "";

    private transient Int2ObjectOpenHashMap<int[]> cachedRows;

    public AreaRegion copy() {
        AreaRegion c = new AreaRegion();
        c.mode = this.mode != null ? this.mode : Mode.NONE;
        c.dimension = this.dimension != null ? this.dimension : "minecraft:overworld";
        c.x1 = this.x1;
        c.z1 = this.z1;
        c.x2 = this.x2;
        c.z2 = this.z2;
        c.columnCount = this.columnCount;
        c.previewSize = this.previewSize;
        c.previewGrid = this.previewGrid != null ? this.previewGrid : "";
        c.blockId = this.blockId != null ? this.blockId : "";
        c.spans = new ArrayList<>(this.spans != null ? this.spans.size() : 0);
        if (this.spans != null) {
            for (int[] s : this.spans) {
                if (s != null && s.length == 3) {
                    c.spans.add(new int[] { s[0], s[1], s[2] });
                }
            }
        }
        return c;
    }

    public boolean isActive() {
        return mode != null && mode != Mode.NONE;
    }

    public boolean contains(RegistryKey<World> worldKey, int x, int z) {
        if (mode == null || mode == Mode.NONE) {
            return true;
        }
        if (worldKey == null || !worldKey.getValue().toString().equals(dimension)) {
            return false;
        }
        if (mode == Mode.RECTANGLE) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
        if (mode == Mode.CUSTOM) {
            Int2ObjectOpenHashMap<int[]> rows = getOrBuildRows();
            int[] intervals = rows.get(z);
            if (intervals == null) {
                return false;
            }
            for (int i = 0; i < intervals.length; i += 2) {
                if (x >= intervals[i] && x <= intervals[i + 1]) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private Int2ObjectOpenHashMap<int[]> getOrBuildRows() {
        Int2ObjectOpenHashMap<int[]> rows = cachedRows;
        if (rows != null) {
            return rows;
        }
        rows = new Int2ObjectOpenHashMap<>();
        if (spans != null) {
            for (int[] span : spans) {
                if (span == null || span.length != 3) continue;
                int z = span[0];
                int start = Math.min(span[1], span[2]);
                int end = Math.max(span[1], span[2]);
                int[] prev = rows.get(z);
                if (prev == null) {
                    rows.put(z, new int[] { start, end });
                } else {
                    int[] next = new int[prev.length + 2];
                    System.arraycopy(prev, 0, next, 0, prev.length);
                    next[prev.length] = start;
                    next[prev.length + 1] = end;
                    rows.put(z, next);
                }
            }
        }
        cachedRows = rows;
        return rows;
    }

    public void rebuildCache() {
        cachedRows = null;
        getOrBuildRows();
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }
}
