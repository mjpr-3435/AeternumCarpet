package smp.aeternum.carpet.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** World-persistent, server-authoritative command automation catalog. */
public final class CommandPackStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<List<CommandPack>>() {}.getType();
    private static final int MAX_PACKS = 64, MAX_ACTIONS = 64, MAX_STEPS = 128, MAX_TEXT = 80, MAX_COMMAND = 2048;
    private static List<CommandPack> packs = new ArrayList<>();
    private static Path file;

    private CommandPackStore() { }

    public static void load(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_command_packs.json");
        packs = new ArrayList<>();
        try {
            if (Files.exists(file)) {
                List<CommandPack> decoded = GSON.fromJson(Files.readString(file), TYPE);
                packs = normalize(decoded);
            }
        } catch (Exception exception) { AeternumCarpet.LOGGER.error("Could not load command packs", exception); }
    }

    public static List<CommandPack> all() { return packs; }
    public static String snapshot() { return GSON.toJson(packs); }

    /** Validates every field before accepting client-provided editor state. */
    public static boolean replace(String json) {
        try {
            List<CommandPack> decoded = GSON.fromJson(json, TYPE);
            packs = normalize(decoded);
            save();
            return true;
        } catch (Exception exception) { return false; }
    }

    public static CommandPack get(String packId) {
        for (CommandPack pack : packs) if (pack.id.equals(packId)) return pack;
        return null;
    }

    private static List<CommandPack> normalize(List<CommandPack> input) {
        if (input == null || input.size() > MAX_PACKS) throw new IllegalArgumentException("Invalid pack count");
        List<CommandPack> result = new ArrayList<>(); Set<String> packIds = new HashSet<>();
        for (CommandPack pack : input) {
            if (pack == null) throw new IllegalArgumentException("Invalid pack");
            if (pack.commands == null) pack.commands = new ArrayList<>();
            // Migration from the short-lived package > action > step format.
            if (pack.commands.isEmpty() && pack.actions != null) for (CommandAction legacy : pack.actions)
                if (legacy != null && legacy.steps != null) pack.commands.addAll(legacy.steps);
            if (pack.commands.size() > MAX_STEPS) throw new IllegalArgumentException("Invalid command count");
            pack.id = uniqueId(pack.id, packIds); pack.name = text(pack.name, MAX_TEXT); pack.description = text(pack.description, 240);
            for (CommandStep step : pack.commands) normalizeStep(step);
            // Do not persist the retired nesting after the migration has completed.
            pack.actions = new ArrayList<>();
            /*
            Set<String> actionIds = new HashSet<>();
            for (CommandAction action : pack.actions) {
                if (action == null || action.steps == null || action.steps.size() > MAX_STEPS) throw new IllegalArgumentException("Invalid action");
                action.id = uniqueId(action.id, actionIds); action.name = text(action.name, MAX_TEXT); action.description = text(action.description, 240);
                for (CommandStep step : action.steps) {
                    if (step == null || step.type == null) throw new IllegalArgumentException("Invalid step");
                    step.value = text(step.value, step.type == CommandStep.Type.COMMAND ? MAX_COMMAND : 8);
                    if (step.type == CommandStep.Type.COMMAND && step.value.isBlank()) throw new IllegalArgumentException("Empty command");
                    if (step.type == CommandStep.Type.WAIT) {
                        int seconds = Integer.parseInt(step.value);
                        if (seconds < 0 || seconds > 3600) throw new IllegalArgumentException("Invalid wait");
                        step.value = Integer.toString(seconds);
                    }
                }
            }
            */
            result.add(pack);
        }
        return result;
    }
    private static void normalizeStep(CommandStep step) {
        if (step == null || step.type == null) throw new IllegalArgumentException("Invalid step");
        step.value = text(step.value, step.type == CommandStep.Type.COMMAND ? MAX_COMMAND : 8);
        if (step.type == CommandStep.Type.COMMAND && step.value.isBlank()) throw new IllegalArgumentException("Empty command");
        if (step.type == CommandStep.Type.WAIT) {
            int seconds = Integer.parseInt(step.value);
            if (seconds < 0 || seconds > 3600) throw new IllegalArgumentException("Invalid wait");
            step.value = Integer.toString(seconds);
        }
    }

    private static String uniqueId(String id, Set<String> used) {
        try { UUID.fromString(id); } catch (Exception exception) { id = UUID.randomUUID().toString(); }
        if (!used.add(id)) id = UUID.randomUUID().toString();
        used.add(id); return id;
    }
    private static String text(String value, int max) {
        if (value == null) return "";
        value = value.strip();
        if (value.length() > max || value.indexOf('\u0000') >= 0) throw new IllegalArgumentException("Text too long");
        return value;
    }
    private static void save() {
        try { Files.createDirectories(file.getParent()); Files.writeString(file, GSON.toJson(packs)); }
        catch (Exception exception) { AeternumCarpet.LOGGER.error("Could not save command packs", exception); }
    }
}
