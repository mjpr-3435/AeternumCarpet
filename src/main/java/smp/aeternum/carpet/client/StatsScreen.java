package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.network.StatsNetworking.RegistryEntrySnapshot;
import smp.aeternum.carpet.network.StatsNetworking.StatsSnapshot;
import smp.aeternum.carpet.network.StatsRegistryActionPayload;

public final class StatsScreen extends UiScreen {
    private static final Gson GSON = new Gson();
    private static final int TAB_BAR_H = 24;
    private static final int SIDEBAR_W = 156;
    private static final int SIDEBAR_ROW_H = 19;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 4;
    private static final int ROW_STRIDE = CARD_H + CARD_GAP;
    private static final int UNEXPECTED_ROW_H = 22;

    private enum Tab {
        GUIDE,
        REGISTRY,
        UNEXPECTED
    }

    private enum Filter {
        ALL("Todos"),
        HUMAN("Humans"),
        BOT("Bots"),
        UNKNOWN("Unknown"),
        ONLINE("Online"),
        OFFLINE("Offline"),
        NAMED("Con nombre"),
        UNNAMED("Sin nombre"),
        ANOMALY("Anomalías");

        final String label;

        Filter(String label) {
            this.label = label;
        }
    }

    private enum SortMode {
        NAME("Nombre"),
        UNNAMED_FIRST("Sin nombre"),
        TYPE("Tipo"),
        ORIGIN("Online/Offline"),
        ANOMALY("Anomalías");

        final String label;

        SortMode(String label) {
            this.label = label;
        }

        SortMode next() {
            SortMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    private List<RegistryEntrySnapshot> entries = List.of();
    private final Map<String, RegistryEntrySnapshot> entriesByUuid = new HashMap<>();
    private final List<String> displayOrder = new ArrayList<>();
    private boolean recomputeOrderOnNextSnapshot = true;
    private List<String> unexpectedFiles = List.of();
    private boolean lookupRunning;
    private int lookupDone;
    private int lookupTotal;
    private int lookupResolved;
    private Tab activeTab = Tab.GUIDE;
    private Filter activeFilter = Filter.ALL;
    private SortMode activeSort = SortMode.NAME;

    private String statusMessage = "";
    private boolean statusError;

    private double listScroll, targetListScroll;
    private boolean draggingScrollbar;

    private UiTextField searchField;
    private UiButton sortButton;
    private UiButton lookupAllButton;
    private UiButton rescanButton;
    private UiButton healAnomaliesButton;
    private UiButton deleteAllButton;
    private String hoveredUnexpectedDelete;
    private String hoveredRowDeleteUuid;
    private String hoveredRowHealUuid;
    private String armedDeleteUuid;
    private long armedDeleteUntilMs;

    public StatsScreen(String json) {
        super(Text.literal("Stats Admin"));
        applySnapshot(json);
    }

    public void replaceData(String json, String status, boolean error) {
        applySnapshot(json);
        if (status != null && !status.isEmpty()) {
            this.statusMessage = status;
            this.statusError = error;
        }
    }

    private void applySnapshot(String json) {
        try {
            StatsSnapshot snap = GSON.fromJson(json, StatsSnapshot.class);
            if (snap != null) {
                this.entries = snap.entries != null ? snap.entries : List.of();
                this.unexpectedFiles = snap.unexpectedFiles != null ? snap.unexpectedFiles : List.of();
                this.lookupRunning = snap.lookupRunning;
                this.lookupDone = snap.lookupDone;
                this.lookupTotal = snap.lookupTotal;
                this.lookupResolved = snap.lookupResolved;
                this.entriesByUuid.clear();
                for (RegistryEntrySnapshot e : this.entries) {
                    if (e.uuid != null) {
                        this.entriesByUuid.put(e.uuid, e);
                    }
                }
                if (recomputeOrderOnNextSnapshot || displayOrder.isEmpty()) {
                    recomputeDisplayOrder();
                    recomputeOrderOnNextSnapshot = false;
                } else {
                    for (RegistryEntrySnapshot e : this.entries) {
                        if (e.uuid != null && !displayOrder.contains(e.uuid)) {
                            displayOrder.add(e.uuid);
                        }
                    }
                }
                return;
            }
        } catch (Exception ignored) {
        }
        this.entries = List.of();
        this.entriesByUuid.clear();
        this.displayOrder.clear();
        this.unexpectedFiles = List.of();
    }

    private void recomputeDisplayOrder() {
        Comparator<RegistryEntrySnapshot> byNameOrUuid = Comparator
            .comparing((RegistryEntrySnapshot e) -> e.name == null || e.name.isEmpty())
            .thenComparing(e -> (e.name == null || e.name.isEmpty()) ? e.uuid : e.name, String.CASE_INSENSITIVE_ORDER);
        Comparator<RegistryEntrySnapshot> byUnnamedFirst = Comparator
            .comparing((RegistryEntrySnapshot e) -> e.name != null && !e.name.isEmpty())
            .thenComparing(e -> (e.name == null || e.name.isEmpty()) ? e.uuid : e.name, String.CASE_INSENSITIVE_ORDER);
        Comparator<RegistryEntrySnapshot> comparator = switch (activeSort) {
            case NAME -> byNameOrUuid;
            case UNNAMED_FIRST -> byUnnamedFirst;
            case TYPE -> Comparator.comparingInt((RegistryEntrySnapshot e) -> switch (e.type) {
                case "HUMAN" -> 0;
                case "BOT" -> 1;
                default -> 2;
            }).thenComparing(byNameOrUuid);
            case ORIGIN -> Comparator.comparingInt((RegistryEntrySnapshot e) -> switch (e.uuidVersion) {
                case 4 -> 0;
                case 3 -> 1;
                default -> 2;
            }).thenComparing(byNameOrUuid);
            case ANOMALY -> Comparator.comparing((RegistryEntrySnapshot e) -> !e.anomaly).thenComparing(byNameOrUuid);
        };
        List<RegistryEntrySnapshot> sorted = new ArrayList<>(entries);
        sorted.sort(comparator);
        displayOrder.clear();
        for (RegistryEntrySnapshot e : sorted) {
            if (e.uuid != null) {
                displayOrder.add(e.uuid);
            }
        }
    }

    @Override
    protected void init() {
        String prevQuery = searchField != null ? searchField.getText() : "";
        clearChildren();
        computePanel(Math.min(780, (int) (width * 0.88)), Math.min(470, (int) (height * 0.85)));

        int contentTop = panelY + 44 + TAB_BAR_H;
        if (activeTab == Tab.REGISTRY) {
            int contentX = panelX + SIDEBAR_W + 10;
            int contentW = panelW - SIDEBAR_W - 22;
            int sortW = 132;
            int searchW = Math.max(100, contentW - sortW - 6);
            searchField = new UiTextField(textRenderer, contentX, contentTop + 8, searchW, 20, "Buscar nombre o UUID…");
            searchField.setMaxLength(80);
            searchField.setText(prevQuery);
            searchField.setChangedListener(s -> {
                targetListScroll = 0;
                listScroll = 0;
            });
            addDrawableChild(searchField);

            sortButton = UiButton.secondary(contentX + searchW + 6, contentTop + 8, sortW, 20, "Orden: " + activeSort.label, b -> {
                activeSort = activeSort.next();
                b.setLabel("Orden: " + activeSort.label);
                recomputeDisplayOrder();
                targetListScroll = 0;
                listScroll = 0;
            });
            addDrawableChild(sortButton);

            deleteAllButton = null;
            lookupAllButton = UiButton.secondary(panelX + 8, panelY + panelH - 114, SIDEBAR_W - 16, 22,
                lookupRunning ? "Cancelar" : "Consultar online", b -> {
                    if (lookupRunning) {
                        ClientPlayNetworking.send(new StatsRegistryActionPayload("cancel_lookup_all", "", ""));
                    } else {
                        ClientPlayNetworking.send(new StatsRegistryActionPayload("lookup_all_online", "", ""));
                    }
                });
            addDrawableChild(lookupAllButton);

            rescanButton = UiButton.secondary(panelX + 8, panelY + panelH - 88, SIDEBAR_W - 16, 22, "Re-escanear", b -> {
                statusMessage = "Re-escaneando archivos…";
                statusError = false;
                armedDeleteUuid = null;
                recomputeOrderOnNextSnapshot = true;
                ClientPlayNetworking.send(new StatsRegistryActionPayload("rescan", "", ""));
            });
            addDrawableChild(rescanButton);

            healAnomaliesButton = UiButton.primary(panelX + 8, panelY + panelH - 62, SIDEBAR_W - 16, 22, "Subsanar todo", b -> {
                if (countFor(Filter.ANOMALY) <= 0) return;
                statusMessage = "Subsanando anomalías…";
                statusError = false;
                armedDeleteUuid = null;
                ClientPlayNetworking.send(new StatsRegistryActionPayload("heal_anomalies", "", ""));
            });
            healAnomaliesButton.active = countFor(Filter.ANOMALY) > 0;
            addDrawableChild(healAnomaliesButton);
        } else if (activeTab == Tab.UNEXPECTED) {
            searchField = null;
            sortButton = null;
            lookupAllButton = null;
            healAnomaliesButton = null;
            deleteAllButton = UiButton.danger(panelX + panelW - 216, contentTop + 8, 98, 20, "Borrar todo", b -> {
                if (!hasShiftDown() || unexpectedFiles.isEmpty()) return;
                statusMessage = "Borrando todos los archivos raros…";
                statusError = false;
                ClientPlayNetworking.send(new StatsRegistryActionPayload("delete_all_unexpected", "", ""));
            });
            deleteAllButton.active = !unexpectedFiles.isEmpty() && hasShiftDown();
            addDrawableChild(deleteAllButton);

            rescanButton = UiButton.secondary(panelX + panelW - 114, contentTop + 8, 102, 20, "Re-escanear", b -> {
                statusMessage = "Re-escaneando archivos…";
                statusError = false;
                ClientPlayNetworking.send(new StatsRegistryActionPayload("rescan", "", ""));
            });
            addDrawableChild(rescanButton);
        } else {
            searchField = null;
            sortButton = null;
            lookupAllButton = null;
            healAnomaliesButton = null;
            deleteAllButton = null;
            rescanButton = null;
        }
    }

    private List<RegistryEntrySnapshot> filteredEntries() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<RegistryEntrySnapshot> out = new ArrayList<>();
        for (String uuid : displayOrder) {
            RegistryEntrySnapshot e = entriesByUuid.get(uuid);
            if (e == null || !matchesFilter(e)) continue;
            if (!q.isEmpty()) {
                String n = e.name != null ? e.name.toLowerCase(Locale.ROOT) : "";
                String u = e.uuid != null ? e.uuid.toLowerCase(Locale.ROOT) : "";
                if (!n.contains(q) && !u.contains(q)) continue;
            }
            out.add(e);
        }
        return out;
    }

    private boolean matchesFilter(RegistryEntrySnapshot e) {
        return switch (activeFilter) {
            case ALL -> true;
            case HUMAN -> "HUMAN".equals(e.type);
            case BOT -> "BOT".equals(e.type);
            case UNKNOWN -> "UNKNOWN".equals(e.type);
            case ONLINE -> e.uuidVersion == 4;
            case OFFLINE -> e.uuidVersion == 3;
            case NAMED -> e.name != null && !e.name.isEmpty();
            case UNNAMED -> e.name == null || e.name.isEmpty();
            case ANOMALY -> e.anomaly;
        };
    }

    private int countFor(Filter f) {
        int count = 0;
        for (RegistryEntrySnapshot e : entries) {
            switch (f) {
                case ALL -> count++;
                case HUMAN -> { if ("HUMAN".equals(e.type)) count++; }
                case BOT -> { if ("BOT".equals(e.type)) count++; }
                case UNKNOWN -> { if ("UNKNOWN".equals(e.type)) count++; }
                case ONLINE -> { if (e.uuidVersion == 4) count++; }
                case OFFLINE -> { if (e.uuidVersion == 3) count++; }
                case NAMED -> { if (e.name != null && !e.name.isEmpty()) count++; }
                case UNNAMED -> { if (e.name == null || e.name.isEmpty()) count++; }
                case ANOMALY -> { if (e.anomaly) count++; }
            }
        }
        return count;
    }

    private int countUnnamedOnline() {
        int count = 0;
        for (RegistryEntrySnapshot e : entries) {
            if (e.uuidVersion == 4 && (e.name == null || e.name.isEmpty())) {
                count++;
            }
        }
        return count;
    }

    private int tabBarY() {
        return panelY + 44;
    }

    private int listTop() {
        return activeTab == Tab.GUIDE ? (tabBarY() + TAB_BAR_H + 8) : (tabBarY() + TAB_BAR_H + 34);
    }

    private int listBottom() {
        return panelY + panelH - 42;
    }

    private int maxListScrollPx(int count, int stride) {
        int viewH = Math.max(1, listBottom() - listTop());
        int totalH = Math.max(0, count * stride - (stride == ROW_STRIDE ? CARD_GAP : 0));
        return Math.max(0, totalH - viewH);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        hoveredUnexpectedDelete = null;
        hoveredRowDeleteUuid = null;
        hoveredRowHealUuid = null;
        if (armedDeleteUuid != null && System.currentTimeMillis() > armedDeleteUntilMs) {
            armedDeleteUuid = null;
        }
        renderBackground(c, mx, my, delta);

        long anomalyCount = entries.stream().filter(e -> e.anomaly).count();
        String subtitle = entries.size() + " UUIDs  ·  " + anomalyCount + " anomalías";
        if (!unexpectedFiles.isEmpty()) {
            subtitle += "  ·  " + unexpectedFiles.size() + " archivos raros";
        }
        drawWindowFrame(c, "STATS ADMIN", subtitle);
        drawCloseButton(c, mx, my);

        drawTabBar(c, mx, my);

        if (activeTab == Tab.GUIDE) {
            renderGuideTab(c, delta);
        } else if (activeTab == Tab.REGISTRY) {
            renderRegistryTab(c, mx, my, delta);
        } else {
            renderUnexpectedTab(c, mx, my, delta);
        }

        // Footer
        int footerY = panelY + panelH - 36;
        AeternumTheme.drawFooter(c, panelX, footerY, panelW, panelY + panelH);
        if (lookupRunning && lookupTotal > 0) {
            int barW = Math.min(220, Math.max(120, panelW / 3));
            int barX = panelX + panelW - barW - 14;
            int barY = footerY + 20;
            float ratio = (float) lookupDone / Math.max(1, lookupTotal);
            int pct = Math.round(ratio * 100f);
            String progTxt = "Mojang: " + lookupDone + "/" + lookupTotal + " (" + pct + "%) \u00B7 " + lookupResolved + " ok";
            c.drawText(textRenderer, progTxt, barX, footerY + 8, AeternumTheme.TEXT_PRIMARY, false);
            AeternumTheme.drawProgressBar(c, barX, barY, barW, 6, ratio, AeternumTheme.ACCENT_GOLD);

            int textMaxW = Math.max(60, barX - panelX - 24);
            if (!statusMessage.isEmpty()) {
                int col = statusError ? 0xFFE56B6B : AeternumTheme.TEXT_SECONDARY;
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, statusMessage, textMaxW), panelX + 14, footerY + 14, col, false);
            } else {
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, "Consultando perfiles en cola...", textMaxW), panelX + 14, footerY + 14, AeternumTheme.TEXT_MUTED, false);
            }
        } else if (!statusMessage.isEmpty()) {
            int col = statusError ? 0xFFE56B6B : AeternumTheme.TEXT_SECONDARY;
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, statusMessage, panelW - 28), panelX + 14, footerY + 14, col, false);
        } else {
            String hint = switch (activeTab) {
                case GUIDE -> "Guía rápida de administración para /stats";
                case REGISTRY -> "Click en el tipo para rotar: UNKNOWN \u2192 HUMAN \u2192 BOT";
                case UNEXPECTED -> "Archivos fuera de formato <uuid>.dat / <uuid>.json";
            };
            c.drawText(textRenderer, hint, panelX + 14, footerY + 14, AeternumTheme.TEXT_MUTED, false);
        }

        if (lookupAllButton != null) {
            if (lookupRunning) {
                lookupAllButton.setLabel("Cancelar");
                lookupAllButton.setVariant(UiButton.Variant.DANGER);
                lookupAllButton.active = true;
            } else {
                lookupAllButton.setLabel("Consultar online");
                lookupAllButton.setVariant(UiButton.Variant.SECONDARY);
                lookupAllButton.active = countUnnamedOnline() > 0;
            }
        }
        if (healAnomaliesButton != null) {
            healAnomaliesButton.active = countFor(Filter.ANOMALY) > 0;
        }
        if (deleteAllButton != null) {
            deleteAllButton.active = !unexpectedFiles.isEmpty() && hasShiftDown();
        }

        super.render(c, mx, my, delta);
        if (searchField != null) {
            searchField.renderBox(c, searchField.boxX(), searchField.boxY(), searchField.getWidth() + 12, 20, mx, my, delta);
        }

        if (isCloseButtonHit(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Cerrar"), mx, my);
        } else if (sortButton != null && sortButton.isHovered()) {
            c.drawTooltip(textRenderer, Text.literal("Cambiar orden (Nombre, Sin nombre, Tipo, Online/Offline, Anomalías)"), mx, my);
        } else if (lookupAllButton != null && lookupAllButton.isHovered()) {
            String tip = lookupRunning
                ? "Cancelar consulta masiva en curso"
                : ("Consultar en cola los " + countUnnamedOnline() + " UUIDs online sin nombre");
            c.drawTooltip(textRenderer, Text.literal(tip), mx, my);
        } else if (healAnomaliesButton != null && healAnomaliesButton.isHovered()) {
            List<Text> tip = List.of(
                Text.literal("Subsanar anomalías:"),
                Text.literal("\u2022 Restaura .dat_old si falta .dat"),
                Text.literal("\u2022 Crea stats y advancements vacíos si existe .dat"),
                Text.literal("\u2022 No inventa inventarios ni .dat desde cero"),
                Text.literal("\u2022 Omite jugadores o bots conectados actualmente")
            );
            c.drawTooltip(textRenderer, tip, mx, my);
        } else if (deleteAllButton != null && deleteAllButton.isHovered()) {
            String tip = unexpectedFiles.isEmpty()
                ? "No hay archivos raros"
                : (hasShiftDown() ? "Clic para borrar todos los archivos raros" : "Mantén Shift para habilitar Borrar todo");
            c.drawTooltip(textRenderer, Text.literal(tip), mx, my);
        } else if (activeTab == Tab.REGISTRY && hoveredRowHealUuid != null) {
            List<Text> tip = List.of(
                Text.literal("Subsanar esta UUID:"),
                Text.literal("\u2022 Restaura .dat_old si falta .dat"),
                Text.literal("\u2022 Crea stats y advancements vacíos si existe .dat"),
                Text.literal("\u2022 No inventa inventarios ni .dat desde cero"),
                Text.literal("\u2022 No aplica si el jugador o bot está conectado")
            );
            c.drawTooltip(textRenderer, tip, mx, my);
        } else if (activeTab == Tab.REGISTRY && hoveredRowDeleteUuid != null) {
            String tip;
            if (!hasShiftDown()) {
                tip = "Mantén Shift para borrar registro y sus archivos";
            } else if (hoveredRowDeleteUuid.equals(armedDeleteUuid)) {
                tip = "Clic de nuevo con Shift para BORRAR entrada y archivos";
            } else {
                tip = "Shift + Clic para armar borrado";
            }
            c.drawTooltip(textRenderer, Text.literal(tip), mx, my);
        } else if (activeTab == Tab.UNEXPECTED && hoveredUnexpectedDelete != null) {
            c.drawTooltip(textRenderer, Text.literal("Borrar " + hoveredUnexpectedDelete), mx, my);
        }
    }

    private void drawTabBar(DrawContext c, int mx, int my) {
        int ty = tabBarY();
        c.fill(panelX + 1, ty, panelX + panelW - 1, ty + TAB_BAR_H, 0xFF0E0E12);
        c.fill(panelX + 1, ty + TAB_BAR_H - 1, panelX + panelW - 1, ty + TAB_BAR_H, AeternumTheme.BORDER_SUBTLE);

        int x = panelX + 10;
        x += drawTabButton(c, x, ty + 3, "Guía", activeTab == Tab.GUIDE, false, mx, my) + 6;
        x += drawTabButton(c, x, ty + 3, "Registro (" + entries.size() + ")", activeTab == Tab.REGISTRY, false, mx, my) + 6;
        String unexpLabel = "Archivos raros (" + unexpectedFiles.size() + ")";
        drawTabButton(c, x, ty + 3, unexpLabel, activeTab == Tab.UNEXPECTED, !unexpectedFiles.isEmpty(), mx, my);
    }

    private int drawTabButton(DrawContext c, int x, int y, String label, boolean selected, boolean alert, int mx, int my) {
        int w = textRenderer.getWidth(label) + 18;
        int h = 18;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        int bg = selected ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_ALT_BG);
        int border = selected ? AeternumTheme.BORDER_BRIGHT : (alert ? 0xFFE59B53 : AeternumTheme.BORDER_SUBTLE);
        AeternumTheme.drawCard(c, x, y, w, h, bg, border);
        if (selected) {
            c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, alert ? 0xFFE59B53 : AeternumTheme.ACCENT_GOLD);
        }
        int fg = selected ? AeternumTheme.TEXT_PRIMARY : (alert ? 0xFFE59B53 : AeternumTheme.TEXT_SECONDARY);
        c.drawTextWithShadow(textRenderer, label, x + 9, y + 5, fg);
        return w;
    }

    private void renderRegistryTab(DrawContext c, int mx, int my, float delta) {
        int topY = tabBarY() + TAB_BAR_H;
        int footerY = panelY + panelH - 36;

        // Sidebar
        c.fill(panelX + 1, topY, panelX + SIDEBAR_W, footerY, AeternumTheme.SIDEBAR_BG);
        c.fill(panelX + SIDEBAR_W, topY, panelX + SIDEBAR_W + 1, footerY, AeternumTheme.BORDER_SUBTLE);

        Filter[] filters = Filter.values();
        int fTop = topY + 8;
        for (int i = 0; i < filters.length; i++) {
            Filter f = filters[i];
            int ry = fTop + i * SIDEBAR_ROW_H;
            boolean sel = f == activeFilter;
            boolean hover = mx >= panelX + 4 && mx < panelX + SIDEBAR_W - 4 && my >= ry && my < ry + SIDEBAR_ROW_H - 2;
            if (sel) {
                c.fill(panelX + 4, ry, panelX + SIDEBAR_W - 4, ry + SIDEBAR_ROW_H - 2, AeternumTheme.CARD_SELECTED);
                c.fill(panelX + 4, ry, panelX + 7, ry + SIDEBAR_ROW_H - 2, AeternumTheme.ACCENT_GOLD);
            } else if (hover) {
                c.fill(panelX + 4, ry, panelX + SIDEBAR_W - 4, ry + SIDEBAR_ROW_H - 2, AeternumTheme.CARD_HOVER);
            }
            int cnt = countFor(f);
            String countStr = String.valueOf(cnt);
            int countW = textRenderer.getWidth(countStr);
            c.drawTextWithShadow(textRenderer, f.label, panelX + 12, ry + 6, sel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
            c.drawText(textRenderer, countStr, panelX + SIDEBAR_W - 10 - countW, ry + 6,
                (f == Filter.ANOMALY && cnt > 0) ? 0xFFE59B53 : AeternumTheme.TEXT_MUTED, false);
        }

        // List
        List<RegistryEntrySnapshot> shown = filteredEntries();
        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = Math.max(1, lBottom - lTop);
        int maxL = maxListScrollPx(shown.size(), ROW_STRIDE);
        targetListScroll = Math.max(0, Math.min(targetListScroll, maxL));
        listScroll += (targetListScroll - listScroll) * Math.min(1.0, delta * 0.45 + 0.25);
        listScroll = Math.max(0, Math.min(listScroll, maxL));

        int cardX = panelX + SIDEBAR_W + 10;
        int cardW = panelW - SIDEBAR_W - 26;

        if (shown.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, "Sin registros para este filtro.", cardX + cardW / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
            return;
        }

        c.enableScissor(panelX + SIDEBAR_W + 2, lTop, panelX + panelW - 8, lBottom);
        for (int i = 0; i < shown.size(); i++) {
            int cy = lTop + i * ROW_STRIDE - (int) Math.round(listScroll);
            if (cy + CARD_H < lTop || cy > lBottom) continue;

            RegistryEntrySnapshot item = shown.get(i);
            boolean inArea = my >= lTop && my < lBottom;
            boolean overCard = inArea && mx >= cardX && mx < cardX + cardW && my >= cy && my < cy + CARD_H;

            int bg = overCard ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG;
            int border = item.anomaly ? 0x70E59B53 : (overCard ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE);
            AeternumTheme.drawCard(c, cardX, cy, cardW, CARD_H, bg, border);

            // Right-side controls at title level
            int delW = 18;
            int delH = 18;
            int delX = cardX + cardW - delW - 6;
            int rotW = 68;
            int rotH = 18;
            int rotX = delX - rotW - 4;
            int rotY = cy + 5;

            boolean showHeal = item.anomaly;
            int healW = 54;
            int healX = rotX - healW - 4;
            int leftOfHeal = showHeal ? healX : rotX;

            boolean showLookup = item.uuidVersion == 4 && (item.name == null || item.name.isEmpty());
            int lookW = 66;
            int lookX = leftOfHeal - lookW - 4;
            int controlsLeftX = showLookup ? lookX : leftOfHeal;

            // Delete entry button (Shift + confirmation)
            boolean overDel = inArea && mx >= delX && mx < delX + delW && my >= rotY && my < rotY + delH;
            if (overDel) {
                hoveredRowDeleteUuid = item.uuid;
            }
            boolean shift = hasShiftDown();
            boolean armed = item.uuid.equals(armedDeleteUuid);
            int delBg = !shift ? 0xFF131318 : (armed ? (overDel ? 0xFF8C2A2A : 0xFF6B1E1E) : (overDel ? 0xFF3A2024 : 0xFF221619));
            int delBorder = !shift ? AeternumTheme.BORDER_SUBTLE : (armed ? 0xFFFF6B6B : (overDel ? 0xFFE56B6B : 0x80E56B6B));
            int delFg = !shift ? AeternumTheme.TEXT_MUTED : (armed ? 0xFFFFFFFF : 0xFFE58A8A);
            AeternumTheme.drawCard(c, delX, rotY, delW, delH, delBg, delBorder);
            c.drawTextWithShadow(textRenderer, armed ? "!" : "\u00D7", delX + (armed ? 7 : 6), rotY + 5, delFg);

            // Rotator button
            boolean overRot = inArea && mx >= rotX && mx < rotX + rotW && my >= rotY && my < rotY + rotH;
            int rotBg = switch (item.type) {
                case "HUMAN" -> overRot ? 0xFF26382B : 0xFF1A281E;
                case "BOT" -> overRot ? 0xFF24303D : 0xFF18222C;
                default -> overRot ? 0xFF2B2B35 : 0xFF1A1A20;
            };
            int rotBorder = switch (item.type) {
                case "HUMAN" -> 0xFF7AD694;
                case "BOT" -> 0xFF72B6F2;
                default -> AeternumTheme.BORDER_BRIGHT;
            };
            int rotFg = switch (item.type) {
                case "HUMAN" -> 0xFF9DF0B4;
                case "BOT" -> 0xFF9CD0FF;
                default -> AeternumTheme.TEXT_SECONDARY;
            };
            AeternumTheme.drawCard(c, rotX, rotY, rotW, rotH, rotBg, rotBorder);
            c.drawTextWithShadow(textRenderer, item.type, rotX + (rotW - textRenderer.getWidth(item.type)) / 2, rotY + 5, rotFg);

            if (showHeal) {
                boolean overHeal = inArea && mx >= healX && mx < healX + healW && my >= rotY && my < rotY + rotH;
                if (overHeal) {
                    hoveredRowHealUuid = item.uuid;
                }
                AeternumTheme.drawCard(c, healX, rotY, healW, rotH,
                    overHeal ? 0xFF3A2D18 : 0xFF281F12,
                    overHeal ? 0xFFFFB866 : 0xFFE59B53);
                String healLbl = "Subsanar";
                c.drawTextWithShadow(textRenderer, healLbl, healX + (healW - textRenderer.getWidth(healLbl)) / 2, rotY + 5, 0xFFFFC57A);
            }

            if (showLookup) {
                boolean overLook = inArea && mx >= lookX && mx < lookX + lookW && my >= rotY && my < rotY + rotH;
                AeternumTheme.drawCard(c, lookX, rotY, lookW, rotH, overLook ? 0xFF2D2D38 : 0xFF1B1B22,
                    overLook ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_BRIGHT);
                String lookLbl = "Consultar";
                c.drawTextWithShadow(textRenderer, lookLbl, lookX + (lookW - textRenderer.getWidth(lookLbl)) / 2, rotY + 5, AeternumTheme.TEXT_PRIMARY);
            }

            // Title + UUID + Online/Offline badge
            int cursorX = cardX + 10;
            int maxTitleArea = controlsLeftX - cursorX - 8;
            String verText = switch (item.uuidVersion) {
                case 4 -> "ONLINE";
                case 3 -> "OFFLINE";
                default -> "v" + item.uuidVersion;
            };
            int verW = textRenderer.getWidth(verText) + 10;
            int verBg = item.uuidVersion == 4 ? 0xFF1A2A22 : (item.uuidVersion == 3 ? 0xFF24242D : 0xFF2A2020);
            int verFg = item.uuidVersion == 4 ? 0xFF8BE0B2 : (item.uuidVersion == 3 ? 0xFFB8B8C8 : 0xFFE59B53);

            boolean hasName = item.name != null && !item.name.isEmpty();
            if (hasName) {
                int availName = Math.max(40, maxTitleArea - verW - 10);
                String nameShown = AeternumTheme.ellipsize(textRenderer, item.name, availName);
                c.drawTextWithShadow(textRenderer, nameShown, cursorX, cy + 7, AeternumTheme.TEXT_PRIMARY);
                cursorX += textRenderer.getWidth(nameShown) + 5;

                String uuidParen = "(" + item.uuid + ")";
                float scale = 0.78f;
                int scaledUuidW = Math.round(textRenderer.getWidth(uuidParen) * scale);
                int availForUuid = controlsLeftX - cursorX - verW - 12;
                if (availForUuid >= scaledUuidW) {
                    c.getMatrices().pushMatrix();
                    c.getMatrices().translate(cursorX, cy + 8.5f);
                    c.getMatrices().scale(scale, scale);
                    c.drawText(textRenderer, uuidParen, 0, 0, AeternumTheme.TEXT_MUTED, false);
                    c.getMatrices().popMatrix();
                    cursorX += scaledUuidW + 6;
                } else if (availForUuid > 36) {
                    String trimmed = AeternumTheme.ellipsize(textRenderer, uuidParen, Math.round(availForUuid / scale));
                    c.getMatrices().pushMatrix();
                    c.getMatrices().translate(cursorX, cy + 8.5f);
                    c.getMatrices().scale(scale, scale);
                    c.drawText(textRenderer, trimmed, 0, 0, AeternumTheme.TEXT_MUTED, false);
                    c.getMatrices().popMatrix();
                    cursorX += Math.round(textRenderer.getWidth(trimmed) * scale) + 6;
                }
            } else {
                int availUuid = Math.max(40, maxTitleArea - verW - 8);
                String uuidShown = AeternumTheme.ellipsize(textRenderer, item.uuid, availUuid);
                c.drawTextWithShadow(textRenderer, uuidShown, cursorX, cy + 7, AeternumTheme.TEXT_PRIMARY);
                cursorX += textRenderer.getWidth(uuidShown) + 6;
            }

            if (cursorX + verW <= controlsLeftX - 4) {
                AeternumTheme.drawBadge(c, textRenderer, cursorX, cy + 5, verText, verBg, verFg);
            }

            // Description row: anomaly / files summary
            String desc = buildFileSummary(item);
            int descColor = item.anomaly ? 0xFFE59B53 : AeternumTheme.TEXT_SECONDARY;
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, desc, cardW - 20), cardX + 10, cy + 27, descColor, false);
        }
        c.disableScissor();

        if (maxL > 0) {
            AeternumTheme.drawScrollbar(c, panelX + panelW - 9, lTop, lBottom,
                shown.size() * ROW_STRIDE, viewH, listScroll, draggingScrollbar);
        }
    }

    private record GuideBadge(String text, int bg, int fg) {
    }

    private record GuideSection(String title, List<GuideBadge> badges, List<String> lines) {
    }

    private static final List<GuideSection> GUIDE_SECTIONS = List.of(
        new GuideSection(
            "1. Por qué cada jugador tiene 3 archivos (y qué pasa si falta alguno)",
            List.of(),
            List.of(
                "Cuando un jugador o bot se guarda en el mundo (al desconectarse o al guardar el servidor), Minecraft divide su estado en 3 archivos distintos identificados por su UUID:",
                "\u2022 playerdata/<uuid>.dat: es el personaje en el mundo (inventario, Ender Chest, posición, dimensión, XP y modo de juego). A partir del 2.\u00BA guardado crea además <uuid>.dat_old como respaldo automático del guardado anterior.",
                "\u2022 stats/<uuid>.json: sus estadísticas (bloques minados, tiempo jugado, muertes, crafteos, etc.).",
                "\u2022 advancements/<uuid>.json: su progreso de logros y recetas.",
                "¿Por qué importa? Porque Minecraft escribe los 3 archivos juntos en cada guardado normal. Si en el disco falta alguno de los 3, significa que hubo un guardado interrumpido, un borrado parcial o restos huérfanos: por eso aparece marcado como anomalía. (Excepción normal: alguien que entró por primera vez y sigue conectado antes del primer guardado.)"
            )
        ),
        new GuideSection(
            "2. Por qué hay UUIDs ONLINE y OFFLINE en un servidor online",
            List.of(
                new GuideBadge("ONLINE", 0xFF1A2A22, 0xFF8BE0B2),
                new GuideBadge("OFFLINE", 0xFF24242D, 0xFFB8B8C8)
            ),
            List.of(
                "Aunque el servidor esté en online-mode=true, verás dos tipos de UUID en las carpetas:",
                "\u2022 ONLINE (UUID v4): es la UUID oficial que Mojang asigna a cada cuenta premium. Aunque el jugador cambie de nick, su UUID v4 nunca cambia y sus datos siguen siendo los mismos.",
                "\u2022 OFFLINE (UUID v3): se calcula matemáticamente a partir del nombre (OfflinePlayer:<nick>). En este servidor solo aparecen por dos razones:",
                "  1) Bots de Carpet (/player <nick> spawn): si el nick del bot no existe en Mojang, Carpet le crea una UUID v3. (En cambio, si el nick sí existe en Mojang, Carpet usa su UUID v4 real porque el servidor está en modo online.)",
                "  2) Jugadores antiguos del servidor: cuentas de épocas previas en modo offline/Waterfall. Muchas de esas personas hoy tienen cuenta Mojang v4 y sus datos podrían migrarse; otras nunca tuvieron cuenta premium (HTTP 404) y no tienen UUID v4 a la que mudarse."
            )
        ),
        new GuideSection(
            "3. Cómo clasifica el Registro",
            List.of(
                new GuideBadge("HUMAN", 0xFF1A281E, 0xFF9DF0B4),
                new GuideBadge("BOT", 0xFF18222C, 0xFF9CD0FF),
                new GuideBadge("UNKNOWN", 0xFF1A1A20, AeternumTheme.TEXT_SECONDARY)
            ),
            List.of(
                "El registro usa la UUID como identidad única (no el nombre), porque dos UUIDs distintas pueden compartir nick sin ser el mismo archivo:",
                "\u2022 UNKNOWN: estado inicial de cualquier archivo viejo que ya estaba en disco antes de activar este registro. Se queda así hasta que entre en una sesión o un admin lo clasifique manualmente.",
                "\u2022 HUMAN: se asigna automáticamente cuando entra una persona real. Un bot nunca puede degradar un HUMAN a BOT, aunque alguien invoque un bot con el nombre de un jugador real.",
                "\u2022 BOT: se asigna cuando entra un bot de Carpet, siempre que la UUID fuera nueva o estuviera en UNKNOWN. Si más adelante entra una persona real con esa misma UUID, se promoverá a HUMAN."
            )
        ),
        new GuideSection(
            "4. Reglas de seguridad antes de subsanar o borrar",
            List.of(),
            List.of(
                "\u2022 Jugadores o bots conectados: Minecraft mantiene el inventario, las stats y los logros en RAM mientras están en el servidor y sobrescribiría el disco al guardar; por eso la interfaz bloquea u omite automáticamente cualquier borrado o subsanación sobre alguien conectado.",
                "\u2022 Qué hace Subsanar (y qué NO hace): si falta .dat pero existe .dat_old, restaura el respaldo; si existe .dat, crea stats y advancements vacíos para quitar la anomalía. Nunca inventa inventarios ni crea un .dat desde cero (si falta .dat sin .dat_old, lo omite para que tú decidas si borrar esos restos).",
                "\u2022 Qué hace Borrar (Shift + 2 clics): elimina la entrada del registro y borra sus archivos en disco (.dat, .dat_old, stats, advancements).",
                "\u2022 Archivos raros: cualquier archivo o subcarpeta dentro de playerdata/, stats/ o advancements/ que no siga el formato <uuid>.dat, <uuid>.dat_old o <uuid>.json. Puedes limpiarlos uno a uno o con Shift + Borrar todo."
            )
        )
    );

    private void renderGuideTab(DrawContext c, float delta) {
        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = Math.max(1, lBottom - lTop);
        int cardX = panelX + 12;
        int cardW = panelW - 28;
        int wrapW = Math.max(120, cardW - 24);

        int totalH = measureGuideContentHeight(wrapW);
        int maxL = Math.max(0, totalH - viewH);
        targetListScroll = Math.max(0, Math.min(targetListScroll, maxL));
        listScroll += (targetListScroll - listScroll) * Math.min(1.0, delta * 0.45 + 0.25);
        listScroll = Math.max(0, Math.min(listScroll, maxL));

        c.enableScissor(panelX + 4, lTop, panelX + panelW - 8, lBottom);
        int curY = lTop - (int) Math.round(listScroll);
        for (GuideSection section : GUIDE_SECTIONS) {
            int cardH = measureGuideCardHeight(section, wrapW);
            if (curY + cardH >= lTop && curY <= lBottom) {
                AeternumTheme.drawCard(c, cardX, curY, cardW, cardH, AeternumTheme.CARD_BG, AeternumTheme.BORDER_SUBTLE);
                c.fill(cardX + 1, curY + 1, cardX + 3, curY + cardH - 1, AeternumTheme.ACCENT_GOLD);

                int titleX = cardX + 12;
                c.drawTextWithShadow(textRenderer, section.title, titleX, curY + 8, AeternumTheme.TEXT_PRIMARY);
                int badgeX = titleX + textRenderer.getWidth(section.title) + 8;
                for (GuideBadge badge : section.badges) {
                    int bw = textRenderer.getWidth(badge.text) + 10;
                    if (badgeX + bw <= cardX + cardW - 8) {
                        AeternumTheme.drawBadge(c, textRenderer, badgeX, curY + 6, badge.text, badge.bg, badge.fg);
                        badgeX += bw + 4;
                    }
                }

                int lineY = curY + 24;
                for (String para : section.lines) {
                    for (var ordered : textRenderer.wrapLines(Text.literal(para), wrapW)) {
                        c.drawText(textRenderer, ordered, cardX + 12, lineY, AeternumTheme.TEXT_SECONDARY, false);
                        lineY += 10;
                    }
                    lineY += 4;
                }
            }
            curY += cardH + 6;
        }
        c.disableScissor();

        if (maxL > 0) {
            AeternumTheme.drawScrollbar(c, panelX + panelW - 9, lTop, lBottom, totalH, viewH, listScroll, draggingScrollbar);
        }
    }

    private int measureGuideCardHeight(GuideSection section, int wrapW) {
        int h = 24;
        for (String para : section.lines) {
            h += textRenderer.wrapLines(Text.literal(para), wrapW).size() * 10 + 4;
        }
        return h + 6;
    }

    private int measureGuideContentHeight(int wrapW) {
        int total = 0;
        for (int i = 0; i < GUIDE_SECTIONS.size(); i++) {
            total += measureGuideCardHeight(GUIDE_SECTIONS.get(i), wrapW);
            if (i + 1 < GUIDE_SECTIONS.size()) {
                total += 6;
            }
        }
        return total;
    }

    private void renderUnexpectedTab(DrawContext c, int mx, int my, float delta) {
        int topY = tabBarY() + TAB_BAR_H;
        c.drawText(textRenderer,
            "Archivos fuera de formato esperado:",
            panelX + 14, topY + 13, AeternumTheme.TEXT_SECONDARY, false);

        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = Math.max(1, lBottom - lTop);
        int maxL = maxListScrollPx(unexpectedFiles.size(), UNEXPECTED_ROW_H);
        targetListScroll = Math.max(0, Math.min(targetListScroll, maxL));
        listScroll += (targetListScroll - listScroll) * Math.min(1.0, delta * 0.45 + 0.25);
        listScroll = Math.max(0, Math.min(listScroll, maxL));

        int cardX = panelX + 12;
        int cardW = panelW - 28;

        if (unexpectedFiles.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer,
                "Sin archivos inesperados. Las 3 carpetas están limpias.",
                cardX + cardW / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
            return;
        }

        c.enableScissor(panelX + 4, lTop, panelX + panelW - 8, lBottom);
        for (int i = 0; i < unexpectedFiles.size(); i++) {
            int cy = lTop + i * UNEXPECTED_ROW_H - (int) Math.round(listScroll);
            if (cy + UNEXPECTED_ROW_H < lTop || cy > lBottom) continue;

            String path = unexpectedFiles.get(i);
            int rowH = UNEXPECTED_ROW_H - 2;
            AeternumTheme.drawCard(c, cardX, cy, cardW, rowH, AeternumTheme.CARD_BG, 0x60E59B53);
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, path, cardW - 32), cardX + 8, cy + 6, 0xFFE59B53, false);

            int delX = cardX + cardW - 20;
            int delY = cy + 2;
            boolean inArea = my >= lTop && my < lBottom;
            boolean overDel = inArea && mx >= delX && mx < delX + 16 && my >= delY && my < delY + 16;
            if (overDel) {
                hoveredUnexpectedDelete = path;
                c.fill(delX, delY, delX + 16, delY + 16, 0x45E55353);
                c.drawBorder(delX, delY, 16, 16, AeternumTheme.withAlpha(AeternumTheme.ACCENT_RED, 0x90));
            }
            c.drawTextWithShadow(textRenderer, "\u00D7", delX + 5, delY + 4, overDel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
        }
        c.disableScissor();

        if (maxL > 0) {
            AeternumTheme.drawScrollbar(c, panelX + panelW - 9, lTop, lBottom,
                unexpectedFiles.size() * UNEXPECTED_ROW_H, viewH, listScroll, draggingScrollbar);
        }
    }

    private static String buildFileSummary(RegistryEntrySnapshot item) {
        if (!item.anomaly) {
            return "Archivos: OK (playerdata, stats, advancements)";
        }
        List<String> missing = new ArrayList<>(3);
        if (!item.playerdata) missing.add("playerdata");
        if (!item.stats) missing.add("stats");
        if (!item.advancements) missing.add("advancements");
        if (missing.size() == 3) {
            return "Anomal\u00eda: sin archivos en disco (playerdata, stats, advancements)";
        }
        return "Anomal\u00eda: falta " + String.join(", ", missing);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && isCloseButtonHit(mx, my)) {
            close();
            return true;
        }

        // Tab clicks
        if (button == 0) {
            int ty = tabBarY();
            int tabY = ty + 3;
            int tabX = panelX + 10;
            int guideW = textRenderer.getWidth("Guía") + 18;
            if (mx >= tabX && mx < tabX + guideW && my >= tabY && my < tabY + 18) {
                if (activeTab != Tab.GUIDE) {
                    activeTab = Tab.GUIDE;
                    listScroll = targetListScroll = 0;
                    init();
                }
                return true;
            }
            int regX = tabX + guideW + 6;
            int regW = textRenderer.getWidth("Registro (" + entries.size() + ")") + 18;
            if (mx >= regX && mx < regX + regW && my >= tabY && my < tabY + 18) {
                if (activeTab != Tab.REGISTRY) {
                    activeTab = Tab.REGISTRY;
                    listScroll = targetListScroll = 0;
                    init();
                }
                return true;
            }
            int unexpX = regX + regW + 6;
            int unexpW = textRenderer.getWidth("Archivos raros (" + unexpectedFiles.size() + ")") + 18;
            if (mx >= unexpX && mx < unexpX + unexpW && my >= tabY && my < tabY + 18) {
                if (activeTab != Tab.UNEXPECTED) {
                    activeTab = Tab.UNEXPECTED;
                    listScroll = targetListScroll = 0;
                    init();
                }
                return true;
            }
        }

        if (activeTab == Tab.REGISTRY && button == 0) {
            int topY = tabBarY() + TAB_BAR_H;
            int fTop = topY + 8;
            Filter[] filters = Filter.values();
            if (mx >= panelX + 4 && mx < panelX + SIDEBAR_W - 4 && my >= fTop && my < fTop + filters.length * SIDEBAR_ROW_H) {
                int idx = (int) ((my - fTop) / SIDEBAR_ROW_H);
                if (idx >= 0 && idx < filters.length) {
                    activeFilter = filters[idx];
                    recomputeDisplayOrder();
                    listScroll = targetListScroll = 0;
                    return true;
                }
            }

            int lTop = listTop();
            int lBottom = listBottom();
            if (my >= lTop && my < lBottom) {
                List<RegistryEntrySnapshot> shown = filteredEntries();
                int cardX = panelX + SIDEBAR_W + 10;
                int cardW = panelW - SIDEBAR_W - 26;
                int delW = 18;
                int delX = cardX + cardW - delW - 6;
                int rotW = 68;
                int rotH = 18;
                int rotX = delX - rotW - 4;
                int healW = 54;
                int healX = rotX - healW - 4;
                int lookW = 66;

                for (int i = 0; i < shown.size(); i++) {
                    int cy = lTop + i * ROW_STRIDE - (int) Math.round(listScroll);
                    if (cy + CARD_H < lTop || cy > lBottom) continue;
                    int rotY = cy + 5;
                    if (my < rotY || my >= rotY + rotH) continue;

                    RegistryEntrySnapshot item = shown.get(i);
                    int leftOfHeal = item.anomaly ? healX : rotX;
                    int lookX = leftOfHeal - lookW - 4;
                    if (mx >= delX && mx < delX + delW) {
                        if (!hasShiftDown()) {
                            statusMessage = "Mantén Shift para habilitar el borrado de registros.";
                            statusError = false;
                            return true;
                        }
                        if (item.uuid.equals(armedDeleteUuid)) {
                            armedDeleteUuid = null;
                            statusMessage = "Eliminando registro y archivos…";
                            statusError = false;
                            ClientPlayNetworking.send(new StatsRegistryActionPayload("delete_entry", item.uuid, ""));
                        } else {
                            armedDeleteUuid = item.uuid;
                            armedDeleteUntilMs = System.currentTimeMillis() + 5000L;
                            String targetLabel = (item.name != null && !item.name.isEmpty()) ? item.name : item.uuid;
                            statusMessage = "Confirma Shift + Clic en ! para borrar a " + targetLabel;
                            statusError = true;
                        }
                        return true;
                    }
                    if (mx >= rotX && mx < rotX + rotW) {
                        String nextType = switch (item.type) {
                            case "UNKNOWN" -> "HUMAN";
                            case "HUMAN" -> "BOT";
                            default -> "UNKNOWN";
                        };
                        ClientPlayNetworking.send(new StatsRegistryActionPayload("set_type", item.uuid, nextType));
                        return true;
                    }
                    if (item.anomaly && mx >= healX && mx < healX + healW) {
                        statusMessage = "Subsanando " + item.uuid + "…";
                        statusError = false;
                        ClientPlayNetworking.send(new StatsRegistryActionPayload("heal_entry", item.uuid, ""));
                        return true;
                    }
                    boolean showLookup = item.uuidVersion == 4 && (item.name == null || item.name.isEmpty());
                    if (showLookup && mx >= lookX && mx < lookX + lookW) {
                        statusMessage = "Consultando Mojang para " + item.uuid + "…";
                        statusError = false;
                        ClientPlayNetworking.send(new StatsRegistryActionPayload("lookup_name", item.uuid, ""));
                        return true;
                    }
                }
            }
        }

        if (activeTab == Tab.UNEXPECTED && button == 0) {
            int lTop = listTop();
            int lBottom = listBottom();
            if (my >= lTop && my < lBottom) {
                int cardX = panelX + 12;
                int cardW = panelW - 28;
                int delX = cardX + cardW - 20;
                for (int i = 0; i < unexpectedFiles.size(); i++) {
                    int cy = lTop + i * UNEXPECTED_ROW_H - (int) Math.round(listScroll);
                    if (cy + UNEXPECTED_ROW_H < lTop || cy > lBottom) continue;
                    int delY = cy + 2;
                    if (mx >= delX && mx < delX + 16 && my >= delY && my < delY + 16) {
                        String path = unexpectedFiles.get(i);
                        statusMessage = "Borrando " + path + "…";
                        statusError = false;
                        ClientPlayNetworking.send(new StatsRegistryActionPayload("delete_unexpected", "", path));
                        return true;
                    }
                }
            }
        }

        if (button == 0 && mx >= panelX + panelW - 14 && mx <= panelX + panelW - 4 && my >= listTop() && my < listBottom()) {
            draggingScrollbar = true;
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, button);
    }

    private int maxActiveScrollPx() {
        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = Math.max(1, lBottom - lTop);
        if (activeTab == Tab.GUIDE) {
            int wrapW = Math.max(120, (panelW - 28) - 24);
            return Math.max(0, measureGuideContentHeight(wrapW) - viewH);
        }
        int count = activeTab == Tab.REGISTRY ? filteredEntries().size() : unexpectedFiles.size();
        int stride = activeTab == Tab.REGISTRY ? ROW_STRIDE : UNEXPECTED_ROW_H;
        return maxListScrollPx(count, stride);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar) {
            int lTop = listTop();
            int lBottom = listBottom();
            int maxL = maxActiveScrollPx();
            double frac = Math.max(0.0, Math.min(1.0, (my - lTop) / Math.max(1.0, lBottom - lTop)));
            targetListScroll = listScroll = frac * maxL;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (my >= listTop() && my < listBottom()) {
            int maxL = maxActiveScrollPx();
            targetListScroll = Math.max(0.0, Math.min(maxL, targetListScroll - vAmt * 26.0));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public void removed() {
        if (client != null && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new StatsRegistryActionPayload("close_screen", "", ""));
        }
        super.removed();
    }
}
