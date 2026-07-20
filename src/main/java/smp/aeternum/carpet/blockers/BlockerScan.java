package smp.aeternum.carpet.blockers;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class BlockerScan {
    public String id;
    public String dimension;
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;
    public int scannedChunks;
    public int totalChunks;
    public boolean complete;
    public long createdAt;
    public String requestedBy;
    public List<BlockerEntry> entries = new ArrayList<>();

    public BlockerScan() {
    }

    public BlockerScan(String id, RegistryKey<World> dimension, BlockPos first, BlockPos second) {
        this.id = id;
        this.createdAt = System.currentTimeMillis();
        this.dimension = dimension.getValue().toString();
        minX = Math.min(first.getX(), second.getX());
        minY = Math.min(first.getY(), second.getY());
        minZ = Math.min(first.getZ(), second.getZ());
        maxX = Math.max(first.getX(), second.getX());
        maxY = Math.max(first.getY(), second.getY());
        maxZ = Math.max(first.getZ(), second.getZ());
        totalChunks = (Math.floorDiv(maxX, 16) - Math.floorDiv(minX, 16) + 1)
            * (Math.floorDiv(maxZ, 16) - Math.floorDiv(minZ, 16) + 1);
    }

    public boolean belongsTo(World world) {
        return dimension.equals(world.getRegistryKey().getValue().toString());
    }
}
