package smp.aeternum.carpet.client;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.network.ProjectUploadPayload;

public final class LitematicPickerScreen extends UiScreen {
    private static final int CARD_H = 40;
    private static final int CARD_GAP = 4;
    private static final int ROW_STRIDE = CARD_H + CARD_GAP;
    private static final int MAX_UPLOAD_BYTES = 1_000_000;

    private final Screen parent;
    private List<LitematicaBridge.ActivePlacement> placements = new ArrayList<>();
    private LitematicaBridge.ActivePlacement selected;
    private String errorMsg = "";

    private double scroll, targetScroll;
    private boolean draggingScrollbar;

    private UiTextField searchField;
    private UiTextField nameField;
    private UiButton uploadBtn;

    public LitematicPickerScreen(Screen parent) {
        super(Text.literal("Subir Proyecto"));
        this.parent = parent;
        reloadPlacements();
    }

    private void reloadPlacements() {
        placements = LitematicaBridge.getActivePlacements();
        if (selected != null) {
            selected = placements.stream()
                .filter(p -> p.name().equals(selected.name()) && p.origin().equals(selected.origin()))
                .findFirst().orElse(null);
        }
    }

    @Override
    protected void init() {
        String prevSearch = searchField != null ? searchField.getText() : "";
        String prevName = nameField != null ? nameField.getText() : "";
        clearChildren();
        computePanel(Math.min(640, (int) (width * 0.76)), Math.min(430, (int) (height * 0.80)));

        addDrawableChild(UiButton.ghost(panelX + 10, panelY + 10, 24, 24, "\u2190", b -> close()));

        searchField = new UiTextField(textRenderer, panelX + 12, panelY + 52, panelW - 24, 20, "Filtrar placements activos de Litematica…");
        searchField.setMaxLength(80);
        searchField.setText(prevSearch);
        searchField.setChangedListener(s -> { targetScroll = 0; scroll = 0; });
        addDrawableChild(searchField);

        int nameY = panelY + panelH - 64;
        nameField = new UiTextField(textRenderer, panelX + 12, nameY, panelW - 24, 20, "Título del proyecto (obligatorio)");
        nameField.setMaxLength(48);
        nameField.setText(prevName);
        addDrawableChild(nameField);

        int footerY = panelY + panelH - 28;
        addDrawableChild(UiButton.secondary(panelX + 12, footerY, 88, 20, "Actualizar", b -> reloadPlacements()));
        uploadBtn = UiButton.primary(panelX + panelW - 132, footerY, 120, 20, "Subir Proyecto", b -> submitUpload());
        addDrawableChild(uploadBtn);
    }

    private void submitUpload() {
        if (selected == null || !Files.isRegularFile(selected.schematicFile())) {
            errorMsg = "Selecciona un placement de Litematica.";
            return;
        }
        String projectName = nameField != null ? nameField.getText().trim() : "";
        if (projectName.isEmpty()) {
            errorMsg = "Escribe un título para el proyecto.";
            return;
        }
        try {
            long size = Files.size(selected.schematicFile());
            if (size > MAX_UPLOAD_BYTES) {
                errorMsg = "Archivo demasiado grande (" + (size / 1024) + " KB, máx 976 KB).";
                return;
            }
            byte[] bytes = Files.readAllBytes(selected.schematicFile());
            ClientPlayNetworking.send(new ProjectUploadPayload(
                projectName,
                bytes,
                true,
                selected.dimension(),
                selected.origin().getX(),
                selected.origin().getY(),
                selected.origin().getZ(),
                selected.rotation(),
                selected.mirror()
            ));
            close();
        } catch (IOException e) {
            errorMsg = "Error leyendo archivo: " + e.getMessage();
        }
    }

    private List<LitematicaBridge.ActivePlacement> filtered() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        if (q.isEmpty()) return placements;
        List<LitematicaBridge.ActivePlacement> out = new ArrayList<>();
        for (var p : placements) {
            String file = p.schematicFile().getFileName().toString().toLowerCase();
            if (p.name().toLowerCase().contains(q) || file.contains(q)) out.add(p);
        }
        return out;
    }

    private int listTop() { return panelY + 78; }
    private int listBottom() { return panelY + panelH - 72; }

    private int maxScrollPx(int count) {
        int contentH = count * ROW_STRIDE - CARD_GAP;
        int viewH = listBottom() - listTop();
        return Math.max(0, contentH - viewH);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        computePanel(Math.min(640, (int) (width * 0.76)), Math.min(430, (int) (height * 0.80)));
        List<LitematicaBridge.ActivePlacement> shown = filtered();
        int maxScroll = maxScrollPx(shown.size());
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        scroll += (targetScroll - scroll) * Math.min(1.0, d * 0.45 + 0.25);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        renderBackground(c, mx, my, d);
        AeternumTheme.drawPanel(c, panelX, panelY, panelW, panelH);
        AeternumTheme.drawHeader(c, panelX, panelY, panelW, 44);
        c.fill(panelX + 1, panelY + 1, panelX + 4, panelY + 43, AeternumTheme.ACCENT_GOLD);
        c.drawTextWithShadow(textRenderer, "SUBIR DESDE LITEMATICA", panelX + 38, panelY + 10, AeternumTheme.TEXT_PRIMARY);
        c.drawText(textRenderer, shown.size() + " placements activos detectados", panelX + 38, panelY + 25, AeternumTheme.TEXT_SECONDARY, false);
        drawCloseButton(c, mx, my);

        int lTop = listTop(), lBottom = listBottom(), viewH = lBottom - lTop;
        int rowX = panelX + 12, rowW = panelW - 30;

        if (!LitematicaBridge.isLitematicaLoaded()) {
            c.drawCenteredTextWithShadow(textRenderer, "Litematica no está instalado en este cliente.", width / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
        } else if (shown.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, "No hay placements activos en Litematica.", width / 2, lTop + viewH / 2 - 10, AeternumTheme.TEXT_SECONDARY);
            c.drawCenteredTextWithShadow(textRenderer, "Coloca y alinea tu plano en Litematica primero, luego pulsa Actualizar.", width / 2, lTop + viewH / 2 + 6, AeternumTheme.TEXT_MUTED);
        } else {
            c.enableScissor(panelX + 6, lTop, panelX + panelW - 10, lBottom);
            for (int i = 0; i < shown.size(); i++) {
                int ry = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
                if (ry + CARD_H < lTop || ry > lBottom) continue;

                LitematicaBridge.ActivePlacement p = shown.get(i);
                boolean isSel = p == selected;
                boolean hover = my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW && my >= ry && my < ry + CARD_H;
                int bg = isSel ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG);
                int border = isSel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE;
                AeternumTheme.drawCard(c, rowX, ry, rowW, CARD_H, bg, border);

                String fileName = p.schematicFile().getFileName().toString();
                c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, p.name(), rowW - 140), rowX + 10, ry + 7, AeternumTheme.TEXT_PRIMARY);

                try {
                    String kb = Math.max(1, Files.size(p.schematicFile()) / 1024) + " KB";
                    c.drawText(textRenderer, kb, rowX + rowW - textRenderer.getWidth(kb) - 10, ry + 8, AeternumTheme.TEXT_MUTED, false);
                } catch (IOException ignored) {}

                String meta = p.origin().getX() + ", " + p.origin().getY() + ", " + p.origin().getZ()
                    + "  ·  " + rotLabel(p.rotation()) + "  ·  " + fileName;
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, meta, rowW - 20), rowX + 10, ry + 23, AeternumTheme.TEXT_SECONDARY, false);
            }
            c.disableScissor();

            AeternumTheme.drawScrollbar(c, panelX + panelW - 12, lTop, lBottom, shown.size() * ROW_STRIDE - CARD_GAP, viewH, scroll, draggingScrollbar);
        }

        if (uploadBtn != null) uploadBtn.active = selected != null && nameField != null && !nameField.getText().trim().isEmpty();

        AeternumTheme.drawFooter(c, panelX, panelY + panelH - 36, panelW, panelY + panelH);
        if (!errorMsg.isEmpty()) {
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, errorMsg, panelW - 240), panelX + 108, panelY + panelH - 22, AeternumTheme.TEXT_PRIMARY, false);
        }

        if (searchField != null) searchField.renderBox(c, panelX + 12, panelY + 52, panelW - 24, 20, mx, my, d);
        if (nameField != null) nameField.renderBox(c, panelX + 12, panelY + panelH - 64, panelW - 24, 20, mx, my, d);
        super.render(c, mx, my, d);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0 && isCloseButtonHit(mx, my)) {
            close();
            return true;
        }
        if (super.mouseClicked(mx, my, b)) return true;
        if (b != 0) return false;

        List<LitematicaBridge.ActivePlacement> shown = filtered();
        int lTop = listTop(), lBottom = listBottom(), viewH = lBottom - lTop;
        int maxScroll = maxScrollPx(shown.size());
        if (maxScroll > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(my, lTop, viewH, maxScroll, shown.size());
            return true;
        }

        int rowX = panelX + 12, rowW = panelW - 30;
        if (my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW) {
            int relY = (int) (my - lTop + scroll);
            int idx = relY / ROW_STRIDE;
            if (idx >= 0 && idx < shown.size() && (relY % ROW_STRIDE) < CARD_H) {
                selected = shown.get(idx);
                errorMsg = "";
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v != 0) {
            int maxScroll = maxScrollPx(filtered().size());
            targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - v * 28));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            List<LitematicaBridge.ActivePlacement> shown = filtered();
            updateScrollFromMouse(my, listTop(), listBottom() - listTop(), maxScrollPx(shown.size()), shown.size());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        if (b == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mx, my, b);
    }

    private void updateScrollFromMouse(double my, int top, int viewH, int maxScroll, int count) {
        if (maxScroll == 0) return;
        int totalH = count * ROW_STRIDE - CARD_GAP;
        int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
        double f = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
        targetScroll = Math.max(0, Math.min(1, f)) * maxScroll;
        scroll = targetScroll;
    }

    private static String rotLabel(String rot) {
        return switch (rot) {
            case "CLOCKWISE_90" -> "90°";
            case "CLOCKWISE_180" -> "180°";
            case "COUNTERCLOCKWISE_90" -> "270°";
            default -> "0°";
        };
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
