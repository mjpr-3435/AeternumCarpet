package smp.aeternum.carpet.exploration;

import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.config.AeternumCarpetConfig;
import smp.aeternum.carpet.config.AllowedGenerationArea;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerExplorationGuard {
    private static final Text EXPLORATION_DISABLED_MESSAGE = Text.literal("Exploration is disabled outside allowed areas.");
    private static final int MESSAGE_COOLDOWN_TICKS = 100;

    private static final Map<UUID, Integer> nextMessageTickByPlayer = new ConcurrentHashMap<>();
    private static final Set<String> missingDimensionsWarningLogged = ConcurrentHashMap.newKeySet();

    private PlayerExplorationGuard() {
    }

    public static void tick(MinecraftServer server) {
        if (AeternumCarpetSettings.allowPlayerExploration) {
            missingDimensionsWarningLogged.clear();
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            handlePlayer(player);
        }
    }

    private static void handlePlayer(ServerPlayerEntity player) {
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        List<AllowedGenerationArea> areas = AeternumCarpetConfig.findAreasByDimension(dimension);
        if (areas.isEmpty()) {
            if (missingDimensionsWarningLogged.add(dimension)) {
                AeternumCarpet.LOGGER.warn("allowPlayerExploration is false but no allowedGenerationAreas are configured for dimension {}. Exploration guard is disabled in that dimension until at least one area exists.", dimension);
            }
            return;
        }

        int chunkX = Math.floorDiv(player.getBlockX(), 16);
        int chunkZ = Math.floorDiv(player.getBlockZ(), 16);
        if (AeternumCarpetConfig.isChunkInsideAllowedGenerationArea(dimension, chunkX, chunkZ)) {
            return;
        }

        AreaBounds nearestArea = findNearestArea(player.getX(), player.getZ(), areas);
        if (nearestArea == null) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        int targetBlockX = MathHelper.clamp(player.getBlockX(), nearestArea.minBlockX(), nearestArea.maxBlockX());
        int targetBlockZ = MathHelper.clamp(player.getBlockZ(), nearestArea.minBlockZ(), nearestArea.maxBlockZ());

        BlockPos safePos = findSafeDestination(world, targetBlockX, player.getBlockY(), targetBlockZ);
        if (safePos == null) {
            BlockPos spawnPos = world.getSpawnPos();
            safePos = findSafeDestination(world, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        }
        if (safePos == null) {
            return;
        }

        player.teleport(world, safePos.getX() + 0.5D, safePos.getY(), safePos.getZ() + 0.5D, Set.<PositionFlag>of(), player.getYaw(), player.getPitch(), false);
        maybeSendMessage(player);
    }

    private static AreaBounds findNearestArea(double playerX, double playerZ, List<AllowedGenerationArea> areas) {
        AreaBounds nearestArea = null;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (AllowedGenerationArea area : areas) {
            AreaBounds bounds = AreaBounds.from(area);
            double clampedX = MathHelper.clamp(playerX, bounds.minBlockX(), bounds.maxBlockX());
            double clampedZ = MathHelper.clamp(playerZ, bounds.minBlockZ(), bounds.maxBlockZ());
            double deltaX = playerX - clampedX;
            double deltaZ = playerZ - clampedZ;
            double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;

            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearestArea = bounds;
            }
        }

        return nearestArea;
    }

    private static BlockPos findSafeDestination(ServerWorld world, int blockX, int preferredY, int blockZ) {
        if (isSafeStandingPos(world, blockX, preferredY, blockZ)) {
            return new BlockPos(blockX, preferredY, blockZ);
        }

        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        if (isSafeStandingPos(world, blockX, surfaceY, blockZ)) {
            return new BlockPos(blockX, surfaceY, blockZ);
        }

        int bottomY = world.getBottomY();
        int minY = Math.max(bottomY, surfaceY - 8);
        int maxY = surfaceY + 8;
        for (int y = minY; y <= maxY; y++) {
            if (isSafeStandingPos(world, blockX, y, blockZ)) {
                return new BlockPos(blockX, y, blockZ);
            }
        }

        return null;
    }

    private static boolean isSafeStandingPos(ServerWorld world, int blockX, int blockY, int blockZ) {
        if (blockY <= world.getBottomY()) {
            return false;
        }

        BlockPos feetPos = new BlockPos(blockX, blockY, blockZ);
        BlockPos headPos = feetPos.up();
        BlockPos belowPos = feetPos.down();

        if (!world.getBlockState(feetPos).isAir()) {
            return false;
        }
        if (!world.getBlockState(headPos).isAir()) {
            return false;
        }

        return world.getBlockState(belowPos).blocksMovement();
    }

    private static void maybeSendMessage(ServerPlayerEntity player) {
        int currentTick = player.age;
        int nextAllowedTick = nextMessageTickByPlayer.getOrDefault(player.getUuid(), 0);
        if (currentTick < nextAllowedTick) {
            return;
        }

        nextMessageTickByPlayer.put(player.getUuid(), currentTick + MESSAGE_COOLDOWN_TICKS);
        player.sendMessage(EXPLORATION_DISABLED_MESSAGE, true);
    }

    private record AreaBounds(int minBlockX, int maxBlockX, int minBlockZ, int maxBlockZ) {
        private static AreaBounds from(AllowedGenerationArea area) {
            int centerChunkX = Math.floorDiv(area.centerX(), 16);
            int centerChunkZ = Math.floorDiv(area.centerZ(), 16);

            int halfBefore = area.size() / 2;
            int halfAfter = area.size() - halfBefore - 1;

            int minChunkX = centerChunkX - halfBefore;
            int maxChunkX = centerChunkX + halfAfter;
            int minChunkZ = centerChunkZ - halfBefore;
            int maxChunkZ = centerChunkZ + halfAfter;

            return new AreaBounds(
                minChunkX * 16,
                maxChunkX * 16 + 15,
                minChunkZ * 16,
                maxChunkZ * 16 + 15
            );
        }
    }
}
