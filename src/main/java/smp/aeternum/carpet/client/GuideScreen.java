package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.guides.GuideDocument;
import smp.aeternum.carpet.motd.MotdMarkup;

/** Personal formatted-text library. The guide markup is the same as the join MOTD. */
public final class GuideScreen extends UiScreen {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<List<GuideDocument>>() {}.getType();
    private static final int SIDEBAR_W = 160;
    private static final int SIDEBAR_ROW_H = 22;
    private static final int LINE_H = 11;

    private List<GuideDocument> guides;
    private boolean canEdit, editing, helpOpen, dirty;
    private int selected;
    private double sidebarScroll, targetSidebarScroll;
    private double readerScroll, targetReaderScroll;
    private boolean draggingReaderScrollbar;

    private UiTextField searchField;
    private UiTextField titleField;
    private EditBoxWidget editor;
    private UiButton renameButton, refreshButton, deleteButton, helpButton;
    private UiButton esButton, enButton;
    private long saveAt, refreshEnableAt;
    private String currentLang = "es";

    public GuideScreen(String json, boolean canEdit, String lang) {
        super(Text.literal("Guias"));
        guides = decode(json);
        this.canEdit = canEdit;
        this.currentLang = lang;
    }

    public void replaceData(String json, boolean canEdit, String lang) {
        if (editing && dirty) return;
        if (!lang.equals(currentLang)) return;
        String selectedTitle = current() == null ? null : current().title;
        guides = decode(json);
        this.canEdit = canEdit;
        if (selectedTitle != null) {
            for (int i = 0; i < guides.size(); i++) {
                if (guides.get(i).title.equals(selectedTitle)) {
                    selected = i;
                    return;
                }
            }
        }
        selected = Math.min(selected, Math.max(0, guides.size() - 1));
    }

    @Override
    protected void init() {
        build();
    }

    private void build() {
        String prevFilter = searchField != null ? searchField.getText() : "";
        clearChildren();
        editor = null;
        titleField = null;
        renameButton = null;
        refreshButton = null;
        deleteButton = null;
        helpButton = null;

        computePanel(Math.min(720, (int) (width * 0.82)), Math.min(440, (int) (height * 0.82)));

        esButton = UiButton.secondary(panelX + 126, panelY + 11, 28, 22, "ES", b -> {
            if (!currentLang.equals("es")) {
                currentLang = "es";
                GuideRequest.send("reload", "", "", currentLang);
                build();
            }
        });
        enButton = UiButton.secondary(panelX + 156, panelY + 11, 28, 22, "EN", b -> {
            if (!currentLang.equals("en")) {
                currentLang = "en";
                GuideRequest.send("reload", "", "", currentLang);
                build();
            }
        });
        esButton.setVariant(currentLang.equals("es") ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
        enButton.setVariant(currentLang.equals("en") ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
        addDrawableChild(esButton);
        addDrawableChild(enButton);

        searchField = new UiTextField(textRenderer, panelX + 8, panelY + 52, SIDEBAR_W - 16, 18, "Buscar guía…");
        searchField.setMaxLength(64);
        searchField.setText(prevFilter);
        searchField.setChangedListener(s -> {
            targetSidebarScroll = 0;
            sidebarScroll = 0;
        });
        addDrawableChild(searchField);

        int contentX = panelX + SIDEBAR_W + 10;
        int contentW = panelW - SIDEBAR_W - 22;
        int footerY = panelY + panelH - 28;

        if (editing && canEdit) {
            if (current() != null) {
                titleField = new UiTextField(textRenderer, contentX, panelY + 52, contentW - 30, 20, "Título de la guía");
                titleField.setText(current().title);
                titleField.setMaxLength(64);
                addDrawableChild(titleField);

                renameButton = UiButton.success(contentX + contentW - 24, panelY + 52, 24, 20, "\u2713", b -> {
                    if (current() != null && !titleField.getText().trim().equals(current().title)) {
                        GuideRequest.send("rename", current().title, titleField.getText().trim(), currentLang);
                    }
                });
                renameButton.active = false;
                addDrawableChild(renameButton);

                int editorH = panelH - 118;
                editor = EditBoxWidget.builder().x(contentX).y(panelY + 78).hasOverlay(false)
                    .build(textRenderer, contentW, Math.max(80, editorH), Text.literal("Texto de guia"));
                editor.setText(current().markdown);
                editor.setMaxLength(100_000);
                editor.setChangeListener(v -> {
                    dirty = true;
                    saveAt = System.currentTimeMillis() + 700;
                });
                addDrawableChild(editor);

                helpButton = UiButton.secondary(panelX + 36, footerY, 22, 20, "?", b -> helpOpen = !helpOpen);
                addDrawableChild(helpButton);

                deleteButton = UiButton.danger(panelX + 62, footerY, 22, 20, "\u00D7", b -> {
                    if (hasShiftDown() && current() != null) {
                        GuideRequest.send("delete", current().title, "", currentLang);
                    }
                });
                deleteButton.active = false;
                addDrawableChild(deleteButton);
            }

            addDrawableChild(UiButton.primary(panelX + 10, footerY, 22, 20, "+",
                b -> GuideRequest.send("create", "Guia nueva", "", currentLang)));

            addDrawableChild(UiButton.secondary(panelX + panelW - 86, footerY, 74, 20, "Leer", b -> {
                editing = false;
                helpOpen = false;
                build();
            }));
        } else if (canEdit) {
            refreshButton = UiButton.secondary(panelX + panelW - 166, footerY, 76, 20, "Refrescar", b -> {
                b.active = false;
                b.setFocused(false);
                refreshEnableAt = System.currentTimeMillis() + 150;
                GuideRequest.send("reload", "", "", currentLang);
            });
            addDrawableChild(refreshButton);
            addDrawableChild(UiButton.primary(panelX + panelW - 84, footerY, 72, 20, "Editar", b -> {
                editing = true;
                build();
            }));
        }
    }

    private GuideDocument current() {
        return guides.isEmpty() ? null : guides.get(Math.max(0, Math.min(selected, guides.size() - 1)));
    }

    private List<Integer> filteredIndices() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < guides.size(); i++) {
            if (q.isEmpty() || guides.get(i).title.toLowerCase().contains(q)) {
                out.add(i);
            }
        }
        return out;
    }

    @Override
    public void tick() {
        if (refreshButton != null && !refreshButton.active && System.currentTimeMillis() >= refreshEnableAt) {
            refreshButton.active = true;
        }
        if (dirty && editor != null && System.currentTimeMillis() >= saveAt && current() != null) {
            GuideRequest.send("save", current().title, editor.getText(), currentLang);
            dirty = false;
        }
    }

    private int sidebarTop() { return panelY + 76; }
    private int sidebarBottom() { return panelY + panelH - 40; }

    private int readerTop() { return panelY + 52; }
    private int readerBottom() { return panelY + panelH - 42; }
    private int readerX() { return panelX + SIDEBAR_W + 12; }
    private int readerW() { return panelW - SIDEBAR_W - 32; }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        computePanel(Math.min(720, (int) (width * 0.82)), Math.min(440, (int) (height * 0.82)));
        renderBackground(c, mx, my, d);

        String headerTitle = currentLang.equals("en") ? "GUIDES" : "GUÍAS";
        String subTitle = current() != null ? current().title : (guides.size() + " guías");
        drawWindowFrame(c, headerTitle, subTitle);
        drawCloseButton(c, mx, my);

        // Sidebar background
        c.fill(panelX + 1, panelY + 44, panelX + SIDEBAR_W, panelY + panelH - 36, AeternumTheme.SIDEBAR_BG);
        c.fill(panelX + SIDEBAR_W, panelY + 44, panelX + SIDEBAR_W + 1, panelY + panelH - 36, AeternumTheme.BORDER_SUBTLE);

        List<Integer> visibleIdx = filteredIndices();
        int sTop = sidebarTop();
        int sBottom = sidebarBottom();
        int sViewH = sBottom - sTop;
        int maxSidebarScroll = Math.max(0, visibleIdx.size() * SIDEBAR_ROW_H - sViewH);
        targetSidebarScroll = Math.max(0, Math.min(targetSidebarScroll, maxSidebarScroll));
        sidebarScroll += (targetSidebarScroll - sidebarScroll) * Math.min(1.0, d * 0.45 + 0.25);

        if (guides.isEmpty()) {
            c.drawText(textRenderer, "Sin guías.", panelX + 12, sTop + 8, AeternumTheme.TEXT_MUTED, false);
        } else if (visibleIdx.isEmpty()) {
            c.drawText(textRenderer, "Sin coincidencias.", panelX + 12, sTop + 8, AeternumTheme.TEXT_MUTED, false);
        } else {
            c.enableScissor(panelX + 2, sTop, panelX + SIDEBAR_W - 1, sBottom);
            for (int row = 0; row < visibleIdx.size(); row++) {
                int idx = visibleIdx.get(row);
                int ry = sTop + row * SIDEBAR_ROW_H - (int) Math.round(sidebarScroll);
                if (ry + SIDEBAR_ROW_H < sTop || ry > sBottom) continue;

                boolean isSelected = idx == selected;
                boolean hover = mx >= panelX + 4 && mx < panelX + SIDEBAR_W - 4 && my >= ry && my < ry + SIDEBAR_ROW_H - 2;
                if (isSelected) {
                    c.fill(panelX + 4, ry, panelX + SIDEBAR_W - 4, ry + SIDEBAR_ROW_H - 2, AeternumTheme.CARD_SELECTED);
                    c.fill(panelX + 4, ry, panelX + 7, ry + SIDEBAR_ROW_H - 2, AeternumTheme.ACCENT_GOLD);
                } else if (hover) {
                    c.fill(panelX + 4, ry, panelX + SIDEBAR_W - 4, ry + SIDEBAR_ROW_H - 2, AeternumTheme.CARD_HOVER);
                }

                int maxTitleW = (canEdit && editing) ? SIDEBAR_W - 42 : SIDEBAR_W - 18;
                String title = AeternumTheme.ellipsize(textRenderer, guides.get(idx).title, maxTitleW);
                c.drawTextWithShadow(textRenderer, title, panelX + 11, ry + 6,
                    isSelected ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);

                if (canEdit && editing) {
                    int upX = panelX + SIDEBAR_W - 28;
                    int dnX = panelX + SIDEBAR_W - 16;
                    c.drawText(textRenderer, "\u25B2", upX, ry + 6, idx > 0 ? AeternumTheme.TEXT_SECONDARY : AeternumTheme.TEXT_MUTED, false);
                    c.drawText(textRenderer, "\u25BC", dnX, ry + 6, idx < guides.size() - 1 ? AeternumTheme.TEXT_SECONDARY : AeternumTheme.TEXT_MUTED, false);
                }
            }
            c.disableScissor();
        }

        // Reader or Editor surface
        int rTop = readerTop();
        int rBottom = readerBottom();
        int rX = readerX();
        int rW = readerW();
        if (!editing && current() != null) {
            AeternumTheme.drawCard(c, rX - 6, rTop - 4, rW + 12, rBottom - rTop + 8, AeternumTheme.CARD_ALT_BG, AeternumTheme.BORDER_SUBTLE);
            List<OrderedText> lines = guideLines(current().markdown, rW - 10);
            int viewLines = Math.max(1, (rBottom - rTop) / LINE_H);
            int maxReaderScroll = Math.max(0, lines.size() - viewLines);
            targetReaderScroll = Math.max(0, Math.min(targetReaderScroll, maxReaderScroll));
            readerScroll += (targetReaderScroll - readerScroll) * Math.min(1.0, d * 0.45 + 0.25);
            readerScroll = Math.max(0, Math.min(readerScroll, maxReaderScroll));

            c.enableScissor(rX - 2, rTop, rX + rW + 2, rBottom);
            int startLine = (int) Math.floor(readerScroll);
            int fracPx = (int) Math.round((readerScroll - startLine) * LINE_H);
            for (int i = startLine; i < lines.size() && (i - startLine) * LINE_H - fracPx < (rBottom - rTop); i++) {
                c.drawTextWithShadow(textRenderer, lines.get(i), rX + 2, rTop + (i - startLine) * LINE_H - fracPx, AeternumTheme.TEXT_PRIMARY);
            }
            c.disableScissor();

            AeternumTheme.drawScrollbar(c, rX + rW + 8, rTop, rBottom, lines.size(), viewLines, readerScroll, draggingReaderScrollbar);
        }

        AeternumTheme.drawFooter(c, panelX, panelY + panelH - 36, panelW, panelY + panelH);

        if (editing && canEdit) {
            if (renameButton != null && titleField != null && current() != null) {
                renameButton.active = !titleField.getText().trim().isEmpty() && !titleField.getText().trim().equals(current().title);
            }
            if (deleteButton != null) {
                deleteButton.active = hasShiftDown();
            }
            String statusText = dirty ? "Guardando…" : "Guardado";
            int statusBg = dirty ? 0xFF2D2514 : 0xFF152920;
            int statusFg = dirty ? AeternumTheme.ACCENT_GOLD : AeternumTheme.ACCENT_GREEN;
            AeternumTheme.drawBadge(c, textRenderer, panelX + SIDEBAR_W + 12, panelY + panelH - 25, statusText, statusBg, statusFg);
        }

        if (searchField != null) {
            searchField.renderBox(c, panelX + 8, panelY + 52, SIDEBAR_W - 16, 18, mx, my, d);
        }
        if (titleField != null) {
            titleField.renderBox(c, titleField.boxX(), titleField.boxY(), (panelW - SIDEBAR_W - 22) - 30, 20, mx, my, d);
        }
        super.render(c, mx, my, d);

        if (editing && canEdit && helpOpen) {
            renderHelp(c);
        }
        renderTooltips(c, mx, my);
    }

    private void renderHelp(DrawContext c) {
        int hw = 320;
        int hh = 178;
        int hx = panelX + SIDEBAR_W + (panelW - SIDEBAR_W - hw) / 2;
        int hy = panelY + (panelH - hh) / 2;
        AeternumTheme.drawPanel(c, hx, hy, hw, hh);
        AeternumTheme.drawHeader(c, hx, hy, hw, 28);
        c.drawTextWithShadow(textRenderer, "FORMATO DE GUÍAS", hx + 12, hy + 10, AeternumTheme.ACCENT_GOLD);

        String[] help = {
            "Colores: \\black \\dark_blue \\dark_green \\dark_aqua",
            "\\dark_red \\dark_purple \\gold \\gray \\dark_gray",
            "\\blue \\green \\aqua \\red \\light_purple \\yellow \\white",
            "Estilos: \\bold \\italic \\underline \\strikethrough",
            "Reiniciar estilo: \\reset",
            "Ejemplo: \\gold\\boldTÍTULO\\reset  \\grayTexto normal"
        };
        for (int i = 0; i < help.length; i++) {
            c.drawText(textRenderer, help[i], hx + 12, hy + 40 + i * 20, AeternumTheme.TEXT_SECONDARY, false);
        }
    }

    private void renderTooltips(DrawContext c, int mx, int my) {
        if (isCloseButtonHit(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Cerrar"), mx, my);
        } else if (esButton != null && esButton.isMouseOver(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Cambiar idioma a Español"), mx, my);
        } else if (enButton != null && enButton.isMouseOver(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Switch language to English"), mx, my);
        } else if (renameButton != null && renameButton.isMouseOver(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal(renameButton.active ? "Confirmar nuevo nombre" : "Escribe un nombre distinto para renombrar"), mx, my);
        } else if (helpButton != null && helpButton.isMouseOver(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Ver sintaxis de formato"), mx, my);
        } else if (deleteButton != null && deleteButton.isMouseOver(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal(hasShiftDown() ? "Eliminar esta guía" : "Mantén Shift para eliminar"), mx, my);
        } else if (refreshButton != null && refreshButton.isMouseOver(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Recargar guías desde el servidor"), mx, my);
        }
    }

    private List<OrderedText> guideLines(String content, int width) {
        List<OrderedText> lines = new ArrayList<>();
        for (String raw : content.split("\\R", -1)) {
            List<OrderedText> wrapped = textRenderer.wrapLines(MotdMarkup.parse(raw), Math.max(40, width));
            if (wrapped.isEmpty()) lines.add(OrderedText.EMPTY);
            else lines.addAll(wrapped);
        }
        return lines;
    }

    private int maxReaderScroll() {
        if (current() == null) return 0;
        int viewLines = Math.max(1, (readerBottom() - readerTop()) / LINE_H);
        return Math.max(0, guideLines(current().markdown, readerW() - 10).size() - viewLines);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0 && isCloseButtonHit(mx, my)) {
            client.setScreen(null);
            return true;
        }
        if (super.mouseClicked(mx, my, b)) return true;
        if (b != 0) return false;

        int rTop = readerTop();
        int rBottom = readerBottom();
        int barX = readerX() + readerW() + 4;
        if (!editing && maxReaderScroll() > 0 && mx >= barX && mx <= barX + 12 && my >= rTop && my <= rBottom) {
            draggingReaderScrollbar = true;
            updateReaderScroll(my);
            return true;
        }

        int sTop = sidebarTop();
        int sBottom = sidebarBottom();
        if (mx >= panelX + 2 && mx < panelX + SIDEBAR_W && my >= sTop && my < sBottom) {
            List<Integer> visibleIdx = filteredIndices();
            int row = (int) ((my - sTop + sidebarScroll) / SIDEBAR_ROW_H);
            if (row >= 0 && row < visibleIdx.size()) {
                int idx = visibleIdx.get(row);
                if (canEdit && editing && mx >= panelX + SIDEBAR_W - 30 && mx < panelX + SIDEBAR_W - 18) {
                    if (idx > 0) GuideRequest.send("move_up", guides.get(idx).title, "", currentLang);
                    return true;
                }
                if (canEdit && editing && mx >= panelX + SIDEBAR_W - 18 && mx < panelX + SIDEBAR_W - 4) {
                    if (idx < guides.size() - 1) GuideRequest.send("move_down", guides.get(idx).title, "", currentLang);
                    return true;
                }
                selected = idx;
                readerScroll = 0;
                targetReaderScroll = 0;
                if (editing) {
                    dirty = false;
                    helpOpen = false;
                    build();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v == 0) return super.mouseScrolled(mx, my, h, v);
        if (mx >= panelX && mx < panelX + SIDEBAR_W && my >= sidebarTop() && my < sidebarBottom()) {
            int max = Math.max(0, filteredIndices().size() * SIDEBAR_ROW_H - (sidebarBottom() - sidebarTop()));
            targetSidebarScroll = Math.max(0, Math.min(max, targetSidebarScroll - v * 22));
            return true;
        }
        if (!editing && mx >= panelX + SIDEBAR_W && mx < panelX + panelW && my >= readerTop() && my < readerBottom() && current() != null) {
            targetReaderScroll = Math.max(0, Math.min(maxReaderScroll(), targetReaderScroll - v * 3));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingReaderScrollbar && button == 0) {
            updateReaderScroll(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingReaderScrollbar) {
            draggingReaderScrollbar = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void updateReaderScroll(double my) {
        int max = maxReaderScroll();
        if (max == 0) return;
        int top = readerTop(), bottom = readerBottom(), track = bottom - top;
        int viewLines = Math.max(1, track / LINE_H);
        int bar = Math.max(22, track * viewLines / (max + viewLines));
        double f = (my - top - bar / 2.0) / Math.max(1, track - bar);
        targetReaderScroll = Math.max(0, Math.min(1, f)) * max;
        readerScroll = targetReaderScroll;
    }

    @Override
    public void close() {
        if (editing) {
            editing = false;
            helpOpen = false;
            build();
        } else {
            client.setScreen(null);
        }
    }

    private static List<GuideDocument> decode(String json) {
        List<GuideDocument> result = GSON.fromJson(json, TYPE);
        return result == null ? new ArrayList<>() : result;
    }
}
