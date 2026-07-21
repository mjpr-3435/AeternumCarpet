package smp.aeternum.carpet.protect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.block.ChestBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import smp.aeternum.carpet.AeternumCarpet;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Server-authoritative, persistent coordinates of OP-protected blocks. */
public final class ProtectedBlockStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ENTRIES = new TypeToken<List<Entry>>() {}.getType();
    private static final List<Entry> PROTECTED = new ArrayList<>();
    private static Path file;

    private ProtectedBlockStore() { }

    public static void load(MinecraftServer server) {
        PROTECTED.clear();
        file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_protected_blocks.json");
        if (!Files.exists(file)) return;
        try {
            List<Entry> loaded = GSON.fromJson(Files.readString(file), ENTRIES);
            if (loaded != null) PROTECTED.addAll(loaded);
        } catch (IOException | RuntimeException exception) {
            AeternumCarpet.LOGGER.error("Could not load protected blocks", exception);
        }
    }

    public static boolean add(World world, BlockPos pos) {
        if (isProtected(world, pos)) return false;
        PROTECTED.add(Entry.of(world, pos));
        save();
        return true;
    }

    public static boolean remove(World world, BlockPos pos) {
        boolean changed = PROTECTED.removeIf(entry -> entry.matches(world, pos));
        if (changed) save();
        return changed;
    }

    public static boolean isProtected(World world, BlockPos pos) {
        return PROTECTED.stream().anyMatch(entry -> entry.matches(world, pos));
    }

    /** Either half of a protected double chest is one protected inventory. */
    public static boolean isProtectedInventory(World world, BlockPos pos) {
        if (isProtected(world, pos)) return true;
        if (!(world.getBlockState(pos).getBlock() instanceof ChestBlock)) return false;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = pos.offset(direction);
            if (isProtected(world, neighbor) && world.getBlockState(neighbor).getBlock() instanceof ChestBlock) return true;
        }
        return false;
    }

    /** Prevent turning a protected single chest into a double chest from its unprotected half. */
    public static boolean wouldJoinProtectedChest(World world, BlockPos placement) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = placement.offset(direction);
            if (isProtected(world, neighbor) && world.getBlockState(neighbor).getBlock() instanceof ChestBlock) return true;
        }
        return false;
    }

    public static List<Entry> all() { return List.copyOf(PROTECTED); }

    private static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(PROTECTED), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AeternumCarpet.LOGGER.error("Could not save protected blocks", exception);
        }
    }

    public record Entry(String dimension, int x, int y, int z) {
        static Entry of(World world, BlockPos pos) { return new Entry(world.getRegistryKey().getValue().toString(), pos.getX(), pos.getY(), pos.getZ()); }
        boolean matches(World world, BlockPos pos) {
            return dimension.equals(world.getRegistryKey().getValue().toString()) && x == pos.getX() && y == pos.getY() && z == pos.getZ();
        }
    }
}
