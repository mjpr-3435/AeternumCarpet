package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.motd.MotdMarkup;
import smp.aeternum.carpet.network.StatsNetworking.CreateObjectiveRequest;
import smp.aeternum.carpet.network.StatsNetworking.EditObjectiveRequest;
import smp.aeternum.carpet.network.StatsNetworking.RegistryEntrySnapshot;
import smp.aeternum.carpet.network.StatsNetworking.ScoreboardEntrySnapshot;
import smp.aeternum.carpet.network.StatsNetworking.ScoreboardObjectiveSnapshot;
import smp.aeternum.carpet.network.StatsNetworking.StatsSnapshot;
import smp.aeternum.carpet.network.StatsRegistryActionPayload;

public final class StatsScreen extends UiScreen {
    private static final Gson GSON = new Gson();
    private static final Type SCORE_ENTRY_LIST_TYPE = new TypeToken<List<ScoreboardEntrySnapshot>>() {}.getType();
    private static final int TAB_BAR_H = 24;
    private static final int SIDEBAR_W = 156;
    private static final int SIDEBAR_ROW_H = 19;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 4;
    private static final int ROW_STRIDE = CARD_H + CARD_GAP;
    private static final int UNEXPECTED_ROW_H = 22;
    private static final int SB_SIDEBAR_ROW_H = 28;
    private static final int SB_ENTRY_H = 26;
    private static final int SB_ENTRY_GAP = 3;
    private static final int SB_ENTRY_STRIDE = SB_ENTRY_H + SB_ENTRY_GAP;

    private enum SbPanelMode {
        VIEW,
        CREATE,
        EDIT
    }

    private enum Tab {
        GUIDE,
        REGISTRY,
        SCOREBOARDS,
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

    private enum ScoreFilter {
        ALL("Todos"),
        HUMAN("Humans"),
        BOT("Bots"),
        BOTH("Ambos"),
        UNKNOWN("Unknown");

        final String label;

        ScoreFilter(String label) {
            this.label = label;
        }
    }

    private enum ScoreSort {
        SCORE_DESC("Score \u2193"),
        SCORE_ASC("Score \u2191"),
        NAME("Nombre"),
        TYPE("Tipo");

        final String label;

        ScoreSort(String label) {
            this.label = label;
        }

        ScoreSort next() {
            ScoreSort[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    private List<RegistryEntrySnapshot> entries = List.of();
    private final Map<String, RegistryEntrySnapshot> entriesByUuid = new HashMap<>();
    private final List<String> displayOrder = new ArrayList<>();
    private boolean recomputeOrderOnNextSnapshot = true;
    private List<String> unexpectedFiles = List.of();
    private List<ScoreboardObjectiveSnapshot> scoreboards = List.of();
    private String selectedObjectiveName;
    private String loadedObjectiveName;
    private List<ScoreboardEntrySnapshot> selectedObjectiveEntries = List.of();
    private ScoreFilter activeScoreFilter = ScoreFilter.ALL;
    private ScoreSort activeScoreSort = ScoreSort.SCORE_DESC;
    private double sbSidebarScroll, targetSbSidebarScroll;
    private String hoveredObjectiveDelete;
    private String hoveredObjectiveEdit;
    private String armedObjectiveDelete;
    private long armedObjectiveDeleteUntilMs;
    private SbPanelMode sbPanelMode = SbPanelMode.VIEW;
    private String editingObjectiveName = "";
    private String editingObjectiveCriterion = "dummy";
    private boolean formFillFromStats;
    private String formSlot = "";
    private boolean formTotalEnabled;
    private boolean formTotalHumans = true;
    private boolean formTotalBots;
    private boolean formTotalUnknown;
    private int criterionSugScroll;
    private boolean draggingCriterionScrollbar;
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
    private boolean draggingSbSidebarScrollbar;

    private UiTextField searchField;
    private UiButton sortButton;
    private UiButton lookupAllButton;
    private UiButton rescanButton;
    private UiButton healAnomaliesButton;
    private UiButton deleteAllButton;
    private UiTextField scoreSearchField;
    private UiTextField objectiveSearchField;
    private UiButton sbCreateButton;
    private UiButton scoreSortButton;
    private UiButton scoreRefreshButton;
    private UiTextField sbNameField;
    private UiTextField sbDisplayField;
    private UiTextField sbCriterionField;
    private UiTextField sbAliasField;
    private UiButton sbSaveButton;
    private UiButton sbCancelButton;
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
        if (activeTab == Tab.SCOREBOARDS && selectedObjectiveName != null && !selectedObjectiveName.equals(loadedObjectiveName)) {
            requestSelectedObjectiveDetail();
        }
    }

    public void replaceScoreboardDetail(String objectiveName, String json) {
        this.loadedObjectiveName = objectiveName;
        try {
            List<ScoreboardEntrySnapshot> list = GSON.fromJson(json, SCORE_ENTRY_LIST_TYPE);
            this.selectedObjectiveEntries = list != null ? list : List.of();
        } catch (Exception ignored) {
            this.selectedObjectiveEntries = List.of();
        }
    }

    private void requestSelectedObjectiveDetail() {
        if (selectedObjectiveName == null || selectedObjectiveName.isEmpty()) {
            loadedObjectiveName = null;
            selectedObjectiveEntries = List.of();
            return;
        }
        ClientPlayNetworking.send(new StatsRegistryActionPayload("select_objective", "", selectedObjectiveName));
    }

    private void applySnapshot(String json) {
        try {
            StatsSnapshot snap = GSON.fromJson(json, StatsSnapshot.class);
            if (snap != null) {
                this.entries = snap.entries != null ? snap.entries : List.of();
                this.unexpectedFiles = snap.unexpectedFiles != null ? snap.unexpectedFiles : List.of();
                this.scoreboards = snap.scoreboards != null ? snap.scoreboards : List.of();
                if (this.scoreboards.isEmpty()) {
                    this.selectedObjectiveName = null;
                    this.loadedObjectiveName = null;
                    this.selectedObjectiveEntries = List.of();
                } else if (this.selectedObjectiveName == null
                    || this.scoreboards.stream().noneMatch(o -> o.name.equals(this.selectedObjectiveName))) {
                    this.selectedObjectiveName = this.scoreboards.get(0).name;
                    this.loadedObjectiveName = null;
                    this.selectedObjectiveEntries = List.of();
                }
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
        String prevScoreQuery = scoreSearchField != null ? scoreSearchField.getText() : "";
        String prevObjQuery = objectiveSearchField != null ? objectiveSearchField.getText() : "";
        String prevSbName = sbNameField != null ? sbNameField.getText() : "";
        String prevSbDisplay = sbDisplayField != null ? sbDisplayField.getText() : "";
        String prevSbCriterion = sbCriterionField != null ? sbCriterionField.getText() : "";
        String prevSbAlias = sbAliasField != null ? sbAliasField.getText() : "";
        clearChildren();
        computePanel(Math.min(780, (int) (width * 0.88)), Math.min(470, (int) (height * 0.85)));

        int contentTop = panelY + 44 + TAB_BAR_H;
        searchField = null;
        sortButton = null;
        lookupAllButton = null;
        rescanButton = null;
        healAnomaliesButton = null;
        deleteAllButton = null;
        scoreSearchField = null;
        objectiveSearchField = null;
        sbCreateButton = null;
        scoreSortButton = null;
        scoreRefreshButton = null;
        sbNameField = null;
        sbDisplayField = null;
        sbCriterionField = null;
        sbAliasField = null;
        sbSaveButton = null;
        sbCancelButton = null;

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
        } else if (activeTab == Tab.SCOREBOARDS) {
            objectiveSearchField = new UiTextField(textRenderer, panelX + 8, contentTop + 6, SIDEBAR_W - 38, 18, "Buscar objetivo…");
            objectiveSearchField.setMaxLength(64);
            objectiveSearchField.setText(prevObjQuery);
            objectiveSearchField.setChangedListener(s -> {
                targetSbSidebarScroll = 0;
                sbSidebarScroll = 0;
            });
            addDrawableChild(objectiveSearchField);

            sbCreateButton = UiButton.primary(panelX + SIDEBAR_W - 26, contentTop + 6, 18, 18, "+", b -> openCreateObjectiveForm());
            addDrawableChild(sbCreateButton);

            scoreRefreshButton = UiButton.secondary(panelX + 8, panelY + panelH - 62, SIDEBAR_W - 16, 22, "Refrescar", b -> {
                statusMessage = "Actualizando scoreboards…";
                statusError = false;
                armedObjectiveDelete = null;
                loadedObjectiveName = null;
                ClientPlayNetworking.send(new StatsRegistryActionPayload("refresh_scoreboards", "", ""));
            });
            addDrawableChild(scoreRefreshButton);

            int contentX = panelX + SIDEBAR_W + 10;
            int contentW = panelW - SIDEBAR_W - 22;
            if (sbPanelMode == SbPanelMode.VIEW) {
                int sortW = 122;
                int searchW = Math.max(100, contentW - sortW - 6);
                scoreSearchField = new UiTextField(textRenderer, contentX, contentTop + 8, searchW, 20, "Buscar entrada…");
                scoreSearchField.setMaxLength(80);
                scoreSearchField.setText(prevScoreQuery);
                scoreSearchField.setChangedListener(s -> {
                    targetListScroll = 0;
                    listScroll = 0;
                });
                addDrawableChild(scoreSearchField);

                scoreSortButton = UiButton.secondary(contentX + searchW + 6, contentTop + 8, sortW, 20, "Orden: " + activeScoreSort.label, b -> {
                    activeScoreSort = activeScoreSort.next();
                    b.setLabel("Orden: " + activeScoreSort.label);
                    targetListScroll = 0;
                    listScroll = 0;
                });
                addDrawableChild(scoreSortButton);
            } else {
                int dispLabelY = formDisplayLabelY(contentTop);
                if (sbPanelMode == SbPanelMode.CREATE) {
                    sbNameField = new UiTextField(textRenderer, contentX, contentTop + 35, contentW, 18, "ej: dig_diamantes");
                    sbNameField.setMaxLength(16);
                    sbNameField.setText(prevSbName);
                    addDrawableChild(sbNameField);

                    sbCriterionField = new UiTextField(textRenderer, contentX, contentTop + 69, contentW, 18, "vacío = dummy");
                    sbCriterionField.setMaxLength(128);
                    sbCriterionField.setText(prevSbCriterion);
                    sbCriterionField.setChangedListener(s -> criterionSugScroll = 0);
                    addDrawableChild(sbCriterionField);
                }

                sbDisplayField = new UiTextField(textRenderer, contentX, dispLabelY + 11, contentW, 18, "Ej: \\gold\\boldMi Scoreboard (vacío = ID)");
                sbDisplayField.setMaxLength(128);
                sbDisplayField.setText(prevSbDisplay);
                addDrawableChild(sbDisplayField);

                int aliasFieldY = dispLabelY + 60;
                sbAliasField = new UiTextField(textRenderer, contentX, aliasFieldY, 150, 18, "ej: gold (vacío = sin atajo)");
                sbAliasField.setMaxLength(32);
                sbAliasField.setText(prevSbAlias);
                addDrawableChild(sbAliasField);

                int btnY = panelY + panelH - 64;
                sbSaveButton = UiButton.primary(contentX + contentW - 156, btnY, 76, 20,
                    sbPanelMode == SbPanelMode.CREATE ? "Crear" : "Guardar", b -> submitScoreboardForm());
                sbCancelButton = UiButton.secondary(contentX + contentW - 76, btnY, 76, 20, "Cancelar", b -> {
                    sbPanelMode = SbPanelMode.VIEW;
                    init();
                });
                addDrawableChild(sbSaveButton);
                addDrawableChild(sbCancelButton);
            }
        } else if (activeTab == Tab.UNEXPECTED) {
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
        return switch (activeTab) {
            case GUIDE -> tabBarY() + TAB_BAR_H + 8;
            case SCOREBOARDS -> tabBarY() + TAB_BAR_H + 54;
            default -> tabBarY() + TAB_BAR_H + 34;
        };
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
        hoveredObjectiveDelete = null;
        hoveredObjectiveEdit = null;
        if (armedDeleteUuid != null && System.currentTimeMillis() > armedDeleteUntilMs) {
            armedDeleteUuid = null;
        }
        if (armedObjectiveDelete != null && System.currentTimeMillis() > armedObjectiveDeleteUntilMs) {
            armedObjectiveDelete = null;
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
        } else if (activeTab == Tab.SCOREBOARDS) {
            renderScoreboardsTab(c, mx, my, delta);
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
                case SCOREBOARDS -> "Datos de scoreboard.dat  ·  Shift + 2 clics en × para borrar objetivo";
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
        if (scoreSearchField != null) {
            scoreSearchField.renderBox(c, scoreSearchField.boxX(), scoreSearchField.boxY(), scoreSearchField.getWidth() + 12, 20, mx, my, delta);
        }
        if (objectiveSearchField != null) {
            objectiveSearchField.renderBox(c, objectiveSearchField.boxX(), objectiveSearchField.boxY(), objectiveSearchField.getWidth() + 12, 18, mx, my, delta);
        }
        if (sbNameField != null) {
            sbNameField.renderBox(c, sbNameField.boxX(), sbNameField.boxY(), sbNameField.getWidth() + 12, 18, mx, my, delta);
        }
        if (sbCriterionField != null) {
            sbCriterionField.renderBox(c, sbCriterionField.boxX(), sbCriterionField.boxY(), sbCriterionField.getWidth() + 12, 18, mx, my, delta);
        }
        if (sbDisplayField != null) {
            sbDisplayField.renderBox(c, sbDisplayField.boxX(), sbDisplayField.boxY(), sbDisplayField.getWidth() + 12, 18, mx, my, delta);
        }
        if (sbAliasField != null) {
            sbAliasField.renderBox(c, sbAliasField.boxX(), sbAliasField.boxY(), sbAliasField.getWidth() + 12, 18, mx, my, delta);
        }
        if (activeTab == Tab.SCOREBOARDS && sbPanelMode == SbPanelMode.CREATE && sbCriterionField != null && sbCriterionField.isFocused()) {
            renderCriterionSuggestions(c, mx, my);
        }

        if (isCloseButtonHit(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Cerrar"), mx, my);
        } else if (sbCreateButton != null && sbCreateButton.isHovered()) {
            c.drawTooltip(textRenderer, Text.literal("Crear nueva scoreboard"), mx, my);
        } else if (sortButton != null && sortButton.isHovered()) {
            c.drawTooltip(textRenderer, Text.literal("Cambiar orden (Nombre, Sin nombre, Tipo, Online/Offline, Anomalías)"), mx, my);
        } else if (scoreSortButton != null && scoreSortButton.isHovered()) {
            c.drawTooltip(textRenderer, Text.literal("Cambiar orden (Score \u2193, Score \u2191, Nombre, Tipo)"), mx, my);
        } else if (activeTab == Tab.SCOREBOARDS && hoveredObjectiveEdit != null) {
            c.drawTooltip(textRenderer, Text.literal("Editar scoreboard '" + hoveredObjectiveEdit + "'"), mx, my);
        } else if (activeTab == Tab.SCOREBOARDS && hoveredObjectiveDelete != null) {
            String tip;
            if (!hasShiftDown()) {
                tip = "Mantén Shift para borrar scoreboard '" + hoveredObjectiveDelete + "'";
            } else if (hoveredObjectiveDelete.equals(armedObjectiveDelete)) {
                tip = "Clic de nuevo con Shift para BORRAR scoreboard '" + hoveredObjectiveDelete + "'";
            } else {
                tip = "Shift + Clic para armar borrado de '" + hoveredObjectiveDelete + "'";
            }
            c.drawTooltip(textRenderer, Text.literal(tip), mx, my);
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
        x += drawTabButton(c, x, ty + 3, "Scoreboards (" + scoreboards.size() + ")", activeTab == Tab.SCOREBOARDS, false, mx, my) + 6;
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

    private ScoreboardObjectiveSnapshot selectedObjectiveSnapshot() {
        if (selectedObjectiveName == null) return null;
        for (ScoreboardObjectiveSnapshot obj : scoreboards) {
            if (selectedObjectiveName.equals(obj.name)) {
                return obj;
            }
        }
        return null;
    }

    private List<ScoreboardEntrySnapshot> filteredScoreEntries(ScoreboardObjectiveSnapshot obj) {
        if (obj == null || !obj.name.equals(loadedObjectiveName) || selectedObjectiveEntries == null) return List.of();
        String q = scoreSearchField != null ? scoreSearchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<ScoreboardEntrySnapshot> out = new ArrayList<>();
        for (ScoreboardEntrySnapshot e : selectedObjectiveEntries) {
            boolean matchFilter = switch (activeScoreFilter) {
                case ALL -> true;
                case HUMAN -> "HUMAN".equals(e.badge);
                case BOT -> "BOT".equals(e.badge);
                case BOTH -> "AMBOS".equals(e.badge);
                case UNKNOWN -> "UNKNOWN".equals(e.badge);
            };
            if (!matchFilter) continue;
            if (!q.isEmpty() && (e.owner == null || !e.owner.toLowerCase(Locale.ROOT).contains(q))) {
                continue;
            }
            out.add(e);
        }
        Comparator<ScoreboardEntrySnapshot> comp = switch (activeScoreSort) {
            case SCORE_DESC -> Comparator.comparingInt((ScoreboardEntrySnapshot e) -> e.score).reversed()
                .thenComparing(e -> e.owner, String.CASE_INSENSITIVE_ORDER);
            case SCORE_ASC -> Comparator.comparingInt((ScoreboardEntrySnapshot e) -> e.score)
                .thenComparing(e -> e.owner, String.CASE_INSENSITIVE_ORDER);
            case NAME -> Comparator.comparing((ScoreboardEntrySnapshot e) -> e.owner, String.CASE_INSENSITIVE_ORDER);
            case TYPE -> Comparator.comparingInt((ScoreboardEntrySnapshot e) -> switch (e.badge) {
                case "HUMAN" -> 0;
                case "AMBOS" -> 1;
                case "BOT" -> 2;
                default -> 3;
            }).thenComparing(Comparator.comparingInt((ScoreboardEntrySnapshot e) -> e.score).reversed());
        };
        out.sort(comp);
        return out;
    }

    private int countScoreFilter(ScoreboardObjectiveSnapshot obj, ScoreFilter filter) {
        if (obj == null) return 0;
        if (!obj.name.equals(loadedObjectiveName)) {
            return filter == ScoreFilter.ALL ? obj.entryCount : 0;
        }
        if (filter == ScoreFilter.ALL) return selectedObjectiveEntries.size();
        int count = 0;
        for (ScoreboardEntrySnapshot e : selectedObjectiveEntries) {
            boolean match = switch (filter) {
                case ALL -> true;
                case HUMAN -> "HUMAN".equals(e.badge);
                case BOT -> "BOT".equals(e.badge);
                case BOTH -> "AMBOS".equals(e.badge);
                case UNKNOWN -> "UNKNOWN".equals(e.badge);
            };
            if (match) count++;
        }
        return count;
    }

    private List<ScoreboardObjectiveSnapshot> filteredObjectives() {
        String q = objectiveSearchField != null ? objectiveSearchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return scoreboards;
        List<ScoreboardObjectiveSnapshot> out = new ArrayList<>();
        for (ScoreboardObjectiveSnapshot obj : scoreboards) {
            if (obj.name != null && obj.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(obj);
            }
        }
        return out;
    }

    private void openCreateObjectiveForm() {
        sbPanelMode = SbPanelMode.CREATE;
        editingObjectiveName = "";
        editingObjectiveCriterion = "dummy";
        formFillFromStats = false;
        formSlot = "";
        formTotalEnabled = false;
        formTotalHumans = true;
        formTotalBots = false;
        formTotalUnknown = false;
        init();
        if (sbNameField != null) sbNameField.setText("");
        if (sbCriterionField != null) sbCriterionField.setText("");
        if (sbDisplayField != null) sbDisplayField.setText("");
        if (sbAliasField != null) sbAliasField.setText("");
    }

    private void openEditObjectiveForm(ScoreboardObjectiveSnapshot obj) {
        sbPanelMode = SbPanelMode.EDIT;
        editingObjectiveName = obj.name;
        editingObjectiveCriterion = obj.criterion != null ? obj.criterion : "dummy";
        formFillFromStats = false;
        formSlot = obj.slot != null ? obj.slot : "";
        formTotalEnabled = obj.totalEnabled;
        formTotalHumans = obj.totalHumans;
        formTotalBots = obj.totalBots;
        formTotalUnknown = obj.totalUnknown;
        init();
        if (sbDisplayField != null) {
            sbDisplayField.setText(obj.displayNameMarkup != null && !obj.displayNameMarkup.isEmpty()
                ? obj.displayNameMarkup
                : (obj.displayName != null ? obj.displayName : obj.name));
        }
        if (sbAliasField != null) {
            sbAliasField.setText(obj.chatAlias != null ? obj.chatAlias : "");
        }
    }

    private void submitScoreboardForm() {
        if (sbPanelMode == SbPanelMode.CREATE) {
            String name = sbNameField != null ? sbNameField.getText().trim() : "";
            if (name.isEmpty()) {
                statusMessage = "Escribe un ID interno para la scoreboard.";
                statusError = true;
                return;
            }
            CreateObjectiveRequest req = new CreateObjectiveRequest();
            req.name = name;
            req.criterion = sbCriterionField != null ? sbCriterionField.getText().trim() : "dummy";
            req.displayName = sbDisplayField != null ? sbDisplayField.getText().trim() : "";
            req.fillFromStats = formFillFromStats;
            req.slot = formSlot;
            req.chatAlias = sbAliasField != null ? sbAliasField.getText().trim() : "";
            req.totalEnabled = formTotalEnabled;
            req.totalHumans = formTotalHumans;
            req.totalBots = formTotalBots;
            req.totalUnknown = formTotalUnknown;
            selectedObjectiveName = name;
            loadedObjectiveName = null;
            sbPanelMode = SbPanelMode.VIEW;
            statusMessage = "Creando scoreboard '" + name + "'…";
            statusError = false;
            ClientPlayNetworking.send(new StatsRegistryActionPayload("create_objective", "", GSON.toJson(req)));
            init();
        } else if (sbPanelMode == SbPanelMode.EDIT) {
            EditObjectiveRequest req = new EditObjectiveRequest();
            req.name = editingObjectiveName;
            req.displayName = sbDisplayField != null ? sbDisplayField.getText().trim() : "";
            req.slot = formSlot;
            req.chatAlias = sbAliasField != null ? sbAliasField.getText().trim() : "";
            req.totalEnabled = formTotalEnabled;
            req.totalHumans = formTotalHumans;
            req.totalBots = formTotalBots;
            req.totalUnknown = formTotalUnknown;
            sbPanelMode = SbPanelMode.VIEW;
            statusMessage = "Guardando '" + editingObjectiveName + "'…";
            statusError = false;
            ClientPlayNetworking.send(new StatsRegistryActionPayload("edit_objective", "", GSON.toJson(req)));
            init();
        }
    }

    private long computeObjectiveTotal(ScoreboardObjectiveSnapshot obj) {
        if (obj == null || !obj.totalEnabled || selectedObjectiveEntries == null) {
            return 0L;
        }
        long sum = 0L;
        for (ScoreboardEntrySnapshot e : selectedObjectiveEntries) {
            boolean include = switch (e.badge) {
                case "HUMAN" -> obj.totalHumans;
                case "BOT" -> obj.totalBots;
                case "AMBOS" -> obj.totalHumans || obj.totalBots;
                default -> obj.totalUnknown;
            };
            if (include) {
                sum += e.score;
            }
        }
        return sum;
    }

    private void renderScoreboardsTab(DrawContext c, int mx, int my, float delta) {
        int topY = tabBarY() + TAB_BAR_H;
        int footerY = panelY + panelH - 36;
        int sTop = topY + 28;
        int sBottom = footerY - 34;

        // Sidebar: list of objectives
        c.fill(panelX + 1, topY, panelX + SIDEBAR_W, footerY, AeternumTheme.SIDEBAR_BG);
        c.fill(panelX + SIDEBAR_W, topY, panelX + SIDEBAR_W + 1, footerY, AeternumTheme.BORDER_SUBTLE);

        List<ScoreboardObjectiveSnapshot> shownObjectives = filteredObjectives();
        int maxSbScroll = Math.max(0, shownObjectives.size() * SB_SIDEBAR_ROW_H - (sBottom - sTop));
        targetSbSidebarScroll = Math.max(0, Math.min(targetSbSidebarScroll, maxSbScroll));
        sbSidebarScroll += (targetSbSidebarScroll - sbSidebarScroll) * Math.min(1.0, delta * 0.45 + 0.25);

        if (shownObjectives.isEmpty()) {
            c.drawText(textRenderer, "Sin scoreboards", panelX + 12, sTop + 8, AeternumTheme.TEXT_MUTED, false);
        } else {
            c.enableScissor(panelX + 2, sTop, panelX + SIDEBAR_W - 1, sBottom);
            for (int i = 0; i < shownObjectives.size(); i++) {
                ScoreboardObjectiveSnapshot obj = shownObjectives.get(i);
                int ry = sTop + i * SB_SIDEBAR_ROW_H - (int) Math.round(sbSidebarScroll);
                if (ry + SB_SIDEBAR_ROW_H < sTop || ry > sBottom) continue;

                boolean sel = obj.name.equals(selectedObjectiveName);
                boolean inSidebar = mx >= panelX + 4 && mx < panelX + SIDEBAR_W - 4 && my >= sTop && my < sBottom;
                boolean hover = inSidebar && my >= ry && my < ry + SB_SIDEBAR_ROW_H - 2;
                if (sel) {
                    c.fill(panelX + 4, ry, panelX + SIDEBAR_W - 4, ry + SB_SIDEBAR_ROW_H - 2, AeternumTheme.CARD_SELECTED);
                    c.fill(panelX + 4, ry, panelX + 7, ry + SB_SIDEBAR_ROW_H - 2, AeternumTheme.ACCENT_GOLD);
                } else if (hover) {
                    c.fill(panelX + 4, ry, panelX + SIDEBAR_W - 4, ry + SB_SIDEBAR_ROW_H - 2, AeternumTheme.CARD_HOVER);
                }

                int delX = panelX + SIDEBAR_W - 22;
                int editX = delX - 16;
                int delY = ry + 5;
                boolean overEdit = inSidebar && mx >= editX && mx < editX + 14 && my >= delY && my < delY + 14;
                if (overEdit) {
                    hoveredObjectiveEdit = obj.name;
                }
                AeternumTheme.drawCard(c, editX, delY, 14, 14,
                    overEdit ? 0xFF2D2D38 : 0xFF1A1A22,
                    overEdit ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE);
                c.drawTextWithShadow(textRenderer, "E", editX + 4, delY + 3, overEdit ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);

                boolean overDel = inSidebar && mx >= delX && mx < delX + 14 && my >= delY && my < delY + 14;
                if (overDel) {
                    hoveredObjectiveDelete = obj.name;
                }
                boolean shift = hasShiftDown();
                boolean armed = obj.name.equals(armedObjectiveDelete);
                int delBg = !shift ? 0xFF131318 : (armed ? 0xFF6B1E1E : (overDel ? 0xFF3A2024 : 0xFF221619));
                int delBorder = !shift ? AeternumTheme.BORDER_SUBTLE : (armed ? 0xFFFF6B6B : 0x80E56B6B);
                int delFg = !shift ? AeternumTheme.TEXT_MUTED : (armed ? 0xFFFFFFFF : 0xFFE58A8A);
                AeternumTheme.drawCard(c, delX, delY, 14, 14, delBg, delBorder);
                c.drawTextWithShadow(textRenderer, armed ? "!" : "\u00D7", delX + (armed ? 5 : 4), delY + 3, delFg);

                String lbl = AeternumTheme.ellipsize(textRenderer, obj.name, SIDEBAR_W - 50);
                c.drawTextWithShadow(textRenderer, lbl, panelX + 10, ry + 3, sel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
                StringBuilder meta = new StringBuilder();
                meta.append(obj.entryCount).append(" ent.");
                if (obj.slot != null && !obj.slot.isEmpty()) meta.append(" \u00B7 ").append(obj.slot);
                if (obj.chatAlias != null && !obj.chatAlias.isEmpty()) meta.append(" \u00B7 !!").append(obj.chatAlias);
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, meta.toString(), SIDEBAR_W - 50), panelX + 10, ry + 14, AeternumTheme.TEXT_MUTED, false);
            }
            c.disableScissor();
            if (maxSbScroll > 0) {
                AeternumTheme.drawScrollbar(c, panelX + SIDEBAR_W - 5, sTop, sBottom,
                    shownObjectives.size() * SB_SIDEBAR_ROW_H, Math.max(1, sBottom - sTop),
                    sbSidebarScroll, draggingSbSidebarScrollbar);
            }
        }

        if (sbPanelMode != SbPanelMode.VIEW) {
            renderScoreboardForm(c, mx, my);
            return;
        }

        // Right panel: filter chips + entries list
        ScoreboardObjectiveSnapshot selected = selectedObjectiveSnapshot();
        int contentX = panelX + SIDEBAR_W + 10;
        int chipY = topY + 32;
        int chipX = contentX;
        for (ScoreFilter sf : ScoreFilter.values()) {
            String chipLabel = sf.label + " (" + countScoreFilter(selected, sf) + ")";
            int cw = textRenderer.getWidth(chipLabel) + 12;
            boolean sel = sf == activeScoreFilter;
            boolean over = mx >= chipX && mx < chipX + cw && my >= chipY && my < chipY + 16;
            AeternumTheme.drawCard(c, chipX, chipY, cw, 16,
                sel ? AeternumTheme.CARD_SELECTED : (over ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_ALT_BG),
                sel ? AeternumTheme.ACCENT_GOLD : AeternumTheme.BORDER_SUBTLE);
            c.drawText(textRenderer, chipLabel, chipX + 6, chipY + 4, sel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY, false);
            chipX += cw + 4;
        }
        if (selected != null && selected.totalEnabled && selected.name.equals(loadedObjectiveName)) {
            String totStr = "Total: " + String.format(Locale.US, "%,d", computeObjectiveTotal(selected));
            int tw = textRenderer.getWidth(totStr) + 12;
            int tx = panelX + panelW - tw - 12;
            if (tx > chipX + 4) {
                AeternumTheme.drawCard(c, tx, chipY, tw, 16, 0xFF1E1A12, AeternumTheme.ACCENT_GOLD);
                c.drawTextWithShadow(textRenderer, totStr, tx + 6, chipY + 4, AeternumTheme.ACCENT_GOLD);
            }
        }

        int lTop = listTop();
        int lBottom = listBottom();
        int viewH = Math.max(1, lBottom - lTop);
        int cardX = contentX;
        int cardW = panelW - SIDEBAR_W - 26;

        if (selected == null) {
            c.drawCenteredTextWithShadow(textRenderer, "Selecciona una scoreboard.", cardX + cardW / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
            return;
        }
        if (!selected.name.equals(loadedObjectiveName)) {
            c.drawCenteredTextWithShadow(textRenderer, "Cargando entradas...", cardX + cardW / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
            return;
        }

        List<ScoreboardEntrySnapshot> shown = filteredScoreEntries(selected);
        int maxL = maxListScrollPx(shown.size(), SB_ENTRY_STRIDE);
        targetListScroll = Math.max(0, Math.min(targetListScroll, maxL));
        listScroll += (targetListScroll - listScroll) * Math.min(1.0, delta * 0.45 + 0.25);
        listScroll = Math.max(0, Math.min(listScroll, maxL));

        if (shown.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, "Sin entradas en esta scoreboard.", cardX + cardW / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
            return;
        }

        c.enableScissor(panelX + SIDEBAR_W + 2, lTop, panelX + panelW - 8, lBottom);
        for (int i = 0; i < shown.size(); i++) {
            int cy = lTop + i * SB_ENTRY_STRIDE - (int) Math.round(listScroll);
            if (cy + SB_ENTRY_H < lTop || cy > lBottom) continue;

            ScoreboardEntrySnapshot item = shown.get(i);
            boolean inArea = my >= lTop && my < lBottom;
            boolean over = inArea && mx >= cardX && mx < cardX + cardW && my >= cy && my < cy + SB_ENTRY_H;
            AeternumTheme.drawCard(c, cardX, cy, cardW, SB_ENTRY_H, over ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG,
                over ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE);

            String scoreStr = String.format(Locale.US, "%,d", item.score);
            int scoreW = textRenderer.getWidth(scoreStr);
            int scoreX = cardX + cardW - scoreW - 10;
            c.drawTextWithShadow(textRenderer, scoreStr, scoreX, cy + 9, AeternumTheme.ACCENT_GOLD);

            int badgeW = textRenderer.getWidth(item.badge) + 10;
            int ownerMaxW = Math.max(40, scoreX - cardX - badgeW - 22);
            String ownerShown = AeternumTheme.ellipsize(textRenderer, item.owner, ownerMaxW);
            c.drawTextWithShadow(textRenderer, ownerShown, cardX + 10, cy + 9, AeternumTheme.TEXT_PRIMARY);

            int badgeX = cardX + 16 + textRenderer.getWidth(ownerShown);
            int badgeBg = switch (item.badge) {
                case "HUMAN" -> 0xFF1A281E;
                case "BOT" -> 0xFF18222C;
                case "AMBOS" -> 0xFF2D2414;
                default -> 0xFF1A1A20;
            };
            int badgeFg = switch (item.badge) {
                case "HUMAN" -> 0xFF9DF0B4;
                case "BOT" -> 0xFF9CD0FF;
                case "AMBOS" -> 0xFFFFC57A;
                default -> AeternumTheme.TEXT_SECONDARY;
            };
            if (badgeX + badgeW < scoreX - 6) {
                AeternumTheme.drawBadge(c, textRenderer, badgeX, cy + 6, item.badge, badgeBg, badgeFg);
            }
        }
        c.disableScissor();

        if (maxL > 0) {
            AeternumTheme.drawScrollbar(c, panelX + panelW - 9, lTop, lBottom,
                shown.size() * SB_ENTRY_STRIDE, viewH, listScroll, draggingScrollbar);
        }
    }

    private static final String[][] CRITERION_PRESETS = {
        {"clear", ""},
        {"dig_all", "aeternum:dig_all"},
        {"placed_blocks", "aeternum:placed_blocks"},
        {"mined", "minecraft.mined:minecraft."},
        {"used", "minecraft.used:minecraft."},
        {"crafted", "minecraft.crafted:minecraft."},
        {"killed", "minecraft.killed:minecraft."},
        {"custom", "minecraft.custom:minecraft."}
    };

    private static final String[][] SLOT_OPTIONS = {
        {"", "Ninguno"},
        {"SIDEBAR", "SIDEBAR"},
        {"LIST", "LIST"},
        {"BELOW_NAME", "BELOW_NAME"}
    };

    private int formDisplayLabelY(int contentTop) {
        return sbPanelMode == SbPanelMode.CREATE ? (contentTop + 111) : (contentTop + 40);
    }

    private void renderScoreboardForm(DrawContext c, int mx, int my) {
        int contentTop = panelY + 44 + TAB_BAR_H;
        int contentX = panelX + SIDEBAR_W + 10;
        int contentW = panelW - SIDEBAR_W - 22;

        String header = sbPanelMode == SbPanelMode.CREATE
            ? "Crear nueva scoreboard"
            : ("Editando: " + editingObjectiveName);
        c.drawTextWithShadow(textRenderer, header, contentX, contentTop + 8, AeternumTheme.TEXT_PRIMARY);

        if (sbPanelMode == SbPanelMode.CREATE) {
            c.drawText(textRenderer, "ID interno (sin espacios)", contentX, contentTop + 24, AeternumTheme.TEXT_SECONDARY, false);
            c.drawText(textRenderer, "Criterio", contentX, contentTop + 58, AeternumTheme.TEXT_SECONDARY, false);

            int pillY = contentTop + 90;
            int px = contentX;
            for (String[] preset : CRITERION_PRESETS) {
                int pw = textRenderer.getWidth(preset[0]) + 10;
                boolean over = mx >= px && mx < px + pw && my >= pillY && my < pillY + 15;
                AeternumTheme.drawCard(c, px, pillY, pw, 15,
                    over ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_ALT_BG,
                    over ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE);
                c.drawText(textRenderer, preset[0], px + 5, pillY + 4, AeternumTheme.TEXT_SECONDARY, false);
                px += pw + 4;
            }
            String curCrit = sbCriterionField != null ? sbCriterionField.getText().trim() : "";
            if (curCrit.isEmpty() || "dummy".equalsIgnoreCase(curCrit)) {
                String hint = "Vacío = dummy  \u00B7  o elige mined/used/crafted/killed/custom";
                int hx = px + 4;
                int avail = contentX + contentW - hx;
                if (avail > 50) {
                    c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, hint, avail), hx, pillY + 4, AeternumTheme.TEXT_MUTED, false);
                }
            }
        } else {
            c.drawText(textRenderer, "Criterio: " + editingObjectiveCriterion + " (solo lectura)", contentX, contentTop + 24, AeternumTheme.TEXT_MUTED, false);
        }

        int dispLabelY = formDisplayLabelY(contentTop);
        c.drawText(textRenderer, "Título visible (soporta \\gold, \\aqua, \\bold, \\reset...)", contentX, dispLabelY, AeternumTheme.TEXT_SECONDARY, false);
        String dispRaw = sbDisplayField != null ? sbDisplayField.getText() : "";
        String previewSrc = dispRaw.isBlank()
            ? (sbPanelMode == SbPanelMode.CREATE ? (sbNameField != null ? sbNameField.getText() : "") : editingObjectiveName)
            : dispRaw;
        Text preview = MotdMarkup.parse(previewSrc);
        c.drawText(textRenderer, "Vista previa: ", contentX, dispLabelY + 33, AeternumTheme.TEXT_MUTED, false);
        c.drawTextWithShadow(textRenderer, preview, contentX + textRenderer.getWidth("Vista previa: "), dispLabelY + 33, AeternumTheme.TEXT_PRIMARY);

        int aliasLabelY = dispLabelY + 49;
        c.drawText(textRenderer, "Atajo chat (!!)", contentX, aliasLabelY, AeternumTheme.TEXT_SECONDARY, false);
        int slotX = contentX + 162;
        c.drawText(textRenderer, "Slot en pantalla", slotX, aliasLabelY, AeternumTheme.TEXT_SECONDARY, false);
        int slotPillY = aliasLabelY + 11;
        int sx = slotX;
        for (String[] opt : SLOT_OPTIONS) {
            int sw = textRenderer.getWidth(opt[1]) + 10;
            boolean sel = opt[0].equals(formSlot);
            boolean over = mx >= sx && mx < sx + sw && my >= slotPillY && my < slotPillY + 18;
            AeternumTheme.drawCard(c, sx, slotPillY, sw, 18,
                sel ? AeternumTheme.CARD_SELECTED : (over ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_ALT_BG),
                sel ? AeternumTheme.ACCENT_GOLD : AeternumTheme.BORDER_SUBTLE);
            c.drawText(textRenderer, opt[1], sx + 5, slotPillY + 5, sel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY, false);
            sx += sw + 4;
        }

        int totalLabelY = aliasLabelY + 37;
        c.drawText(textRenderer, "Cálculo de Total", contentX, totalLabelY, AeternumTheme.TEXT_SECONDARY, false);
        int ty = totalLabelY + 11;
        int tx = contentX;
        tx += drawTogglePill(c, tx, ty, "Total: " + (formTotalEnabled ? "ON" : "OFF"), formTotalEnabled, 0xFF7AD694, mx, my) + 8;
        tx += drawTogglePill(c, tx, ty, "Humans", formTotalHumans, 0xFF9DF0B4, mx, my) + 4;
        tx += drawTogglePill(c, tx, ty, "Bots", formTotalBots, 0xFF9CD0FF, mx, my) + 4;
        drawTogglePill(c, tx, ty, "Unknown", formTotalUnknown, AeternumTheme.ACCENT_GOLD, mx, my);

        if (sbPanelMode == SbPanelMode.CREATE) {
            int btnY = panelY + panelH - 64;
            String curCrit = sbCriterionField != null ? sbCriterionField.getText().trim() : "";
            boolean isStat = curCrit.contains(":");
            String box = (formFillFromStats && isStat) ? "[x] " : "[ ] ";
            String lbl = box + "Poblar con stats previas";
            int fw = textRenderer.getWidth(lbl) + 14;
            boolean over = isStat && mx >= contentX && mx < contentX + fw && my >= btnY && my < btnY + 20;
            int bg = !isStat ? 0xFF121216 : (formFillFromStats ? 0xFF1A281E : (over ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_ALT_BG));
            int border = !isStat ? AeternumTheme.BORDER_SUBTLE : (formFillFromStats ? 0xFF7AD694 : AeternumTheme.BORDER_BRIGHT);
            int fg = !isStat ? AeternumTheme.TEXT_MUTED : (formFillFromStats ? 0xFF9DF0B4 : AeternumTheme.TEXT_PRIMARY);
            AeternumTheme.drawCard(c, contentX, btnY, fw, 20, bg, border);
            c.drawTextWithShadow(textRenderer, lbl, contentX + 7, btnY + 6, fg);
        }
    }

    private int drawTogglePill(DrawContext c, int x, int y, String label, boolean on, int accent, int mx, int my) {
        int w = textRenderer.getWidth(label) + 14;
        int h = 18;
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
        int bg = on ? AeternumTheme.lerpColor(0xFF18181E, accent, 0.20f) : (over ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_ALT_BG);
        int border = on ? accent : AeternumTheme.BORDER_SUBTLE;
        int fg = on ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_MUTED;
        AeternumTheme.drawCard(c, x, y, w, h, bg, border);
        c.drawTextWithShadow(textRenderer, label, x + 7, y + 5, fg);
        return w;
    }

    private boolean handleScoreboardFormClick(double mx, double my) {
        int contentTop = panelY + 44 + TAB_BAR_H;
        int contentX = panelX + SIDEBAR_W + 10;
        int contentW = panelW - SIDEBAR_W - 22;

        if (sbPanelMode == SbPanelMode.CREATE) {
            int pillY = contentTop + 90;
            if (my >= pillY && my < pillY + 15) {
                int px = contentX;
                for (String[] preset : CRITERION_PRESETS) {
                    int pw = textRenderer.getWidth(preset[0]) + 10;
                    if (mx >= px && mx < px + pw) {
                        if (sbCriterionField != null) {
                            if (sbNameField != null) sbNameField.setFocused(false);
                            sbCriterionField.setText(preset[1]);
                            sbCriterionField.setCursorToEnd(false);
                            boolean keepFocus = preset[1].endsWith(".");
                            sbCriterionField.setFocused(keepFocus);
                            if (!keepFocus && getFocused() == sbCriterionField) {
                                setFocused(null);
                            }
                        }
                        return true;
                    }
                    px += pw + 4;
                }
            }

            int btnY = panelY + panelH - 64;
            String curCrit = sbCriterionField != null ? sbCriterionField.getText().trim() : "";
            if (curCrit.contains(":") && my >= btnY && my < btnY + 20) {
                String box = formFillFromStats ? "[x] " : "[ ] ";
                int fw = textRenderer.getWidth(box + "Poblar con stats previas") + 14;
                if (mx >= contentX && mx < contentX + fw) {
                    formFillFromStats = !formFillFromStats;
                    return true;
                }
            }
        }

        int dispLabelY = formDisplayLabelY(contentTop);
        int aliasLabelY = dispLabelY + 49;
        int slotX = contentX + 162;
        int slotPillY = aliasLabelY + 11;
        if (my >= slotPillY && my < slotPillY + 18) {
            int sx = slotX;
            for (String[] opt : SLOT_OPTIONS) {
                int sw = textRenderer.getWidth(opt[1]) + 10;
                if (mx >= sx && mx < sx + sw) {
                    formSlot = opt[0];
                    return true;
                }
                sx += sw + 4;
            }
        }

        int totalLabelY = aliasLabelY + 37;
        int ty = totalLabelY + 11;
        if (my >= ty && my < ty + 18) {
            int tx = contentX;
            int wTot = textRenderer.getWidth("Total: " + (formTotalEnabled ? "ON" : "OFF")) + 14;
            if (mx >= tx && mx < tx + wTot) {
                formTotalEnabled = !formTotalEnabled;
                return true;
            }
            tx += wTot + 8;
            int wHum = textRenderer.getWidth("Humans") + 14;
            if (mx >= tx && mx < tx + wHum) {
                formTotalHumans = !formTotalHumans;
                return true;
            }
            tx += wHum + 4;
            int wBot = textRenderer.getWidth("Bots") + 14;
            if (mx >= tx && mx < tx + wBot) {
                formTotalBots = !formTotalBots;
                return true;
            }
            tx += wBot + 4;
            int wUnk = textRenderer.getWidth("Unknown") + 14;
            if (mx >= tx && mx < tx + wUnk) {
                formTotalUnknown = !formTotalUnknown;
                return true;
            }
        }
        return false;
    }

    private List<String> criterionSuggestions() {
        if (sbCriterionField == null) return List.of();
        String raw = sbCriterionField.getText().trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();
        if (raw.startsWith("minecraft.mined:")) {
            String q = raw.substring("minecraft.mined:".length()).replace("minecraft.", "");
            for (var id : Registries.BLOCK.getIds()) {
                String path = id.getPath();
                if (q.isEmpty() || path.contains(q)) {
                    out.add("minecraft.mined:minecraft." + path);
                }
            }
        } else if (raw.startsWith("minecraft.used:") || raw.startsWith("minecraft.crafted:")
            || raw.startsWith("minecraft.broken:") || raw.startsWith("minecraft.picked_up:")
            || raw.startsWith("minecraft.dropped:")) {
            int colon = raw.indexOf(':');
            String prefix = raw.substring(0, colon + 1);
            String q = raw.substring(colon + 1).replace("minecraft.", "");
            for (var id : Registries.ITEM.getIds()) {
                String path = id.getPath();
                if (q.isEmpty() || path.contains(q)) {
                    out.add(prefix + "minecraft." + path);
                }
            }
        } else if (raw.startsWith("minecraft.killed:") || raw.startsWith("minecraft.killed_by:")) {
            int colon = raw.indexOf(':');
            String prefix = raw.substring(0, colon + 1);
            String q = raw.substring(colon + 1).replace("minecraft.", "");
            for (var id : Registries.ENTITY_TYPE.getIds()) {
                String path = id.getPath();
                if (q.isEmpty() || path.contains(q)) {
                    out.add(prefix + "minecraft." + path);
                }
            }
        } else if (raw.startsWith("minecraft.custom:")) {
            String q = raw.substring("minecraft.custom:".length()).replace("minecraft.", "");
            for (var id : Registries.CUSTOM_STAT.getIds()) {
                String path = id.getPath();
                if (q.isEmpty() || path.contains(q)) {
                    out.add("minecraft.custom:minecraft." + path);
                }
            }
        } else {
            String[] basics = {
                "aeternum:dig_all", "aeternum:placed_blocks",
                "dummy", "trigger", "health", "deathCount", "playerKillCount",
                "totalKillCount", "armor", "food", "air", "xp", "level",
                "minecraft.mined:minecraft.", "minecraft.used:minecraft.",
                "minecraft.crafted:minecraft.", "minecraft.killed:minecraft.",
                "minecraft.custom:minecraft."
            };
            for (String b : basics) {
                if (b.toLowerCase(Locale.ROOT).contains(raw) && !b.equalsIgnoreCase(raw)) {
                    out.add(b);
                }
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        if (out.size() == 1 && out.get(0).equalsIgnoreCase(raw)) {
            return List.of();
        }
        return out;
    }

    private void renderCriterionSuggestions(DrawContext c, int mx, int my) {
        List<String> suggestions = criterionSuggestions();
        if (suggestions.isEmpty() || sbCriterionField == null) return;
        int visible = Math.min(8, suggestions.size());
        int maxScroll = Math.max(0, suggestions.size() - visible);
        criterionSugScroll = Math.max(0, Math.min(maxScroll, criterionSugScroll));

        int x = sbCriterionField.boxX();
        int y = sbCriterionField.boxY() + 19;
        int w = sbCriterionField.getWidth() + 12;
        int rowH = 14;
        int h = visible * rowH + 2;
        c.fill(x, y, x + w, y + h, 0xFF0B0B0F);
        c.drawBorder(x, y, w, h, AeternumTheme.BORDER_BRIGHT);

        int textMaxW = maxScroll > 0 ? (w - 16) : (w - 10);
        int rowRight = maxScroll > 0 ? (x + w - 9) : (x + w - 1);
        for (int i = 0; i < visible; i++) {
            int idx = criterionSugScroll + i;
            int ry = y + 1 + i * rowH;
            boolean over = mx >= x + 1 && mx < rowRight && my >= ry && my < ry + rowH;
            if (over) {
                c.fill(x + 1, ry, rowRight, ry + rowH, AeternumTheme.CARD_HOVER);
            }
            String shown = AeternumTheme.ellipsize(textRenderer, suggestions.get(idx), textMaxW);
            c.drawText(textRenderer, shown, x + 5, ry + 3, over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY, false);
        }
        if (maxScroll > 0) {
            AeternumTheme.drawScrollbar(c, x + w - 6, y + 2, y + h - 2, suggestions.size(), visible, criterionSugScroll, draggingCriterionScrollbar);
        }
    }

    private boolean handleCriterionSuggestionClick(double mx, double my) {
        if (sbCriterionField == null || !sbCriterionField.isFocused()) return false;
        List<String> suggestions = criterionSuggestions();
        if (suggestions.isEmpty()) return false;
        int visible = Math.min(8, suggestions.size());
        int maxScroll = Math.max(0, suggestions.size() - visible);
        criterionSugScroll = Math.max(0, Math.min(maxScroll, criterionSugScroll));

        int x = sbCriterionField.boxX();
        int y = sbCriterionField.boxY() + 19;
        int w = sbCriterionField.getWidth() + 12;
        int rowH = 14;
        int h = visible * rowH + 2;
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;

        if (maxScroll > 0 && mx >= x + w - 9) {
            draggingCriterionScrollbar = true;
            double frac = Math.max(0.0, Math.min(1.0, (my - (y + 2)) / Math.max(1.0, h - 4)));
            criterionSugScroll = (int) Math.round(frac * maxScroll);
            return true;
        }

        int idx = criterionSugScroll + (int) ((my - y - 1) / rowH);
        if (idx >= 0 && idx < suggestions.size()) {
            sbCriterionField.setText(suggestions.get(idx));
            sbCriterionField.setCursorToEnd(false);
            sbCriterionField.setFocused(false);
            if (getFocused() == sbCriterionField) {
                setFocused(null);
            }
            criterionSugScroll = 0;
            return true;
        }
        return false;
    }

    private void clearTextFieldFocusIfOutside(double mx, double my) {
        UiTextField[] fields = { searchField, scoreSearchField, objectiveSearchField, sbNameField, sbCriterionField, sbDisplayField, sbAliasField };
        for (UiTextField tf : fields) {
            if (tf == null || !tf.isFocused()) continue;
            int bx = tf.boxX();
            int by = tf.boxY();
            int bw = tf.getWidth() + 12;
            int bh = 20;
            if (mx < bx || mx >= bx + bw || my < by || my >= by + bh) {
                tf.setFocused(false);
                if (getFocused() == tf) {
                    setFocused(null);
                }
            }
        }
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
        if (button == 0 && activeTab == Tab.SCOREBOARDS && sbPanelMode == SbPanelMode.CREATE
            && handleCriterionSuggestionClick(mx, my)) {
            return true;
        }
        if (button == 0) {
            clearTextFieldFocusIfOutside(mx, my);
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
            int sbX = regX + regW + 6;
            int sbW = textRenderer.getWidth("Scoreboards (" + scoreboards.size() + ")") + 18;
            if (mx >= sbX && mx < sbX + sbW && my >= tabY && my < tabY + 18) {
                if (activeTab != Tab.SCOREBOARDS) {
                    activeTab = Tab.SCOREBOARDS;
                    listScroll = targetListScroll = 0;
                    init();
                    if (selectedObjectiveName != null && !selectedObjectiveName.equals(loadedObjectiveName)) {
                        requestSelectedObjectiveDetail();
                    }
                }
                return true;
            }
            int unexpX = sbX + sbW + 6;
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

        if (activeTab == Tab.SCOREBOARDS && button == 0) {
            int topY = tabBarY() + TAB_BAR_H;
            int footerY = panelY + panelH - 36;
            int sTop = topY + 28;
            int sBottom = footerY - 34;

            if (mx >= panelX + SIDEBAR_W - 8 && mx <= panelX + SIDEBAR_W && my >= sTop && my < sBottom) {
                draggingSbSidebarScrollbar = true;
                return true;
            }

            if (mx >= panelX + 4 && mx < panelX + SIDEBAR_W - 4 && my >= sTop && my < sBottom) {
                int delX = panelX + SIDEBAR_W - 22;
                int editX = delX - 16;
                List<ScoreboardObjectiveSnapshot> shownObjectives = filteredObjectives();
                for (int i = 0; i < shownObjectives.size(); i++) {
                    int ry = sTop + i * SB_SIDEBAR_ROW_H - (int) Math.round(sbSidebarScroll);
                    if (ry + SB_SIDEBAR_ROW_H < sTop || ry > sBottom) continue;
                    if (my >= ry && my < ry + SB_SIDEBAR_ROW_H - 2) {
                        ScoreboardObjectiveSnapshot obj = shownObjectives.get(i);
                        int delY = ry + 5;
                        if (mx >= editX && mx < editX + 14 && my >= delY && my < delY + 14) {
                            selectedObjectiveName = obj.name;
                            openEditObjectiveForm(obj);
                            return true;
                        }
                        if (mx >= delX && mx < delX + 14 && my >= delY && my < delY + 14) {
                            if (!hasShiftDown()) {
                                statusMessage = "Mantén Shift para habilitar el borrado de scoreboards.";
                                statusError = false;
                                return true;
                            }
                            if (obj.name.equals(armedObjectiveDelete)) {
                                armedObjectiveDelete = null;
                                if (sbPanelMode == SbPanelMode.EDIT && obj.name.equals(editingObjectiveName)) {
                                    sbPanelMode = SbPanelMode.VIEW;
                                    init();
                                }
                                statusMessage = "Eliminando scoreboard " + obj.name + "…";
                                statusError = false;
                                ClientPlayNetworking.send(new StatsRegistryActionPayload("delete_objective", "", obj.name));
                            } else {
                                armedObjectiveDelete = obj.name;
                                armedObjectiveDeleteUntilMs = System.currentTimeMillis() + 5000L;
                                statusMessage = "Confirma Shift + Clic en ! para borrar scoreboard '" + obj.name + "'";
                                statusError = true;
                            }
                            return true;
                        }
                        selectedObjectiveName = obj.name;
                        listScroll = targetListScroll = 0;
                        if (sbPanelMode != SbPanelMode.VIEW) {
                            sbPanelMode = SbPanelMode.VIEW;
                            init();
                        }
                        requestSelectedObjectiveDetail();
                        return true;
                    }
                }
            }

            if (sbPanelMode != SbPanelMode.VIEW) {
                if (sbPanelMode == SbPanelMode.CREATE && handleCriterionSuggestionClick(mx, my)) {
                    return true;
                }
                if (handleScoreboardFormClick(mx, my)) {
                    return true;
                }
            } else {
                ScoreboardObjectiveSnapshot selected = selectedObjectiveSnapshot();
                int chipY = topY + 32;
                if (my >= chipY && my < chipY + 16) {
                    int chipX = panelX + SIDEBAR_W + 10;
                    for (ScoreFilter sf : ScoreFilter.values()) {
                        String chipLabel = sf.label + " (" + countScoreFilter(selected, sf) + ")";
                        int cw = textRenderer.getWidth(chipLabel) + 12;
                        if (mx >= chipX && mx < chipX + cw) {
                            activeScoreFilter = sf;
                            listScroll = targetListScroll = 0;
                            return true;
                        }
                        chipX += cw + 4;
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
        draggingSbSidebarScrollbar = false;
        draggingCriterionScrollbar = false;
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
        if (activeTab == Tab.SCOREBOARDS) {
            int count = filteredScoreEntries(selectedObjectiveSnapshot()).size();
            return maxListScrollPx(count, SB_ENTRY_STRIDE);
        }
        int count = activeTab == Tab.REGISTRY ? filteredEntries().size() : unexpectedFiles.size();
        int stride = activeTab == Tab.REGISTRY ? ROW_STRIDE : UNEXPECTED_ROW_H;
        return maxListScrollPx(count, stride);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingCriterionScrollbar && sbCriterionField != null) {
            List<String> suggestions = criterionSuggestions();
            int visible = Math.min(8, suggestions.size());
            int maxScroll = Math.max(0, suggestions.size() - visible);
            int y = sbCriterionField.boxY() + 19;
            int h = visible * 14 + 2;
            double frac = Math.max(0.0, Math.min(1.0, (my - (y + 2)) / Math.max(1.0, h - 4)));
            criterionSugScroll = (int) Math.round(frac * maxScroll);
            return true;
        }
        if (draggingSbSidebarScrollbar) {
            int sTop = tabBarY() + TAB_BAR_H + 28;
            int sBottom = panelY + panelH - 36 - 34;
            int maxSb = Math.max(0, filteredObjectives().size() * SB_SIDEBAR_ROW_H - (sBottom - sTop));
            double frac = Math.max(0.0, Math.min(1.0, (my - sTop) / Math.max(1.0, sBottom - sTop)));
            targetSbSidebarScroll = sbSidebarScroll = frac * maxSb;
            return true;
        }
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
        if (activeTab == Tab.SCOREBOARDS && sbPanelMode == SbPanelMode.CREATE
            && sbCriterionField != null && sbCriterionField.isFocused()) {
            List<String> suggestions = criterionSuggestions();
            if (!suggestions.isEmpty()) {
                int visible = Math.min(8, suggestions.size());
                int x = sbCriterionField.boxX();
                int y = sbCriterionField.boxY() + 19;
                int w = sbCriterionField.getWidth() + 12;
                int h = visible * 14 + 2;
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    int maxScroll = Math.max(0, suggestions.size() - visible);
                    criterionSugScroll = Math.max(0, Math.min(maxScroll, criterionSugScroll - (int) Math.signum(vAmt) * 2));
                    return true;
                }
            }
        }
        if (activeTab == Tab.SCOREBOARDS && mx >= panelX + 4 && mx < panelX + SIDEBAR_W) {
            int sTop = tabBarY() + TAB_BAR_H + 28;
            int sBottom = panelY + panelH - 36 - 34;
            if (my >= sTop && my < sBottom) {
                int maxSb = Math.max(0, filteredObjectives().size() * SB_SIDEBAR_ROW_H - (sBottom - sTop));
                targetSbSidebarScroll = Math.max(0, Math.min(maxSb, targetSbSidebarScroll - vAmt * 22.0));
                return true;
            }
        }
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
