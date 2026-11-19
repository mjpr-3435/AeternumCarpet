package smp.aeternum.carpet.materials;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Parses .litematic files into subregions, material entries, and compact block templates. */
public final class LitematicParser {
    private static final Set<String> AIR_BLOCKS = Set.of(
        "minecraft:air",
        "minecraft:cave_air",
        "minecraft:void_air"
    );

    private LitematicParser() {
    }

    public static ParsedSchematic parse(Path file) throws IOException {
        return parse(Files.readAllBytes(file));
    }

    public static ParsedSchematic parse(byte[] bytes) throws IOException {
        NbtCompound root;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            root = NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
        }
        NbtCompound regionsNbt = root.getCompoundOrEmpty("Regions");
        if (regionsNbt.isEmpty()) {
            throw new IOException("El archivo .litematic no contiene subregiones.");
        }

        List<ParsedSubregion> subregions = new ArrayList<>();
        Map<String, Integer> globalCounts = new LinkedHashMap<>();
        Map<String, String> globalNames = new LinkedHashMap<>();
        int grandTotalBlocks = 0;

        for (String regionName : regionsNbt.getKeys()) {
            NbtCompound rData = regionsNbt.getCompoundOrEmpty(regionName);
            NbtCompound sizeNbt = rData.getCompoundOrEmpty("Size");
            NbtCompound posNbt = rData.getCompoundOrEmpty("Position");

            int sx = sizeNbt.getInt("x", 0);
            int sy = sizeNbt.getInt("y", 0);
            int sz = sizeNbt.getInt("z", 0);
            int dx = Math.abs(sx);
            int dy = Math.abs(sy);
            int dz = Math.abs(sz);
            long volLong = (long) dx * dy * dz;
            if (dx == 0 || dy == 0 || dz == 0 || volLong > 20_000_000L) continue;
            int vol = (int) volLong;

            int px = posNbt.getInt("x", 0);
            int py = posNbt.getInt("y", 0);
            int pz = posNbt.getInt("z", 0);
            int minRelX = sx >= 0 ? px : px + sx + 1;
            int minRelY = sy >= 0 ? py : py + sy + 1;
            int minRelZ = sz >= 0 ? pz : pz + sz + 1;

            NbtList paletteNbt = rData.getListOrEmpty("BlockStatePalette");
            if (paletteNbt.isEmpty()) continue;

            List<String> paletteBlockIds = new ArrayList<>(paletteNbt.size());
            List<Block> paletteBlocks = new ArrayList<>(paletteNbt.size());
            List<String> paletteItemIds = new ArrayList<>(paletteNbt.size());
            List<String> paletteItemNames = new ArrayList<>(paletteNbt.size());

            for (int i = 0; i < paletteNbt.size(); i++) {
                NbtCompound entry = paletteNbt.getCompoundOrEmpty(i);
                String blockId = entry.getString("Name", "minecraft:air");
                paletteBlockIds.add(blockId);
                if (AIR_BLOCKS.contains(blockId)) {
                    paletteBlocks.add(Blocks.AIR);
                    paletteItemIds.add("");
                    paletteItemNames.add("");
                } else {
                    Identifier bid = Identifier.tryParse(blockId);
                    Block block = bid != null ? Registries.BLOCK.get(bid) : Blocks.AIR;
                    paletteBlocks.add(block);
                    String itemId = resolveItemId(blockId, block);
                    paletteItemIds.add(itemId);
                    paletteItemNames.add(resolveItemName(itemId, blockId));
                }
            }

            Map<String, Integer> regionCounts = new LinkedHashMap<>();
            Map<String, String> regionNames = new LinkedHashMap<>();
            List<TemplateBlock> templateBlocks = new ArrayList<>();
            int regionTotal = 0;

            if (paletteBlockIds.size() == 1) {
                String blockId = paletteBlockIds.get(0);
                if (!AIR_BLOCKS.contains(blockId)) {
                    String itemId = paletteItemIds.get(0);
                    String itemName = paletteItemNames.get(0);
                    Block block = paletteBlocks.get(0);
                    regionTotal = vol;
                    if (!itemId.isEmpty()) {
                        regionCounts.merge(itemId, vol, Integer::sum);
                        regionNames.putIfAbsent(itemId, itemName);
                    }
                    for (int y = 0; y < dy; y++) {
                        for (int z = 0; z < dz; z++) {
                            for (int x = 0; x < dx; x++) {
                                templateBlocks.add(new TemplateBlock(minRelX + x, minRelY + y, minRelZ + z, block, itemId));
                            }
                        }
                    }
                }
            } else {
                int bitsPerBlock = Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteBlockIds.size() - 1));
                long mask = (1L << bitsPerBlock) - 1L;
                long[] rawLongs = rData.getLongArray("BlockStates").orElse(new long[0]);

                for (int i = 0; i < vol; i++) {
                    int startBit = i * bitsPerBlock;
                    int startIdx = startBit >> 6;
                    int startOffset = startBit & 63;
                    int endBit = startBit + bitsPerBlock - 1;
                    int endIdx = endBit >> 6;

                    int val;
                    if (startIdx >= rawLongs.length) {
                        val = 0;
                    } else if (startIdx == endIdx) {
                        val = (int) ((rawLongs[startIdx] >>> startOffset) & mask);
                    } else {
                        long part1 = rawLongs[startIdx] >>> startOffset;
                        long part2 = endIdx < rawLongs.length ? (rawLongs[endIdx] << (64 - startOffset)) : 0L;
                        val = (int) ((part1 | part2) & mask);
                    }

                    if (val < 0 || val >= paletteBlockIds.size()) continue;
                    String blockId = paletteBlockIds.get(val);
                    if (AIR_BLOCKS.contains(blockId)) continue;

                    regionTotal++;
                    String itemId = paletteItemIds.get(val);
                    String itemName = paletteItemNames.get(val);
                    if (!itemId.isEmpty()) {
                        regionCounts.merge(itemId, 1, Integer::sum);
                        regionNames.putIfAbsent(itemId, itemName);
                    }

                    int y = i / (dx * dz);
                    int rem = i % (dx * dz);
                    int z = rem / dx;
                    int x = rem % dx;
                    templateBlocks.add(new TemplateBlock(minRelX + x, minRelY + y, minRelZ + z, paletteBlocks.get(val), itemId));
                }
            }

            if (regionTotal == 0) continue;

            grandTotalBlocks += regionTotal;
            for (var e : regionCounts.entrySet()) {
                globalCounts.merge(e.getKey(), e.getValue(), Integer::sum);
                globalNames.putIfAbsent(e.getKey(), regionNames.get(e.getKey()));
            }

            subregions.add(new ParsedSubregion(
                regionName,
                regionTotal,
                toEntries(regionCounts, regionNames),
                templateBlocks
            ));
        }

        subregions.sort(Comparator.comparing(s -> s.name, String.CASE_INSENSITIVE_ORDER));
        return new ParsedSchematic(
            grandTotalBlocks,
            toEntries(globalCounts, globalNames),
            subregions
        );
    }

    private static List<MaterialEntry> toEntries(Map<String, Integer> counts, Map<String, String> names) {
        List<MaterialEntry> entries = new ArrayList<>();
        for (var e : counts.entrySet()) {
            entries.add(new MaterialEntry(names.getOrDefault(e.getKey(), e.getKey()), e.getKey(), e.getValue(), 0));
        }
        entries.sort(Comparator.comparingInt((MaterialEntry m) -> m.total).reversed());
        return entries;
    }

    private static String resolveItemId(String blockId, Block block) {
        if ("minecraft:water".equals(blockId) || "minecraft:lava".equals(blockId)
            || "minecraft:bubble_column".equals(blockId) || "minecraft:fire".equals(blockId)
            || "minecraft:soul_fire".equals(blockId) || "minecraft:piston_head".equals(blockId)
            || "minecraft:moving_piston".equals(blockId) || "minecraft:nether_portal".equals(blockId)) {
            return "";
        }
        if (block != Blocks.AIR) {
            Item item = block.asItem();
            if (item != Items.AIR) {
                return Registries.ITEM.getId(item).toString();
            }
        }
        return "";
    }

    private static String resolveItemName(String itemId, String blockId) {
        if (!itemId.isEmpty()) {
            Identifier id = Identifier.tryParse(itemId);
            if (id != null) {
                Item item = Registries.ITEM.get(id);
                if (item != Items.AIR) {
                    return item.getName().getString();
                }
            }
        }
        String raw = blockId.contains(":") ? blockId.split(":", 2)[1] : blockId;
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static BlockPos transform(int rx, int ry, int rz, MaterialList.PlacementInfo placement) {
        BlockMirror mirror = parseMirror(placement.mirror);
        BlockRotation rotation = parseRotation(placement.rotation);

        int mx = switch (mirror) {
            case FRONT_BACK -> -rx;
            default -> rx;
        };
        int mz = switch (mirror) {
            case LEFT_RIGHT -> -rz;
            default -> rz;
        };

        int tx;
        int tz;
        switch (rotation) {
            case CLOCKWISE_90 -> { tx = -mz; tz = mx; }
            case CLOCKWISE_180 -> { tx = -mx; tz = -mz; }
            case COUNTERCLOCKWISE_90 -> { tx = mz; tz = -mx; }
            default -> { tx = mx; tz = mz; }
        }
        return new BlockPos(placement.x + tx, placement.y + ry, placement.z + tz);
    }

    public static BlockRotation parseRotation(String s) {
        if (s == null) return BlockRotation.NONE;
        return switch (s) {
            case "CLOCKWISE_90" -> BlockRotation.CLOCKWISE_90;
            case "CLOCKWISE_180" -> BlockRotation.CLOCKWISE_180;
            case "COUNTERCLOCKWISE_90" -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    public static BlockMirror parseMirror(String s) {
        if (s == null) return BlockMirror.NONE;
        return switch (s) {
            case "LEFT_RIGHT" -> BlockMirror.LEFT_RIGHT;
            case "FRONT_BACK" -> BlockMirror.FRONT_BACK;
            default -> BlockMirror.NONE;
        };
    }

    public record TemplateBlock(int relX, int relY, int relZ, Block expectedBlock, String itemId) {}

    public static final class ParsedSubregion {
        public final String name;
        public final int totalBlocks;
        public final List<MaterialEntry> entries;
        public final List<TemplateBlock> templateBlocks;
        public final BitSet matchedBits;
        public int cursor;

        public ParsedSubregion(String name, int totalBlocks, List<MaterialEntry> entries, List<TemplateBlock> templateBlocks) {
            this.name = name;
            this.totalBlocks = totalBlocks;
            this.entries = entries;
            this.templateBlocks = templateBlocks;
            this.matchedBits = new BitSet(templateBlocks.size());
        }

        public void resetMatchState() {
            matchedBits.clear();
            cursor = 0;
        }
    }

    public record ParsedSchematic(int totalBlocks, List<MaterialEntry> entries, List<ParsedSubregion> subregions) {}
}
