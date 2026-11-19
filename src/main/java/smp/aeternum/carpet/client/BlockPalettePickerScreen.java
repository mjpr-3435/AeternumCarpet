package smp.aeternum.carpet.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.network.PerimeterActionPayload;
import smp.aeternum.carpet.perimeters.Perimeter;

/** Visual Axiom-style block & #tag picker for customizing a perimeter's scan palette. */
public final class BlockPalettePickerScreen extends UiScreen {
    private static final List<String> COMMON_TAGS = List.of(
        "#minecraft:logs",
        "#minecraft:leaves",
        "#minecraft:planks",
        "#minecraft:base_stone_overworld",
        "#minecraft:coal_ores",
        "#minecraft:iron_ores",
        "#minecraft:diamond_ores",
        "#minecraft:gold_ores",
        "#minecraft:wool",
        "#minecraft:fire"
    );

    private static final int CELL = 24;
    private static final int CELL_GAP = 4;
    private static final int STRIDE = CELL + CELL_GAP;

    private final PerimetersScreen parentScreen;
    private final String perimeterId;
    private List<String> activePalette;
    private final List<Entry> allEntries = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();

    private UiTextField searchField;
    private double scroll, targetScroll;
    private boolean draggingScrollbar;

    public BlockPalettePickerScreen(PerimetersScreen parentScreen, Perimeter perimeter) {
        super(Text.literal("Paleta de bloques"));
        this.parentScreen = parentScreen;
        this.perimeterId = perimeter.id;
        this.activePalette = new ArrayList<>(perimeter.palette != null ? perimeter.palette : List.of());
        buildCatalog();
    }

    public void syncFromParent(List<String> updatedPalette) {
        this.activePalette = new ArrayList<>(updatedPalette != null ? updatedPalette : List.of());
    }

    public void onServerSnapshot(String json, boolean canDelete) {
        parentScreen.replaceData(json, canDelete);
    }

    private static final List<String> EXTRA_NON_ITEM_BLOCKS = List.of(
        "minecraft:lava",
        "minecraft:water",
        "minecraft:powder_snow",
        "minecraft:fire",
        "minecraft:soul_fire",
        "minecraft:nether_portal",
        "minecraft:end_portal",
        "minecraft:end_gateway",
        "minecraft:bubble_column",
        "minecraft:frosted_ice",
        "minecraft:piston_head",
        "minecraft:moving_piston"
    );

    private void buildCatalog() {
        allEntries.clear();
        for (String tag : COMMON_TAGS) {
            allEntries.add(new Entry(tag, PerimeterHudOverlay.shortName(tag), true));
        }
        for (String idStr : EXTRA_NON_ITEM_BLOCKS) {
            Identifier id = Identifier.tryParse(idStr);
            if (id != null) {
                Registries.BLOCK.getOptionalValue(id).ifPresent(b ->
                    allEntries.add(new Entry(id.toString(), b.getName().getString(), false))
                );
            }
        }
        for (Block block : Registries.BLOCK) {
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) continue;
            if (block.asItem() == Items.AIR) continue;
            Identifier id = Registries.BLOCK.getId(block);
            allEntries.add(new Entry(id.toString(), block.getName().getString(), false));
        }
        applyFilter("");
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>();
        for (Entry e : allEntries) {
            if (q.isEmpty() || e.id.toLowerCase(Locale.ROOT).contains(q) || e.label.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(e);
            }
        }
        targetScroll = 0;
        scroll = 0;
    }

    @Override
    protected void init() {
        String prev = searchField != null ? searchField.getText() : "";
        clearChildren();
        computePanel(Math.min(540, (int) (width * 0.78)), Math.min(390, (int) (height * 0.80)));

        searchField = new UiTextField(textRenderer, panelX + 12, panelY + 46, panelW - 88, 20, "Buscar bloque o #tag (ej: lava, logs)...");
        searchField.setMaxLength(80);
        searchField.setText(prev);
        searchField.setFocused(true);
        searchField.setChangedListener(this::applyFilter);
        addDrawableChild(searchField);

        addDrawableChild(UiButton.primary(panelX + panelW - 68, panelY + 46, 56, 20, "Listo", b -> close()));
    }

    private int gridLeft() { return panelX + 14; }
    private int gridTop() { return panelY + 76; }
    private int gridBottom() { return panelY + panelH - 28; }
    private int cols() { return Math.max(1, (panelW - 32) / STRIDE); }

    private int maxScrollPx() {
        int rows = (filtered.size() + cols() - 1) / cols();
        int totalH = rows * STRIDE - CELL_GAP;
        return Math.max(0, totalH - (gridBottom() - gridTop()));
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        computePanel(Math.min(540, (int) (width * 0.78)), Math.min(390, (int) (height * 0.80)));
        int maxS = maxScrollPx();
        targetScroll = Math.max(0, Math.min(targetScroll, maxS));
        scroll += (targetScroll - scroll) * Math.min(1.0, delta * 0.45 + 0.25);
        scroll = Math.max(0, Math.min(scroll, maxS));

        renderBackground(c, mx, my, delta);
        drawWindowFrame(c, "PALETA DE BLOQUES", activePalette.size() + " seleccionado(s) · clic para añadir o quitar");
        drawCloseButton(c, mx, my);

        if (searchField != null) {
            searchField.renderBox(c, panelX + 12, panelY + 46, panelW - 88, 20, mx, my, delta);
        }

        int gx0 = gridLeft();
        int gy0 = gridTop();
        int gBot = gridBottom();
        int cols = cols();
        Entry hovered = null;

        c.enableScissor(panelX + 8, gy0, panelX + panelW - 10, gBot);
        for (int i = 0; i < filtered.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            int cx = gx0 + col * STRIDE;
            int cy = gy0 + row * STRIDE - (int) Math.round(scroll);
            if (cy + CELL < gy0 || cy > gBot) continue;

            Entry e = filtered.get(i);
            boolean selected = activePalette.contains(e.id);
            boolean hover = my >= gy0 && my < gBot && mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL;
            if (hover) hovered = e;

            int bg = selected ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG);
            int border = selected ? AeternumTheme.ACCENT_GOLD : (e.isTag ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE);
            AeternumTheme.drawCard(c, cx, cy, CELL, CELL, bg, border);
            c.drawItem(PerimeterHudOverlay.iconForEntry(e.id), cx + 4, cy + 4);
            if (e.isTag) {
                c.drawText(textRenderer, "#", cx + 2, cy + 2, AeternumTheme.ACCENT_GOLD, false);
            }
        }
        c.disableScissor();

        AeternumTheme.drawScrollbar(c, panelX + panelW - 12, gy0, gBot,
            ((filtered.size() + cols - 1) / cols) * STRIDE - CELL_GAP, gBot - gy0, scroll, draggingScrollbar);
        AeternumTheme.drawFooter(c, panelX, panelY + panelH - 24, panelW, panelY + panelH);
        c.drawText(textRenderer, "Clic en un bloque o #tag para alternar · Los cambios aplican al instante",
            panelX + 12, panelY + panelH - 16, AeternumTheme.TEXT_MUTED, false);

        super.render(c, mx, my, delta);

        if (hovered != null) {
            boolean selected = activePalette.contains(hovered.id);
            List<Text> tip = new ArrayList<>();
            tip.add(Text.literal(hovered.label + (selected ? " (En paleta)" : "")));
            tip.add(Text.literal(hovered.id));
            c.drawTooltip(textRenderer, tip, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && isCloseButtonHit(mx, my)) {
            close();
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;

        int gx0 = gridLeft();
        int gy0 = gridTop();
        int gBot = gridBottom();
        int cols = cols();
        int maxS = maxScrollPx();
        if (maxS > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= gy0 && my <= gBot) {
            draggingScrollbar = true;
            updateScrollFromMouse(my);
            return true;
        }
        if (my >= gy0 && my < gBot && mx >= gx0 && mx < gx0 + cols * STRIDE) {
            int col = (int) ((mx - gx0) / STRIDE);
            int relX = (int) ((mx - gx0) % STRIDE);
            int relY = (int) (my - gy0 + scroll);
            int row = relY / STRIDE;
            int cellY = relY % STRIDE;
            int idx = row * cols + col;
            if (col >= 0 && col < cols && relX < CELL && cellY < CELL && idx >= 0 && idx < filtered.size()) {
                Entry e = filtered.get(idx);
                boolean remove = activePalette.contains(e.id);
                if (remove) {
                    activePalette.remove(e.id);
                    ClientPlayNetworking.send(new PerimeterActionPayload("remove_palette", perimeterId, e.id, ""));
                } else {
                    activePalette.add(e.id);
                    ClientPlayNetworking.send(new PerimeterActionPayload("add_palette", perimeterId, e.id, ""));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v != 0 && my >= gridTop() && my < gridBottom()) {
            targetScroll = Math.max(0, Math.min(maxScrollPx(), targetScroll - v * 28));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void updateScrollFromMouse(double my) {
        int maxScroll = maxScrollPx();
        if (maxScroll == 0) return;
        int top = gridTop();
        int viewH = gridBottom() - top;
        int cols = cols();
        int rows = (filtered.size() + cols - 1) / cols;
        int totalH = rows * STRIDE - CELL_GAP;
        int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
        double frac = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
        targetScroll = Math.max(0, Math.min(1, frac)) * maxScroll;
        scroll = targetScroll;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parentScreen);
        } else {
            super.close();
        }
    }

    private record Entry(String id, String label, boolean isTag) {}
}
