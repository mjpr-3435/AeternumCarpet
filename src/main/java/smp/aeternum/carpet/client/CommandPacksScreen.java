package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import smp.aeternum.carpet.actions.CommandPack;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;

/** Package catalog with real controls per row and a local title filter. */
public final class CommandPacksScreen extends UiScreen {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<List<CommandPack>>() {}.getType();
    private static final int CARD_H = 38;
    private static final int CARD_GAP = 4;
    private static final int ROW_STRIDE = CARD_H + CARD_GAP;

    private List<CommandPack> packs;
    private boolean canEdit;
    private double scroll, targetScroll;
    private boolean draggingScrollbar;
    private String runFeedback = "";
    private UiTextField filter;
    private UiButton addButton;

    public CommandPacksScreen(String json, boolean canEdit) {
        super(Text.literal("Acciones"));
        packs = decode(json);
        this.canEdit = canEdit;
    }

    public void replaceData(String json, boolean canEdit) {
        packs = decode(json);
        this.canEdit = canEdit;
    }

    @Override
    protected void init() {
        String prev = filter != null ? filter.getText() : "";
        clearChildren();
        computePanel(Math.min(600, (int) (width * 0.72)), Math.min(410, (int) (height * 0.76)));

        filter = new UiTextField(textRenderer, panelX + 12, panelY + 52, panelW - 24, 20, "Filtrar paquetes por título…");
        filter.setMaxLength(80);
        filter.setText(prev);
        filter.setChangedListener(s -> {
            targetScroll = 0;
            scroll = 0;
        });
        addDrawableChild(filter);

        addButton = UiButton.primary(panelX + panelW - 142, panelY + panelH - 28, 130, 20, "+ Nuevo paquete", b -> {
            CommandPack p = new CommandPack();
            packs.add(p);
            client.setScreen(new CommandPackEditorScreen(this, packs, p, canEdit));
        });
        addButton.active = canEdit;
        addDrawableChild(addButton);
    }

    private List<CommandPack> shown() {
        String q = filter == null ? "" : filter.getText().strip().toLowerCase();
        return q.isEmpty() ? packs : packs.stream().filter(p -> p.name != null && p.name.toLowerCase().contains(q)).toList();
    }

    private int listTop() { return panelY + 80; }
    private int listBottom() { return panelY + panelH - 42; }

    private int maxScrollPx(int count) {
        int contentH = count * ROW_STRIDE - CARD_GAP;
        int viewH = listBottom() - listTop();
        return Math.max(0, contentH - viewH);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        computePanel(Math.min(600, (int) (width * 0.72)), Math.min(410, (int) (height * 0.76)));
        List<CommandPack> visiblePacks = shown();
        int maxScroll = maxScrollPx(visiblePacks.size());
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        scroll += (targetScroll - scroll) * Math.min(1.0, d * 0.45 + 0.25);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        renderBackground(c, mx, my, d);
        String subtitle = canEdit ? "Selecciona un paquete para editarlo o ejecutarlo" : "Selecciona un paquete para ver o ejecutar";
        drawWindowFrame(c, "PAQUETES DE ACCIONES", subtitle);
        drawCloseButton(c, mx, my);

        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = lBottom - lTop;
        int cardX = panelX + 12;
        int cardW = panelW - 30;

        if (visiblePacks.isEmpty()) {
            String msg = filter != null && !filter.getText().isBlank() ? "No hay coincidencias." : "No hay paquetes todavía.";
            c.drawCenteredTextWithShadow(textRenderer, msg, width / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
        } else {
            c.enableScissor(panelX + 6, lTop, panelX + panelW - 10, lBottom);
            for (int i = 0; i < visiblePacks.size(); i++) {
                int cardY = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
                if (cardY + CARD_H < lTop || cardY > lBottom) continue;

                CommandPack p = visiblePacks.get(i);
                int delX = cardX + cardW - 30;
                int playX = delX - 30;
                int btnY = cardY + (CARD_H - 20) / 2;

                boolean inArea = my >= lTop && my < lBottom;
                boolean overPlay = inArea && mx >= playX && mx < playX + 24 && my >= btnY && my < btnY + 20;
                boolean overDel = inArea && mx >= delX && mx < delX + 24 && my >= btnY && my < btnY + 20;
                boolean overCard = inArea && mx >= cardX && mx < cardX + cardW && my >= cardY && my < cardY + CARD_H;

                int bg = (overCard && !overPlay && !overDel) ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG;
                int border = (overCard && !overPlay && !overDel) ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE;
                AeternumTheme.drawCard(c, cardX, cardY, cardW, CARD_H, bg, border);

                String name = p.name == null || p.name.isBlank() ? "(sin nombre)" : p.name;
                String shortName = AeternumTheme.ellipsize(textRenderer, name, playX - cardX - 90);
                c.drawTextWithShadow(textRenderer, shortName, cardX + 12, cardY + 7, AeternumTheme.TEXT_PRIMARY);

                String stepsTxt = p.commands.size() + (p.commands.size() == 1 ? " paso" : " pasos");
                AeternumTheme.drawBadge(c, textRenderer, cardX + 18 + textRenderer.getWidth(shortName), cardY + 5, stepsTxt, 0xFF1E2436, AeternumTheme.ACCENT_BLUE);

                String desc = (p.description != null && !p.description.isBlank()) ? p.description : "Sin descripción";
                String shortDesc = AeternumTheme.ellipsize(textRenderer, desc, playX - cardX - 24);
                c.drawText(textRenderer, shortDesc, cardX + 12, cardY + 23, AeternumTheme.TEXT_MUTED, false);

                // Play button
                int playBg = overPlay ? 0xFF1F4235 : 0xFF172D25;
                AeternumTheme.drawCard(c, playX, btnY, 24, 20, playBg, AeternumTheme.withAlpha(AeternumTheme.ACCENT_GREEN, 0xC0));
                c.drawTextWithShadow(textRenderer, "\u25B6", playX + 9, btnY + 6, 0xFF9AE6B4);

                // Delete button
                boolean shift = canEdit && hasShiftDown();
                int delBg = overDel ? (shift ? 0xFF5A2229 : 0xFF282D3B) : (shift ? 0xFF3A1A1F : 0xFF161A24);
                int delBorder = shift ? AeternumTheme.withAlpha(AeternumTheme.ACCENT_RED, 0xB0) : AeternumTheme.BORDER_SUBTLE;
                int delFg = shift ? 0xFFFCA5A5 : AeternumTheme.TEXT_MUTED;
                AeternumTheme.drawCard(c, delX, btnY, 24, 20, delBg, delBorder);
                c.drawTextWithShadow(textRenderer, "\u00D7", delX + 9, btnY + 6, delFg);
            }
            c.disableScissor();

            AeternumTheme.drawScrollbar(
                c, panelX + panelW - 12, lTop, lBottom,
                visiblePacks.size() * ROW_STRIDE - CARD_GAP, viewH, scroll, draggingScrollbar
            );
        }

        AeternumTheme.drawFooter(c, panelX, panelY + panelH - 36, panelW, panelY + panelH);
        if (!runFeedback.isEmpty()) {
            c.drawText(textRenderer, runFeedback, panelX + 14, panelY + panelH - 22, AeternumTheme.ACCENT_GREEN, false);
        }

        if (filter != null) {
            filter.renderBox(c, panelX + 12, panelY + 52, panelW - 24, 20, mx, my, d);
        }
        super.render(c, mx, my, d);

        renderTooltips(c, mx, my, visiblePacks, lTop, lBottom, cardX, cardW);
    }

    private void renderTooltips(DrawContext c, int mx, int my, List<CommandPack> visiblePacks, int lTop, int lBottom, int cardX, int cardW) {
        if (isCloseButtonHit(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Cerrar"), mx, my);
        } else if (addButton != null && addButton.isMouseOver(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal(canEdit ? "Crear un paquete" : "Solo operadores pueden crear paquetes"), mx, my);
        } else if (my >= lTop && my < lBottom) {
            for (int i = 0; i < visiblePacks.size(); i++) {
                int cardY = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
                int delX = cardX + cardW - 30;
                int playX = delX - 30;
                int btnY = cardY + (CARD_H - 20) / 2;
                if (mx >= playX && mx < playX + 24 && my >= btnY && my < btnY + 20) {
                    c.drawTooltip(textRenderer, Text.literal("Ejecutar paquete"), mx, my);
                    break;
                }
                if (mx >= delX && mx < delX + 24 && my >= btnY && my < btnY + 20) {
                    c.drawTooltip(textRenderer, Text.literal(canEdit ? (hasShiftDown() ? "Eliminar paquete" : "Mantén Shift para eliminar") : "Solo operadores"), mx, my);
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0 && isCloseButtonHit(mx, my)) {
            client.setScreen(null);
            return true;
        }
        if (super.mouseClicked(mx, my, b)) return true;
        if (b != 0) return false;

        List<CommandPack> visible = shown();
        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = lBottom - lTop;
        int maxScroll = maxScrollPx(visible.size());
        if (maxScroll > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(my, lTop, viewH, maxScroll, visible.size());
            return true;
        }

        int cardX = panelX + 12;
        int cardW = panelW - 30;
        if (my >= lTop && my < lBottom && mx >= cardX && mx < cardX + cardW) {
            int relY = (int) (my - lTop + scroll);
            int idx = relY / ROW_STRIDE;
            if (idx >= 0 && idx < visible.size() && (relY % ROW_STRIDE) < CARD_H) {
                int cardY = lTop + idx * ROW_STRIDE - (int) Math.round(scroll);
                int delX = cardX + cardW - 30;
                int playX = delX - 30;
                int btnY = cardY + (CARD_H - 20) / 2;
                CommandPack target = visible.get(idx);
                if (mx >= playX && mx < playX + 24 && my >= btnY && my < btnY + 20) {
                    CommandPacksRequest.run(target);
                    runFeedback = "Ejecutando paquete…";
                    return true;
                }
                if (mx >= delX && mx < delX + 24 && my >= btnY && my < btnY + 20) {
                    if (canEdit && hasShiftDown()) {
                        packs.remove(target);
                        CommandPacksRequest.save(packs);
                    }
                    return true;
                }
                client.setScreen(new CommandPackEditorScreen(this, packs, target, canEdit));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v != 0) {
            int maxScroll = maxScrollPx(shown().size());
            targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - v * 28));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            List<CommandPack> visible = shown();
            updateScrollFromMouse(my, listTop(), listBottom() - listTop(), maxScrollPx(visible.size()), visible.size());
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

    private void updateScrollFromMouse(double my, int top, int viewH, int maxScroll, int count) {
        if (maxScroll == 0) return;
        int totalH = count * ROW_STRIDE - CARD_GAP;
        int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
        double f = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
        targetScroll = Math.max(0, Math.min(1, f)) * maxScroll;
        scroll = targetScroll;
    }

    private static List<CommandPack> decode(String json) {
        List<CommandPack> r = GSON.fromJson(json, TYPE);
        if (r == null) r = new ArrayList<>();
        r.sort(Comparator.comparing(p -> p.name == null ? "" : p.name, String.CASE_INSENSITIVE_ORDER));
        return r;
    }
}
