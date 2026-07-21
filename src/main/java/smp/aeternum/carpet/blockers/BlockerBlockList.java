package smp.aeternum.carpet.blockers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** World-persistent explicit explosion-resistant blocker list. */
public final class BlockerBlockList {
    private static final Set<String> DEFAULTS = Set.of(
        "minecraft:obsidian",
        "minecraft:crying_obsidian",
        "minecraft:ender_chest",
        "minecraft:bedrock",
        "minecraft:reinforced_deepslate",
        "minecraft:end_portal_frame",
        "minecraft:end_portal"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST = new TypeToken<LinkedHashSet<String>>() {}.getType();
    private static final Set<String> BLOCKS = new LinkedHashSet<>();
    private static Path file;

    private BlockerBlockList() { }

    public static void load(MinecraftServer server) {
        BLOCKS.clear();
        file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_blocker_block_list.json");
        if (!Files.exists(file)) {
            BLOCKS.addAll(DEFAULTS);
            save();
            return;
        }
        try {
            Set<String> loaded = GSON.fromJson(Files.readString(file), LIST);
            if (loaded != null) BLOCKS.addAll(loaded);
        } catch (IOException | RuntimeException exception) {
            AeternumCarpet.LOGGER.error("Could not load blocker block list", exception);
        }
    }

    public static boolean contains(String blockId) { return BLOCKS.contains(blockId); }
    public static boolean add(String blockId) { boolean changed = BLOCKS.add(blockId); if (changed) save(); return changed; }
    public static boolean remove(String blockId) { boolean changed = BLOCKS.remove(blockId); if (changed) save(); return changed; }
    public static Set<String> all() { return Set.copyOf(BLOCKS); }

    private static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(BLOCKS), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Could not save blocker block list", exception);
        }
    }
}
