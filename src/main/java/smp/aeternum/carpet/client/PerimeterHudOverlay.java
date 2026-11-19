package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.network.PerimeterNetworking.HudSnapshot;
import smp.aeternum.carpet.perimeters.BlockCluster;

/** Top-right HUD card (Materials style) showing pinned perimeter clusters and scan progress. */
public final class PerimeterHudOverlay {
    private static final Gson GSON = new Gson();
    private static HudSnapshot current;

    private PerimeterHudOverlay() {}

    public static void update(String json) {
        if (json == null || json.isBlank()) {
            current = null;
            return;
        }
        try {
            current = GSON.fromJson(json, HudSnapshot.class);
        } catch (Exception ignored) {
            current = null;
        }
    }

    public static void clear() {
        current = null;
    }

    public static ItemStack iconForEntry(String entry) {
        if (entry == null || entry.isBlank()) return new ItemStack(Items.BARRIER);
        if (entry.startsWith("#")) {
            if (entry.contains("log") || entry.contains("wood")) return new ItemStack(Items.OAK_LOG);
            if (entry.contains("leave")) return new ItemStack(Items.OAK_LEAVES);
            if (entry.contains("plank")) return new ItemStack(Items.OAK_PLANKS);
            if (entry.contains("ore")) return new ItemStack(Items.IRON_ORE);
            if (entry.contains("stone")) return new ItemStack(Items.STONE);
            if (entry.contains("wool")) return new ItemStack(Items.WHITE_WOOL);
            if (entry.contains("fire")) return new ItemStack(Items.FLINT_AND_STEEL);
            return new ItemStack(Items.NAME_TAG);
        }
        return switch (entry) {
            case "minecraft:lava", "minecraft:lava_cauldron" -> new ItemStack(Items.LAVA_BUCKET);
            case "minecraft:water", "minecraft:bubble_column", "minecraft:water_cauldron" -> new ItemStack(Items.WATER_BUCKET);
            case "minecraft:powder_snow", "minecraft:powder_snow_cauldron" -> new ItemStack(Items.POWDER_SNOW_BUCKET);
            case "minecraft:fire", "minecraft:soul_fire" -> new ItemStack(Items.FLINT_AND_STEEL);
            case "minecraft:nether_portal" -> new ItemStack(Items.OBSIDIAN);
            case "minecraft:end_portal", "minecraft:end_gateway" -> new ItemStack(Items.ENDER_EYE);
            case "minecraft:frosted_ice" -> new ItemStack(Items.ICE);
            case "minecraft:piston_head", "minecraft:moving_piston" -> new ItemStack(Items.PISTON);
            default -> {
                Identifier id = Identifier.tryParse(entry);
                if (id != null) {
                    Block b = Registries.BLOCK.getOptionalValue(id).orElse(null);
                    if (b != null) {
                        Item item = b.asItem();
                        if (item != Items.AIR) yield new ItemStack(item);
                    }
                    String path = id.getPath();
                    if (path.startsWith("potted_")) {
                        Block inner = Registries.BLOCK.getOptionalValue(Identifier.of(id.getNamespace(), path.substring(7))).orElse(null);
                        if (inner != null && inner.asItem() != Items.AIR) yield new ItemStack(inner.asItem());
                        yield new ItemStack(Items.FLOWER_POT);
                    }
                    if (path.contains("wall_")) {
                        Block base = Registries.BLOCK.getOptionalValue(Identifier.of(id.getNamespace(), path.replace("wall_", ""))).orElse(null);
                        if (base != null && base.asItem() != Items.AIR) yield new ItemStack(base.asItem());
                    }
                }
                yield new ItemStack(Items.BARRIER);
            }
        };
    }

    public static String shortName(String entry) {
        if (entry == null || entry.isBlank()) return "?";
        if (entry.startsWith("#")) {
            int colon = entry.indexOf(':');
            return "#" + (colon >= 0 ? entry.substring(colon + 1) : entry.substring(1));
        }
        Identifier id = Identifier.tryParse(entry);
        if (id != null) {
            Block b = Registries.BLOCK.getOptionalValue(id).orElse(null);
            if (b != null) return b.getName().getString();
            return id.getPath();
        }
        return entry;
    }

    public static void render(DrawContext c) {
        if (current == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        List<BlockCluster> clusters = current.clusters != null ? new ArrayList<>(current.clusters) : List.of();
        clusters.sort(Comparator.comparingInt(cl -> {
            double dx = cl.centerX - client.player.getX();
            double dy = cl.centerY - client.player.getY();
            double dz = cl.centerZ - client.player.getZ();
            return (int) (dx * dx + dy * dy + dz * dz);
        }));

        int shown = Math.min(clusters.size(), 6);
        int w = 156;
        int weRow = current.weEnabled ? 14 : 0;
        int extraEmptyRow = (!current.scanning && shown == 0) ? 18 : (current.scanning ? 18 : 0);
        int h = 23 + weRow + shown * 20 + extraEmptyRow;

        int sw = client.getWindow().getScaledWidth();
        int x = sw - w - 6;
        int y = 6 + MaterialsHudOverlay.topRightOccupiedHeight();

        c.fill(x, y, x + w, y + h, 0xCC0D0D10);
        c.drawBorder(x, y, w, h, AeternumTheme.BORDER_BRIGHT);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, AeternumTheme.ACCENT_GOLD);

        String title = AeternumTheme.ellipsize(client.textRenderer, "PERIM: " + current.name, w - 52);
        c.drawText(client.textRenderer, title, x + 6, y + 6, AeternumTheme.ACCENT_GOLD, false);
        String summary = current.scanning && current.totalChunks > 0
            ? ((current.scannedChunks * 100) / Math.max(1, current.totalChunks)) + "%"
            : current.totalClusters + "z · " + current.totalMatchedBlocks;
        c.drawText(client.textRenderer, summary, x + w - 6 - client.textRenderer.getWidth(summary), y + 6, AeternumTheme.TEXT_MUTED, false);

        int rowStartY = y + 19;
        if (current.weEnabled) {
            String stLabel = switch (current.weStatus != null ? current.weStatus : "IDLE") {
                case "STALLED" -> "ATASCADO";
                case "LOW_Y" -> "CAPA BAJA";
                case "UNLOADED" -> "DESCARGADA";
                case "RUNNING" -> "ACTIVO";
                default -> "INACTIVO";
            };
            int stColor = switch (current.weStatus != null ? current.weStatus : "IDLE") {
                case "STALLED" -> 0xFFFF6B6B;
                case "LOW_Y" -> 0xFF5CE07B;
                case "UNLOADED" -> AeternumTheme.TEXT_MUTED;
                default -> AeternumTheme.ACCENT_GOLD;
            };
            String tntTxt = current.weCurrentTntY == Integer.MIN_VALUE ? "--" : String.valueOf(current.weCurrentTntY);
            String weLine = "WE " + stLabel + " · Y=" + tntTxt + " (" + current.weLoadedPct + "%)";
            c.drawText(client.textRenderer, AeternumTheme.ellipsize(client.textRenderer, weLine, w - 12), x + 6, rowStartY + 1, stColor, false);
            rowStartY += 14;
        }
        if (current.scanning) {
            float ratio = current.totalChunks <= 0 ? 0f : (float) current.scannedChunks / current.totalChunks;
            c.drawText(client.textRenderer, "Escaneando " + current.scannedChunks + "/" + current.totalChunks, x + 6, rowStartY + 2, AeternumTheme.TEXT_SECONDARY, false);
            AeternumTheme.drawProgressBar(c, x + 6, rowStartY + 13, w - 12, 3, ratio, AeternumTheme.ACCENT_GOLD);
            return;
        }

        if (shown == 0) {
            c.drawText(client.textRenderer, "Sin bloqueadores pendientes", x + 6, rowStartY + 4, AeternumTheme.TEXT_SECONDARY, false);
            return;
        }

        for (int i = 0; i < shown; i++) {
            BlockCluster cl = clusters.get(i);
            int rowY = rowStartY + i * 20;
            if (i % 2 == 0) c.fill(x + 2, rowY, x + w - 2, rowY + 19, 0x381E1E24);

            c.drawItem(iconForEntry(cl.blockId), x + 4, rowY + 1);
            String label = AeternumTheme.ellipsize(client.textRenderer, shortName(cl.blockId) + " x" + cl.count, w - 62);
            c.drawText(client.textRenderer, label, x + 24, rowY + 2, AeternumTheme.TEXT_PRIMARY, false);

            int dist = (int) Math.round(Math.sqrt(
                Math.pow(cl.centerX - client.player.getX(), 2)
                + Math.pow(cl.centerY - client.player.getY(), 2)
                + Math.pow(cl.centerZ - client.player.getZ(), 2)
            ));
            String distStr = dist + "m";
            c.drawText(client.textRenderer, distStr, x + w - 5 - client.textRenderer.getWidth(distStr), rowY + 2, AeternumTheme.TEXT_MUTED, false);
            String coords = cl.centerX + ", " + cl.centerY + ", " + cl.centerZ;
            c.drawText(client.textRenderer, AeternumTheme.ellipsize(client.textRenderer, coords, w - 29), x + 24, rowY + 11, AeternumTheme.TEXT_SECONDARY, false);
        }
    }
}
