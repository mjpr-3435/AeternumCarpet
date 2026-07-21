package smp.aeternum.carpet.finder;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FinderPlace {
    public String name;
    public String dimension;
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public long createdAt, updatedAt;
    public List<FinderContainer> containers = new ArrayList<>();
    public Set<String> excluded = new LinkedHashSet<>();

    public FinderPlace() { }
    public FinderPlace(String name, RegistryKey<World> world, BlockPos a, BlockPos b) {
        this.name = name;
        this.dimension = world.getValue().toString();
        minX = Math.min(a.getX(), b.getX()); minY = Math.min(a.getY(), b.getY()); minZ = Math.min(a.getZ(), b.getZ());
        maxX = Math.max(a.getX(), b.getX()); maxY = Math.max(a.getY(), b.getY()); maxZ = Math.max(a.getZ(), b.getZ());
        createdAt = updatedAt = System.currentTimeMillis();
    }
    public boolean belongsTo(RegistryKey<World> world) { return dimension.equals(world.getValue().toString()); }
    public boolean contains(BlockPos p) { return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ; }
    public int chunks() { return (Math.floorDiv(maxX, 16) - Math.floorDiv(minX, 16) + 1) * (Math.floorDiv(maxZ, 16) - Math.floorDiv(minZ, 16) + 1); }
    public String key(BlockPos p) { return p.getX() + "," + p.getY() + "," + p.getZ(); }
}
