package smp.aeternum.carpet.perimeters;

import carpet.patches.EntityPlayerMPFake;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/** Computes minimal-overlap chunk coverage grids and spawns/removes Carpet fake players for perimeters. */
public final class BotGridManager {
    private static final int BOT_SPAWN_Y = 128;

    private BotGridManager() {}

    public static String botPrefix(Perimeter p) {
        String clean = (p.id == null ? "p" : p.id).replaceAll("[^a-zA-Z0-9]", "");
        if (clean.isEmpty()) clean = "p";
        if (clean.length() > 7) clean = clean.substring(0, 7);
        return ("we_" + clean + "_").toLowerCase(Locale.ROOT);
    }

    public static List<BlockPos> computeGridPositions(MinecraftServer server, Perimeter p) {
        int simDist = Math.max(2, server.getPlayerManager().getSimulationDistance());
        int minCx = Math.floorDiv(p.minX, 16);
        int maxCx = Math.floorDiv(p.maxX, 16);
        int minCz = Math.floorDiv(p.minZ, 16);
        int maxCz = Math.floorDiv(p.maxZ, 16);

        List<Integer> cxList = axisCenters(minCx, maxCx, simDist);
        List<Integer> czList = axisCenters(minCz, maxCz, simDist);

        List<BlockPos> out = new ArrayList<>(cxList.size() * czList.size());
        for (int cx : cxList) {
            for (int cz : czList) {
                out.add(new BlockPos((cx << 4) + 8, BOT_SPAWN_Y, (cz << 4) + 8));
            }
        }
        return out;
    }

    private static List<Integer> axisCenters(int minC, int maxC, int radiusChunks) {
        int span = maxC - minC + 1;
        int coverage = radiusChunks * 2 + 1;
        if (span <= coverage) {
            return List.of(minC + span / 2);
        }
        int step = Math.max(1, radiusChunks * 2 - 1);
        List<Integer> centers = new ArrayList<>();
        int pos = minC + radiusChunks;
        while (pos + radiusChunks < maxC) {
            centers.add(pos);
            pos += step;
        }
        centers.add(Math.max(minC + radiusChunks, maxC - radiusChunks));
        return centers;
    }

    public static int previewCount(MinecraftServer server, Perimeter p) {
        return computeGridPositions(server, p).size();
    }

    public static int countActiveGridBots(MinecraftServer server, Perimeter p) {
        String prefix = botPrefix(p);
        int count = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player instanceof EntityPlayerMPFake && player.getName().getString().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    public static int spawnGrid(MinecraftServer server, Perimeter p) {
        Identifier dimId = Identifier.tryParse(p.dimension);
        if (dimId == null) return 0;
        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
        if (server.getWorld(dimKey) == null) return 0;

        String prefix = botPrefix(p);
        List<BlockPos> points = computeGridPositions(server, p);

        int spawned = 0;
        for (int i = 0; i < points.size(); i++) {
            String suffix = String.valueOf(i + 1);
            String name = prefix + suffix;
            if (name.length() > 16) {
                name = prefix.substring(0, Math.max(3, 16 - suffix.length())) + suffix;
            }
            if (server.getPlayerManager().getPlayer(name) != null) continue;

            BlockPos pt = points.get(i);
            boolean ok = EntityPlayerMPFake.createFake(
                name, server, new Vec3d(pt.getX() + 0.5, pt.getY(), pt.getZ() + 0.5),
                0.0, 0.0, dimKey, GameMode.SPECTATOR, true
            );
            if (ok) spawned++;
        }
        return spawned;
    }

    public static int killGrid(MinecraftServer server, Perimeter p) {
        String prefix = botPrefix(p);
        List<EntityPlayerMPFake> targets = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player instanceof EntityPlayerMPFake fake
                && fake.getName().getString().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                targets.add(fake);
            }
        }
        for (EntityPlayerMPFake fake : targets) {
            fake.kill(Text.literal("World Eater grid cleared"));
        }
        return targets.size();
    }
}
