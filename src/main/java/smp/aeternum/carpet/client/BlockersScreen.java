package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import smp.aeternum.carpet.blockers.BlockerScan;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiScreen;

/** Landing page: scans first, then an explicit detail screen. */
public final class BlockersScreen extends UiScreen {
    private static final Gson GSON = new Gson();
    private static final Type SCANS = new TypeToken<List<BlockerScan>>() {}.getType();
    private static final int CARD_H = 40;
    private static final int CARD_GAP = 4;
    private static final int ROW_STRIDE = CARD_H + CARD_GAP;

    private List<BlockerScan> scans;
    private boolean canDelete;
    private double scroll, targetScroll;
    private boolean draggingScrollbar;

    public BlockersScreen(String json, boolean canDelete) {
        super(Text.literal("Blocker scans"));
        this.canDelete = canDelete;
        scans = decode(json);
    }

    public void replaceData(String json, boolean canDelete) {
        this.canDelete = canDelete;
        scans = decode(json);
    }

    private int listTop() { return panelY + 54; }
    private int listBottom() { return panelY + panelH - 16; }

    private int maxScrollPx() {
        int contentH = scans.size() * ROW_STRIDE - CARD_GAP;
        int viewH = listBottom() - listTop();
        return Math.max(0, contentH - viewH);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        computePanel(Math.min(640, (int) (width * 0.74)), Math.min(410, (int) (height * 0.76)));
        int maxScroll = maxScrollPx();
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        scroll += (targetScroll - scroll) * Math.min(1.0, delta * 0.45 + 0.25);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        renderBackground(c, mx, my, delta);
        drawWindowFrame(c, "BLOCKER SCANS", "Escoge un análisis para ver e inspeccionar sus bloqueadores");
        drawCloseButton(c, mx, my);

        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = lBottom - lTop;
        int cardX = panelX + 12;
        int cardW = panelW - 30;

        if (scans.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, "No hay análisis guardados.", width / 2, lTop + viewH / 2 - 12, AeternumTheme.TEXT_SECONDARY);
            c.drawCenteredTextWithShadow(textRenderer, "Usa /blockers <coord1> <coord2> <nombre>", width / 2, lTop + viewH / 2 + 4, AeternumTheme.TEXT_MUTED);
            super.render(c, mx, my, delta);
            return;
        }

        c.enableScissor(panelX + 6, lTop, panelX + panelW - 10, lBottom);
        for (int i = 0; i < scans.size(); i++) {
            int cardY = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
            if (cardY + CARD_H < lTop || cardY > lBottom) continue;

            BlockerScan scan = scans.get(i);
            int delX = cardX + cardW - 34;
            int delY = cardY + (CARD_H - 20) / 2;
            boolean inArea = my >= lTop && my < lBottom;
            boolean overCard = inArea && mx >= cardX && mx < cardX + cardW && my >= cardY && my < cardY + CARD_H;
            boolean overDel = inArea && mx >= delX && mx < delX + 26 && my >= delY && my < delY + 20;

            int bg = (overCard && !overDel) ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG;
            int border = (overCard && !overDel) ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE;
            AeternumTheme.drawCard(c, cardX, cardY, cardW, CARD_H, bg, border);

            String title = AeternumTheme.ellipsize(textRenderer, scan.id, cardW - 220);
            c.drawTextWithShadow(textRenderer, title, cardX + 12, cardY + 7, AeternumTheme.TEXT_PRIMARY);

            String shortDim = scan.dimension == null ? "" : scan.dimension.replace("minecraft:", "");
            int badgeX = cardX + 16 + textRenderer.getWidth(title);
            AeternumTheme.drawBadge(c, textRenderer, badgeX, cardY + 5, shortDim, 0xFF1E2436, AeternumTheme.ACCENT_BLUE);

            int matches = scan.matchedEntries > 0 ? scan.matchedEntries : scan.entries.size();
            String results = scan.entriesTruncated ? matches + "+ bloqueadores" : matches + " bloqueadores";
            c.drawText(textRenderer, results, cardX + 12, cardY + 24, matches > 0 ? AeternumTheme.ACCENT_GOLD : AeternumTheme.TEXT_SECONDARY, false);

            if (scan.complete) {
                String doneLabel = "Terminado";
                int bw = textRenderer.getWidth(doneLabel) + 10;
                AeternumTheme.drawBadge(c, textRenderer, delX - bw - 10, cardY + 13, doneLabel, 0xFF152920, AeternumTheme.ACCENT_GREEN);
            } else {
                String prog = scan.scannedChunks + "/" + scan.totalChunks + " chunks";
                int pw = textRenderer.getWidth(prog);
                c.drawText(textRenderer, prog, delX - pw - 12, cardY + 8, AeternumTheme.ACCENT_CYAN, false);
                float ratio = scan.totalChunks <= 0 ? 0f : (float) scan.scannedChunks / (float) scan.totalChunks;
                AeternumTheme.drawProgressBar(c, delX - 112, cardY + 24, 100, 5, ratio, AeternumTheme.ACCENT_CYAN);
            }

            boolean shift = canDelete && hasShiftDown();
            int delBg = overDel ? (shift ? 0xFF5A2229 : 0xFF282D3B) : (shift ? 0xFF3A1A1F : 0xFF161A24);
            int delBorder = shift ? AeternumTheme.withAlpha(AeternumTheme.ACCENT_RED, 0xB0) : AeternumTheme.BORDER_SUBTLE;
            int delFg = shift ? 0xFFFCA5A5 : AeternumTheme.TEXT_MUTED;
            AeternumTheme.drawCard(c, delX, delY, 26, 20, delBg, delBorder);
            c.drawTextWithShadow(textRenderer, "\u00D7", delX + 10, delY + 6, delFg);
        }
        c.disableScissor();

        AeternumTheme.drawScrollbar(
            c, panelX + panelW - 12, lTop, lBottom,
            scans.size() * ROW_STRIDE - CARD_GAP, viewH, scroll, draggingScrollbar
        );

        super.render(c, mx, my, delta);

        if (isCloseButtonHit(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Cerrar"), mx, my);
        } else if (my >= lTop && my < lBottom) {
            for (int i = 0; i < scans.size(); i++) {
                int cardY = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
                int delX = cardX + cardW - 34;
                int delY = cardY + (CARD_H - 20) / 2;
                if (mx >= delX && mx < delX + 26 && my >= delY && my < delY + 20) {
                    c.drawTooltip(textRenderer, Text.literal(canDelete ? (hasShiftDown() ? "Eliminar scan" : "Mantén Shift para borrar el scan") : "Solo operadores pueden borrar scans"), mx, my);
                    break;
                }
            }
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

        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = lBottom - lTop;
        int maxScroll = maxScrollPx();
        if (maxScroll > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(my, lTop, viewH, maxScroll);
            return true;
        }

        int cardX = panelX + 12;
        int cardW = panelW - 30;
        if (my >= lTop && my < lBottom && mx >= cardX && mx < cardX + cardW) {
            int relY = (int) (my - lTop + scroll);
            int idx = relY / ROW_STRIDE;
            if (idx >= 0 && idx < scans.size() && (relY % ROW_STRIDE) < CARD_H) {
                int cardY = lTop + idx * ROW_STRIDE - (int) Math.round(scroll);
                int delX = cardX + cardW - 34;
                int delY = cardY + (CARD_H - 20) / 2;
                BlockerScan scan = scans.get(idx);
                if (mx >= delX && mx < delX + 26 && my >= delY && my < delY + 20) {
                    if (canDelete && hasShiftDown()) {
                        BlockerDeleteRequest.send(scan.id);
                    }
                    return true;
                }
                client.setScreen(new BlockerScanScreen(this, scan));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(my, listTop(), listBottom() - listTop(), maxScrollPx());
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

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v != 0 && maxScrollPx() > 0) {
            targetScroll = Math.max(0, Math.min(maxScrollPx(), targetScroll - v * 28));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    private void updateScrollFromMouse(double my, int top, int viewH, int maxScroll) {
        int totalH = scans.size() * ROW_STRIDE - CARD_GAP;
        int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
        double frac = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
        targetScroll = Math.max(0, Math.min(1, frac)) * maxScroll;
        scroll = targetScroll;
    }

    private static List<BlockerScan> decode(String json) {
        List<BlockerScan> decoded = GSON.fromJson(json, SCANS);
        if (decoded == null) return List.of();
        return decoded.stream().sorted(Comparator.comparingLong((BlockerScan scan) -> scan.createdAt).reversed()).toList();
    }
}
