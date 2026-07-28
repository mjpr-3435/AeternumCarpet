package smp.aeternum.carpet.exploration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.config.AeternumCarpetConfig;
import smp.aeternum.carpet.config.AllowedGenerationArea;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;

/** Low-impact, resumable pregeneration of exploration regions. */
public final class ExplorationPregenerator {
    private static final int PADDING_CHUNKS = 16;
    private static final int PLAYER_GUARD_CHUNKS = 8;
    private static final int SAMPLE_INTERVAL_TICKS = 20;
    private static final int REQUIRED_SAMPLES = 300; // five minutes, sampled once per second
    private static final Gson GSON = new Gson();
    private static final Path STATE_PATH = FabricLoader.getInstance().getConfigDir().resolve("aeternum-exploration-pregen.json");
    private static final ArrayDeque<Double> MSPT_SAMPLES = new ArrayDeque<>();
    private static State state = new State();
    private static int tick, saveCooldown;
    private static double workCredits;

    private ExplorationPregenerator() {}

    public static void load(MinecraftServer server) {
        state = new State();
        MSPT_SAMPLES.clear(); workCredits = 0; tick = 0; saveCooldown = 0;
        if (!Files.isRegularFile(STATE_PATH)) return;
        try {
            JsonObject json = GSON.fromJson(Files.readString(STATE_PATH), JsonObject.class);
            if (json != null) state.read(json);
        } catch (Exception exception) {
            AeternumCarpet.LOGGER.error("Failed to load exploration pregeneration state", exception);
        }
    }

    public static void tick(MinecraftServer server) {
        if (++tick % SAMPLE_INTERVAL_TICKS == 0) sample(server);
        if (!AeternumCarpetSettings.explorationManager) return;
        if (!state.active) return;
        if (!isHealthy()) return;
        double factor = speedFactor();
        workCredits = Math.min(1.0, workCredits + state.maxChunksPerMinute * factor / 1200.0);
        if (workCredits < 1.0) return;
        if (generateOne(server)) workCredits -= 1.0;
        if (++saveCooldown >= 200) save();
    }

    public static void start() { state.active = true; state.paused = false; save(); }
    public static void pause() { state.active = false; state.paused = true; save(); }
    public static void stop() { state = new State(state.maxChunksPerMinute); workCredits = 0; save(); }
    public static void setSpeed(int chunksPerMinute) { state.maxChunksPerMinute = chunksPerMinute; save(); }

    public static List<String> statusLines() {
        String health = MSPT_SAMPLES.size() < REQUIRED_SAMPLES ? "analizando rendimiento " + MSPT_SAMPLES.size() + "/" + REQUIRED_SAMPLES
            : isHealthy() ? "estable" : "pausado por MSPT";
        String mode = state.active ? "Activo" : state.paused ? "Pausado" : "Detenido";
        String region = state.regionKey.isEmpty() ? "sin región actual" : state.regionKey;
        return List.of("Estado: " + mode, "Región: " + region, "Velocidad máxima: " + state.maxChunksPerMinute + " chunks/minuto", "Rendimiento: " + health, "MSPT: " + msptSummary());
    }

    private static boolean generateOne(MinecraftServer server) {
        List<AllowedGenerationArea> areas = AeternumCarpetConfig.getAllowedGenerationAreas();
        if (areas.isEmpty()) return false;
        AllowedGenerationArea area = resolveArea(areas);
        if (area == null) { state.resetCursor(); return false; }
        ServerWorld world = null;
        for (ServerWorld candidate : server.getWorlds()) {
            if (candidate.getRegistryKey().getValue().toString().equals(area.normalizedDimension())) { world = candidate; break; }
        }
        if (world == null) { advanceRegion(areas, area); return false; }
        int radius = area.maxRadius() + PADDING_CHUNKS;
        if (state.chunkX < area.centerChunkX() - radius || state.chunkX > area.centerChunkX() + radius || state.chunkZ < area.centerChunkZ() - radius || state.chunkZ > area.centerChunkZ() + radius) state.resetCursor(area, radius);
        if (nearPlayer(server, world, state.chunkX, state.chunkZ)) return false;
        // FULL shares Minecraft's normal chunk task if a player has already requested this chunk.
        world.getChunk(state.chunkX, state.chunkZ, ChunkStatus.FULL, true);
        state.completedChunks++;
        advanceChunk(areas, area, radius);
        return true;
    }

    private static AllowedGenerationArea resolveArea(List<AllowedGenerationArea> areas) {
        if (state.regionKey.isEmpty()) { state.areaIndex = 0; return areas.getFirst(); }
        for (int i = 0; i < areas.size(); i++) if (key(areas.get(i)).equals(state.regionKey)) { state.areaIndex = i; return areas.get(i); }
        state.areaIndex = 0; state.regionKey = ""; return areas.getFirst();
    }

    private static void advanceChunk(List<AllowedGenerationArea> areas, AllowedGenerationArea area, int radius) {
        int maxX = area.centerChunkX() + radius, maxZ = area.centerChunkZ() + radius;
        if (++state.chunkZ <= maxZ) return;
        state.chunkZ = area.centerChunkZ() - radius;
        if (++state.chunkX <= maxX) return;
        advanceRegion(areas, area);
    }

    private static void advanceRegion(List<AllowedGenerationArea> areas, AllowedGenerationArea area) {
        int index = areas.indexOf(area);
        if (index + 1 >= areas.size()) { state.active = false; state.paused = true; save(); return; }
        state.areaIndex = index + 1; state.resetCursor();
    }

    private static boolean nearPlayer(MinecraftServer server, ServerWorld world, int chunkX, int chunkZ) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || player.getWorld() != world) continue;
            ChunkPos pos = player.getChunkPos();
            if (Math.abs(pos.x - chunkX) <= PLAYER_GUARD_CHUNKS && Math.abs(pos.z - chunkZ) <= PLAYER_GUARD_CHUNKS) return true;
        }
        return false;
    }

    private static void sample(MinecraftServer server) {
        MSPT_SAMPLES.addLast((double) server.getAverageTickTime());
        while (MSPT_SAMPLES.size() > REQUIRED_SAMPLES) MSPT_SAMPLES.removeFirst();
    }

    private static boolean isHealthy() {
        if (MSPT_SAMPLES.size() < REQUIRED_SAMPLES) return false;
        double mean = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).average().orElse(50);
        double min = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).min().orElse(50);
        double max = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).max().orElse(50);
        return mean < 20 && max < 25 && max - min <= 6;
    }

    private static double speedFactor() {
        double mean = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).average().orElse(50);
        if (mean < 12) return 1.0;
        if (mean < 16) return 0.5;
        return 0.25;
    }

    private static String msptSummary() {
        if (MSPT_SAMPLES.isEmpty()) return "sin muestras";
        double mean = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return String.format(java.util.Locale.ROOT, "MSPT %.1f", mean);
    }

    private static void save() {
        saveCooldown = 0;
        try { Files.createDirectories(STATE_PATH.getParent()); Files.writeString(STATE_PATH, GSON.toJson(state.write())); }
        catch (IOException exception) { AeternumCarpet.LOGGER.error("Failed to save exploration pregeneration state", exception); }
    }

    private static String key(AllowedGenerationArea area) { return area.name() + "|" + area.normalizedDimension() + "|" + area.minChunkX() + "|" + area.minChunkZ() + "|" + area.maxChunkX() + "|" + area.maxChunkZ(); }

    private static final class State {
        boolean active, paused; int maxChunksPerMinute = 60, areaIndex, chunkX, chunkZ; long completedChunks; String regionKey = "";
        State() {} State(int speed) { maxChunksPerMinute = speed; }
        void resetCursor() { regionKey = ""; chunkX = chunkZ = 0; }
        void resetCursor(AllowedGenerationArea area, int radius) { regionKey = key(area); chunkX = area.centerChunkX() - radius; chunkZ = area.centerChunkZ() - radius; }
        void read(JsonObject json) { active=get(json,"active",false); paused=get(json,"paused",false); maxChunksPerMinute=get(json,"speed",60); areaIndex=get(json,"area",0); chunkX=get(json,"x",0); chunkZ=get(json,"z",0); completedChunks=get(json,"completed",0L); regionKey=get(json,"region",""); }
        JsonObject write() { JsonObject json=new JsonObject(); json.addProperty("active",active);json.addProperty("paused",paused);json.addProperty("speed",maxChunksPerMinute);json.addProperty("area",areaIndex);json.addProperty("x",chunkX);json.addProperty("z",chunkZ);json.addProperty("completed",completedChunks);json.addProperty("region",regionKey);return json; }
        private static boolean get(JsonObject j,String k,boolean d){return j.has(k)?j.get(k).getAsBoolean():d;} private static int get(JsonObject j,String k,int d){return j.has(k)?j.get(k).getAsInt():d;} private static long get(JsonObject j,String k,long d){return j.has(k)?j.get(k).getAsLong():d;} private static String get(JsonObject j,String k,String d){return j.has(k)?j.get(k).getAsString():d;}
    }
}
