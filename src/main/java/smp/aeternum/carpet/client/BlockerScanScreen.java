package smp.aeternum.carpet.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import smp.aeternum.carpet.blockers.BlockerEntry;
import smp.aeternum.carpet.blockers.BlockerScan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Detail view keeps the target unset until the player explicitly chooses Guide. */
public final class BlockerScanScreen extends Screen {
    private final Screen parent;
    private final BlockerScan scan;
    private final List<BlockerEntry> entries;
    private int scroll;
    private ButtonWidget clearButton;
    private ButtonWidget selectAllButton;
    private boolean draggingScrollbar;

    public BlockerScanScreen(Screen parent, BlockerScan scan) {
        super(Text.literal(scan.id));
        this.parent = parent;
        this.scan = scan;
        entries = new ArrayList<>(scan.entries);
        entries.sort(Comparator.comparing(BlockerEntry::blockId).thenComparingInt(BlockerEntry::y));
        syncGuide();
    }

    @Override public void close() { client.setScreen(parent); }

    @Override
    protected void init() {
        clearButton = ButtonWidget.builder(Text.literal("Limpiar selección"), button -> {
            BlockerSelectionStore.clear(scan.id);
            BlockerGuide.clear();
        }).dimensions(0, 0, 112, 20).build();
        selectAllButton = ButtonWidget.builder(Text.literal("Seleccionar todo"), button -> {
            BlockerSelectionStore.selectAll(scan.id, entries);
            syncGuide();
        }).dimensions(0, 0, 112, 20).build();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int panelW = Math.max(540, (int) (width * 0.70)), panelH = Math.max(360, (int) (height * 0.70));
        panelW = Math.min(panelW, width - 32);
        panelH = Math.min(panelH, height - 32);
        int left = (width - panelW) / 2, top = (height - panelH) / 2;
        context.fill(left, top, left + panelW, top + panelH, 0xE0101010);
        context.drawBorder(left, top, panelW, panelH, 0xFF909090);
        context.fill(left + 1, top + 1, left + panelW - 1, top + 43, 0xCC242424);
        context.drawTextWithShadow(textRenderer, "‹  " + scan.id, left + 12, top + 10, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, entries.size() + " bloqueadores", left + 12, top + 26, 0xFFFFD080);
        context.drawTextWithShadow(textRenderer, scan.dimension, left + 145, top + 26, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, "×", left + panelW - 18, top + 10, 0xFFFFFFFF);
        int listTop = top + 52, listBottom = top + panelH - 78, visible = (listBottom - listTop) / 18;
        context.fill(left + 9, listTop - 18, left + panelW - 10, listTop - 2, 0xFF1D1D1D);
        context.drawTextWithShadow(textRenderer, "BLOQUE", left + 14, listTop - 14, 0xFF8F8F8F);
        context.drawTextWithShadow(textRenderer, "COORDENADAS", left + 145, listTop - 14, 0xFF8F8F8F);
        context.drawTextWithShadow(textRenderer, "MOTIVO", left + 290, listTop - 14, 0xFF8F8F8F);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visible)));
        for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
            BlockerEntry e = entries.get(i + scroll); int y = listTop + i * 18;
            boolean hover = mouseX >= left + 10 && mouseX < left + panelW - 23 && mouseY >= y - 2 && mouseY < y + 15;
            if (BlockerSelectionStore.isSelected(scan.id, e)) context.fill(left + 9, y - 2, left + panelW - 23, y + 15, 0xFF176C71);
            else if (hover) context.fill(left + 9, y - 2, left + panelW - 23, y + 15, 0xFF30465A);
            context.drawTextWithShadow(textRenderer, e.blockId().replace("minecraft:", ""), left + 14, y + 1, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, e.x() + ", " + e.y() + ", " + e.z(), left + 145, y + 1, 0xFFB8D8FF);
            context.drawTextWithShadow(textRenderer, e.reason(), left + 290, y + 1, 0xFFAAAAAA);
        }
        if (entries.size() > visible) {
            int barH = Math.max(20, (listBottom - listTop) * visible / entries.size());
            int barY = listTop + (listBottom - listTop - barH) * scroll / Math.max(1, entries.size() - visible);
            context.fill(left + panelW - 17, listTop, left + panelW - 11, listBottom, 0xFF333333);
            context.fill(left + panelW - 17, barY, left + panelW - 11, barY + barH, 0xFFAAAAAA);
        }
        int selectedCount = BlockerSelectionStore.keys(scan.id).size();
        String target = selectedCount == 0 ? "Sin objetivo seleccionado" : selectedCount + " objetivos guiados";
        context.drawTextWithShadow(textRenderer, target, left + 12, top + panelH - 60, 0xFF55FFFF);
        context.drawTextWithShadow(textRenderer, selectedCount == 0 ? "Selecciona bloques de la lista para guiarlos." : "", left + 12, top + panelH - 38, 0xFF666666);
        clearButton.setPosition(left + 12, top + panelH - 48);
        clearButton.active = selectedCount > 0;
        clearButton.render(context, mouseX, mouseY, delta);
        selectAllButton.setPosition(left + 128, top + panelH - 48);
        selectAllButton.active = selectedCount < entries.size();
        selectAllButton.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = Math.max(540, (int) (width * 0.70)), panelH = Math.max(360, (int) (height * 0.70));
        panelW = Math.min(panelW, width - 32);
        panelH = Math.min(panelH, height - 32);
        int left = (width - panelW) / 2, top = (height - panelH) / 2, listTop = top + 52, listBottom = top + panelH - 78;
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (mouseX >= left && mouseX < left + 45 && mouseY >= top && mouseY < top + 35) { close(); return true; }
        if (mouseX >= left + panelW - 25 && mouseY >= top && mouseY < top + 30) { client.setScreen(null); return true; }
        if (mouseX >= left + panelW - 20 && mouseX <= left + panelW - 7 && mouseY >= listTop && mouseY <= listBottom && entries.size() > visibleRows(panelH)) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY, top, panelH);
            return true;
        }
        if (mouseY >= listTop && mouseY < listBottom) {
            int i = (int)((mouseY - listTop) / 18) + scroll;
            if (i < entries.size()) {
                BlockerSelectionStore.toggle(scan.id, entries.get(i));
                syncGuide();
            }
            return true;
        }
        if (clearButton.mouseClicked(mouseX, mouseY, button) || selectAllButton.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) { scroll -= (int)Math.signum(v); return true; }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0) {
            int panelH = Math.min(Math.max(360, (int) (height * 0.70)), height - 32);
            updateScrollFromMouse(mouseY, (height - panelH) / 2, panelH);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int visibleRows(int panelH) { return (panelH - 78 - 52) / 18; }

    private void updateScrollFromMouse(double mouseY, int top, int panelH) {
        int listTop = top + 52, listBottom = top + panelH - 78, visible = visibleRows(panelH);
        int max = Math.max(0, entries.size() - visible);
        if (max == 0) return;
        int barH = Math.max(20, (listBottom - listTop) * visible / entries.size());
        double fraction = (mouseY - listTop - barH / 2.0) / Math.max(1.0, listBottom - listTop - barH);
        scroll = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * max);
    }

    private void syncGuide() {
        Set<String> selected = BlockerSelectionStore.keys(scan.id);
        if (selected.isEmpty()) BlockerGuide.clear();
        else BlockerGuide.select(entries.stream().filter(entry -> selected.contains(BlockerSelectionStore.key(entry))).toList());
    }
}
