package smp.aeternum.carpet.bot;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.helpers.EntityPlayerActionPack.Action;
import carpet.helpers.EntityPlayerActionPack.ActionType;
import carpet.patches.EntityPlayerMPFake;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;

/** Persists active Carpet fake players and their ActionPack states across server restarts. */
public final class FakePlayerReloadManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<SavedBot>>() {}.getType();
    private static final int RELOAD_DELAY_TICKS = 20;
    private static final int SAVE_DEBOUNCE_TICKS = 10;

    private static final Field ACTIONS_FIELD = field(EntityPlayerActionPack.class, "actions");
    private static final Field SNEAKING_FIELD = field(EntityPlayerActionPack.class, "sneaking");
    private static final Field SPRINTING_FIELD = field(EntityPlayerActionPack.class, "sprinting");
    private static final Field FORWARD_FIELD = field(EntityPlayerActionPack.class, "forward");
    private static final Field STRAFING_FIELD = field(EntityPlayerActionPack.class, "strafing");
    private static final Field CONTINUOUS_FIELD = field(Action.class, "isContinuous");

    private static Path file;
    private static List<SavedBot> pendingReload = List.of();
    private static int reloadTicksRemaining;
    private static int saveDebounceTicks;
    private static String lastSavedJson = "";

    private FakePlayerReloadManager() {}

    private static Field field(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            AeternumCarpet.LOGGER.error("Failed to reflect {}.{}", owner.getSimpleName(), name, e);
            return null;
        }
    }

    public static void load(MinecraftServer server) {
        file = server.getSavePath(WorldSavePathAccess.root()).resolve("data").resolve("aeternum_fake_players.json");
        pendingReload = List.of();
        reloadTicksRemaining = 0;
        saveDebounceTicks = 0;
        lastSavedJson = "";
        if (Files.isRegularFile(file)) {
            try {
                String raw = Files.readString(file);
                lastSavedJson = raw;
                List<SavedBot> loaded = GSON.fromJson(raw, LIST_TYPE);
                if (loaded != null && !loaded.isEmpty()) {
                    pendingReload = loaded;
                    reloadTicksRemaining = RELOAD_DELAY_TICKS;
                }
            } catch (Exception e) {
                AeternumCarpet.LOGGER.error("Could not load saved fake players", e);
            }
        }
    }

    public static void markDirty() {
        if (AeternumCarpetSettings.reloadFakePlayers && reloadTicksRemaining <= 0) {
            saveDebounceTicks = SAVE_DEBOUNCE_TICKS;
        }
    }

    public static void tick(MinecraftServer server) {
        if (reloadTicksRemaining > 0) {
            reloadTicksRemaining--;
            if (reloadTicksRemaining == 0) {
                List<SavedBot> toSpawn = pendingReload;
                pendingReload = List.of();
                if (AeternumCarpetSettings.reloadFakePlayers && !toSpawn.isEmpty()) {
                    respawnBots(server, toSpawn);
                    saveDebounceTicks = SAVE_DEBOUNCE_TICKS;
                }
            }
            return;
        }

        if (saveDebounceTicks > 0 && --saveDebounceTicks == 0) {
            if (AeternumCarpetSettings.reloadFakePlayers) {
                saveCurrentBots(server);
            }
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        if (AeternumCarpetSettings.reloadFakePlayers && reloadTicksRemaining <= 0) {
            saveCurrentBots(server);
        }
    }

    private static void respawnBots(MinecraftServer server, List<SavedBot> bots) {
        for (SavedBot bot : bots) {
            if (bot.name == null || bot.name.isBlank()) continue;
            if (server.getPlayerManager().getPlayer(bot.name) != null) continue;
            Identifier dimId = bot.dimension == null ? null : Identifier.tryParse(bot.dimension);
            RegistryKey<World> dimKey = dimId == null ? World.OVERWORLD : RegistryKey.of(RegistryKeys.WORLD, dimId);
            if (server.getWorld(dimKey) == null) dimKey = World.OVERWORLD;
            GameMode mode = bot.gameMode == null ? GameMode.SURVIVAL : GameMode.byId(bot.gameMode, GameMode.SURVIVAL);

            boolean spawned = EntityPlayerMPFake.createFake(
                bot.name, server, new Vec3d(bot.x, bot.y, bot.z), bot.yaw, bot.pitch, dimKey, mode, bot.flying
            );
            if (!spawned) continue;

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(bot.name);
            if (player == null) continue;
            if (bot.selectedSlot >= 0 && bot.selectedSlot < 9) {
                player.getInventory().setSelectedSlot(bot.selectedSlot);
            }
            applyActionPack(player, bot);
        }
    }

    @SuppressWarnings("unchecked")
    private static void saveCurrentBots(MinecraftServer server) {
        if (file == null) return;
        List<SavedBot> snapshot = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!(p instanceof EntityPlayerMPFake bot)) continue;
            SavedBot entry = new SavedBot();
            entry.name = bot.getName().getString();
            entry.dimension = bot.getWorld().getRegistryKey().getValue().toString();
            entry.x = bot.getX();
            entry.y = bot.getY();
            entry.z = bot.getZ();
            entry.yaw = bot.getYaw();
            entry.pitch = bot.getPitch();
            entry.gameMode = bot.interactionManager.getGameMode().getId();
            entry.flying = bot.getAbilities().flying;
            entry.selectedSlot = bot.getInventory().getSelectedSlot();

            EntityPlayerActionPack pack = ((ServerPlayerInterface) bot).getActionPack();
            entry.sneaking = readBool(SNEAKING_FIELD, pack);
            entry.sprinting = readBool(SPRINTING_FIELD, pack);
            entry.forward = readFloat(FORWARD_FIELD, pack);
            entry.strafing = readFloat(STRAFING_FIELD, pack);

            if (ACTIONS_FIELD != null) {
                try {
                    Map<ActionType, Action> map = (Map<ActionType, Action>) ACTIONS_FIELD.get(pack);
                    if (map != null) {
                        for (var e : map.entrySet()) {
                            Action act = e.getValue();
                            if (act == null || act.done) continue;
                            boolean continuous = readBool(CONTINUOUS_FIELD, act);
                            if (continuous || act.interval > 1) {
                                entry.actions.put(e.getKey().name(), new SavedAction(continuous, act.interval));
                            }
                        }
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
            snapshot.add(entry);
        }

        String json = GSON.toJson(snapshot);
        if (json.equals(lastSavedJson)) return;
        lastSavedJson = json;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json);
        } catch (IOException e) {
            AeternumCarpet.LOGGER.error("Could not save fake players", e);
        }
    }

    private static void applyActionPack(ServerPlayerEntity player, SavedBot bot) {
        EntityPlayerActionPack pack = ((ServerPlayerInterface) player).getActionPack();
        if (bot.sneaking) pack.setSneaking(true);
        if (bot.sprinting) pack.setSprinting(true);
        if (bot.forward != 0f) pack.setForward(bot.forward);
        if (bot.strafing != 0f) pack.setStrafing(bot.strafing);
        if (bot.actions != null) {
            for (var e : bot.actions.entrySet()) {
                try {
                    ActionType type = ActionType.valueOf(e.getKey());
                    SavedAction sa = e.getValue();
                    if (sa.continuous) {
                        pack.start(type, Action.continuous());
                    } else if (sa.interval > 0) {
                        pack.start(type, Action.interval(sa.interval));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private static boolean readBool(Field f, Object target) {
        if (f == null || target == null) return false;
        try { return f.getBoolean(target); } catch (IllegalAccessException e) { return false; }
    }

    private static float readFloat(Field f, Object target) {
        if (f == null || target == null) return 0f;
        try { return f.getFloat(target); } catch (IllegalAccessException e) { return 0f; }
    }

    private static final class WorldSavePathAccess {
        static net.minecraft.util.WorldSavePath root() {
            return net.minecraft.util.WorldSavePath.ROOT;
        }
    }

    public static final class SavedBot {
        public String name;
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;
        public String gameMode;
        public boolean flying;
        public int selectedSlot;
        public boolean sneaking;
        public boolean sprinting;
        public float forward;
        public float strafing;
        public Map<String, SavedAction> actions = new LinkedHashMap<>();
    }

    public static final class SavedAction {
        public boolean continuous;
        public int interval;

        public SavedAction() {}

        public SavedAction(boolean continuous, int interval) {
            this.continuous = continuous;
            this.interval = interval;
        }
    }
}
