package smp.aeternum.carpet.perimeters;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Persistent perimeter region with block palette, cluster results, and World Eater monitor settings. */
public final class Perimeter {
    public String id;
    public String name;
    public String dimension;
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;
    public List<String> palette = new ArrayList<>();
    public int scannedChunks;
    public int totalChunks;
    public boolean complete;
    public int totalMatchedBlocks;
    public boolean resultsTruncated;
    public long createdAt;
    public long lastScanAt;
    public String requestedBy;
    public List<BlockCluster> clusters = new ArrayList<>();

    // Fase 2: World Eater monitor & bot grid
    public boolean weEnabled;
    public int weTargetLowY = Integer.MIN_VALUE;
    public int weStallSeconds = 30;
    public String weStatus = "IDLE";
    public int weCurrentTntY = Integer.MIN_VALUE;
    public int weLoadedPct;
    public int weActiveBots;
    public int wePreviewBots;
    public String weHint = "";

    public Perimeter() {}

    public Perimeter(String id, String name, RegistryKey<World> dimension, BlockPos first, BlockPos second) {
        this.id = id;
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        setBounds(dimension, first, second);
        this.weTargetLowY = defaultLowYFor(this.dimension);
    }

    public static int defaultLowYFor(String dim) {
        if ("minecraft:the_nether".equals(dim)) return 8;
        if ("minecraft:the_end".equals(dim)) return 4;
        return -55;
    }

    public int effectiveTargetLowY() {
        return weTargetLowY == Integer.MIN_VALUE ? defaultLowYFor(dimension) : weTargetLowY;
    }

    public void setBounds(RegistryKey<World> dimension, BlockPos first, BlockPos second) {
        this.dimension = dimension.getValue().toString();
        this.minX = Math.min(first.getX(), second.getX());
        this.minY = Math.min(first.getY(), second.getY());
        this.minZ = Math.min(first.getZ(), second.getZ());
        this.maxX = Math.max(first.getX(), second.getX());
        this.maxY = Math.max(first.getY(), second.getY());
        this.maxZ = Math.max(first.getZ(), second.getZ());
        if (this.weTargetLowY == Integer.MIN_VALUE) {
            this.weTargetLowY = defaultLowYFor(this.dimension);
        }
        recomputeChunks();
    }

    public void recomputeChunks() {
        this.totalChunks = (Math.floorDiv(maxX, 16) - Math.floorDiv(minX, 16) + 1)
            * (Math.floorDiv(maxZ, 16) - Math.floorDiv(minZ, 16) + 1);
    }

    public boolean belongsTo(World world) {
        return dimension != null && dimension.equals(world.getRegistryKey().getValue().toString());
    }
}
