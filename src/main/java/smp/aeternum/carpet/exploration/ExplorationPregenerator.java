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
import java.util.ArrayList;
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
        double factor = speedInfo().factor();
        workCredits = Math.min(1.0, workCredits + state.maxChunksPerMinute * factor / 1200.0);
        if (workCredits < 1.0) return;
        if (generateOne(server)) workCredits -= 1.0;
        if (++saveCooldown >= 200) save();
    }

    public static void start() { state.active = true; state.paused = false; state.completed = false; save(); }
    public static void pause() { state.active = false; state.paused = true; save(); }
    public static void stop() { state = new State(state.maxChunksPerMinute); workCredits = 0; save(); }
    public static void setSpeed(int chunksPerMinute) { state.maxChunksPerMinute = chunksPerMinute; save(); }

    public static List<String> statusLines() {
        String idle = idleReason();
        boolean sampled = MSPT_SAMPLES.size() >= REQUIRED_SAMPLES;
        boolean healthy = sampled && isHealthy();
        String salud = !sampled ? "analizando rendimiento " + MSPT_SAMPLES.size() + "/" + REQUIRED_SAMPLES
            : !healthy ? "pausado por MSPT"
            : idle != null ? "nada que generar" : "estable";
        String mode = !state.active ? (state.paused ? "Pausado" : "Detenido") : (idle != null ? "Pausado" : "Activo");
        String region = state.regionKey.isEmpty() ? "sin región actual" : state.regionKey;
        List<String> lines = new ArrayList<>(List.of(
            "Estado: " + mode,
            "Región: " + region,
            "Velocidad máxima: " + state.maxChunksPerMinute + " chunks/minuto",
            "Rendimiento: " + salud,
            "MSPT: " + msptSummary()));
        if (MSPT_SAMPLES.size() < REQUIRED_SAMPLES) {
            lines.add("Velocidad actual: analizando rendimiento");
        } else {
            SpeedInfo info = speedInfo();
            double realRate = state.maxChunksPerMinute * info.factor();
            lines.add("Velocidad actual: " + String.format(java.util.Locale.ROOT, "%.0f", realRate) + " chunks/minuto (x" + info.factor() + ", " + info.band() + ")");
        }
        if (sampled && !healthy) lines.add("Motivo de pausa: " + healthReason());
        else if (idle != null) lines.add("Motivo: " + idle);
        return lines;
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
        int minCX = area.minChunkX() - PADDING_CHUNKS, maxCX = area.maxChunkX() + PADDING_CHUNKS;
        int minCZ = area.minChunkZ() - PADDING_CHUNKS, maxCZ = area.maxChunkZ() + PADDING_CHUNKS;
        if (state.chunkX < minCX || state.chunkX > maxCX || state.chunkZ < minCZ || state.chunkZ > maxCZ) state.resetCursor(area, minCX, minCZ);
        if (nearPlayer(server, world, state.chunkX, state.chunkZ)) return false;
        // FULL shares Minecraft's normal chunk task if a player has already requested this chunk.
        world.getChunk(state.chunkX, state.chunkZ, ChunkStatus.FULL, true);
        state.completedChunks++;
        advanceChunk(area, minCX, maxCX, minCZ, maxCZ);
        return true;
    }

    private static AllowedGenerationArea resolveArea(List<AllowedGenerationArea> areas) {
        if (state.regionKey.isEmpty()) { state.areaIndex = 0; return areas.getFirst(); }
        for (int i = 0; i < areas.size(); i++) if (key(areas.get(i)).equals(state.regionKey)) { state.areaIndex = i; return areas.get(i); }
        state.areaIndex = 0; state.regionKey = ""; return areas.getFirst();
    }

    private static void advanceChunk(AllowedGenerationArea area, int minCX, int maxCX, int minCZ, int maxCZ) {
        if (++state.chunkZ <= maxCZ) return;
        state.chunkZ = minCZ;
        if (++state.chunkX <= maxCX) return;
        advanceRegion(AeternumCarpetConfig.getAllowedGenerationAreas(), area);
    }

    private static void advanceRegion(List<AllowedGenerationArea> areas, AllowedGenerationArea area) {
        int index = areas.indexOf(area);
        if (index + 1 >= areas.size()) { state.active = false; state.paused = true; state.completed = true; save(); return; }
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
        double max = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).max().orElse(50);
        return mean < 20 && max < 25;
    }

    private record SpeedInfo(double factor, String band) {}

    private static SpeedInfo speedInfo() {
        double mean = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).average().orElse(50);
        if (mean < 12) return new SpeedInfo(1.0, "umbral <12 mspt");
        if (mean < 16) return new SpeedInfo(0.5, "umbral 12–16 mspt");
        return new SpeedInfo(0.25, "umbral ≥16 mspt");
    }

    private static String msptSummary() {
        if (MSPT_SAMPLES.isEmpty()) return "sin muestras";
        double mean = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double min = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        return String.format(java.util.Locale.ROOT, "media %.1f (min %.1f / max %.1f)", mean, min, max);
    }

    private static String healthReason() {
        double mean = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).average().orElse(50);
        double max = MSPT_SAMPLES.stream().mapToDouble(Double::doubleValue).max().orElse(50);
        List<String> reasons = new ArrayList<>();
        if (mean >= 20) reasons.add(String.format(java.util.Locale.ROOT, "media %.1f >= 20", mean));
        if (max >= 25) reasons.add(String.format(java.util.Locale.ROOT, "max %.1f >= 25", max));
        return reasons.isEmpty() ? "desconocido" : String.join(", ", reasons);
    }

    private static String idleReason() {
        if (AeternumCarpetConfig.getAllowedGenerationAreas().isEmpty()) return "sin áreas de generación configuradas";
        if (state.completed) return "generación completada";
        return null;
    }

    private static void save() {
        saveCooldown = 0;
        try { Files.createDirectories(STATE_PATH.getParent()); Files.writeString(STATE_PATH, GSON.toJson(state.write())); }
        catch (IOException exception) { AeternumCarpet.LOGGER.error("Failed to save exploration pregeneration state", exception); }
    }

    private static String key(AllowedGenerationArea area) { return area.name() + "|" + area.normalizedDimension() + "|" + area.minChunkX() + "|" + area.minChunkZ() + "|" + area.maxChunkX() + "|" + area.maxChunkZ(); }

    private static final class State {
        boolean active, paused, completed; int maxChunksPerMinute = 60, areaIndex, chunkX, chunkZ; long completedChunks; String regionKey = "";
        State() {} State(int speed) { maxChunksPerMinute = speed; }
        void resetCursor() { regionKey = ""; chunkX = chunkZ = 0; }
        void resetCursor(AllowedGenerationArea area, int minCX, int minCZ) { regionKey = key(area); chunkX = minCX; chunkZ = minCZ; }
        void read(JsonObject json) { active=get(json,"active",false); paused=get(json,"paused",false); completed=get(json,"done",false); maxChunksPerMinute=get(json,"speed",60); areaIndex=get(json,"area",0); chunkX=get(json,"x",0); chunkZ=get(json,"z",0); completedChunks=get(json,"completed",0L); regionKey=get(json,"region",""); }
        JsonObject write() { JsonObject json=new JsonObject(); json.addProperty("active",active);json.addProperty("paused",paused);json.addProperty("done",completed);json.addProperty("speed",maxChunksPerMinute);json.addProperty("area",areaIndex);json.addProperty("x",chunkX);json.addProperty("z",chunkZ);json.addProperty("completed",completedChunks);json.addProperty("region",regionKey);return json; }
        private static boolean get(JsonObject j,String k,boolean d){return j.has(k)?j.get(k).getAsBoolean():d;} private static int get(JsonObject j,String k,int d){return j.has(k)?j.get(k).getAsInt():d;} private static long get(JsonObject j,String k,long d){return j.has(k)?j.get(k).getAsLong():d;} private static String get(JsonObject j,String k,String d){return j.has(k)?j.get(k).getAsString():d;}
    }
}
