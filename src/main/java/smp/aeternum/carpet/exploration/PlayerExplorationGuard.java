package smp.aeternum.carpet.exploration;

import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.config.AeternumCarpetConfig;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerExplorationGuard {
    private static final Text EXPLORATION_DISABLED_MESSAGE = Text.literal("Zona no permitida. Si quieres ir a este lugar, pide a los administradores que permitan el uso de esta zona.");
    private static final int MESSAGE_COOLDOWN_TICKS = 100;

    private static final Map<UUID, Integer> nextMessageTickByPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, LastValidPosition> lastValidPositionByPlayer = new ConcurrentHashMap<>();
    private static final Set<String> missingDimensionsWarningLogged = ConcurrentHashMap.newKeySet();

    private PlayerExplorationGuard() {
    }

    public static void tick(MinecraftServer server) {
        if (AeternumCarpetSettings.explorationManager || AeternumCarpetSettings.allowPlayerExploration) {
            missingDimensionsWarningLogged.clear();
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            handlePlayer(player);
        }
    }

    private static void handlePlayer(ServerPlayerEntity player) {
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        // A safe position only authorizes movement inside the dimension where it was earned.
        // Crossing a portal must never pull a player back using an old world's coordinates.
        LastValidPosition remembered = lastValidPositionByPlayer.get(player.getUuid());
        if (remembered != null && !remembered.dimension().equals(dimension)) {
            lastValidPositionByPlayer.remove(player.getUuid());
        }
        if (AeternumCarpetConfig.findAreasByDimension(dimension).isEmpty()) {
            if (missingDimensionsWarningLogged.add(dimension)) {
                AeternumCarpet.LOGGER.warn("allowPlayerExploration is false but no allowedGenerationAreas are configured for dimension {}. Exploration guard is disabled in that dimension until at least one area exists.", dimension);
            }
            return;
        }

        int chunkX = Math.floorDiv(player.getBlockX(), 16);
        int chunkZ = Math.floorDiv(player.getBlockZ(), 16);
        if (AeternumCarpetConfig.isChunkInsideAllowedGenerationArea(dimension, chunkX, chunkZ)) {
            rememberLastValidPosition(player, dimension);
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        LastValidPosition lastValidPosition = lastValidPositionByPlayer.get(player.getUuid());
        if (teleportToLastValidPosition(player, world, dimension, lastValidPosition)) {
            maybeSendMessage(player);
        }
        return;
    }

    private static void rememberLastValidPosition(ServerPlayerEntity player, String dimension) {
        rememberLastValidPosition(player, dimension, player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
    }

    private static void rememberLastValidPosition(ServerPlayerEntity player, String dimension, double x, double y, double z, float yaw, float pitch) {
        lastValidPositionByPlayer.put(player.getUuid(), new LastValidPosition(dimension, x, y, z, yaw, pitch));
    }

    private static boolean teleportToLastValidPosition(ServerPlayerEntity player, ServerWorld world, String dimension, LastValidPosition lastValidPosition) {
        if (lastValidPosition == null || !lastValidPosition.dimension().equals(dimension)) {
            return false;
        }

        BlockPos lastPos = BlockPos.ofFloored(lastValidPosition.x(), lastValidPosition.y(), lastValidPosition.z());
        int chunkX = Math.floorDiv(lastPos.getX(), 16);
        int chunkZ = Math.floorDiv(lastPos.getZ(), 16);
        if (!AeternumCarpetConfig.isChunkInsideAllowedGenerationArea(dimension, chunkX, chunkZ)) {
            return false;
        }

        player.teleport(world, lastValidPosition.x(), lastValidPosition.y(), lastValidPosition.z(), Set.<PositionFlag>of(), lastValidPosition.yaw(), lastValidPosition.pitch(), false);
        return true;
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

    private record LastValidPosition(String dimension, double x, double y, double z, float yaw, float pitch) {
    }
}
