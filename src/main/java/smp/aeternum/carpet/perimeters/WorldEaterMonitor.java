package smp.aeternum.carpet.perimeters;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.network.PerimeterNetworking;

/** Server-tick World Eater monitor: tracks chunk load state, TNT explosion Y levels, and stalls in real game ticks. */
public final class WorldEaterMonitor {
    private static final Gson GSON = new Gson();
    private static final Map<String, MonitorState> STATES = new HashMap<>();
    private static int syncCounter;

    private WorldEaterMonitor() {}

    public static void clearAll() {
        STATES.clear();
        syncCounter = 0;
    }

    public static void onToggle(MinecraftServer server, Perimeter p, boolean enabled) {
        p.weEnabled = enabled;
        MonitorState st = STATES.computeIfAbsent(p.id, k -> new MonitorState());
        st.reset();
        p.weHint = "";
        if (!enabled) {
            p.weStatus = "IDLE";
            emitLog("stopped", p);
        } else {
            p.weStatus = "RUNNING";
            emitLog("started", p);
        }
        PerimeterStore.save();
        PerimeterNetworking.broadcastAll(server);
    }

    public static void onConfigUpdated(Perimeter p) {
        MonitorState st = STATES.get(p.id);
        if (st != null) {
            st.lowYNotified = false;
            st.stalledNotified = false;
        }
    }

    public static void tick(MinecraftServer server) {
        if (!AeternumCarpetSettings.commandPerimeters) return;
        boolean frozen = server.getTickManager().isFrozen();
        boolean anyTransition = false;
        boolean anyActive = false;

        for (Perimeter p : PerimeterStore.all()) {
            if (!p.weEnabled) continue;
            anyActive = true;
            MonitorState st = STATES.computeIfAbsent(p.id, k -> new MonitorState());

            Identifier dimId = Identifier.tryParse(p.dimension);
            ServerWorld world = dimId != null ? server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId)) : null;
            if (world == null) continue;

            // Update chunk load percentage once per second
            if (++st.chunkCheckCooldown >= 20 || st.lastLoadedPct < 0) {
                st.chunkCheckCooldown = 0;
                st.lastLoadedPct = computeLoadedPct(world, p);
                p.weLoadedPct = st.lastLoadedPct;
                p.weActiveBots = BotGridManager.countActiveGridBots(server, p);
            }

            if (p.weLoadedPct <= 0) {
                if (!"UNLOADED".equals(p.weStatus)) {
                    p.weStatus = "UNLOADED";
                    anyTransition = true;
                    if (!st.unloadedNotified && st.hasSeenExplosion) {
                        st.unloadedNotified = true;
                        emitLog("unloaded", p);
                    }
                }
                continue;
            }
            st.unloadedNotified = false;

            // Check TNT entities inside perimeter bounds (covering full vertical range)
            Box box = new Box(
                p.minX - 4.0, world.getBottomY(), p.minZ - 4.0,
                p.maxX + 5.0, world.getTopYInclusive() + 1.0, p.maxZ + 5.0
            );
            List<TntEntity> tnts = world.getEntitiesByType(EntityType.TNT, box, t -> !t.isRemoved());
            boolean sawExplosion = false;
            int lowestTntY = Integer.MAX_VALUE;
            BlockPos lastPos = null;
            for (TntEntity tnt : tnts) {
                if (tnt.getFuse() <= 2) {
                    sawExplosion = true;
                    int y = (int) Math.floor(tnt.getY());
                    if (y < lowestTntY) {
                        lowestTntY = y;
                        lastPos = tnt.getBlockPos();
                    }
                }
            }

            if (sawExplosion) {
                boolean firstExplosion = !st.hasSeenExplosion;
                st.hasSeenExplosion = true;
                st.ticksSinceExplosion = 0;
                st.lastTntPos = lastPos;
                p.weCurrentTntY = lowestTntY;
                p.weHint = "";

                if (st.stalledNotified) {
                    st.stalledNotified = false;
                    p.weStatus = lowestTntY <= p.effectiveTargetLowY() ? "LOW_Y" : "RUNNING";
                    emitLog("resumed", p);
                    anyTransition = true;
                } else if (firstExplosion) {
                    p.weStatus = lowestTntY <= p.effectiveTargetLowY() ? "LOW_Y" : "RUNNING";
                    emitLog("active", p);
                    if (lowestTntY <= p.effectiveTargetLowY() && !st.lowYNotified) {
                        st.lowYNotified = true;
                        emitLog("low_y", p);
                    }
                    anyTransition = true;
                } else if (lowestTntY <= p.effectiveTargetLowY()) {
                    if (!"LOW_Y".equals(p.weStatus)) {
                        p.weStatus = "LOW_Y";
                        anyTransition = true;
                    }
                    if (!st.lowYNotified) {
                        st.lowYNotified = true;
                        emitLog("low_y", p);
                        anyTransition = true;
                    }
                } else if (!"RUNNING".equals(p.weStatus)) {
                    p.weStatus = "RUNNING";
                    anyTransition = true;
                }
            } else if (!frozen) {
                st.ticksSinceExplosion++;
                if (!st.hasSeenExplosion) {
                    if (!"RUNNING".equals(p.weStatus)) {
                        p.weStatus = "RUNNING";
                        anyTransition = true;
                    }
                } else {
                    int timeoutTicks = Math.max(5, p.weStallSeconds) * 20;
                    if (st.ticksSinceExplosion >= timeoutTicks && !st.stalledNotified) {
                        st.stalledNotified = true;
                        p.weStatus = "STALLED";
                        p.weHint = findNearestClusterHint(p, st.lastTntPos);
                        emitLog("stalled", p);
                        anyTransition = true;
                    }
                }
            }
        }

        if (anyTransition) {
            syncCounter = 0;
            PerimeterNetworking.broadcastAll(server);
        } else if (anyActive && ++syncCounter >= 40) {
            syncCounter = 0;
            PerimeterNetworking.broadcastHud(server);
        }
    }

    private static int computeLoadedPct(ServerWorld world, Perimeter p) {
        int minCx = Math.floorDiv(p.minX, 16);
        int maxCx = Math.floorDiv(p.maxX, 16);
        int minCz = Math.floorDiv(p.minZ, 16);
        int maxCz = Math.floorDiv(p.maxZ, 16);
        int total = (maxCx - minCx + 1) * (maxCz - minCz + 1);
        if (total <= 0) return 0;
        int loaded = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (world.isChunkLoaded(cx, cz)) loaded++;
            }
        }
        return (loaded * 100) / total;
    }

    private static String findNearestClusterHint(Perimeter p, BlockPos lastTntPos) {
        if (p.clusters == null || p.clusters.isEmpty() || lastTntPos == null) return "";
        BlockCluster best = null;
        double bestDistSq = 2500.0; // within 50 blocks of last TNT
        for (BlockCluster c : p.clusters) {
            double dx = c.centerX - lastTntPos.getX();
            double dy = c.centerY - lastTntPos.getY();
            double dz = c.centerZ - lastTntPos.getZ();
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = c;
            }
        }
        if (best == null) return "";
        return best.blockId + " x" + best.count + " en [" + best.centerX + ", " + best.centerY + ", " + best.centerZ + "]";
    }

    private static void emitLog(String event, Perimeter p) {
        WeLogEvent evt = new WeLogEvent();
        evt.event = event;
        evt.id = p.id;
        evt.perimeter = p.name;
        evt.dimension = p.dimension;
        evt.status = p.weStatus;
        evt.tntY = p.weCurrentTntY == Integer.MIN_VALUE ? null : p.weCurrentTntY;
        evt.targetY = p.effectiveTargetLowY();
        evt.loadedPct = p.weLoadedPct;
        evt.activeBots = p.weActiveBots;
        evt.hint = p.weHint;
        evt.bounds = new int[]{p.minX, p.minZ, p.maxX, p.maxZ};
        AeternumCarpet.LOGGER.info("[AeternumCarpet] [WE] {}", GSON.toJson(evt));
    }

    private static final class MonitorState {
        int chunkCheckCooldown;
        int lastLoadedPct = -1;
        int ticksSinceExplosion;
        boolean hasSeenExplosion;
        boolean lowYNotified;
        boolean stalledNotified;
        boolean unloadedNotified;
        BlockPos lastTntPos;

        void reset() {
            chunkCheckCooldown = 0;
            lastLoadedPct = -1;
            ticksSinceExplosion = 0;
            hasSeenExplosion = false;
            lowYNotified = false;
            stalledNotified = false;
            unloadedNotified = false;
            lastTntPos = null;
        }
    }

    @SuppressWarnings("unused")
    private static final class WeLogEvent {
        public String event;
        public String id;
        public String perimeter;
        public String dimension;
        public String status;
        public Integer tntY;
        public int targetY;
        public int loadedPct;
        public int activeBots;
        public String hint;
        public int[] bounds;
    }
}
