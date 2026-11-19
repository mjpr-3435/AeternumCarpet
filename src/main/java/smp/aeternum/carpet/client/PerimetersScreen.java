package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.network.PerimeterActionPayload;
import smp.aeternum.carpet.network.PerimeterNetworking.DataSnapshot;
import smp.aeternum.carpet.perimeters.BlockCluster;
import smp.aeternum.carpet.perimeters.Perimeter;

/** Unified Perimeters screen with two tabs per perimeter: Análisis (palette/clusters) and Vigilancia (World Eater). */
public final class PerimetersScreen extends UiScreen {
    private static final Gson GSON = new Gson();
    private static final int SIDEBAR_W = 148;
    private static final int SIDEBAR_ROW_H = 26;
    private static final int CLUSTER_CARD_H = 38;
    private static final int CLUSTER_GAP = 4;
    private static final int CLUSTER_STRIDE = CLUSTER_CARD_H + CLUSTER_GAP;
    private static final int PALETTE_TILE = 20;
    private static final int PALETTE_GAP = 4;
    private static final int TAB_ANALYSIS = 0;
    private static final int TAB_MONITOR = 1;

    private DataSnapshot data;
    private boolean canDelete;
    private String selectedId;
    private int activeTab = TAB_ANALYSIS;
    private boolean showPresetsBar;
    private boolean savingPreset;
    private String lastPresetName = "";
    private UiTextField presetNameField;
    private UiTextField targetYField;
    private UiTextField stallSecField;
    private String syncedMonitorPerimeterId;

    private double sidebarScroll, targetSidebarScroll;
    private double clusterScroll, targetClusterScroll;
    private boolean draggingScrollbar;

    public PerimetersScreen(String json, boolean canDelete) {
        super(Text.literal("Perímetros"));
        this.canDelete = canDelete;
        this.data = decode(json);
        pickDefaultSelection();
    }

    public void replaceData(String json, boolean canDelete) {
        this.canDelete = canDelete;
        this.data = decode(json);
        pickDefaultSelection();
        if (this.client != null && this.client.currentScreen instanceof BlockPalettePickerScreen picker) {
            Perimeter sel = selectedPerimeter();
            if (sel != null) picker.syncFromParent(sel.palette);
        }
        syncMonitorFields(false);
    }

    public DataSnapshot currentData() {
        return data;
    }

    private void pickDefaultSelection() {
        if (data.perimeters == null || data.perimeters.isEmpty()) {
            selectedId = null;
            return;
        }
        if (selectedId != null && selectedPerimeter() != null) return;
        selectedId = (data.pinnedId != null && findPerimeter(data.pinnedId) != null)
            ? data.pinnedId
            : data.perimeters.get(0).id;
    }

    private Perimeter findPerimeter(String id) {
        if (data.perimeters == null || id == null) return null;
        for (Perimeter p : data.perimeters) {
            if (id.equals(p.id)) return p;
        }
        return null;
    }

    private Perimeter selectedPerimeter() {
        return findPerimeter(selectedId);
    }

    @Override
    protected void init() {
        clearChildren();
        computePanel(Math.min(760, (int) (width * 0.88)), Math.min(460, (int) (height * 0.85)));

        if (activeTab == TAB_ANALYSIS && savingPreset) {
            int cx = contentX();
            int cy = panelY + 96;
            int cw = contentW();
            presetNameField = new UiTextField(textRenderer, cx, cy, Math.max(100, cw - 134), 20, "Nombre del preset...");
            presetNameField.setMaxLength(40);
            if (!lastPresetName.isEmpty()) presetNameField.setText(lastPresetName);
            presetNameField.setFocused(true);
            addDrawableChild(presetNameField);
            addDrawableChild(UiButton.primary(cx + cw - 128, cy, 62, 20, "Guardar", b -> submitSavePreset()));
            addDrawableChild(UiButton.secondary(cx + cw - 62, cy, 62, 20, "Cancelar", b -> {
                savingPreset = false;
                init();
            }));
        } else {
            presetNameField = null;
        }

        if (activeTab == TAB_MONITOR && selectedPerimeter() != null) {
            int cx = contentX();
            int cw = contentW();
            int c3Y = panelY + 238;
            int fieldX = cx + cw - 120;
            int r1Y = c3Y + 23;
            int r2Y = c3Y + 46;

            targetYField = new UiTextField(textRenderer, fieldX, r1Y, 56, 18, "-64..320");
            targetYField.setMaxLength(4);
            targetYField.setTextPredicate(s -> s.isEmpty() || s.matches("-?\\d{0,3}"));
            addDrawableChild(targetYField);

            stallSecField = new UiTextField(textRenderer, fieldX, r2Y, 56, 18, "5..600");
            stallSecField.setMaxLength(3);
            stallSecField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d{0,3}"));
            addDrawableChild(stallSecField);

            syncMonitorFields(true);
        } else {
            targetYField = null;
            stallSecField = null;
            syncedMonitorPerimeterId = null;
        }
    }

    private void syncMonitorFields(boolean force) {
        if (activeTab != TAB_MONITOR || targetYField == null || stallSecField == null) return;
        Perimeter sel = selectedPerimeter();
        if (sel == null) return;
        boolean changedPerimeter = !sel.id.equals(syncedMonitorPerimeterId);
        if (force || changedPerimeter || (!targetYField.isFocused() && !stallSecField.isFocused())) {
            targetYField.setText(String.valueOf(sel.effectiveTargetLowY()));
            stallSecField.setText(String.valueOf(sel.weStallSeconds));
            syncedMonitorPerimeterId = sel.id;
        }
    }

    private void submitSavePreset() {
        Perimeter p = selectedPerimeter();
        if (p == null || presetNameField == null) return;
        String name = presetNameField.getText().trim();
        if (name.isEmpty()) return;
        lastPresetName = name;
        ClientPlayNetworking.send(new PerimeterActionPayload("save_preset", p.id, name, ""));
        savingPreset = false;
        showPresetsBar = false;
        init();
    }

    private int contentX() { return panelX + SIDEBAR_W + 10; }
    private int contentW() { return panelW - SIDEBAR_W - 24; }
    private int sidebarTop() { return panelY + 50; }
    private int sidebarBottom() { return panelY + panelH - 38; }

    private int paletteBoxY() {
        return panelY + 94 + (showPresetsBar && !savingPreset ? 22 : 0) + (savingPreset ? 26 : 0);
    }

    private int clusterHeaderY() { return paletteBoxY() + 62; }
    private int listTop() { return clusterHeaderY() + 20; }
    private int listBottom() { return panelY + panelH - 38; }

    private int maxClusterScrollPx(int count) {
        int totalH = count * CLUSTER_STRIDE - CLUSTER_GAP;
        return Math.max(0, totalH - (listBottom() - listTop()));
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        computePanel(Math.min(760, (int) (width * 0.88)), Math.min(460, (int) (height * 0.85)));
        Perimeter sel = selectedPerimeter();

        List<Perimeter> perimeters = data.perimeters != null ? data.perimeters : List.of();
        int sTop = sidebarTop(), sBot = sidebarBottom();
        int maxS = Math.max(0, perimeters.size() * SIDEBAR_ROW_H - (sBot - sTop));
        targetSidebarScroll = Math.max(0, Math.min(targetSidebarScroll, maxS));
        sidebarScroll += (targetSidebarScroll - sidebarScroll) * Math.min(1.0, delta * 0.45 + 0.25);

        List<BlockCluster> clusters = sortedClusters(sel);
        int lTop = listTop(), lBot = listBottom();
        int maxC = maxClusterScrollPx(clusters.size());
        targetClusterScroll = Math.max(0, Math.min(targetClusterScroll, maxC));
        clusterScroll += (targetClusterScroll - clusterScroll) * Math.min(1.0, delta * 0.45 + 0.25);

        renderBackground(c, mx, my, delta);
        drawWindowFrame(c, "PERIMETERS", perimeters.size() + " región(es) · /perimeter <nombre> <pos1> <pos2>");
        drawCloseButton(c, mx, my);

        // Sidebar
        c.fill(panelX + 1, panelY + 44, panelX + SIDEBAR_W, panelY + panelH - 36, AeternumTheme.SIDEBAR_BG);
        c.fill(panelX + SIDEBAR_W, panelY + 44, panelX + SIDEBAR_W + 1, panelY + panelH - 36, AeternumTheme.BORDER_SUBTLE);

        c.enableScissor(panelX + 2, sTop, panelX + SIDEBAR_W - 1, sBot);
        for (int i = 0; i < perimeters.size(); i++) {
            Perimeter p = perimeters.get(i);
            int ry = sTop + i * SIDEBAR_ROW_H - (int) Math.round(sidebarScroll);
            if (ry + SIDEBAR_ROW_H < sTop || ry > sBot) continue;
            boolean isSel = p.id.equals(selectedId);
            boolean isPinned = p.id.equals(data.pinnedId);
            boolean hover = my >= sTop && my < sBot && mx >= panelX + 4 && mx < panelX + SIDEBAR_W - 4 && my >= ry && my < ry + SIDEBAR_ROW_H - 2;
            if (isSel) {
                c.fill(panelX + 4, ry, panelX + SIDEBAR_W - 4, ry + SIDEBAR_ROW_H - 2, AeternumTheme.CARD_SELECTED);
                c.fill(panelX + 4, ry, panelX + 7, ry + SIDEBAR_ROW_H - 2, AeternumTheme.ACCENT_GOLD);
            } else if (hover) {
                c.fill(panelX + 4, ry, panelX + SIDEBAR_W - 4, ry + SIDEBAR_ROW_H - 2, AeternumTheme.CARD_HOVER);
            }
            String prefix = isPinned ? "\u2022 " : "";
            String lbl = AeternumTheme.ellipsize(textRenderer, prefix + p.name, SIDEBAR_W - 32);
            c.drawTextWithShadow(textRenderer, lbl, panelX + 10, ry + 4, isSel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
            String sub = p.weEnabled
                ? "WE " + formatWeStatus(p.weStatus)
                : ((!p.complete && p.totalChunks > 0 && p.scannedChunks > 0)
                    ? ((p.scannedChunks * 100) / p.totalChunks) + "%"
                    : p.clusters.size() + "z · " + p.totalMatchedBlocks + "b");
            c.drawText(textRenderer, sub, panelX + 10, ry + 14, p.weEnabled ? AeternumTheme.ACCENT_GOLD : AeternumTheme.TEXT_MUTED, false);

            if (canDelete) {
                int dx = panelX + SIDEBAR_W - 18;
                boolean overDel = hover && mx >= dx;
                c.drawText(textRenderer, "\u00D7", dx + 3, ry + 7, overDel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_MUTED, false);
            }
        }
        c.disableScissor();

        int cx = contentX();
        int cw = contentW();
        String tooltipText = null;

        if (sel == null) {
            c.drawCenteredTextWithShadow(textRenderer, "No hay perímetros definidos.", cx + cw / 2, panelY + panelH / 2 - 12, AeternumTheme.TEXT_SECONDARY);
            c.drawCenteredTextWithShadow(textRenderer, "Usa /perimeter <nombre> <pos1> <pos2> para crear uno.", cx + cw / 2, panelY + panelH / 2 + 4, AeternumTheme.TEXT_MUTED);
        } else {
            // Top header bar: perimeter bounds + Tabs (Análisis | Vigilancia) + HUD
            String boundsTxt = "[" + sel.minX + "," + sel.minY + "," + sel.minZ + "] .. [" + sel.maxX + "," + sel.maxY + "," + sel.maxZ + "]";
            c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, sel.name + "  " + boundsTxt, cw - 204), cx, panelY + 51, AeternumTheme.TEXT_PRIMARY);

            int tabY = panelY + 46;
            boolean pinned = sel.id.equals(data.pinnedId);
            drawPillButton(c, cx + cw - 198, tabY, 64, 20, "Análisis", activeTab == TAB_ANALYSIS, mx, my);
            drawPillButton(c, cx + cw - 130, tabY, 72, 20, "Vigilancia", activeTab == TAB_MONITOR, mx, my);
            drawPillButton(c, cx + cw - 54, tabY, 54, 20, pinned ? "\u2022 HUD" : "HUD", pinned, mx, my);

            if (activeTab == TAB_ANALYSIS) {
                tooltipText = renderAnalysisTab(c, sel, clusters, cx, cw, mx, my, delta);
            } else {
                renderMonitorTab(c, sel, cx, cw, mx, my, delta);
            }
        }

        AeternumTheme.drawFooter(c, panelX, panelY + panelH - 36, panelW, panelY + panelH);
        String footerText = activeTab == TAB_ANALYSIS
            ? "Clic en una zona para mostrar/ocultar · Update re-escanea al instante"
            : "Vigilancia en game-ticks reales · Los eventos se envían al Discord de AeternumBot";
        c.drawText(textRenderer, footerText, panelX + 14, panelY + panelH - 22, AeternumTheme.TEXT_MUTED, false);

        super.render(c, mx, my, delta);
        if (tooltipText != null) {
            c.drawTooltip(textRenderer, Text.literal(tooltipText), mx, my);
        }
    }

    private String renderAnalysisTab(DrawContext c, Perimeter sel, List<BlockCluster> clusters, int cx, int cw, int mx, int my, float delta) {
        String tooltipText = null;
        int actY = panelY + 70;
        drawPillButton(c, cx, actY, 68, 18, "+ Bloques", false, mx, my);
        drawPillButton(c, cx + 72, actY, 56, 18, "Presets", showPresetsBar, mx, my);
        drawPillButton(c, cx + 132, actY, 58, 18, "Update", false, mx, my);

        if (showPresetsBar && !savingPreset) {
            int py = panelY + 92;
            int px = cx;
            for (String presetName : data.presets.keySet()) {
                String shownName = AeternumTheme.ellipsize(textRenderer, presetName, 76);
                int pw = textRenderer.getWidth(shownName) + 22;
                if (px + pw > cx + cw - 108) break;
                boolean overPill = mx >= px && mx < px + pw && my >= py && my < py + 18;
                boolean overDel = overPill && mx >= px + pw - 14;
                boolean active = presetName.equals(lastPresetName);
                AeternumTheme.drawCard(c, px, py, pw, 18,
                    active ? AeternumTheme.CARD_SELECTED : (overPill ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG),
                    active ? AeternumTheme.ACCENT_GOLD : (overPill ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE));
                c.drawTextWithShadow(textRenderer, shownName, px + 6, py + 5,
                    active ? AeternumTheme.ACCENT_GOLD : AeternumTheme.TEXT_PRIMARY);
                c.drawText(textRenderer, "\u00D7", px + pw - 11, py + 5,
                    overDel ? 0xFFFF6B6B : AeternumTheme.TEXT_MUTED, false);
                if (overDel) {
                    tooltipText = "Eliminar preset '" + presetName + "'";
                } else if (overPill) {
                    tooltipText = "Clic para cargar '" + presetName + "' · Para editar, modifica la paleta y guarda con este nombre";
                }
                px += pw + 4;
            }
            drawPillButton(c, cx + cw - 104, py, 104, 18, "+ Guardar Preset", false, mx, my);
        }

        if (savingPreset && presetNameField != null) {
            presetNameField.renderBox(c, cx, panelY + 96, Math.max(100, cw - 134), 20, mx, my, delta);
        }

        int pBoxY = paletteBoxY();
        AeternumTheme.drawCard(c, cx, pBoxY, cw, 56, AeternumTheme.CARD_BG, AeternumTheme.BORDER_SUBTLE);
        c.drawText(textRenderer, "PALETA (" + sel.palette.size() + ") · clic en un bloque para quitar", cx + 8, pBoxY + 5, AeternumTheme.TEXT_MUTED, false);

        if (sel.palette.isEmpty()) {
            c.drawText(textRenderer, "Paleta vacía — pulsa '+ Bloques' o carga un Preset para empezar.", cx + 8, pBoxY + 28, AeternumTheme.TEXT_SECONDARY, false);
        } else {
            int tileX0 = cx + 8;
            int tileY0 = pBoxY + 17;
            int perRow = Math.max(1, (cw - 16) / (PALETTE_TILE + PALETTE_GAP));
            int maxTiles = Math.min(sel.palette.size(), perRow);
            for (int i = 0; i < maxTiles; i++) {
                String entry = sel.palette.get(i);
                int tx = tileX0 + i * (PALETTE_TILE + PALETTE_GAP);
                boolean hover = mx >= tx && mx < tx + PALETTE_TILE && my >= tileY0 && my < tileY0 + PALETTE_TILE;
                AeternumTheme.drawCard(c, tx, tileY0, PALETTE_TILE, PALETTE_TILE,
                    hover ? AeternumTheme.CARD_HOVER : AeternumTheme.SIDEBAR_BG,
                    hover ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE);
                c.drawItem(PerimeterHudOverlay.iconForEntry(entry), tx + 2, tileY0 + 2);
                if (entry.startsWith("#")) {
                    c.drawText(textRenderer, "#", tx + 2, tileY0 + 1, AeternumTheme.ACCENT_GOLD, false);
                }
                if (hover) {
                    tooltipText = PerimeterHudOverlay.shortName(entry) + " (" + entry + ") · clic para quitar";
                }
            }
            if (sel.palette.size() > maxTiles) {
                String more = "+" + (sel.palette.size() - maxTiles);
                c.drawText(textRenderer, more, tileX0 + maxTiles * (PALETTE_TILE + PALETTE_GAP), tileY0 + 6, AeternumTheme.TEXT_MUTED, false);
            }
        }

        int chY = clusterHeaderY();
        String cTitle = !sel.complete && sel.totalChunks > 0
            ? "Escaneando: " + sel.scannedChunks + "/" + sel.totalChunks + " chunks..."
            : "Zonas detectadas (" + clusters.size() + ") · " + sel.totalMatchedBlocks + " bloques";
        c.drawText(textRenderer, cTitle, cx, chY + 4, AeternumTheme.TEXT_SECONDARY, false);
        drawPillButton(c, cx + cw - 116, chY, 52, 16, "Todo", false, mx, my);
        drawPillButton(c, cx + cw - 60, chY, 60, 16, "Limpiar", false, mx, my);

        int lTop = listTop(), lBot = listBottom();
        if (clusters.isEmpty()) {
            String emptyMsg = sel.complete ? "No se encontraron bloques de la paleta." : "Sin resultados todavía — pulsa Update.";
            c.drawCenteredTextWithShadow(textRenderer, emptyMsg, cx + cw / 2, lTop + (lBot - lTop) / 2 - 4, AeternumTheme.TEXT_MUTED);
        } else {
            int cardW = cw - 6;
            c.enableScissor(panelX + SIDEBAR_W + 2, lTop, panelX + panelW - 8, lBot);
            for (int i = 0; i < clusters.size(); i++) {
                int cy = lTop + i * CLUSTER_STRIDE - (int) Math.round(clusterScroll);
                if (cy + CLUSTER_CARD_H < lTop || cy > lBot) continue;

                BlockCluster cl = clusters.get(i);
                boolean focused = PerimeterGuide.isSelected(cl);
                boolean hover = my >= lTop && my < lBot && mx >= cx && mx < cx + cardW && my >= cy && my < cy + CLUSTER_CARD_H;
                int bg = focused ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG);
                int border = focused ? AeternumTheme.ACCENT_GOLD : AeternumTheme.BORDER_SUBTLE;
                AeternumTheme.drawCard(c, cx, cy, cardW, CLUSTER_CARD_H, bg, border);

                c.drawItem(PerimeterHudOverlay.iconForEntry(cl.blockId), cx + 6, cy + 11);
                String title = PerimeterHudOverlay.shortName(cl.blockId) + "  \u00D7" + cl.count;
                c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, title, cardW - 120), cx + 28, cy + 6, AeternumTheme.TEXT_PRIMARY);
                String coords = "Centro [" + cl.centerX + ", " + cl.centerY + ", " + cl.centerZ + "]  ("
                    + (cl.maxX - cl.minX + 1) + "\u00D7" + (cl.maxY - cl.minY + 1) + "\u00D7" + (cl.maxZ - cl.minZ + 1) + ")";
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, coords, cardW - 120), cx + 28, cy + 21, AeternumTheme.TEXT_SECONDARY, false);

                String tag = focused ? "Ocultar" : "Mostrar";
                int tagX = cx + cardW - textRenderer.getWidth(tag) - 10;
                c.drawTextWithShadow(textRenderer, tag, tagX, cy + 15, focused ? AeternumTheme.ACCENT_GOLD : AeternumTheme.TEXT_MUTED);
            }
            c.disableScissor();
            AeternumTheme.drawScrollbar(c, panelX + panelW - 12, lTop, lBot,
                clusters.size() * CLUSTER_STRIDE - CLUSTER_GAP, lBot - lTop, clusterScroll, draggingScrollbar);
        }
        return tooltipText;
    }

    private void renderMonitorTab(DrawContext c, Perimeter sel, int cx, int cw, int mx, int my, float delta) {
        // Card 1: Monitor status
        int c1Y = panelY + 74;
        AeternumTheme.drawCard(c, cx, c1Y, cw, 86, AeternumTheme.CARD_BG, sel.weEnabled ? AeternumTheme.ACCENT_GOLD : AeternumTheme.BORDER_SUBTLE);
        c.drawTextWithShadow(textRenderer, "MONITOR DE WORLD EATER", cx + 10, c1Y + 8, AeternumTheme.ACCENT_GOLD);

        String statusLabel = formatWeStatus(sel.weStatus);
        int stColor = switch (sel.weStatus != null ? sel.weStatus : "IDLE") {
            case "STALLED" -> 0xFFFF6B6B;
            case "LOW_Y" -> 0xFF5CE07B;
            case "UNLOADED" -> AeternumTheme.TEXT_MUTED;
            case "RUNNING" -> AeternumTheme.TEXT_PRIMARY;
            default -> AeternumTheme.TEXT_SECONDARY;
        };
        c.drawTextWithShadow(textRenderer, "Estado: " + statusLabel, cx + 10, c1Y + 24, stColor);

        String tntYStr = sel.weCurrentTntY == Integer.MIN_VALUE ? "--" : String.valueOf(sel.weCurrentTntY);
        c.drawText(textRenderer, "TNT Y actual: " + tntYStr + "   (Objetivo: Y <= " + sel.effectiveTargetLowY() + ")", cx + 10, c1Y + 40, AeternumTheme.TEXT_SECONDARY, false);
        c.drawText(textRenderer, "Chunks cargados: " + sel.weLoadedPct + "%   ·   Dimensión: " + sel.dimension, cx + 10, c1Y + 54, AeternumTheme.TEXT_MUTED, false);

        if (sel.weHint != null && !sel.weHint.isEmpty()) {
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, "Posible blocker: " + sel.weHint, cw - 20), cx + 10, c1Y + 69, 0xFFFFB74D, false);
        } else {
            c.drawText(textRenderer, "La detección de atascos se pausa automáticamente si la zona se descarga.", cx + 10, c1Y + 69, AeternumTheme.TEXT_MUTED, false);
        }

        drawPillButton(c, cx + cw - 112, c1Y + 8, 102, 20, sel.weEnabled ? "Vigilar: ON" : "Vigilar: OFF", sel.weEnabled, mx, my);

        // Card 2: Bot Grid
        int c2Y = panelY + 168;
        AeternumTheme.drawCard(c, cx, c2Y, cw, 64, AeternumTheme.CARD_BG, AeternumTheme.BORDER_SUBTLE);
        c.drawTextWithShadow(textRenderer, "GRILLA DE BOTS (MÍNIMO SOLAPE)", cx + 10, c2Y + 8, AeternumTheme.TEXT_PRIMARY);
        c.drawText(textRenderer, "Bots necesarios: " + sel.wePreviewBots + "   ·   Activos en el server: " + sel.weActiveBots, cx + 10, c2Y + 24, AeternumTheme.TEXT_SECONDARY, false);
        c.drawText(textRenderer, "Usa el flujo normal de reloadFakePlayers al reiniciar.", cx + 10, c2Y + 40, AeternumTheme.TEXT_MUTED, false);

        drawPillButton(c, cx + cw - 178, c2Y + 20, 84, 22, "Spawn Bots", false, mx, my);
        drawPillButton(c, cx + cw - 90, c2Y + 20, 80, 22, "Matar Bots", false, mx, my);

        // Card 3: Dimension Target Y & Stall Timeout
        int c3Y = panelY + 238;
        AeternumTheme.drawCard(c, cx, c3Y, cw, 72, AeternumTheme.CARD_BG, AeternumTheme.BORDER_SUBTLE);
        c.drawTextWithShadow(textRenderer, "PARÁMETROS DE ALERTA", cx + 10, c3Y + 8, AeternumTheme.TEXT_PRIMARY);

        int fieldX = cx + cw - 120;
        int btnX = cx + cw - 58;
        int r1Y = c3Y + 23;
        c.drawText(textRenderer, "Capa baja (Target Y)", cx + 10, r1Y + 5, AeternumTheme.TEXT_SECONDARY, false);
        if (targetYField != null) {
            targetYField.renderBox(c, fieldX, r1Y, 56, 18, mx, my, delta);
        }
        drawPillButton(c, btnX, r1Y, 48, 18, "Auto", false, mx, my);

        int r2Y = c3Y + 46;
        c.drawText(textRenderer, "Atasco sin explosiones (s)", cx + 10, r2Y + 5, AeternumTheme.TEXT_SECONDARY, false);
        if (stallSecField != null) {
            stallSecField.renderBox(c, fieldX, r2Y, 56, 18, mx, my, delta);
        }
        drawPillButton(c, btnX, r2Y, 48, 18, "Aplicar", false, mx, my);
    }

    private static String formatWeStatus(String status) {
        if (status == null) return "INACTIVO";
        return switch (status) {
            case "RUNNING" -> "ACTIVO";
            case "STALLED" -> "ATASCADO";
            case "LOW_Y" -> "CAPA BAJA";
            case "UNLOADED" -> "DESCARGADA";
            default -> "INACTIVO";
        };
    }

    private void drawPillButton(DrawContext c, int x, int y, int w, int h, String label, boolean active, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        int bg = active ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG);
        int border = active ? AeternumTheme.ACCENT_GOLD : (hover ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE);
        AeternumTheme.drawCard(c, x, y, w, h, bg, border);
        int tw = textRenderer.getWidth(label);
        c.drawTextWithShadow(textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, active ? AeternumTheme.ACCENT_GOLD : AeternumTheme.TEXT_PRIMARY);
    }

    private static List<BlockCluster> sortedClusters(Perimeter p) {
        if (p == null || p.clusters == null) return List.of();
        MinecraftClient mc = MinecraftClient.getInstance();
        List<BlockCluster> copy = new ArrayList<>(p.clusters);
        if (mc.player != null) {
            copy.sort(Comparator.comparingInt(cl -> {
                double dx = cl.centerX - mc.player.getX();
                double dy = cl.centerY - mc.player.getY();
                double dz = cl.centerZ - mc.player.getZ();
                return (int) (dx * dx + dy * dy + dz * dz);
            }));
        }
        return copy;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && isCloseButtonHit(mx, my)) {
            close();
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;

        // Sidebar clicks
        int sTop = sidebarTop(), sBot = sidebarBottom();
        List<Perimeter> perimeters = data.perimeters != null ? data.perimeters : List.of();
        if (mx >= panelX + 2 && mx < panelX + SIDEBAR_W && my >= sTop && my < sBot) {
            int idx = (int) ((my - sTop + sidebarScroll) / SIDEBAR_ROW_H);
            if (idx >= 0 && idx < perimeters.size()) {
                Perimeter p = perimeters.get(idx);
                if (canDelete && mx >= panelX + SIDEBAR_W - 18) {
                    ClientPlayNetworking.send(new PerimeterActionPayload("delete", p.id, "", ""));
                    return true;
                }
                selectedId = p.id;
                clusterScroll = targetClusterScroll = 0;
                if (activeTab == TAB_MONITOR) init();
                return true;
            }
        }

        Perimeter sel = selectedPerimeter();
        if (sel == null) return false;

        int cx = contentX();
        int cw = contentW();

        // Top tabs + HUD button
        int tabY = panelY + 46;
        if (my >= tabY && my < tabY + 20) {
            if (mx >= cx + cw - 198 && mx < cx + cw - 134) {
                activeTab = TAB_ANALYSIS;
                init();
                return true;
            }
            if (mx >= cx + cw - 130 && mx < cx + cw - 58) {
                activeTab = TAB_MONITOR;
                savingPreset = false;
                init();
                return true;
            }
            if (mx >= cx + cw - 54 && mx < cx + cw) {
                String nextPin = sel.id.equals(data.pinnedId) ? "" : sel.id;
                ClientPlayNetworking.send(new PerimeterActionPayload("pin_hud", nextPin, "", ""));
                return true;
            }
        }

        if (activeTab == TAB_MONITOR) {
            return handleMonitorClick(sel, cx, cw, mx, my);
        }
        return handleAnalysisClick(sel, cx, cw, mx, my);
    }

    private boolean handleMonitorClick(Perimeter sel, int cx, int cw, double mx, double my) {
        int c1Y = panelY + 74;
        if (my >= c1Y + 8 && my < c1Y + 28 && mx >= cx + cw - 112 && mx < cx + cw - 10) {
            ClientPlayNetworking.send(new PerimeterActionPayload("we_toggle", sel.id, "", ""));
            return true;
        }

        int c2Y = panelY + 168;
        if (my >= c2Y + 20 && my < c2Y + 42) {
            if (mx >= cx + cw - 178 && mx < cx + cw - 94) {
                ClientPlayNetworking.send(new PerimeterActionPayload("we_spawn_bots", sel.id, "", ""));
                return true;
            }
            if (mx >= cx + cw - 90 && mx < cx + cw - 10) {
                ClientPlayNetworking.send(new PerimeterActionPayload("we_kill_bots", sel.id, "", ""));
                return true;
            }
        }

        int c3Y = panelY + 238;
        int btnX = cx + cw - 58;
        int r1Y = c3Y + 23;
        if (my >= r1Y && my < r1Y + 18 && mx >= btnX && mx < btnX + 48) {
            int autoY = Perimeter.defaultLowYFor(sel.dimension);
            int stall = Math.max(5, Math.min(600, parseIntOr(stallSecField, sel.weStallSeconds)));
            if (targetYField != null) targetYField.setText(String.valueOf(autoY));
            if (stallSecField != null) stallSecField.setText(String.valueOf(stall));
            setFocused(null);
            return sendWeConfig(sel, autoY, stall);
        }

        int r2Y = c3Y + 46;
        if (my >= r2Y && my < r2Y + 18 && mx >= btnX && mx < btnX + 48) {
            return submitWeConfig();
        }
        return false;
    }

    private int parseIntOr(UiTextField field, int fallback) {
        if (field == null) return fallback;
        String raw = field.getText().trim();
        if (raw.isEmpty() || "-".equals(raw)) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean submitWeConfig() {
        Perimeter sel = selectedPerimeter();
        if (sel == null) return false;
        int y = Math.max(-64, Math.min(320, parseIntOr(targetYField, sel.effectiveTargetLowY())));
        int s = Math.max(5, Math.min(600, parseIntOr(stallSecField, sel.weStallSeconds)));
        if (targetYField != null) targetYField.setText(String.valueOf(y));
        if (stallSecField != null) stallSecField.setText(String.valueOf(s));
        setFocused(null);
        return sendWeConfig(sel, y, s);
    }

    private boolean sendWeConfig(Perimeter sel, int targetY, int stallSec) {
        int clampedY = Math.max(-64, Math.min(320, targetY));
        int clampedS = Math.max(5, Math.min(600, stallSec));
        ClientPlayNetworking.send(new PerimeterActionPayload("we_config", sel.id, String.valueOf(clampedY), String.valueOf(clampedS)));
        return true;
    }

    private boolean handleAnalysisClick(Perimeter sel, int cx, int cw, double mx, double my) {
        int actY = panelY + 70;
        if (my >= actY && my < actY + 18) {
            if (mx >= cx && mx < cx + 68) {
                if (this.client != null) {
                    this.client.setScreen(new BlockPalettePickerScreen(this, sel));
                }
                return true;
            }
            if (mx >= cx + 72 && mx < cx + 128) {
                showPresetsBar = !showPresetsBar;
                savingPreset = false;
                init();
                return true;
            }
            if (mx >= cx + 132 && mx < cx + 190) {
                ClientPlayNetworking.send(new PerimeterActionPayload("update", sel.id, "", ""));
                return true;
            }
        }

        if (showPresetsBar && !savingPreset) {
            int py = panelY + 92;
            if (my >= py && my < py + 18) {
                if (mx >= cx + cw - 104 && mx < cx + cw) {
                    savingPreset = true;
                    init();
                    return true;
                }
                int px = cx;
                for (Map.Entry<String, List<String>> e : data.presets.entrySet()) {
                    String presetName = e.getKey();
                    String shownName = AeternumTheme.ellipsize(textRenderer, presetName, 76);
                    int pw = textRenderer.getWidth(shownName) + 22;
                    if (px + pw > cx + cw - 108) break;
                    if (mx >= px && mx < px + pw) {
                        if (mx >= px + pw - 14) {
                            if (presetName.equals(lastPresetName)) lastPresetName = "";
                            ClientPlayNetworking.send(new PerimeterActionPayload("delete_preset", sel.id, presetName, ""));
                        } else {
                            lastPresetName = presetName;
                            ClientPlayNetworking.send(new PerimeterActionPayload("apply_preset", sel.id, presetName, ""));
                        }
                        return true;
                    }
                    px += pw + 4;
                }
            }
        }

        int pBoxY = paletteBoxY();
        int tileX0 = cx + 8;
        int tileY0 = pBoxY + 17;
        int perRow = Math.max(1, (cw - 16) / (PALETTE_TILE + PALETTE_GAP));
        int maxTiles = Math.min(sel.palette.size(), perRow);
        if (my >= tileY0 && my < tileY0 + PALETTE_TILE && mx >= tileX0 && mx < tileX0 + maxTiles * (PALETTE_TILE + PALETTE_GAP)) {
            int idx = (int) ((mx - tileX0) / (PALETTE_TILE + PALETTE_GAP));
            int rel = (int) ((mx - tileX0) % (PALETTE_TILE + PALETTE_GAP));
            if (rel < PALETTE_TILE && idx >= 0 && idx < maxTiles) {
                String entry = sel.palette.get(idx);
                ClientPlayNetworking.send(new PerimeterActionPayload("remove_palette", sel.id, entry, ""));
                return true;
            }
        }

        int chY = clusterHeaderY();
        List<BlockCluster> clusters = sortedClusters(sel);
        if (my >= chY && my < chY + 16) {
            if (mx >= cx + cw - 116 && mx < cx + cw - 64) {
                PerimeterGuide.selectAll(clusters);
                return true;
            }
            if (mx >= cx + cw - 60 && mx < cx + cw) {
                PerimeterGuide.clear();
                return true;
            }
        }

        int lTop = listTop(), lBot = listBottom();
        int maxC = maxClusterScrollPx(clusters.size());
        if (maxC > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBot) {
            draggingScrollbar = true;
            updateClusterScroll(my, lTop, lBot - lTop, maxC, clusters.size());
            return true;
        }
        if (my >= lTop && my < lBot && mx >= cx && mx < cx + cw - 6) {
            int relY = (int) (my - lTop + clusterScroll);
            int idx = relY / CLUSTER_STRIDE;
            if (idx >= 0 && idx < clusters.size() && (relY % CLUSTER_STRIDE) < CLUSTER_CARD_H) {
                PerimeterGuide.toggle(clusters.get(idx));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (savingPreset && (keyCode == 257 || keyCode == 335)) {
            submitSavePreset();
            return true;
        }
        if (savingPreset && keyCode == 256) {
            savingPreset = false;
            init();
            return true;
        }
        if (activeTab == TAB_MONITOR && (keyCode == 257 || keyCode == 335)
                && ((targetYField != null && targetYField.isFocused())
                    || (stallSecField != null && stallSecField.isFocused()))) {
            submitWeConfig();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v == 0) return super.mouseScrolled(mx, my, h, v);
        if (mx >= panelX && mx < panelX + SIDEBAR_W && my >= sidebarTop() && my < sidebarBottom()) {
            int total = data.perimeters != null ? data.perimeters.size() : 0;
            int maxS = Math.max(0, total * SIDEBAR_ROW_H - (sidebarBottom() - sidebarTop()));
            targetSidebarScroll = Math.max(0, Math.min(maxS, targetSidebarScroll - v * 20));
            return true;
        }
        if (activeTab == TAB_ANALYSIS && mx >= panelX + SIDEBAR_W && mx < panelX + panelW && my >= listTop() && my < listBottom()) {
            int maxC = maxClusterScrollPx(sortedClusters(selectedPerimeter()).size());
            targetClusterScroll = Math.max(0, Math.min(maxC, targetClusterScroll - v * 28));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (activeTab == TAB_ANALYSIS && draggingScrollbar && button == 0) {
            List<BlockCluster> clusters = sortedClusters(selectedPerimeter());
            updateClusterScroll(my, listTop(), listBottom() - listTop(), maxClusterScrollPx(clusters.size()), clusters.size());
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

    private void updateClusterScroll(double my, int top, int viewH, int maxScroll, int count) {
        if (maxScroll == 0) return;
        int totalH = count * CLUSTER_STRIDE - CLUSTER_GAP;
        int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
        double frac = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
        targetClusterScroll = Math.max(0, Math.min(1, frac)) * maxScroll;
        clusterScroll = targetClusterScroll;
    }

    private static DataSnapshot decode(String json) {
        try {
            DataSnapshot s = GSON.fromJson(json, DataSnapshot.class);
            if (s != null) {
                if (s.perimeters == null) s.perimeters = new ArrayList<>();
                if (s.presets == null) s.presets = Map.of();
                return s;
            }
        } catch (Exception ignored) {}
        return new DataSnapshot();
    }
}
