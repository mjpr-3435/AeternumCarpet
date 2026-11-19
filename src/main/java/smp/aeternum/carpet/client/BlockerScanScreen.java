package smp.aeternum.carpet.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import smp.aeternum.carpet.blockers.BlockerEntry;
import smp.aeternum.carpet.blockers.BlockerScan;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;

/** Detail view keeps the target unset until the player explicitly chooses Guide. */
public final class BlockerScanScreen extends UiScreen {
    private static final int ROW_H = 26;
    private static final int ROW_GAP = 2;
    private static final int ROW_STRIDE = ROW_H + ROW_GAP;

    private final Screen parent;
    private final BlockerScan scan;
    private final List<BlockerEntry> entries;
    private double scroll, targetScroll;
    private UiTextField searchField;
    private UiButton clearButton, selectAllButton;
    private boolean draggingScrollbar;

    public BlockerScanScreen(Screen parent, BlockerScan scan) {
        super(Text.literal(scan.id));
        this.parent = parent;
        this.scan = scan;
        entries = new ArrayList<>(scan.entries);
        entries.sort(Comparator.comparing(BlockerEntry::blockId).thenComparingInt(BlockerEntry::y));
        syncGuide();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    protected void init() {
        String prevQuery = searchField != null ? searchField.getText() : "";
        clearChildren();
        computePanel(Math.min(700, (int) (width * 0.78)), Math.min(430, (int) (height * 0.80)));

        addDrawableChild(UiButton.ghost(panelX + 10, panelY + 10, 24, 24, "\u2190", b -> close()));

        searchField = new UiTextField(textRenderer, panelX + 12, panelY + 52, panelW - 24, 20, "Filtrar por bloque, coordenada o motivo…");
        searchField.setMaxLength(80);
        searchField.setText(prevQuery);
        searchField.setChangedListener(s -> {
            targetScroll = 0;
            scroll = 0;
        });
        addDrawableChild(searchField);

        int footerY = panelY + panelH - 28;
        clearButton = UiButton.secondary(panelX + panelW - 244, footerY, 114, 20, "Limpiar selección", b -> {
            BlockerSelectionStore.clear(scan.id);
            BlockerGuide.clear();
        });
        selectAllButton = UiButton.primary(panelX + panelW - 124, footerY, 112, 20, "Seleccionar todo", b -> {
            BlockerSelectionStore.selectAll(scan.id, entries);
            syncGuide();
        });
        addDrawableChild(clearButton);
        addDrawableChild(selectAllButton);
    }

    private List<BlockerEntry> filteredEntries() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        if (q.isEmpty()) return entries;
        List<BlockerEntry> out = new ArrayList<>();
        for (BlockerEntry e : entries) {
            String block = e.blockId() == null ? "" : e.blockId().toLowerCase();
            String reason = e.reason() == null ? "" : e.reason().toLowerCase();
            String coords = e.x() + ", " + e.y() + ", " + e.z();
            if (block.contains(q) || reason.contains(q) || coords.contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    private int listTop() { return panelY + 96; }
    private int listBottom() { return panelY + panelH - 42; }

    private int maxScrollPx(int count) {
        int contentH = count * ROW_STRIDE - ROW_GAP;
        int viewH = listBottom() - listTop();
        return Math.max(0, contentH - viewH);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        computePanel(Math.min(700, (int) (width * 0.78)), Math.min(430, (int) (height * 0.80)));
        List<BlockerEntry> shown = filteredEntries();
        int maxScroll = maxScrollPx(shown.size());
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        scroll += (targetScroll - scroll) * Math.min(1.0, delta * 0.45 + 0.25);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        renderBackground(c, mx, my, delta);
        AeternumTheme.drawPanel(c, panelX, panelY, panelW, panelH);
        AeternumTheme.drawHeader(c, panelX, panelY, panelW, 44);
        c.fill(panelX + 1, panelY + 1, panelX + 4, panelY + 43, AeternumTheme.ACCENT_GOLD);

        c.drawTextWithShadow(textRenderer, scan.id, panelX + 38, panelY + 10, AeternumTheme.TEXT_PRIMARY);
        String dim = scan.dimension == null ? "" : scan.dimension.replace("minecraft:", "");
        c.drawText(textRenderer, entries.size() + " bloqueadores  ·  " + dim, panelX + 38, panelY + 25, AeternumTheme.ACCENT_GOLD, false);
        drawCloseButton(c, mx, my);

        int colY = panelY + 78;
        c.fill(panelX + 12, colY, panelX + panelW - 18, colY + 15, AeternumTheme.CARD_ALT_BG);
        int colCoordX = panelX + (int) (panelW * 0.42);
        int colReasonX = panelX + (int) (panelW * 0.64);
        c.drawText(textRenderer, "BLOQUE", panelX + 40, colY + 4, AeternumTheme.TEXT_MUTED, false);
        c.drawText(textRenderer, "COORDENADAS", colCoordX, colY + 4, AeternumTheme.TEXT_MUTED, false);
        c.drawText(textRenderer, "MOTIVO", colReasonX, colY + 4, AeternumTheme.TEXT_MUTED, false);

        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = lBottom - lTop;
        int rowX = panelX + 12;
        int rowW = panelW - 30;

        if (shown.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, "No hay coincidencias.", width / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
        } else {
            c.enableScissor(panelX + 6, lTop, panelX + panelW - 10, lBottom);
            for (int i = 0; i < shown.size(); i++) {
                int ry = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
                if (ry + ROW_H < lTop || ry > lBottom) continue;

                BlockerEntry e = shown.get(i);
                boolean isSel = BlockerSelectionStore.isSelected(scan.id, e);
                boolean hover = my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW && my >= ry && my < ry + ROW_H;

                int bg = isSel ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : (i % 2 == 0 ? AeternumTheme.CARD_BG : AeternumTheme.CARD_ALT_BG));
                int border = isSel ? AeternumTheme.withAlpha(AeternumTheme.ACCENT_CYAN, 0x90) : AeternumTheme.BORDER_SUBTLE;
                AeternumTheme.drawCard(c, rowX, ry, rowW, ROW_H, bg, border);

                // Selection check
                int chkX = rowX + 6;
                int chkY = ry + 7;
                AeternumTheme.drawCard(c, chkX, chkY, 12, 12, isSel ? 0xFF17454D : 0xFF0E1118, isSel ? AeternumTheme.ACCENT_CYAN : AeternumTheme.BORDER_BRIGHT);
                if (isSel) {
                    c.drawText(textRenderer, "\u2713", chkX + 3, chkY + 2, AeternumTheme.ACCENT_CYAN, false);
                }

                AeternumTheme.drawItemIcon(c, e.blockId(), rowX + 24, ry + 5);

                String blockName = e.blockId().replace("minecraft:", "");
                String shortBlock = AeternumTheme.ellipsize(textRenderer, blockName, colCoordX - (rowX + 46));
                c.drawTextWithShadow(textRenderer, shortBlock, rowX + 44, ry + 9, AeternumTheme.TEXT_PRIMARY);

                c.drawText(textRenderer, e.x() + ", " + e.y() + ", " + e.z(), colCoordX, ry + 9, AeternumTheme.ACCENT_BLUE, false);

                String reason = AeternumTheme.ellipsize(textRenderer, e.reason(), rowX + rowW - colReasonX - 8);
                c.drawText(textRenderer, reason, colReasonX, ry + 9, AeternumTheme.TEXT_SECONDARY, false);
            }
            c.disableScissor();

            AeternumTheme.drawScrollbar(
                c, panelX + panelW - 12, lTop, lBottom,
                shown.size() * ROW_STRIDE - ROW_GAP, viewH, scroll, draggingScrollbar
            );
        }

        AeternumTheme.drawFooter(c, panelX, panelY + panelH - 36, panelW, panelY + panelH);
        int selectedCount = BlockerSelectionStore.keys(scan.id).size();
        String targetTxt = selectedCount == 0 ? "Selecciona bloques de la lista para guiarlos" : selectedCount + " objetivos guiados";
        c.drawText(textRenderer, targetTxt, panelX + 14, panelY + panelH - 22,
            selectedCount == 0 ? AeternumTheme.TEXT_MUTED : AeternumTheme.ACCENT_CYAN, false);

        if (clearButton != null) clearButton.active = selectedCount > 0;
        if (selectAllButton != null) selectAllButton.active = selectedCount < entries.size();

        if (searchField != null) {
            searchField.renderBox(c, panelX + 12, panelY + 52, panelW - 24, 20, mx, my, delta);
        }
        super.render(c, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && isCloseButtonHit(mx, my)) {
            client.setScreen(null);
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;

        List<BlockerEntry> shown = filteredEntries();
        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = lBottom - lTop;
        int maxScroll = maxScrollPx(shown.size());
        if (maxScroll > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(my, lTop, viewH, maxScroll, shown.size());
            return true;
        }

        int rowX = panelX + 12;
        int rowW = panelW - 30;
        if (my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW) {
            int relY = (int) (my - lTop + scroll);
            int idx = relY / ROW_STRIDE;
            if (idx >= 0 && idx < shown.size() && (relY % ROW_STRIDE) < ROW_H) {
                BlockerSelectionStore.toggle(scan.id, shown.get(idx));
                syncGuide();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v != 0) {
            int maxScroll = maxScrollPx(filteredEntries().size());
            targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - v * 28));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0) {
            List<BlockerEntry> shown = filteredEntries();
            updateScrollFromMouse(my, listTop(), listBottom() - listTop(), maxScrollPx(shown.size()), shown.size());
            return true;
        }
        return super.mouseDragged(mx, my, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void updateScrollFromMouse(double my, int top, int viewH, int maxScroll, int count) {
        if (maxScroll == 0) return;
        int totalH = count * ROW_STRIDE - ROW_GAP;
        int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
        double fraction = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
        targetScroll = Math.max(0.0, Math.min(1.0, fraction)) * maxScroll;
        scroll = targetScroll;
    }

    private void syncGuide() {
        Set<String> selected = BlockerSelectionStore.keys(scan.id);
        if (selected.isEmpty()) BlockerGuide.clear();
        else BlockerGuide.select(entries.stream().filter(entry -> selected.contains(BlockerSelectionStore.key(entry))).toList());
    }
}
