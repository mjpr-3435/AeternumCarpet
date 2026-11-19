package smp.aeternum.carpet.perimeters;

import net.minecraft.util.math.BlockPos;

/** Contiguous/clustered group of matched blocks inside a perimeter. */
public final class BlockCluster {
    public String uid;
    public String blockId;
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;
    public int centerX;
    public int centerY;
    public int centerZ;
    public int count;
    /** Server-only packed BlockPos coordinates; stripped before sending network snapshots. */
    public long[] positions;

    public BlockCluster() {}

    public BlockCluster(
        String uid,
        String blockId,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        int centerX, int centerY, int centerZ,
        int count,
        long[] positions
    ) {
        this.uid = uid;
        this.blockId = blockId;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.count = count;
        this.positions = positions;
    }

    public BlockCluster copyForNetwork() {
        return new BlockCluster(uid, blockId, minX, minY, minZ, maxX, maxY, maxZ, centerX, centerY, centerZ, count, null);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX
            && pos.getY() >= minY && pos.getY() <= maxY
            && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public boolean removeExact(long packedPos) {
        if (positions == null) return false;
        int foundIdx = -1;
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] == packedPos) {
                foundIdx = i;
                break;
            }
        }
        if (foundIdx < 0) return false;

        long[] next = new long[positions.length - 1];
        System.arraycopy(positions, 0, next, 0, foundIdx);
        if (foundIdx < positions.length - 1) {
            System.arraycopy(positions, foundIdx + 1, next, foundIdx, positions.length - 1 - foundIdx);
        }
        positions = next;
        count = next.length;

        if (count > 0) {
            int nMinX = Integer.MAX_VALUE, nMinY = Integer.MAX_VALUE, nMinZ = Integer.MAX_VALUE;
            int nMaxX = Integer.MIN_VALUE, nMaxY = Integer.MIN_VALUE, nMaxZ = Integer.MIN_VALUE;
            long sumX = 0, sumY = 0, sumZ = 0;
            for (long p : next) {
                int bx = BlockPos.unpackLongX(p);
                int by = BlockPos.unpackLongY(p);
                int bz = BlockPos.unpackLongZ(p);
                if (bx < nMinX) nMinX = bx;
                if (by < nMinY) nMinY = by;
                if (bz < nMinZ) nMinZ = bz;
                if (bx > nMaxX) nMaxX = bx;
                if (by > nMaxY) nMaxY = by;
                if (bz > nMaxZ) nMaxZ = bz;
                sumX += bx;
                sumY += by;
                sumZ += bz;
            }
            minX = nMinX;
            minY = nMinY;
            minZ = nMinZ;
            maxX = nMaxX;
            maxY = nMaxY;
            maxZ = nMaxZ;
            centerX = (int) (sumX / count);
            centerY = (int) (sumY / count);
            centerZ = (int) (sumZ / count);
        }
        return true;
    }

    public String key() {
        if (uid != null && !uid.isEmpty()) return uid;
        return blockId + ":" + minX + "," + minY + "," + minZ + ":" + maxX + "," + maxY + "," + maxZ;
    }
}
