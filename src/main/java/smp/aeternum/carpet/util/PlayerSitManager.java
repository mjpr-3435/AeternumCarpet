package smp.aeternum.carpet.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.mixin.ArmorStandEntityAccessor;

/** Handles playerSit (3 quick sneaks to sit) and orphaned sit armor stand cleanup. */
public final class PlayerSitManager {
    public static final String SIT_TAG = "aeternum_sit";
    private static final int SNEAK_WINDOW_TICKS = 15;
    private static final int REQUIRED_SNEAKS = 3;

    private static final Map<UUID, SneakState> SNEAK_STATES = new HashMap<>();
    private static final List<ArmorStandEntity> ACTIVE_SEATS = new ArrayList<>();

    private PlayerSitManager() {}

    public static void onSneakChange(ServerPlayerEntity player, boolean sneaking) {
        if (!AeternumCarpetSettings.playerSit) {
            SNEAK_STATES.remove(player.getUuid());
            return;
        }
        SneakState state = SNEAK_STATES.computeIfAbsent(player.getUuid(), k -> new SneakState());
        if (sneaking == state.lastSneaking) {
            return;
        }
        state.lastSneaking = sneaking;
        if (!sneaking) {
            return;
        }
        if (player.isSpectator() || player.hasVehicle() || !player.isOnGround()) {
            state.count = 0;
            return;
        }

        int now = player.age;
        if (now - state.lastPressTick <= SNEAK_WINDOW_TICKS) {
            state.count++;
        } else {
            state.count = 1;
        }
        state.lastPressTick = now;

        if (state.count >= REQUIRED_SNEAKS) {
            state.count = 0;
            sit(player);
        }
    }

    private static void sit(ServerPlayerEntity player) {
        ServerWorld world = player.getWorld();
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.refreshPositionAndAngles(player.getX(), player.getY() + 0.05, player.getZ(), player.getYaw(), 0f);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setNoGravity(true);
        ((ArmorStandEntityAccessor) stand).aeternum$setMarker(true);
        stand.setSilent(true);
        stand.addCommandTag(SIT_TAG);

        if (world.spawnEntity(stand)) {
            ACTIVE_SEATS.add(stand);
            player.startRiding(stand, true);
        }
    }

    public static void onPlayerTick(ServerPlayerEntity player) {
        if (player.getVehicle() instanceof ArmorStandEntity stand && stand.getCommandTags().contains(SIT_TAG)) {
            stand.setYaw(player.getYaw());
        }
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<ArmorStandEntity> it = ACTIVE_SEATS.iterator();
        while (it.hasNext()) {
            ArmorStandEntity stand = it.next();
            if (stand.isRemoved() || !stand.hasPassengers()) {
                if (!stand.isRemoved()) {
                    stand.discard();
                }
                it.remove();
            }
        }
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        SNEAK_STATES.remove(player.getUuid());
        if (player.getVehicle() instanceof ArmorStandEntity stand && stand.getCommandTags().contains(SIT_TAG)) {
            player.stopRiding();
            stand.discard();
            ACTIVE_SEATS.remove(stand);
        }
    }

    public static void onServerStopping() {
        for (ArmorStandEntity stand : ACTIVE_SEATS) {
            if (!stand.isRemoved()) {
                stand.discard();
            }
        }
        ACTIVE_SEATS.clear();
        SNEAK_STATES.clear();
    }

    public static int cleanOrphanedArmorStands(MinecraftServer server) {
        int removed = 0;
        for (ServerWorld world : server.getWorlds()) {
            List<ArmorStandEntity> toRemove = new ArrayList<>();
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof ArmorStandEntity stand && !stand.hasPassengers()) {
                    if (stand.getCommandTags().contains(SIT_TAG) || stand.getCommandTags().contains("Sit")) {
                        toRemove.add(stand);
                    }
                }
            }
            for (ArmorStandEntity stand : toRemove) {
                stand.discard();
                ACTIVE_SEATS.remove(stand);
                removed++;
            }
        }
        return removed;
    }

    private static final class SneakState {
        boolean lastSneaking;
        int lastPressTick = -1000;
        int count;
    }
}
