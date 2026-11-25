package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.motd.MotdMarkup;
import smp.aeternum.carpet.network.AreaContourPreviewPayload;
import smp.aeternum.carpet.network.StatsNetworking.CreateObjectiveRequest;
import smp.aeternum.carpet.network.StatsNetworking.DeleteScoreEntryRequest;
import smp.aeternum.carpet.network.StatsNetworking.EditObjectiveRequest;
import smp.aeternum.carpet.network.StatsNetworking.ItemSlotSnapshot;
import smp.aeternum.carpet.network.StatsNetworking.AccountSideSnapshot;
import smp.aeternum.carpet.network.StatsNetworking.MigrationCheckResult;
import smp.aeternum.carpet.network.StatsNetworking.PlayerDetailSnapshot;
import smp.aeternum.carpet.network.StatsNetworking.PlayerStatSnapshot;
import smp.aeternum.carpet.network.StatsNetworking.RegistryEntrySnapshot;
import smp.aeternum.carpet.network.StatsNetworking.ScanContourRequest;
import smp.aeternum.carpet.network.StatsNetworking.ScoreboardEntrySnapshot;
import smp.aeternum.carpet.network.StatsNetworking.ScoreboardObjectiveSnapshot;
import smp.aeternum.carpet.network.StatsNetworking.SetScoreRequest;
import smp.aeternum.carpet.network.StatsNetworking.StatsSnapshot;
import smp.aeternum.carpet.network.StatsNetworking.UnexpectedFileSnapshot;
import smp.aeternum.carpet.network.StatsRegistryActionPayload;

public final class StatsScreen extends UiScreen {
    private static final Gson GSON = new Gson();
    private static final Type SCORE_ENTRY_LIST_TYPE = new TypeToken<List<ScoreboardEntrySnapshot>>() {}.getType();
    private static final DateTimeFormatter UNEXPECTED_TIME_FMT =
        DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
    private static final int TAB_BAR_H = 24;
    private static final int SIDEBAR_W = 156;
    private static final int SIDEBAR_ROW_H = 19;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 4;
    private static final int ROW_STRIDE = CARD_H + CARD_GAP;
    private static final int UNEXPECTED_ROW_H = 40;
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
        REGISTRY,
        SCOREBOARDS,
        UNEXPECTED,
        GUIDE
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
        ANOMALY("Anomalías"),
        ENDER_INV("Ender + Inv"),
        MINED("Minados"),
        CRAFTED("Crafteados"),
        PLAY_TIME("Horas jugadas");

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
    private final Map<String, UnexpectedFileSnapshot> unexpectedDetailsByPath = new HashMap<>();
    private String armedPromotePath;
    private long armedPromoteUntilMs;
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
    private boolean showAeObjectives;
    private String inspectingPlayerUuid;
    private PlayerDetailSnapshot inspectingPlayerDetail;
    private String playerDetailSubtab = "STATS";
    private String playerStatCategory = "custom";
    private double playerDetailScroll, targetPlayerDetailScroll;
    private Map<Integer, ItemStack> inspectingInvStacks = new HashMap<>();
    private Map<Integer, ItemStack> inspectingEnderStacks = new HashMap<>();
    private UiTextField playerStatSearchField;
    private UiButton tpLastPosButton;
    private long armedTpLastPosUntilMs;
    private String migrateMode = "PREMIUM";
    private UiTextField migrateNameField;
    private String migrateTargetNameInput = "";
    private MigrationCheckResult migrationCheck;
    private boolean migrationChecking;
    private String migrateDataChoice = "KEEP_A";
    private String migrateStatsChoice = "MERGE";
    private String migrateAdvChoice = "MERGE";
    private String editingScoreOwner;
    private UiTextField inlineScoreField;
    private String armedDeleteScoreOwner;
    private long armedDeleteScoreUntilMs;
    private SbPanelMode sbPanelMode = SbPanelMode.VIEW;
    private String editingObjectiveName = "";
    private String editingObjectiveCriterion = "dummy";
    private boolean formFillFromStats;
    private String formSlot = "";
    private boolean formTotalEnabled;
    private boolean formTotalHumans = true;
    private boolean formTotalBots;
    private boolean formTotalUnknown;
    private String formAreaMode = "NONE";
    private String formAreaDimension = "minecraft:overworld";
    private int formAreaPreviewCols;
    private int formAreaPreviewSize;
    private String formAreaPreviewGrid = "";
    private String formAreaBlockId = "";
    private int criterionSugScroll;
    private boolean draggingCriterionScrollbar;
    private boolean lookupRunning;
    private int lookupDone;
    private int lookupTotal;
    private int lookupResolved;
    private Tab activeTab = Tab.REGISTRY;
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
    private UiTextField areaX1Field;
    private UiTextField areaZ1Field;
    private UiTextField areaX2Field;
    private UiTextField areaZ2Field;
    private UiTextField areaSeedXField;
    private UiTextField areaSeedYField;
    private UiTextField areaSeedZField;
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

    public void acceptAreaContourPreview(AreaContourPreviewPayload payload) {
        if (payload == null) return;
        this.statusMessage = payload.message();
        this.statusError = !payload.ok();
        if (payload.ok()) {
            this.formAreaDimension = payload.dimension();
            this.formAreaPreviewCols = payload.columnCount();
            this.formAreaPreviewSize = payload.previewSize();
            this.formAreaPreviewGrid = payload.previewGrid();
            this.formAreaBlockId = payload.blockId();
        }
    }

    private void applySnapshot(String json) {
        try {
            StatsSnapshot snap = GSON.fromJson(json, StatsSnapshot.class);
            if (snap != null) {
                this.entries = snap.entries != null ? snap.entries : List.of();
                this.unexpectedFiles = snap.unexpectedFiles != null ? snap.unexpectedFiles : List.of();
                this.unexpectedDetailsByPath.clear();
                if (snap.unexpectedDetails != null) {
                    for (UnexpectedFileSnapshot detail : snap.unexpectedDetails) {
                        if (detail != null && detail.path != null) {
                            this.unexpectedDetailsByPath.put(detail.path, detail);
                        }
                    }
                }
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
        this.unexpectedDetailsByPath.clear();
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
            case ENDER_INV -> byDescMetric(e -> (e.enderSlots < 0 && e.invSlots < 0)
                ? -1L
                : (Math.max(0, e.enderSlots) + Math.max(0, e.invSlots)), byNameOrUuid);
            case MINED -> byDescMetric(e -> e.mined, byNameOrUuid);
            case CRAFTED -> byDescMetric(e -> e.crafted, byNameOrUuid);
            case PLAY_TIME -> byDescMetric(e -> e.playTimeTicks, byNameOrUuid);
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

    private static Comparator<RegistryEntrySnapshot> byDescMetric(
        java.util.function.ToLongFunction<RegistryEntrySnapshot> getter,
        Comparator<RegistryEntrySnapshot> tieBreaker
    ) {
        return Comparator
            .comparing((RegistryEntrySnapshot e) -> getter.applyAsLong(e) < 0L)
            .thenComparing(Comparator.comparingLong(getter).reversed())
            .thenComparing(tieBreaker);
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
        String prevAreaX1 = areaX1Field != null ? areaX1Field.getText() : "0";
        String prevAreaZ1 = areaZ1Field != null ? areaZ1Field.getText() : "0";
        String prevAreaX2 = areaX2Field != null ? areaX2Field.getText() : "0";
        String prevAreaZ2 = areaZ2Field != null ? areaZ2Field.getText() : "0";
        String prevSeedX = areaSeedXField != null ? areaSeedXField.getText() : "";
        String prevSeedY = areaSeedYField != null ? areaSeedYField.getText() : "";
        String prevSeedZ = areaSeedZField != null ? areaSeedZField.getText() : "";
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
        areaX1Field = null;
        areaZ1Field = null;
        areaX2Field = null;
        areaZ2Field = null;
        areaSeedXField = null;
        areaSeedYField = null;
        areaSeedZField = null;
        sbSaveButton = null;
        sbCancelButton = null;
        playerStatSearchField = null;

        if (activeTab == Tab.REGISTRY) {
            int contentX = panelX + SIDEBAR_W + 10;
            int contentW = panelW - SIDEBAR_W - 22;
            if (inspectingPlayerUuid != null) {
                addDrawableChild(UiButton.secondary(contentX, contentTop + 6, 62, 18, "\u2190 Volver", b -> closePlayerDetail()));
                addDrawableChild(
                    "STATS".equals(playerDetailSubtab)
                        ? UiButton.primary(contentX + 66, contentTop + 6, 78, 18, "Estadísticas", b -> {})
                        : UiButton.secondary(contentX + 66, contentTop + 6, 78, 18, "Estadísticas", b -> {
                            playerDetailSubtab = "STATS";
                            playerDetailScroll = targetPlayerDetailScroll = 0;
                            init();
                        })
                );
                addDrawableChild(
                    "INVENTORY".equals(playerDetailSubtab)
                        ? UiButton.primary(contentX + 148, contentTop + 6, 106, 18, "Inventario / Ender", b -> {})
                        : UiButton.secondary(contentX + 148, contentTop + 6, 106, 18, "Inventario / Ender", b -> {
                            playerDetailSubtab = "INVENTORY";
                            playerDetailScroll = targetPlayerDetailScroll = 0;
                            init();
                        })
                );
                addDrawableChild(
                    "MIGRATE".equals(playerDetailSubtab)
                        ? UiButton.primary(contentX + 258, contentTop + 6, 54, 18, "Migrar", b -> {})
                        : UiButton.secondary(contentX + 258, contentTop + 6, 54, 18, "Migrar", b -> {
                            playerDetailSubtab = "MIGRATE";
                            playerDetailScroll = targetPlayerDetailScroll = 0;
                            if (migrateTargetNameInput.isEmpty() && inspectingPlayerDetail != null && inspectingPlayerDetail.name != null) {
                                migrateTargetNameInput = inspectingPlayerDetail.name;
                            }
                            init();
                        })
                );
                boolean tpArmed = System.currentTimeMillis() < armedTpLastPosUntilMs;
                tpLastPosButton = tpArmed
                    ? UiButton.danger(contentX + contentW - 210, contentTop + 6, 98, 18, "Confirmar TP", b -> handleTpLastPosClick())
                    : UiButton.secondary(contentX + contentW - 210, contentTop + 6, 98, 18, "TP última pos.", b -> handleTpLastPosClick());
                tpLastPosButton.active = inspectingPlayerDetail != null && inspectingPlayerDetail.hasLastPos;
                addDrawableChild(tpLastPosButton);
                addDrawableChild(UiButton.primary(contentX + contentW - 108, contentTop + 6, 108, 18, "Abrir editor inv.", b -> {
                    statusMessage = "Abriendo editor de inventario…";
                    statusError = false;
                    ClientPlayNetworking.send(new StatsRegistryActionPayload("open_player_inventory", inspectingPlayerUuid, ""));
                }));
                if ("STATS".equals(playerDetailSubtab)) {
                    playerStatSearchField = new UiTextField(textRenderer, contentX, contentTop + 28, contentW, 18, "Buscar estadística…");
                    playerStatSearchField.setMaxLength(64);
                    playerStatSearchField.setChangedListener(s -> {
                        targetPlayerDetailScroll = 0;
                        playerDetailScroll = 0;
                    });
                    addDrawableChild(playerStatSearchField);
                } else if ("MIGRATE".equals(playerDetailSubtab)) {
                    initMigrateSubtabWidgets(contentX, contentW, contentTop);
                }
            } else {
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
            }

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
            inlineScoreField = new UiTextField(textRenderer, 0, 0, 82, 18, "Score");
            inlineScoreField.setMaxLength(16);
            inlineScoreField.setVisible(editingScoreOwner != null && sbPanelMode == SbPanelMode.VIEW);
            addDrawableChild(inlineScoreField);

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

            int aeHalfW = (SIDEBAR_W - 20) / 2;
            UiButton toggleAeBtn = UiButton.secondary(panelX + 8, panelY + panelH - 88, aeHalfW, 22,
                showAeObjectives ? "ae-*: ON" : "ae-*: OFF", b -> {
                    showAeObjectives = !showAeObjectives;
                    targetSbSidebarScroll = 0;
                    sbSidebarScroll = 0;
                    init();
                });
            addDrawableChild(toggleAeBtn);

            boolean hasAe = scoreboards.stream().anyMatch(o -> o.name != null && o.name.toLowerCase(Locale.ROOT).startsWith("ae-"));
            UiButton clearAeBtn = UiButton.danger(panelX + 12 + aeHalfW, panelY + panelH - 88, SIDEBAR_W - 20 - aeHalfW, 22,
                "Limpiar", b -> {
                    statusMessage = "Limpiando scoreboards ae-*…";
                    statusError = false;
                    ClientPlayNetworking.send(new StatsRegistryActionPayload("clear_ae_objectives", "", ""));
                });
            clearAeBtn.active = hasAe;
            addDrawableChild(clearAeBtn);

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
                sbNameField = new UiTextField(textRenderer, contentX, contentTop + 35, contentW, 18, "ej: dig_diamantes");
                sbNameField.setMaxLength(128);
                sbNameField.setText(prevSbName);
                addDrawableChild(sbNameField);

                sbCriterionField = new UiTextField(textRenderer, contentX, contentTop + 69, contentW, 18, "vacío = dummy");
                sbCriterionField.setMaxLength(128);
                sbCriterionField.setText(prevSbCriterion);
                sbCriterionField.setChangedListener(s -> criterionSugScroll = 0);
                addDrawableChild(sbCriterionField);

                sbDisplayField = new UiTextField(textRenderer, contentX, dispLabelY + 11, contentW, 18, "Ej: \\gold\\boldMi Scoreboard (vacío = ID)");
                sbDisplayField.setMaxLength(128);
                sbDisplayField.setText(prevSbDisplay);
                addDrawableChild(sbDisplayField);

                int aliasY = dispLabelY + 60;
                sbAliasField = new UiTextField(textRenderer, contentX, aliasY, 150, 18, "ej: gold (vacío = sin atajo)");
                sbAliasField.setMaxLength(32);
                sbAliasField.setText(prevSbAlias);
                addDrawableChild(sbAliasField);

                if ("RECTANGLE".equals(formAreaMode)) {
                    int rectY = contentTop + 278;
                    areaX1Field = new UiTextField(textRenderer, contentX + 16, rectY, 54, 18, "X1");
                    areaX1Field.setMaxLength(10);
                    areaX1Field.setText(prevAreaX1);
                    addDrawableChild(areaX1Field);

                    areaZ1Field = new UiTextField(textRenderer, contentX + 88, rectY, 54, 18, "Z1");
                    areaZ1Field.setMaxLength(10);
                    areaZ1Field.setText(prevAreaZ1);
                    addDrawableChild(areaZ1Field);

                    int x2Start = contentX + 196;
                    areaX2Field = new UiTextField(textRenderer, x2Start + 16, rectY, 54, 18, "X2");
                    areaX2Field.setMaxLength(10);
                    areaX2Field.setText(prevAreaX2);
                    addDrawableChild(areaX2Field);

                    areaZ2Field = new UiTextField(textRenderer, x2Start + 88, rectY, 54, 18, "Z2");
                    areaZ2Field.setMaxLength(10);
                    areaZ2Field.setText(prevAreaZ2);
                    addDrawableChild(areaZ2Field);
                } else if ("CUSTOM".equals(formAreaMode)) {
                    int seedY = contentTop + 254;
                    areaSeedXField = new UiTextField(textRenderer, contentX + 12, seedY, 56, 18, "X");
                    areaSeedXField.setMaxLength(10);
                    areaSeedXField.setText(prevSeedX);
                    addDrawableChild(areaSeedXField);

                    areaSeedYField = new UiTextField(textRenderer, contentX + 82, seedY, 48, 18, "Y");
                    areaSeedYField.setMaxLength(6);
                    areaSeedYField.setText(prevSeedY);
                    addDrawableChild(areaSeedYField);

                    areaSeedZField = new UiTextField(textRenderer, contentX + 144, seedY, 56, 18, "Z");
                    areaSeedZField.setMaxLength(10);
                    areaSeedZField.setText(prevSeedZ);
                    addDrawableChild(areaSeedZField);
                }

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
        if (playerStatSearchField != null) {
            playerStatSearchField.renderBox(c, playerStatSearchField.boxX(), playerStatSearchField.boxY(), playerStatSearchField.getWidth() + 12, 18, mx, my, delta);
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
        if (areaX1Field != null) areaX1Field.renderBox(c, areaX1Field.boxX(), areaX1Field.boxY(), areaX1Field.getWidth() + 12, 18, mx, my, delta);
        if (areaZ1Field != null) areaZ1Field.renderBox(c, areaZ1Field.boxX(), areaZ1Field.boxY(), areaZ1Field.getWidth() + 12, 18, mx, my, delta);
        if (areaX2Field != null) areaX2Field.renderBox(c, areaX2Field.boxX(), areaX2Field.boxY(), areaX2Field.getWidth() + 12, 18, mx, my, delta);
        if (areaZ2Field != null) areaZ2Field.renderBox(c, areaZ2Field.boxX(), areaZ2Field.boxY(), areaZ2Field.getWidth() + 12, 18, mx, my, delta);
        if (areaSeedXField != null) areaSeedXField.renderBox(c, areaSeedXField.boxX(), areaSeedXField.boxY(), areaSeedXField.getWidth() + 12, 18, mx, my, delta);
        if (areaSeedYField != null) areaSeedYField.renderBox(c, areaSeedYField.boxX(), areaSeedYField.boxY(), areaSeedYField.getWidth() + 12, 18, mx, my, delta);
        if (areaSeedZField != null) areaSeedZField.renderBox(c, areaSeedZField.boxX(), areaSeedZField.boxY(), areaSeedZField.getWidth() + 12, 18, mx, my, delta);
        if (activeTab == Tab.SCOREBOARDS && sbPanelMode != SbPanelMode.VIEW && sbCriterionField != null && sbCriterionField.isFocused()) {
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
        x += drawTabButton(c, x, ty + 3, "Registro (" + entries.size() + ")", activeTab == Tab.REGISTRY, false, mx, my) + 6;
        x += drawTabButton(c, x, ty + 3, "Scoreboards (" + scoreboards.size() + ")", activeTab == Tab.SCOREBOARDS, false, mx, my) + 6;
        String unexpLabel = "Archivos raros (" + unexpectedFiles.size() + ")";
        x += drawTabButton(c, x, ty + 3, unexpLabel, activeTab == Tab.UNEXPECTED, !unexpectedFiles.isEmpty(), mx, my) + 6;
        drawTabButton(c, x, ty + 3, "Guía", activeTab == Tab.GUIDE, false, mx, my);
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

        if (inspectingPlayerUuid != null) {
            renderPlayerDetail(c, mx, my, delta);
            if (tpLastPosButton != null && tpLastPosButton.isHovered() && inspectingPlayerDetail != null && inspectingPlayerDetail.hasLastPos) {
                List<Text> tip = new ArrayList<>();
                tip.add(Text.literal("Última posición registrada").formatted(Formatting.AQUA));
                tip.add(Text.literal(String.format(Locale.ROOT, "%s · %.1f, %.1f, %.1f",
                    inspectingPlayerDetail.lastDimension,
                    inspectingPlayerDetail.lastX,
                    inspectingPlayerDetail.lastY,
                    inspectingPlayerDetail.lastZ)).formatted(Formatting.GRAY));
                if (client != null && client.player != null && !client.player.isCreative() && !client.player.isSpectator()) {
                    tip.add(Text.literal("Estás en Survival: pedirá confirmación.").formatted(Formatting.YELLOW));
                }
                c.drawTooltip(textRenderer, tip, mx, my);
            }
            return;
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

            // Description row: anomaly / files summary + cached player metrics on the right
            String desc = buildFileSummary(item);
            int descColor = item.anomaly ? 0xFFE59B53 : AeternumTheme.TEXT_SECONDARY;
            String metrics = buildPlayerMetrics(item);
            if (!metrics.isEmpty()) {
                int metricsW = textRenderer.getWidth(metrics);
                int metricsX = cardX + cardW - metricsW - 8;
                int descMaxW = Math.max(40, metricsX - (cardX + 10) - 8);
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, desc, descMaxW), cardX + 10, cy + 27, descColor, false);
                c.drawText(textRenderer, metrics, metricsX, cy + 27, 0xFFB8C0CC, false);
            } else {
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, desc, cardW - 20), cardX + 10, cy + 27, descColor, false);
            }
        }
        c.disableScissor();

        if (maxL > 0) {
            AeternumTheme.drawScrollbar(c, panelX + panelW - 9, lTop, lBottom,
                shown.size() * ROW_STRIDE, viewH, listScroll, draggingScrollbar);
        }
    }

    private static String buildPlayerMetrics(RegistryEntrySnapshot item) {
        StringBuilder sb = new StringBuilder();
        if (item.playerdata && item.enderSlots >= 0 && item.invSlots >= 0) {
            sb.append("E:").append(item.enderSlots).append(" I:").append(item.invSlots);
        }
        if (item.stats && item.playTimeTicks >= 0) {
            if (!sb.isEmpty()) sb.append("  \u00B7  ");
            sb.append("T:").append(formatPlayTime(item.playTimeTicks))
              .append(" M:").append(String.format(Locale.US, "%,d", Math.max(0L, item.mined)))
              .append(" C:").append(String.format(Locale.US, "%,d", Math.max(0L, item.crafted)));
        }
        return sb.toString();
    }

    private static String formatPlayTime(long ticks) {
        long mins = Math.max(0L, ticks) / 1200L;
        if (mins < 60L) {
            return mins + "m";
        }
        double hours = mins / 60.0;
        if (hours < 24.0) {
            return hours < 10.0
                ? String.format(Locale.US, "%.1fh", hours)
                : Math.round(hours) + "h";
        }
        double days = hours / 24.0;
        return String.format(Locale.US, "%.1fd", days);
    }

    private record GuideBadge(String text, int bg, int fg) {
    }

    private record GuideSection(String title, List<GuideBadge> badges, List<String> lines) {
    }

    private static final List<GuideSection> GUIDE_SECTIONS = List.of(
        new GuideSection(
            "1. Modelo de UUIDs en el servidor (v3 vs v4)",
            List.of(
                new GuideBadge("ONLINE v4", 0xFF1A2A22, 0xFF8BE0B2),
                new GuideBadge("OFFLINE v3", 0xFF24242D, 0xFFB8B8C8)
            ),
            List.of(
                "El servidor opera con dos tipos de UUID según el origen de la cuenta:",
                "\u2022 ONLINE (UUID v4): aquí deben vivir únicamente los usuarios con cuenta Premium de Mojang y los bots de Carpet cuyo nick coincide con una cuenta Mojang existente.",
                "\u2022 OFFLINE (UUID v3): calculada como OfflinePlayer:<nick>. Aquí quedan solo los bots de Carpet cuyos nombres no existen en Mojang y las cuentas antiguas que no tienen cuenta Premium a la cual migrarse."
            )
        ),
        new GuideSection(
            "2. Los 3 archivos por cuenta (data, stats y adv)",
            List.of(),
            List.of(
                "Minecraft guarda el progreso de cada UUID en 3 archivos independientes dentro de world/:",
                "\u2022 data (playerdata/<uuid>.dat): inventario, Ender Chest, posición, dimensión, XP y modo de juego (con respaldo automático <uuid>.dat_old).",
                "\u2022 stats (stats/<uuid>.json): bloques minados, tiempo jugado, muertes, crafteos, etc.",
                "\u2022 adv (advancements/<uuid>.json): logros y recetas desbloqueadas.",
                "Si falta alguno de los 3 en disco se marca como anomalía para que puedas subsanarlo o limpiarlo."
            )
        ),
        new GuideSection(
            "3. Migrar cuentas por nombre (Premium / No Premium)",
            List.of(
                new GuideBadge("MIGRAR", 0xFF1C2738, 0xFF8EC8FF)
            ),
            List.of(
                "Desde el detalle de cualquier jugador puedes abrir la subpestaña Migrar sin tocar UUIDs manualmente:",
                "\u2022 Eliges el modo destino (Premium v4 o No Premium v3) y escribes el nombre destino.",
                "\u2022 Comprobar: resuelve la UUID (consulta Mojang si es Premium), verifica que ambos estén desconectados y te muestra qué archivos tiene A (Origen) y B (Destino).",
                "\u2022 Sin colisión: si B está vacío, ejecutas la migración en un clic.",
                "\u2022 Con colisión: decides por separado qué hacer con data (Conservar A o B), stats (Fusionar A+B, Conservar A o B) y adv (Fusionar A+B, Conservar A o B). Para rescatar ítems de ambos inventarios, usa antes Abrir editor inv. Todo cambio crea respaldo automático en world/migrationbackup/."
            )
        ),
        new GuideSection(
            "4. Clasificación del Registro, /viewinv y TP última pos.",
            List.of(
                new GuideBadge("HUMAN", 0xFF1A281E, 0xFF9DF0B4),
                new GuideBadge("BOT", 0xFF18222C, 0xFF9CD0FF),
                new GuideBadge("UNKNOWN", 0xFF1A1A20, AeternumTheme.TEXT_SECONDARY)
            ),
            List.of(
                "\u2022 Clasificación: HUMAN (jugador real; un bot nunca lo degrada), BOT (fake player de Carpet) y UNKNOWN (archivos previos sin clasificar).",
                "\u2022 Abrir editor inv. (/viewinv): permite inspeccionar y editar inventario, armadura, offhand, Ender Chest y gamemode incluso si la cuenta está offline.",
                "\u2022 TP última pos.: te teletransporta a la última dimensión y coordenada registrada del jugador o bot (pide confirmación si estás en Survival)."
            )
        ),
        new GuideSection(
            "5. Scoreboards, Reglas de seguridad y Archivos raros",
            List.of(),
            List.of(
                "\u2022 Scoreboards: permite crear objetivos custom (aeternum:dig_all, aeternum:placed_blocks, áreas Rectángulo/Custom), calcular Total sin bots y administrar objetivos ae-* generados por /stats scoreboard.",
                "\u2022 Seguridad: nunca se borran, subsanan ni migran cuentas conectadas en RAM. Borrar (Shift + 2 clics) elimina registro y archivos en disco.",
                "\u2022 Archivos raros: lista archivos o carpetas dentro de playerdata/, stats/ o advancements/ que no siguen el formato estándar <uuid>.dat/.json."
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
                case ALL -> isIncludedInScoreboard(obj, e);
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

    private static boolean isIncludedInScoreboard(ScoreboardObjectiveSnapshot obj, ScoreboardEntrySnapshot e) {
        if (obj == null || e == null) return true;
        return switch (e.badge) {
            case "HUMAN" -> obj.totalHumans;
            case "BOT" -> obj.totalBots;
            case "AMBOS" -> obj.totalHumans || obj.totalBots;
            default -> obj.totalUnknown;
        };
    }

    private int countScoreFilter(ScoreboardObjectiveSnapshot obj, ScoreFilter filter) {
        if (obj == null) return 0;
        if (!obj.name.equals(loadedObjectiveName)) {
            return filter == ScoreFilter.ALL ? obj.entryCount : 0;
        }
        int count = 0;
        for (ScoreboardEntrySnapshot e : selectedObjectiveEntries) {
            boolean match = switch (filter) {
                case ALL -> isIncludedInScoreboard(obj, e);
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
        List<ScoreboardObjectiveSnapshot> out = new ArrayList<>();
        for (ScoreboardObjectiveSnapshot obj : scoreboards) {
            if (obj == null || obj.name == null) continue;
            if (!showAeObjectives && obj.name.toLowerCase(Locale.ROOT).startsWith("ae-")) {
                continue;
            }
            if (!q.isEmpty() && !obj.name.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(obj);
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
        formAreaMode = "NONE";
        formAreaDimension = "minecraft:overworld";
        formAreaPreviewCols = 0;
        formAreaPreviewSize = 0;
        formAreaPreviewGrid = "";
        formAreaBlockId = "";
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
        formAreaMode = obj.areaMode != null ? obj.areaMode : "NONE";
        formAreaDimension = obj.areaDimension != null && !obj.areaDimension.isEmpty() ? obj.areaDimension : "minecraft:overworld";
        formAreaPreviewCols = obj.areaColumnCount;
        formAreaPreviewSize = obj.areaPreviewSize;
        formAreaPreviewGrid = obj.areaPreviewGrid != null ? obj.areaPreviewGrid : "";
        formAreaBlockId = obj.areaBlockId != null ? obj.areaBlockId : "";
        init();
        if (sbNameField != null) {
            sbNameField.setText(obj.name != null ? obj.name : "");
        }
        if (sbCriterionField != null) {
            String crit = obj.criterion != null ? obj.criterion : "";
            sbCriterionField.setText("dummy".equalsIgnoreCase(crit) ? "" : crit);
        }
        if (sbDisplayField != null) {
            sbDisplayField.setText(obj.displayNameMarkup != null && !obj.displayNameMarkup.isEmpty()
                ? obj.displayNameMarkup
                : (obj.displayName != null ? obj.displayName : obj.name));
        }
        if (sbAliasField != null) {
            sbAliasField.setText(obj.chatAlias != null ? obj.chatAlias : "");
        }
        if (areaX1Field != null) areaX1Field.setText(String.valueOf(obj.areaX1));
        if (areaZ1Field != null) areaZ1Field.setText(String.valueOf(obj.areaZ1));
        if (areaX2Field != null) areaX2Field.setText(String.valueOf(obj.areaX2));
        if (areaZ2Field != null) areaZ2Field.setText(String.valueOf(obj.areaZ2));
    }

    private static int parseIntOrZero(UiTextField field) {
        if (field == null) return 0;
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String validateObjectiveId(String raw) {
        String id = raw == null ? "" : raw.trim();
        if (id.isEmpty()) {
            return "El ID interno no puede estar vacío.";
        }
        if (id.contains(" ")) {
            return "El ID interno no puede tener espacios.";
        }
        boolean isSameExistingAe = sbPanelMode == SbPanelMode.EDIT
            && editingObjectiveName != null
            && editingObjectiveName.equalsIgnoreCase(id);
        if (!isSameExistingAe && id.toLowerCase(Locale.ROOT).startsWith("ae-")) {
            return "El prefijo 'ae-' está reservado.";
        }
        if (scoreboards != null) {
            for (ScoreboardObjectiveSnapshot obj : scoreboards) {
                if (obj == null || obj.name == null) continue;
                if (sbPanelMode == SbPanelMode.EDIT && editingObjectiveName != null && obj.name.equals(editingObjectiveName)) {
                    continue;
                }
                if (obj.name.equalsIgnoreCase(id)) {
                    return "Ya existe una scoreboard con ID '" + obj.name + "'.";
                }
            }
        }
        return null;
    }

    private void submitScoreboardForm() {
        if (sbPanelMode == SbPanelMode.CREATE) {
            String name = sbNameField != null ? sbNameField.getText().trim() : "";
            String idErr = validateObjectiveId(name);
            if (idErr != null) {
                statusMessage = idErr;
                statusError = true;
                return;
            }
            if ("CUSTOM".equals(formAreaMode) && formAreaPreviewCols <= 0) {
                statusMessage = "Escanea un contorno válido antes de crear el área Custom.";
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
            req.areaMode = formAreaMode;
            req.areaDimension = formAreaDimension;
            req.areaX1 = parseIntOrZero(areaX1Field);
            req.areaZ1 = parseIntOrZero(areaZ1Field);
            req.areaX2 = parseIntOrZero(areaX2Field);
            req.areaZ2 = parseIntOrZero(areaZ2Field);
            selectedObjectiveName = name;
            loadedObjectiveName = null;
            sbPanelMode = SbPanelMode.VIEW;
            statusMessage = "Creando scoreboard '" + name + "'…";
            statusError = false;
            ClientPlayNetworking.send(new StatsRegistryActionPayload("create_objective", "", GSON.toJson(req)));
            init();
        } else if (sbPanelMode == SbPanelMode.EDIT) {
            String nextName = sbNameField != null ? sbNameField.getText().trim() : "";
            String idErr = validateObjectiveId(nextName);
            if (idErr != null) {
                statusMessage = idErr;
                statusError = true;
                return;
            }
            if ("CUSTOM".equals(formAreaMode) && formAreaPreviewCols <= 0) {
                statusMessage = "Escanea un contorno válido antes de guardar el área Custom.";
                statusError = true;
                return;
            }
            EditObjectiveRequest req = new EditObjectiveRequest();
            req.originalName = editingObjectiveName;
            req.name = nextName;
            req.criterion = sbCriterionField != null ? sbCriterionField.getText().trim() : "";
            req.displayName = sbDisplayField != null ? sbDisplayField.getText().trim() : "";
            req.slot = formSlot;
            req.chatAlias = sbAliasField != null ? sbAliasField.getText().trim() : "";
            req.totalEnabled = formTotalEnabled;
            req.totalHumans = formTotalHumans;
            req.totalBots = formTotalBots;
            req.totalUnknown = formTotalUnknown;
            req.areaMode = formAreaMode;
            req.areaDimension = formAreaDimension;
            req.areaX1 = parseIntOrZero(areaX1Field);
            req.areaZ1 = parseIntOrZero(areaZ1Field);
            req.areaX2 = parseIntOrZero(areaX2Field);
            req.areaZ2 = parseIntOrZero(areaZ2Field);
            selectedObjectiveName = nextName;
            loadedObjectiveName = null;
            sbPanelMode = SbPanelMode.VIEW;
            statusMessage = "Guardando '" + nextName + "'…";
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
            if (isIncludedInScoreboard(obj, e)) {
                sum += e.score;
            }
        }
        return sum;
    }

    private void renderScoreboardsTab(DrawContext c, int mx, int my, float delta) {
        int topY = tabBarY() + TAB_BAR_H;
        int footerY = panelY + panelH - 36;
        int sTop = topY + 28;
        int sBottom = footerY - 60;

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
        boolean showTotalRow = hasTotalRow(selected);
        int totalRows = shown.size() + (showTotalRow ? 1 : 0);
        int maxL = maxListScrollPx(totalRows, SB_ENTRY_STRIDE);
        targetListScroll = Math.max(0, Math.min(targetListScroll, maxL));
        listScroll += (targetListScroll - listScroll) * Math.min(1.0, delta * 0.45 + 0.25);
        listScroll = Math.max(0, Math.min(listScroll, maxL));

        if (totalRows == 0) {
            if (inlineScoreField != null) inlineScoreField.setVisible(false);
            c.drawCenteredTextWithShadow(textRenderer, "Sin entradas en esta scoreboard.", cardX + cardW / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
            return;
        }

        if (armedDeleteScoreOwner != null && System.currentTimeMillis() > armedDeleteScoreUntilMs) {
            armedDeleteScoreOwner = null;
        }
        Map<String, Integer> globalRank = new HashMap<>();
        if (selectedObjectiveEntries != null) {
            int nextRank = 1;
            for (ScoreboardEntrySnapshot e : selectedObjectiveEntries) {
                if (e != null && e.owner != null && isIncludedInScoreboard(selected, e)) {
                    if (!globalRank.containsKey(e.owner)) {
                        globalRank.put(e.owner, nextRank++);
                    }
                }
            }
        }
        if (inlineScoreField != null) {
            inlineScoreField.setVisible(false);
        }

        c.enableScissor(panelX + SIDEBAR_W + 2, lTop, panelX + panelW - 8, lBottom);
        if (showTotalRow) {
            int cy = lTop - (int) Math.round(listScroll);
            if (cy + SB_ENTRY_H >= lTop && cy <= lBottom) {
                AeternumTheme.drawCard(c, cardX, cy, cardW, SB_ENTRY_H, 0xFF1E1A12, AeternumTheme.ACCENT_GOLD);
                String rankZero = "0. ";
                c.drawTextWithShadow(textRenderer, rankZero, cardX + 10, cy + 9, AeternumTheme.ACCENT_GOLD);
                int labelX = cardX + 10 + textRenderer.getWidth(rankZero);
                c.drawTextWithShadow(textRenderer, "Total", labelX, cy + 9, AeternumTheme.TEXT_PRIMARY);
                String totStr = String.format(Locale.US, "%,d", computeObjectiveTotal(selected));
                int scoreRight = cardX + cardW - 30;
                c.drawTextWithShadow(textRenderer, totStr, scoreRight - textRenderer.getWidth(totStr), cy + 9, AeternumTheme.ACCENT_GOLD);
            }
        }
        for (int i = 0; i < shown.size(); i++) {
            int rowIdx = i + (showTotalRow ? 1 : 0);
            int cy = lTop + rowIdx * SB_ENTRY_STRIDE - (int) Math.round(listScroll);
            if (cy + SB_ENTRY_H < lTop || cy > lBottom) continue;

            ScoreboardEntrySnapshot item = shown.get(i);
            boolean inArea = my >= lTop && my < lBottom;
            boolean over = inArea && mx >= cardX && mx < cardX + cardW && my >= cy && my < cy + SB_ENTRY_H;
            AeternumTheme.drawCard(c, cardX, cy, cardW, SB_ENTRY_H, over ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG,
                over ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE);

            int delX = cardX + cardW - 22;
            int delY = cy + 5;
            boolean armedDel = item.owner.equals(armedDeleteScoreOwner) && System.currentTimeMillis() <= armedDeleteScoreUntilMs;
            boolean overDel = inArea && mx >= delX && mx < delX + 16 && my >= delY && my < delY + 16;
            AeternumTheme.drawCard(c, delX, delY, 16, 16,
                armedDel ? 0xFF5A1D22 : (overDel ? 0xFF3A1A1F : AeternumTheme.CARD_ALT_BG),
                (armedDel || overDel) ? 0xFFFF6B6B : AeternumTheme.BORDER_SUBTLE);
            c.drawCenteredTextWithShadow(textRenderer, armedDel ? "!" : "x", delX + 8, delY + 4,
                (armedDel || overDel) ? 0xFFFF6B6B : AeternumTheme.TEXT_MUTED);

            int scoreRight = delX - 8;
            int scoreLeftBound;
            if (item.owner.equals(editingScoreOwner) && inlineScoreField != null) {
                int fieldW = 82;
                int fieldX = scoreRight - fieldW;
                inlineScoreField.setX(fieldX);
                inlineScoreField.setY(cy + 4);
                inlineScoreField.setWidth(fieldW);
                inlineScoreField.setVisible(cy >= lTop - 2 && cy + SB_ENTRY_H <= lBottom + 2);
                scoreLeftBound = fieldX;
            } else {
                String scoreStr = String.format(Locale.US, "%,d", item.score);
                int scoreW = textRenderer.getWidth(scoreStr);
                int scoreX = scoreRight - scoreW;
                boolean overScore = inArea && mx >= scoreX - 6 && mx < scoreRight + 2 && my >= cy + 2 && my < cy + SB_ENTRY_H - 2;
                c.drawTextWithShadow(textRenderer, scoreStr, scoreX, cy + 9,
                    overScore ? 0xFFFFE599 : AeternumTheme.ACCENT_GOLD);
                if (overScore) {
                    c.fill(scoreX, cy + 18, scoreRight, cy + 19, 0xFFFFE599);
                }
                scoreLeftBound = scoreX;
            }

            Integer rank = globalRank.get(item.owner);
            String rankStr = rank != null ? (rank + ". ") : "- ";
            int rankW = textRenderer.getWidth(rankStr);
            int rankColor = rank == null ? AeternumTheme.TEXT_MUTED : switch (rank) {
                case 1 -> 0xFFFFD166;
                case 2 -> 0xFFD8DEE9;
                case 3 -> 0xFFE59866;
                default -> AeternumTheme.TEXT_MUTED;
            };
            c.drawTextWithShadow(textRenderer, rankStr, cardX + 10, cy + 9, rankColor);

            int ownerX = cardX + 10 + rankW;
            int badgeW = textRenderer.getWidth(item.badge) + 10;
            int ownerMaxW = Math.max(40, scoreLeftBound - ownerX - badgeW - 14);
            String ownerShown = AeternumTheme.ellipsize(textRenderer, item.owner, ownerMaxW);
            c.drawTextWithShadow(textRenderer, ownerShown, ownerX, cy + 9, AeternumTheme.TEXT_PRIMARY);

            int badgeX = ownerX + 6 + textRenderer.getWidth(ownerShown);
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
            if (badgeX + badgeW < scoreLeftBound - 6) {
                AeternumTheme.drawBadge(c, textRenderer, badgeX, cy + 6, item.badge, badgeBg, badgeFg);
            }
        }
        c.disableScissor();

        if (maxL > 0) {
            AeternumTheme.drawScrollbar(c, panelX + panelW - 9, lTop, lBottom,
                totalRows * SB_ENTRY_STRIDE, viewH, listScroll, draggingScrollbar);
        }
    }

    private boolean hasTotalRow(ScoreboardObjectiveSnapshot selected) {
        return selected != null && selected.totalEnabled && selected.name.equals(loadedObjectiveName);
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
        return contentTop + 111;
    }

    private static final String[][] AREA_MODES = {
        {"NONE", "Ninguna"},
        {"RECTANGLE", "Rectángulo"},
        {"CUSTOM", "Custom"}
    };

    private static final String[][] AREA_DIMENSIONS = {
        {"minecraft:overworld", "Overworld"},
        {"minecraft:the_nether", "Nether"},
        {"minecraft:the_end", "End"}
    };

    private void renderScoreboardForm(DrawContext c, int mx, int my) {
        int contentTop = panelY + 44 + TAB_BAR_H;
        int contentX = panelX + SIDEBAR_W + 10;
        int contentW = panelW - SIDEBAR_W - 22;

        String header = sbPanelMode == SbPanelMode.CREATE
            ? "Crear nueva scoreboard"
            : ("Editando: " + editingObjectiveName);
        c.drawTextWithShadow(textRenderer, header, contentX, contentTop + 8, AeternumTheme.TEXT_PRIMARY);

        String idLabel = "ID interno (sin espacios)";
        c.drawText(textRenderer, idLabel, contentX, contentTop + 24, AeternumTheme.TEXT_SECONDARY, false);
        String currentIdText = sbNameField != null ? sbNameField.getText() : "";
        String idErr = validateObjectiveId(currentIdText);
        if (sbSaveButton != null) {
            sbSaveButton.active = (idErr == null);
        }
        int idStatusX = contentX + textRenderer.getWidth(idLabel) + 6;
        int idStatusAvail = contentX + contentW - idStatusX;
        if (idErr != null && idStatusAvail > 24) {
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, "\u00B7 " + idErr, idStatusAvail),
                idStatusX, contentTop + 24, 0xFFFF6B6B, false);
            if (sbNameField != null) {
                int fx = sbNameField.getX(), fy = sbNameField.getY(), fw = sbNameField.getWidth(), fh = sbNameField.getHeight();
                c.fill(fx, fy, fx + fw, fy + 1, 0xFFFF6B6B);
                c.fill(fx, fy + fh - 1, fx + fw, fy + fh, 0xFFFF6B6B);
                c.fill(fx, fy, fx + 1, fy + fh, 0xFFFF6B6B);
                c.fill(fx + fw - 1, fy, fx + fw, fy + fh, 0xFFFF6B6B);
            }
        } else if (idStatusAvail > 24) {
            String trimmedId = currentIdText.trim();
            boolean showAvailable = sbPanelMode == SbPanelMode.CREATE
                || (editingObjectiveName != null && !trimmedId.equals(editingObjectiveName));
            if (showAvailable) {
                c.drawText(textRenderer, "\u00B7 Disponible", idStatusX, contentTop + 24, 0xFF4BA378, false);
            }
        }
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
            String hint = "Vacío = dummy  \u00B7  o elige dig_all/placed_blocks/mined/used/...";
            int hx = px + 4;
            int avail = contentX + contentW - hx;
            if (avail > 50) {
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, hint, avail), hx, pillY + 4, AeternumTheme.TEXT_MUTED, false);
            }
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
        int includeX = contentX + 108;
        c.drawText(textRenderer, "Cálculo de Total", contentX, totalLabelY, AeternumTheme.TEXT_SECONDARY, false);
        c.drawText(textRenderer, "Incluir en scoreboard", includeX, totalLabelY, AeternumTheme.TEXT_SECONDARY, false);
        int ty = totalLabelY + 11;
        drawTogglePill(c, contentX, ty, "Total: " + (formTotalEnabled ? "ON" : "OFF"), formTotalEnabled, 0xFF7AD694, mx, my);
        int tx = includeX;
        tx += drawTogglePill(c, tx, ty, "Humans", formTotalHumans, 0xFF9DF0B4, mx, my) + 4;
        tx += drawTogglePill(c, tx, ty, "Bots", formTotalBots, 0xFF9CD0FF, mx, my) + 4;
        drawTogglePill(c, tx, ty, "Unknown", formTotalUnknown, AeternumTheme.ACCENT_GOLD, mx, my);

        // Area scoreboard section
        int areaHeaderY = contentTop + 233;
        c.drawText(textRenderer, "Scoreboard de área", contentX, areaHeaderY + 3, AeternumTheme.TEXT_SECONDARY, false);
        int amx = contentX + textRenderer.getWidth("Scoreboard de área") + 8;
        for (String[] modeOpt : AREA_MODES) {
            int mw = textRenderer.getWidth(modeOpt[1]) + 10;
            boolean sel = modeOpt[0].equals(formAreaMode);
            boolean over = mx >= amx && mx < amx + mw && my >= areaHeaderY && my < areaHeaderY + 16;
            AeternumTheme.drawCard(c, amx, areaHeaderY, mw, 16,
                sel ? AeternumTheme.CARD_SELECTED : (over ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_ALT_BG),
                sel ? AeternumTheme.ACCENT_GOLD : AeternumTheme.BORDER_SUBTLE);
            c.drawText(textRenderer, modeOpt[1], amx + 5, areaHeaderY + 4, sel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY, false);
            amx += mw + 4;
        }

        if ("RECTANGLE".equals(formAreaMode)) {
            int dimY = contentTop + 254;
            int dx = contentX;
            c.drawText(textRenderer, "Dim:", dx, dimY + 4, AeternumTheme.TEXT_MUTED, false);
            dx += textRenderer.getWidth("Dim:") + 6;
            for (String[] dimOpt : AREA_DIMENSIONS) {
                int dw = textRenderer.getWidth(dimOpt[1]) + 10;
                boolean sel = dimOpt[0].equals(formAreaDimension);
                boolean over = mx >= dx && mx < dx + dw && my >= dimY && my < dimY + 16;
                AeternumTheme.drawCard(c, dx, dimY, dw, 16,
                    sel ? AeternumTheme.CARD_SELECTED : (over ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_ALT_BG),
                    sel ? AeternumTheme.ACCENT_GOLD : AeternumTheme.BORDER_SUBTLE);
                c.drawText(textRenderer, dimOpt[1], dx + 5, dimY + 4, sel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY, false);
                dx += dw + 4;
            }

            int rectY = contentTop + 278;
            c.drawText(textRenderer, "X1", contentX, rectY + 5, AeternumTheme.TEXT_MUTED, false);
            c.drawText(textRenderer, "Z1", contentX + 74, rectY + 5, AeternumTheme.TEXT_MUTED, false);
            drawSmallActionButton(c, contentX + 150, rectY + 1, 36, 16, "Pos1", mx, my);

            int x2Start = contentX + 196;
            c.drawText(textRenderer, "X2", x2Start, rectY + 5, AeternumTheme.TEXT_MUTED, false);
            c.drawText(textRenderer, "Z2", x2Start + 74, rectY + 5, AeternumTheme.TEXT_MUTED, false);
            drawSmallActionButton(c, x2Start + 150, rectY + 1, 36, 16, "Pos2", mx, my);
        } else if ("CUSTOM".equals(formAreaMode)) {
            int seedY = contentTop + 254;
            c.drawText(textRenderer, "X", contentX, seedY + 5, AeternumTheme.TEXT_MUTED, false);
            c.drawText(textRenderer, "Y", contentX + 72, seedY + 5, AeternumTheme.TEXT_MUTED, false);
            c.drawText(textRenderer, "Z", contentX + 134, seedY + 5, AeternumTheme.TEXT_MUTED, false);

            int btnRowY = contentTop + 278;
            int bx = contentX;
            bx += drawSmallActionButton(c, bx, btnRowY, 82, 17, "Bloque mirado", mx, my) + 4;
            bx += drawSmallActionButton(c, bx, btnRowY, 80, 17, "Bajo mis pies", mx, my) + 4;
            drawSmallActionButton(c, bx, btnRowY, 102, 17, "Escanear contorno", mx, my);

            int infoY = contentTop + 302;
            if (formAreaPreviewCols > 0) {
                String dimShort = shortDimName(formAreaDimension);
                String info = formAreaPreviewCols + " cols \u00B7 " + dimShort
                    + (formAreaBlockId.isEmpty() ? "" : (" \u00B7 " + formAreaBlockId.replace("minecraft:", "")));
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, info, contentW - 84), contentX, infoY, 0xFF9DF0B4, false);
            } else {
                c.drawText(textRenderer, "Sin contorno escaneado", contentX, infoY, AeternumTheme.TEXT_MUTED, false);
            }

            // 2D mini preview on the right
            int boxSize = 72;
            int boxX = contentX + contentW - boxSize;
            int boxY = contentTop + 254;
            AeternumTheme.drawCard(c, boxX, boxY, boxSize, boxSize, 0xFF0A0A0D, AeternumTheme.BORDER_BRIGHT);
            if (formAreaPreviewSize > 0 && formAreaPreviewGrid != null && formAreaPreviewGrid.length() == formAreaPreviewSize * formAreaPreviewSize) {
                int inner = boxSize - 4;
                for (int gy = 0; gy < formAreaPreviewSize; gy++) {
                    int y0 = boxY + 2 + (gy * inner) / formAreaPreviewSize;
                    int y1 = boxY + 2 + ((gy + 1) * inner) / formAreaPreviewSize;
                    for (int gx = 0; gx < formAreaPreviewSize; gx++) {
                        char ch = formAreaPreviewGrid.charAt(gy * formAreaPreviewSize + gx);
                        if (ch == '.') continue;
                        int x0 = boxX + 2 + (gx * inner) / formAreaPreviewSize;
                        int x1 = boxX + 2 + ((gx + 1) * inner) / formAreaPreviewSize;
                        int col = (ch == '#') ? 0xFFFFD166 : 0xFF4BA378;
                        c.fill(x0, y0, Math.max(x0 + 1, x1), Math.max(y0 + 1, y1), col);
                    }
                }
            } else {
                c.drawCenteredTextWithShadow(textRenderer, "2D", boxX + boxSize / 2, boxY + boxSize / 2 - 4, AeternumTheme.TEXT_MUTED);
            }
        }

        if (sbPanelMode == SbPanelMode.CREATE) {
            int btnY = panelY + panelH - 64;
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

    private int drawSmallActionButton(DrawContext c, int x, int y, int w, int h, String label, int mx, int my) {
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
        AeternumTheme.drawCard(c, x, y, w, h, over ? 0xFF2B2B36 : 0xFF1A1A22,
            over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_BRIGHT);
        c.drawTextWithShadow(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2,
            over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
        return w;
    }

    private static String shortDimName(String dim) {
        if ("minecraft:the_nether".equals(dim)) return "Nether";
        if ("minecraft:the_end".equals(dim)) return "End";
        return "Overworld";
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

        if (sbPanelMode == SbPanelMode.CREATE) {
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
            int wTot = textRenderer.getWidth("Total: " + (formTotalEnabled ? "ON" : "OFF")) + 14;
            if (mx >= contentX && mx < contentX + wTot) {
                formTotalEnabled = !formTotalEnabled;
                return true;
            }
            int tx = contentX + 108;
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

        int areaHeaderY = contentTop + 233;
        if (my >= areaHeaderY && my < areaHeaderY + 16) {
            int amx = contentX + textRenderer.getWidth("Scoreboard de área") + 8;
            for (String[] modeOpt : AREA_MODES) {
                int mw = textRenderer.getWidth(modeOpt[1]) + 10;
                if (mx >= amx && mx < amx + mw) {
                    if (!modeOpt[0].equals(formAreaMode)) {
                        formAreaMode = modeOpt[0];
                        if ("RECTANGLE".equals(formAreaMode) && client != null && client.player != null) {
                            formAreaDimension = client.player.getWorld().getRegistryKey().getValue().toString();
                        }
                        init();
                    }
                    return true;
                }
                amx += mw + 4;
            }
        }

        if ("RECTANGLE".equals(formAreaMode)) {
            int dimY = contentTop + 254;
            if (my >= dimY && my < dimY + 16) {
                int dx = contentX + textRenderer.getWidth("Dim:") + 6;
                for (String[] dimOpt : AREA_DIMENSIONS) {
                    int dw = textRenderer.getWidth(dimOpt[1]) + 10;
                    if (mx >= dx && mx < dx + dw) {
                        formAreaDimension = dimOpt[0];
                        return true;
                    }
                    dx += dw + 4;
                }
            }
            int rectY = contentTop + 278;
            if (my >= rectY + 1 && my < rectY + 17 && client != null && client.player != null) {
                if (mx >= contentX + 150 && mx < contentX + 186) {
                    if (areaX1Field != null) areaX1Field.setText(String.valueOf(client.player.getBlockX()));
                    if (areaZ1Field != null) areaZ1Field.setText(String.valueOf(client.player.getBlockZ()));
                    formAreaDimension = client.player.getWorld().getRegistryKey().getValue().toString();
                    return true;
                }
                int x2Start = contentX + 196;
                if (mx >= x2Start + 150 && mx < x2Start + 186) {
                    if (areaX2Field != null) areaX2Field.setText(String.valueOf(client.player.getBlockX()));
                    if (areaZ2Field != null) areaZ2Field.setText(String.valueOf(client.player.getBlockZ()));
                    formAreaDimension = client.player.getWorld().getRegistryKey().getValue().toString();
                    return true;
                }
            }
        } else if ("CUSTOM".equals(formAreaMode)) {
            int btnRowY = contentTop + 278;
            if (my >= btnRowY && my < btnRowY + 17) {
                int bx = contentX;
                if (mx >= bx && mx < bx + 82) {
                    if (client != null && client.crosshairTarget instanceof BlockHitResult hit
                        && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                        BlockPos pos = hit.getBlockPos();
                        if (areaSeedXField != null) areaSeedXField.setText(String.valueOf(pos.getX()));
                        if (areaSeedYField != null) areaSeedYField.setText(String.valueOf(pos.getY()));
                        if (areaSeedZField != null) areaSeedZField.setText(String.valueOf(pos.getZ()));
                        statusMessage = "Bloque mirado: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
                        statusError = false;
                    } else {
                        statusMessage = "No estás mirando ningún bloque.";
                        statusError = true;
                    }
                    return true;
                }
                bx += 86;
                if (mx >= bx && mx < bx + 80) {
                    if (client != null && client.player != null) {
                        BlockPos pos = client.player.getBlockPos().down();
                        if (areaSeedXField != null) areaSeedXField.setText(String.valueOf(pos.getX()));
                        if (areaSeedYField != null) areaSeedYField.setText(String.valueOf(pos.getY()));
                        if (areaSeedZField != null) areaSeedZField.setText(String.valueOf(pos.getZ()));
                        statusMessage = "Bajo tus pies: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
                        statusError = false;
                    }
                    return true;
                }
                bx += 84;
                if (mx >= bx && mx < bx + 102) {
                    if (areaSeedXField == null || areaSeedYField == null || areaSeedZField == null
                        || areaSeedXField.getText().trim().isEmpty()
                        || areaSeedYField.getText().trim().isEmpty()
                        || areaSeedZField.getText().trim().isEmpty()) {
                        statusMessage = "Indica X, Y, Z de un bloque del contorno.";
                        statusError = true;
                        return true;
                    }
                    try {
                        ScanContourRequest scanReq = new ScanContourRequest();
                        scanReq.x = Integer.parseInt(areaSeedXField.getText().trim());
                        scanReq.y = Integer.parseInt(areaSeedYField.getText().trim());
                        scanReq.z = Integer.parseInt(areaSeedZField.getText().trim());
                        statusMessage = "Escaneando contorno…";
                        statusError = false;
                        ClientPlayNetworking.send(new StatsRegistryActionPayload("scan_area_contour", "", GSON.toJson(scanReq)));
                    } catch (NumberFormatException ex) {
                        statusMessage = "Coordenadas X, Y, Z inválidas.";
                        statusError = true;
                    }
                    return true;
                }
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
        UiTextField[] fields = {
            searchField, scoreSearchField, objectiveSearchField,
            sbNameField, sbCriterionField, sbDisplayField, sbAliasField,
            areaX1Field, areaZ1Field, areaX2Field, areaZ2Field,
            areaSeedXField, areaSeedYField, areaSeedZField
        };
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

        long nowMs = System.currentTimeMillis();
        if (armedPromotePath != null && nowMs > armedPromoteUntilMs) {
            armedPromotePath = null;
        }

        c.enableScissor(panelX + 4, lTop, panelX + panelW - 8, lBottom);
        for (int i = 0; i < unexpectedFiles.size(); i++) {
            int cy = lTop + i * UNEXPECTED_ROW_H - (int) Math.round(listScroll);
            if (cy + UNEXPECTED_ROW_H < lTop || cy > lBottom) continue;

            String path = unexpectedFiles.get(i);
            UnexpectedFileSnapshot detail = unexpectedDetailsByPath.get(path);
            int rowH = UNEXPECTED_ROW_H - 2;
            boolean missingOfficial = detail != null && !detail.linkedUuid.isEmpty() && !detail.hasOfficialDat;
            int borderColor = missingOfficial ? 0x90E55353 : 0x60E59B53;
            AeternumTheme.drawCard(c, cardX, cy, cardW, rowH, AeternumTheme.CARD_BG, borderColor);

            int delX = cardX + cardW - 20;
            int delY = cy + 3;
            String meta = formatUnexpectedMeta(detail);
            int metaW = meta.isEmpty() ? 0 : textRenderer.getWidth(meta) + 8;
            if (!meta.isEmpty()) {
                boolean badDat = detail != null && detail.path != null && detail.path.startsWith("playerdata/")
                    && detail.path.endsWith(".dat") && !detail.validNbt;
                int metaColor = (detail != null && (detail.sizeBytes == 0L || badDat))
                    ? AeternumTheme.ACCENT_RED
                    : AeternumTheme.TEXT_SECONDARY;
                c.drawText(textRenderer, meta, delX - metaW + 2, cy + 5, metaColor, false);
            }
            int pathMaxW = Math.max(60, delX - metaW - (cardX + 12));
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, path, pathMaxW), cardX + 8, cy + 5, 0xFFE59B53, false);

            boolean inArea = my >= lTop && my < lBottom;
            boolean overDel = inArea && mx >= delX && mx < delX + 16 && my >= delY && my < delY + 16;
            if (overDel) {
                hoveredUnexpectedDelete = path;
                c.fill(delX, delY, delX + 16, delY + 16, 0x45E55353);
                c.drawBorder(delX, delY, 16, 16, AeternumTheme.withAlpha(AeternumTheme.ACCENT_RED, 0x90));
            }
            c.drawTextWithShadow(textRenderer, "\u00D7", delX + 5, delY + 4, overDel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);

            int btnRight = cardX + cardW - 8;
            int btnY = cy + 19;
            int btnH = 14;
            if (detail != null && !detail.linkedUuid.isEmpty() && !detail.hasOfficialDat && detail.validNbt) {
                boolean armed = path.equals(armedPromotePath);
                String promLabel = armed ? "\u00BFConfirmar?" : "Hacer oficial";
                int promW = textRenderer.getWidth(promLabel) + 10;
                int promX = btnRight - promW;
                boolean overProm = inArea && mx >= promX && mx < promX + promW && my >= btnY && my < btnY + btnH;
                int fill = armed ? 0x604ADE80 : (overProm ? 0x454ADE80 : 0x254ADE80);
                int bdr = armed ? 0xFF4ADE80 : 0x904ADE80;
                c.fill(promX, btnY, promX + promW, btnY + btnH, fill);
                c.drawBorder(promX, btnY, promW, btnH, bdr);
                c.drawText(textRenderer, promLabel, promX + 5, btnY + 3, armed ? 0xFFFFFFFF : 0xFF86EFAC, false);
                btnRight = promX - 4;
            }
            if (detail != null && detail.validNbt) {
                String invLabel = "Ver inv.";
                int invW = textRenderer.getWidth(invLabel) + 10;
                int invX = btnRight - invW;
                boolean overInv = inArea && mx >= invX && mx < invX + invW && my >= btnY && my < btnY + btnH;
                c.fill(invX, btnY, invX + invW, btnY + btnH, overInv ? 0x4538BDF8 : 0x2538BDF8);
                c.drawBorder(invX, btnY, invW, btnH, 0x8038BDF8);
                c.drawText(textRenderer, invLabel, invX + 5, btnY + 3, overInv ? 0xFFFFFFFF : 0xFF7DD3FC, false);
                btnRight = invX - 6;
            }

            String crossText = buildUnexpectedCrossRef(detail);
            int crossMaxW = Math.max(40, btnRight - (cardX + 8));
            int crossColor = missingOfficial ? 0xFFFCA5A5 : AeternumTheme.TEXT_SECONDARY;
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, crossText, crossMaxW), cardX + 8, cy + 22, crossColor, false);
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
        if (button == 0 && activeTab == Tab.SCOREBOARDS && sbPanelMode != SbPanelMode.VIEW
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
            int regX = panelX + 10;
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
            int guideX = unexpX + unexpW + 6;
            int guideW = textRenderer.getWidth("Guía") + 18;
            if (mx >= guideX && mx < guideX + guideW && my >= tabY && my < tabY + 18) {
                if (activeTab != Tab.GUIDE) {
                    activeTab = Tab.GUIDE;
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
                    if (inspectingPlayerUuid != null) {
                        closePlayerDetail();
                    }
                    return true;
                }
            }

            int lTop = listTop();
            int lBottom = listBottom();
            if (inspectingPlayerUuid == null && my >= lTop && my < lBottom) {
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
                for (int i = 0; i < shown.size(); i++) {
                    int cy = lTop + i * ROW_STRIDE - (int) Math.round(listScroll);
                    if (cy + CARD_H < lTop || cy > lBottom) continue;
                    if (mx >= cardX && mx < cardX + cardW && my >= cy && my < cy + CARD_H) {
                        openPlayerDetail(shown.get(i));
                        return true;
                    }
                }
            }
            if (inspectingPlayerUuid != null && "STATS".equals(playerDetailSubtab)) {
                int detailTopY = tabBarY() + TAB_BAR_H;
                int contentX = panelX + SIDEBAR_W + 10;
                int chipY = detailTopY + 68;
                if (my >= chipY && my < chipY + 16) {
                    int cx = contentX;
                    for (String[] cat : PLAYER_STAT_CATEGORIES) {
                        int cw = textRenderer.getWidth(cat[1]) + 10;
                        if (mx >= cx && mx < cx + cw) {
                            playerStatCategory = cat[0];
                            playerDetailScroll = targetPlayerDetailScroll = 0;
                            return true;
                        }
                        cx += cw + 4;
                    }
                }
            }
        }

        if (activeTab == Tab.SCOREBOARDS && button == 0) {
            int topY = tabBarY() + TAB_BAR_H;
            int footerY = panelY + panelH - 36;
            int sTop = topY + 28;
            int sBottom = footerY - 60;

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
                            clearInlineScoreEdit();
                            activeScoreFilter = sf;
                            listScroll = targetListScroll = 0;
                            return true;
                        }
                        chipX += cw + 4;
                    }
                }
                int lTop = listTop();
                int lBottom = listBottom();
                if (selected != null && selected.name.equals(loadedObjectiveName)
                    && my >= lTop && my < lBottom && mx >= panelX + SIDEBAR_W + 6 && mx < panelX + panelW - 14) {
                    if (editingScoreOwner != null && inlineScoreField != null && inlineScoreField.isVisible()
                        && mx >= inlineScoreField.getX() && mx < inlineScoreField.getX() + inlineScoreField.getWidth()
                        && my >= inlineScoreField.getY() && my < inlineScoreField.getY() + inlineScoreField.getHeight()) {
                        return super.mouseClicked(mx, my, button);
                    }
                    int cardX = panelX + SIDEBAR_W + 10;
                    int cardW = panelW - SIDEBAR_W - 26;
                    int delX = cardX + cardW - 22;
                    List<ScoreboardEntrySnapshot> shown = filteredScoreEntries(selected);
                    boolean showTotalRow = hasTotalRow(selected);
                    for (int i = 0; i < shown.size(); i++) {
                        int rowIdx = i + (showTotalRow ? 1 : 0);
                        int cy = lTop + rowIdx * SB_ENTRY_STRIDE - (int) Math.round(listScroll);
                        if (cy + SB_ENTRY_H < lTop || cy > lBottom) continue;
                        if (my < cy || my >= cy + SB_ENTRY_H) continue;
                        ScoreboardEntrySnapshot item = shown.get(i);
                        int delY = cy + 5;
                        if (mx >= delX && mx < delX + 16 && my >= delY && my < delY + 16) {
                            clearInlineScoreEdit();
                            if (item.owner.equals(armedDeleteScoreOwner) && System.currentTimeMillis() <= armedDeleteScoreUntilMs) {
                                armedDeleteScoreOwner = null;
                                DeleteScoreEntryRequest req = new DeleteScoreEntryRequest();
                                req.objective = selected.name;
                                req.owner = item.owner;
                                statusMessage = "Eliminando registro de " + item.owner + "…";
                                statusError = false;
                                ClientPlayNetworking.send(new StatsRegistryActionPayload("delete_score_entry", "", GSON.toJson(req)));
                            } else {
                                armedDeleteScoreOwner = item.owner;
                                armedDeleteScoreUntilMs = System.currentTimeMillis() + 5000L;
                                statusMessage = "Clic de nuevo en x para confirmar borrar a " + item.owner;
                                statusError = true;
                            }
                            return true;
                        }
                        int scoreRight = delX - 8;
                        String scoreStr = String.format(Locale.US, "%,d", item.score);
                        int scoreX = scoreRight - textRenderer.getWidth(scoreStr);
                        if (mx >= scoreX - 6 && mx < scoreRight + 2 && my >= cy + 2 && my < cy + SB_ENTRY_H - 2) {
                            armedDeleteScoreOwner = null;
                            startInlineScoreEdit(item.owner, item.score);
                            return true;
                        }
                        break;
                    }
                }
                if (editingScoreOwner != null) {
                    clearInlineScoreEdit();
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
                long nowMs = System.currentTimeMillis();
                for (int i = 0; i < unexpectedFiles.size(); i++) {
                    int cy = lTop + i * UNEXPECTED_ROW_H - (int) Math.round(listScroll);
                    if (cy + UNEXPECTED_ROW_H < lTop || cy > lBottom) continue;
                    String path = unexpectedFiles.get(i);
                    UnexpectedFileSnapshot detail = unexpectedDetailsByPath.get(path);
                    int delY = cy + 3;
                    if (mx >= delX && mx < delX + 16 && my >= delY && my < delY + 16) {
                        statusMessage = "Borrando " + path + "…";
                        statusError = false;
                        ClientPlayNetworking.send(new StatsRegistryActionPayload("delete_unexpected", "", path));
                        return true;
                    }
                    int btnRight = cardX + cardW - 8;
                    int btnY = cy + 19;
                    int btnH = 14;
                    if (detail != null && !detail.linkedUuid.isEmpty() && !detail.hasOfficialDat && detail.validNbt) {
                        boolean armed = path.equals(armedPromotePath) && nowMs <= armedPromoteUntilMs;
                        String promLabel = armed ? "\u00BFConfirmar?" : "Hacer oficial";
                        int promW = textRenderer.getWidth(promLabel) + 10;
                        int promX = btnRight - promW;
                        if (mx >= promX && mx < promX + promW && my >= btnY && my < btnY + btnH) {
                            if (armed) {
                                armedPromotePath = null;
                                statusMessage = "Convirtiendo en .dat oficial…";
                                statusError = false;
                                ClientPlayNetworking.send(new StatsRegistryActionPayload("promote_unexpected", "", path));
                            } else {
                                armedPromotePath = path;
                                armedPromoteUntilMs = nowMs + 4000L;
                                statusMessage = "Haz clic de nuevo en \u00BFConfirmar? para promover este archivo a .dat oficial.";
                                statusError = false;
                            }
                            return true;
                        }
                        btnRight = promX - 4;
                    }
                    if (detail != null && detail.validNbt) {
                        String invLabel = "Ver inv.";
                        int invW = textRenderer.getWidth(invLabel) + 10;
                        int invX = btnRight - invW;
                        if (mx >= invX && mx < invX + invW && my >= btnY && my < btnY + btnH) {
                            statusMessage = "Abriendo inventario de " + path + "…";
                            statusError = false;
                            ClientPlayNetworking.send(new StatsRegistryActionPayload("open_unexpected_inventory", "", path));
                            return true;
                        }
                    }
                }
            }
        }

        if (button == 0 && mx >= panelX + panelW - 14 && mx <= panelX + panelW - 4 && my >= listTop() && my < listBottom()) {
            draggingScrollbar = true;
            if (activeTab == Tab.REGISTRY && inspectingPlayerUuid != null) {
                int dTop = tabBarY() + TAB_BAR_H + ("STATS".equals(playerDetailSubtab) ? 90 : 50);
                int dBottom = listBottom();
                double frac = Math.max(0.0, Math.min(1.0, (my - dTop) / Math.max(1.0, dBottom - dTop)));
                targetPlayerDetailScroll = playerDetailScroll = frac * maxPlayerDetailScrollPx();
            }
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    private String formatUnexpectedMeta(UnexpectedFileSnapshot detail) {
        if (detail == null) return "";
        String date = detail.lastModifiedMs > 0L
            ? UNEXPECTED_TIME_FMT.format(Instant.ofEpochMilli(detail.lastModifiedMs))
            : "";
        String size;
        if (detail.sizeBytes < 0L) {
            size = "carpeta";
        } else if (detail.sizeBytes == 0L) {
            size = "0 B (vac\u00edo)";
        } else if (detail.sizeBytes < 1024L) {
            size = detail.sizeBytes + " B";
        } else {
            size = String.format(Locale.ROOT, "%.1f KB", detail.sizeBytes / 1024.0);
        }
        boolean corruptDat = detail.sizeBytes > 0L && !detail.validNbt
            && detail.path != null && detail.path.startsWith("playerdata/") && detail.path.endsWith(".dat");
        if (corruptDat) {
            size += " (NBT corrupto)";
        }
        return date.isEmpty() ? size : (date + " \u00B7 " + size);
    }

    private String buildUnexpectedCrossRef(UnexpectedFileSnapshot detail) {
        if (detail == null || detail.linkedUuid == null || detail.linkedUuid.isEmpty()) {
            return "Sin UUID reconocida";
        }
        String owner;
        if (detail.linkedName != null && !detail.linkedName.isEmpty()) {
            String role = "BOT".equals(detail.linkedType) ? "Bot" : ("HUMAN".equals(detail.linkedType) ? "Jugador" : "Registro");
            owner = role + ": " + detail.linkedName;
        } else {
            owner = "UUID " + detail.linkedUuid.substring(0, 8) + " (no registrada)";
        }
        String datState;
        if (detail.hasOfficialDat) {
            datState = "\u2713 Tiene .dat";
        } else if (detail.hasOfficialDatOld) {
            datState = "\u26A0 Sin .dat (+ .dat_old)";
        } else {
            datState = "\u26A0 Sin .dat oficial";
        }
        String countStr = detail.sameUuidCount > 1 ? (" \u00B7 x" + detail.sameUuidCount + " temps") : "";
        return owner + " \u00B7 " + datState + countStr;
    }

    private void startInlineScoreEdit(String owner, int currentScore) {
        editingScoreOwner = owner;
        if (inlineScoreField != null) {
            inlineScoreField.setText(String.valueOf(currentScore));
            inlineScoreField.setVisible(true);
            setFocused(inlineScoreField);
            inlineScoreField.setFocused(true);
            inlineScoreField.setCursorToEnd(false);
        }
    }

    private void clearInlineScoreEdit() {
        editingScoreOwner = null;
        if (inlineScoreField != null) {
            inlineScoreField.setVisible(false);
            inlineScoreField.setFocused(false);
        }
    }

    private void commitInlineScoreEdit() {
        if (editingScoreOwner == null || inlineScoreField == null) return;
        ScoreboardObjectiveSnapshot selected = selectedObjectiveSnapshot();
        if (selected == null) {
            clearInlineScoreEdit();
            return;
        }
        String raw = inlineScoreField.getText().trim().replace(",", "");
        int nextScore;
        try {
            nextScore = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            statusMessage = "Número inválido: " + inlineScoreField.getText();
            statusError = true;
            return;
        }
        SetScoreRequest req = new SetScoreRequest();
        req.objective = selected.name;
        req.owner = editingScoreOwner;
        req.score = nextScore;
        statusMessage = "Actualizando score de " + editingScoreOwner + "…";
        statusError = false;
        clearInlineScoreEdit();
        ClientPlayNetworking.send(new StatsRegistryActionPayload("set_score", "", GSON.toJson(req)));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingScoreOwner != null && inlineScoreField != null && inlineScoreField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitInlineScoreEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                clearInlineScoreEdit();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
            ScoreboardObjectiveSnapshot sel = selectedObjectiveSnapshot();
            int count = filteredScoreEntries(sel).size() + (hasTotalRow(sel) ? 1 : 0);
            return maxListScrollPx(count, SB_ENTRY_STRIDE);
        }
        int count = activeTab == Tab.REGISTRY ? filteredEntries().size() : unexpectedFiles.size();
        int stride = activeTab == Tab.REGISTRY ? ROW_STRIDE : UNEXPECTED_ROW_H;
        return maxListScrollPx(count, stride);
    }

    private static final String[][] PLAYER_STAT_CATEGORIES = {
        {"custom", "General"},
        {"mined", "Minados"},
        {"used", "Usados"},
        {"crafted", "Crafteados"},
        {"broken", "Rotos"},
        {"picked_up", "Recogidos"},
        {"dropped", "Soltados"},
        {"killed", "Mobs"},
        {"killed_by", "Muertes"}
    };

    public void acceptPlayerDetail(String uuid, String json) {
        if (uuid != null && uuid.startsWith("__migrate_check__:")) {
            String targetUuid = uuid.substring("__migrate_check__:".length());
            if (!targetUuid.equals(inspectingPlayerUuid)) return;
            try {
                MigrationCheckResult res = GSON.fromJson(json, MigrationCheckResult.class);
                this.migrationCheck = res;
                this.migrationChecking = false;
                if (res != null && res.ok && res.source != null && res.target != null) {
                    this.migrateDataChoice = res.source.hasData ? "KEEP_A" : "KEEP_B";
                    this.migrateStatsChoice = (res.source.hasStats && res.target.hasStats)
                        ? "MERGE"
                        : (res.source.hasStats ? "KEEP_A" : "KEEP_B");
                    this.migrateAdvChoice = (res.source.hasAdv && res.target.hasAdv)
                        ? "MERGE"
                        : (res.source.hasAdv ? "KEEP_A" : "KEEP_B");
                }
                init();
            } catch (Exception ignored) {
                this.migrationChecking = false;
            }
            return;
        }
        if (uuid == null || !uuid.equals(inspectingPlayerUuid)) {
            return;
        }
        try {
            PlayerDetailSnapshot snap = GSON.fromJson(json, PlayerDetailSnapshot.class);
            inspectingPlayerDetail = snap;
            inspectingInvStacks.clear();
            inspectingEnderStacks.clear();
            if (snap != null) {
                if (snap.inventory != null) {
                    for (ItemSlotSnapshot s : snap.inventory) {
                        inspectingInvStacks.put(s.slot, buildClientStack(s));
                    }
                }
                if (snap.enderChest != null) {
                    for (ItemSlotSnapshot s : snap.enderChest) {
                        inspectingEnderStacks.put(s.slot, buildClientStack(s));
                    }
                }
            }
            if (tpLastPosButton != null) {
                tpLastPosButton.active = snap != null && snap.hasLastPos;
            }
        } catch (Exception ignored) {
        }
    }

    private void handleTpLastPosClick() {
        if (inspectingPlayerUuid == null || inspectingPlayerDetail == null || !inspectingPlayerDetail.hasLastPos) {
            return;
        }
        boolean inSurvival = client != null && client.player != null
            && !client.player.isCreative() && !client.player.isSpectator();
        if (inSurvival && System.currentTimeMillis() > armedTpLastPosUntilMs) {
            armedTpLastPosUntilMs = System.currentTimeMillis() + 5000L;
            statusMessage = "Estás en Survival: clic de nuevo en 'Confirmar TP' para teletransportarte.";
            statusError = true;
            init();
            return;
        }
        armedTpLastPosUntilMs = 0L;
        ClientPlayNetworking.send(new StatsRegistryActionPayload("tp_player_last_pos", inspectingPlayerUuid, ""));
        close();
    }

    private static ItemStack buildClientStack(ItemSlotSnapshot s) {
        if (s == null || s.itemId == null) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(s.itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return ItemStack.EMPTY;
        Item item = Registries.ITEM.get(id);
        ItemStack stack = new ItemStack(item, Math.max(1, s.count));
        if (s.maxDamage > 0 && s.damage > 0) {
            stack.setDamage(s.damage);
        }
        return stack;
    }

    private void openPlayerDetail(RegistryEntrySnapshot item) {
        if (item == null || item.uuid == null) return;
        inspectingPlayerUuid = item.uuid;
        inspectingPlayerDetail = null;
        armedTpLastPosUntilMs = 0L;
        migrationCheck = null;
        migrationChecking = false;
        migrateTargetNameInput = item.name != null ? item.name : "";
        inspectingInvStacks.clear();
        inspectingEnderStacks.clear();
        playerDetailSubtab = "STATS";
        playerDetailScroll = targetPlayerDetailScroll = 0;
        ClientPlayNetworking.send(new StatsRegistryActionPayload("request_player_detail", item.uuid, ""));
        init();
    }

    private void closePlayerDetail() {
        inspectingPlayerUuid = null;
        inspectingPlayerDetail = null;
        armedTpLastPosUntilMs = 0L;
        tpLastPosButton = null;
        migrationCheck = null;
        migrationChecking = false;
        migrateNameField = null;
        migrateTargetNameInput = "";
        inspectingInvStacks.clear();
        inspectingEnderStacks.clear();
        playerDetailScroll = targetPlayerDetailScroll = 0;
        init();
    }

    private List<PlayerStatSnapshot> filteredPlayerStats() {
        if (inspectingPlayerDetail == null || inspectingPlayerDetail.stats == null) {
            return List.of();
        }
        String q = playerStatSearchField != null ? playerStatSearchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<PlayerStatSnapshot> out = new ArrayList<>();
        for (PlayerStatSnapshot s : inspectingPlayerDetail.stats) {
            if (!playerStatCategory.equals(s.category)) continue;
            if (!q.isEmpty()) {
                String label = resolveStatDisplayName(s.category, s.key).toLowerCase(Locale.ROOT);
                if (!label.contains(q) && !s.key.toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
            }
            out.add(s);
        }
        return out;
    }

    private int maxPlayerDetailScrollPx() {
        int topY = tabBarY() + TAB_BAR_H;
        int lBottom = listBottom();
        if ("STATS".equals(playerDetailSubtab)) {
            int lTop = topY + 90;
            int viewH = Math.max(1, lBottom - lTop);
            int totalH = filteredPlayerStats().size() * 22;
            return Math.max(0, totalH - viewH);
        }
        int lTop = topY + 52;
        int viewH = Math.max(1, lBottom - lTop);
        int contentH = 250;
        return Math.max(0, contentH - viewH);
    }

    private void renderPlayerDetail(DrawContext c, int mx, int my, float delta) {
        int topY = tabBarY() + TAB_BAR_H;
        int contentX = panelX + SIDEBAR_W + 10;
        int contentW = panelW - SIDEBAR_W - 22;
        int lBottom = listBottom();

        if (inspectingPlayerDetail == null) {
            c.drawCenteredTextWithShadow(textRenderer, "Cargando datos de " + inspectingPlayerUuid + "…",
                contentX + contentW / 2, topY + 120, AeternumTheme.TEXT_SECONDARY);
            return;
        }

        String title = (inspectingPlayerDetail.name != null && !inspectingPlayerDetail.name.isEmpty())
            ? inspectingPlayerDetail.name
            : inspectingPlayerDetail.uuid;
        String headerInfo = title + "  \u00B7  " + inspectingPlayerDetail.type
            + (inspectingPlayerDetail.online ? "  \u00B7  ONLINE" : "  \u00B7  OFFLINE");
        int headerY = topY + ("STATS".equals(playerDetailSubtab) ? 52 : 32);
        c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, headerInfo, contentW),
            contentX, headerY, AeternumTheme.TEXT_PRIMARY);

        if ("STATS".equals(playerDetailSubtab)) {
            int chipY = topY + 68;
            int cx = contentX;
            for (String[] cat : PLAYER_STAT_CATEGORIES) {
                int cw = textRenderer.getWidth(cat[1]) + 10;
                boolean sel = cat[0].equals(playerStatCategory);
                boolean over = mx >= cx && mx < cx + cw && my >= chipY && my < chipY + 16;
                AeternumTheme.drawCard(c, cx, chipY, cw, 16,
                    sel ? AeternumTheme.CARD_SELECTED : (over ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_ALT_BG),
                    sel ? AeternumTheme.ACCENT_GOLD : AeternumTheme.BORDER_SUBTLE);
                c.drawText(textRenderer, cat[1], cx + 5, chipY + 4, sel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY, false);
                cx += cw + 4;
            }

            int lTop = topY + 90;
            int viewH = Math.max(1, lBottom - lTop);
            List<PlayerStatSnapshot> shown = filteredPlayerStats();
            int maxL = Math.max(0, shown.size() * 22 - viewH);
            targetPlayerDetailScroll = Math.max(0, Math.min(targetPlayerDetailScroll, maxL));
            playerDetailScroll += (targetPlayerDetailScroll - playerDetailScroll) * Math.min(1.0, delta * 0.45 + 0.25);
            playerDetailScroll = Math.max(0, Math.min(playerDetailScroll, maxL));

            if (shown.isEmpty()) {
                c.drawCenteredTextWithShadow(textRenderer,
                    inspectingPlayerDetail.hasStats ? "Sin registros en esta categoría." : "Sin archivo stats/*.json.",
                    contentX + contentW / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
                return;
            }

            c.enableScissor(panelX + SIDEBAR_W + 2, lTop, panelX + panelW - 8, lBottom);
            for (int i = 0; i < shown.size(); i++) {
                int ry = lTop + i * 22 - (int) Math.round(playerDetailScroll);
                if (ry + 20 < lTop || ry > lBottom) continue;
                PlayerStatSnapshot st = shown.get(i);
                AeternumTheme.drawCard(c, contentX, ry, contentW - 6, 20,
                    (i % 2 == 0) ? AeternumTheme.CARD_BG : AeternumTheme.CARD_ALT_BG,
                    AeternumTheme.BORDER_SUBTLE);
                String valStr = formatStatValue(st.category, st.key, st.value);
                int vw = textRenderer.getWidth(valStr);
                int vx = contentX + contentW - 14 - vw;
                c.drawTextWithShadow(textRenderer, valStr, vx, ry + 6, AeternumTheme.ACCENT_GOLD);

                ItemStack icon = resolveStatIconStack(st.category, st.key);
                int labelX = contentX + 8;
                if (!icon.isEmpty()) {
                    c.drawItem(icon, contentX + 5, ry + 2);
                    labelX = contentX + 26;
                }
                String label = resolveStatDisplayName(st.category, st.key);
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, label, Math.max(40, vx - labelX - 8)),
                    labelX, ry + 6, AeternumTheme.TEXT_PRIMARY, false);
            }
            c.disableScissor();
            if (maxL > 0) {
                AeternumTheme.drawScrollbar(c, panelX + panelW - 9, lTop, lBottom,
                    shown.size() * 22, viewH, playerDetailScroll, false);
            }
        } else if ("MIGRATE".equals(playerDetailSubtab)) {
            renderMigrateSubtab(c, contentX, topY + 76, contentW, lBottom);
        } else {
            renderPlayerDetailInventory(c, mx, my, delta, contentX, topY + 50, contentW, lBottom);
        }
    }

    private void initMigrateSubtabWidgets(int contentX, int contentW, int contentTop) {
        int rowY = contentTop + 48;
        int premW = 92;
        int noPremW = 104;
        int checkW = 80;
        addDrawableChild(
            "PREMIUM".equals(migrateMode)
                ? UiButton.primary(contentX, rowY, premW, 18, "Premium (v4)", b -> {})
                : UiButton.secondary(contentX, rowY, premW, 18, "Premium (v4)", b -> {
                    migrateMode = "PREMIUM";
                    migrationCheck = null;
                    init();
                })
        );
        addDrawableChild(
            "NON_PREMIUM".equals(migrateMode)
                ? UiButton.primary(contentX + premW + 4, rowY, noPremW, 18, "No Premium (v3)", b -> {})
                : UiButton.secondary(contentX + premW + 4, rowY, noPremW, 18, "No Premium (v3)", b -> {
                    migrateMode = "NON_PREMIUM";
                    migrationCheck = null;
                    init();
                })
        );
        int fieldX = contentX + premW + noPremW + 10;
        int fieldW = Math.max(80, contentW - (premW + noPremW + 10 + checkW + 6));
        migrateNameField = new UiTextField(textRenderer, fieldX, rowY, fieldW, 18, "Nombre destino…");
        migrateNameField.setMaxLength(16);
        migrateNameField.setText(migrateTargetNameInput);
        migrateNameField.setChangedListener(s -> migrateTargetNameInput = s);
        addDrawableChild(migrateNameField);

        UiButton checkBtn = UiButton.primary(contentX + contentW - checkW, rowY, checkW, 18,
            migrationChecking ? "…" : "Comprobar", b -> triggerMigrationCheck());
        checkBtn.active = !migrationChecking;
        addDrawableChild(checkBtn);

        if (migrationCheck == null || !migrationCheck.ok || migrationCheck.source == null || migrationCheck.target == null) {
            return;
        }
        AccountSideSnapshot src = migrationCheck.source;
        AccountSideSnapshot dst = migrationCheck.target;
        if (!migrationCheck.hasCollision) {
            int execY = contentTop + 178;
            addDrawableChild(UiButton.primary(contentX, execY, 150, 20, "Ejecutar migración", b -> executeMigration()));
            return;
        }

        int optStartY = contentTop + 188;
        int optX = contentX + 128;

        // 1. Playerdata (data)
        UiButton dataA = "KEEP_A".equals(migrateDataChoice)
            ? UiButton.primary(optX, optStartY, 92, 16, "Conservar A", b -> {})
            : UiButton.secondary(optX, optStartY, 92, 16, "Conservar A", b -> { migrateDataChoice = "KEEP_A"; init(); });
        dataA.active = src.hasData;
        addDrawableChild(dataA);

        UiButton dataB = "KEEP_B".equals(migrateDataChoice)
            ? UiButton.primary(optX + 96, optStartY, 92, 16, "Conservar B", b -> {})
            : UiButton.secondary(optX + 96, optStartY, 92, 16, "Conservar B", b -> { migrateDataChoice = "KEEP_B"; init(); });
        dataB.active = dst.hasData;
        addDrawableChild(dataB);

        // 2. Stats (stats)
        int statsY = optStartY + 22;
        UiButton statsM = "MERGE".equals(migrateStatsChoice)
            ? UiButton.primary(optX, statsY, 96, 16, "Fusionar (A+B)", b -> {})
            : UiButton.secondary(optX, statsY, 96, 16, "Fusionar (A+B)", b -> { migrateStatsChoice = "MERGE"; init(); });
        statsM.active = src.hasStats && dst.hasStats;
        addDrawableChild(statsM);

        UiButton statsA = "KEEP_A".equals(migrateStatsChoice)
            ? UiButton.primary(optX + 100, statsY, 92, 16, "Conservar A", b -> {})
            : UiButton.secondary(optX + 100, statsY, 92, 16, "Conservar A", b -> { migrateStatsChoice = "KEEP_A"; init(); });
        statsA.active = src.hasStats;
        addDrawableChild(statsA);

        UiButton statsB = "KEEP_B".equals(migrateStatsChoice)
            ? UiButton.primary(optX + 196, statsY, 92, 16, "Conservar B", b -> {})
            : UiButton.secondary(optX + 196, statsY, 92, 16, "Conservar B", b -> { migrateStatsChoice = "KEEP_B"; init(); });
        statsB.active = dst.hasStats;
        addDrawableChild(statsB);

        // 3. Advancements (adv)
        int advY = optStartY + 44;
        UiButton advM = "MERGE".equals(migrateAdvChoice)
            ? UiButton.primary(optX, advY, 96, 16, "Fusionar (A+B)", b -> {})
            : UiButton.secondary(optX, advY, 96, 16, "Fusionar (A+B)", b -> { migrateAdvChoice = "MERGE"; init(); });
        advM.active = src.hasAdv && dst.hasAdv;
        addDrawableChild(advM);

        UiButton advA = "KEEP_A".equals(migrateAdvChoice)
            ? UiButton.primary(optX + 100, advY, 92, 16, "Conservar A", b -> {})
            : UiButton.secondary(optX + 100, advY, 92, 16, "Conservar A", b -> { migrateAdvChoice = "KEEP_A"; init(); });
        advA.active = src.hasAdv;
        addDrawableChild(advA);

        UiButton advB = "KEEP_B".equals(migrateAdvChoice)
            ? UiButton.primary(optX + 196, advY, 92, 16, "Conservar B", b -> {})
            : UiButton.secondary(optX + 196, advY, 92, 16, "Conservar B", b -> { migrateAdvChoice = "KEEP_B"; init(); });
        advB.active = dst.hasAdv;
        addDrawableChild(advB);

        int execY = advY + 26;
        addDrawableChild(UiButton.primary(contentX, execY, 150, 20, "Ejecutar migración", b -> executeMigration()));
    }

    private void triggerMigrationCheck() {
        if (inspectingPlayerUuid == null || migrationChecking) return;
        String name = migrateNameField != null ? migrateNameField.getText().trim() : migrateTargetNameInput.trim();
        if (name.isEmpty()) {
            statusMessage = "Escribe el nombre destino antes de comprobar.";
            statusError = true;
            return;
        }
        migrationChecking = true;
        migrationCheck = null;
        com.google.gson.JsonObject req = new com.google.gson.JsonObject();
        req.addProperty("mode", migrateMode);
        req.addProperty("targetName", name);
        ClientPlayNetworking.send(new StatsRegistryActionPayload("check_migration", inspectingPlayerUuid, GSON.toJson(req)));
        init();
    }

    private void executeMigration() {
        if (inspectingPlayerUuid == null || migrationCheck == null || !migrationCheck.ok || migrationCheck.target == null) return;
        com.google.gson.JsonObject req = new com.google.gson.JsonObject();
        req.addProperty("mode", migrationCheck.mode);
        req.addProperty("targetName", migrationCheck.targetName);
        req.addProperty("targetUuid", migrationCheck.target.uuid);
        req.addProperty("dataChoice", migrateDataChoice);
        req.addProperty("statsChoice", migrateStatsChoice);
        req.addProperty("advChoice", migrateAdvChoice);
        ClientPlayNetworking.send(new StatsRegistryActionPayload("execute_migration", inspectingPlayerUuid, GSON.toJson(req)));
        closePlayerDetail();
    }

    private void renderMigrateSubtab(DrawContext c, int contentX, int y, int contentW, int lBottom) {
        if (migrationChecking) {
            c.drawTextWithShadow(textRenderer, "Comprobando cuenta destino y archivos en disco…",
                contentX, y + 8, AeternumTheme.TEXT_SECONDARY);
            return;
        }
        if (migrationCheck == null) {
            c.drawText(textRenderer, "Elige Premium (v4) o No Premium (v3), escribe el nombre destino y pulsa Comprobar.",
                contentX, y + 8, AeternumTheme.TEXT_SECONDARY, false);
            return;
        }
        if (!migrationCheck.ok) {
            AeternumTheme.drawCard(c, contentX, y, contentW, 34, 0xFF28181A, 0xFFD96B6B);
            c.drawTextWithShadow(textRenderer, "No se puede migrar:", contentX + 10, y + 6, 0xFFF29A9A);
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, migrationCheck.error, contentW - 20),
                contentX + 10, y + 19, AeternumTheme.TEXT_PRIMARY, false);
            return;
        }

        int cardGap = 8;
        int cardW = (contentW - cardGap) / 2;
        int cardH = 72;
        drawMigrateSideCard(c, contentX, y, cardW, cardH, "A \u00B7 Origen", migrationCheck.source);
        drawMigrateSideCard(c, contentX + cardW + cardGap, y, cardW, cardH, "B \u00B7 Destino", migrationCheck.target);

        int infoY = y + cardH + 8;
        if (!migrationCheck.hasCollision) {
            c.drawTextWithShadow(textRenderer, "Sin colisiones en B. Se moverán todos los archivos existentes de A hacia B.",
                contentX, infoY, 0xFF8BE0B2);
            return;
        }

        c.drawTextWithShadow(textRenderer, "Colisión detectada en B. Elige qué conservar o fusionar en cada campo:",
            contentX, infoY, 0xFFE59B53);
        int optStartY = infoY + 18;
        c.drawText(textRenderer, "Playerdata (data):", contentX, optStartY + 4, AeternumTheme.TEXT_PRIMARY, false);
        c.drawText(textRenderer, "Estadísticas (stats):", contentX, optStartY + 26, AeternumTheme.TEXT_PRIMARY, false);
        c.drawText(textRenderer, "Logros (adv):", contentX, optStartY + 48, AeternumTheme.TEXT_PRIMARY, false);

        AccountSideSnapshot src = migrationCheck.source;
        AccountSideSnapshot dst = migrationCheck.target;
        if (src != null && dst != null && (src.invSlots + src.enderSlots) > 0 && (dst.invSlots + dst.enderSlots) > 0) {
            c.drawText(textRenderer, "Tip: ambos tienen ítems; si quieres rescatar de los dos, usa antes 'Abrir editor inv.'",
                contentX + 160, optStartY + 76, 0xFFE5C46B, false);
        }
    }

    private void drawMigrateSideCard(DrawContext c, int x, int y, int w, int h, String heading, AccountSideSnapshot side) {
        if (side == null) return;
        AeternumTheme.drawCard(c, x, y, w, h, AeternumTheme.CARD_BG, AeternumTheme.BORDER_SUBTLE);
        String ver = side.uuidVersion == 4 ? "v4" : (side.uuidVersion == 3 ? "v3" : "v?");
        String title = heading + " · " + (side.name != null && !side.name.isEmpty() ? side.name : "(sin nick)") + " [" + ver + "]";
        c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, title, w - 16),
            x + 8, y + 6, AeternumTheme.TEXT_PRIMARY);
        c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, side.uuid, w - 16),
            x + 8, y + 18, AeternumTheme.TEXT_MUTED, false);

        int bx = x + 8;
        int by = y + 31;
        bx += drawPresenceBadge(c, bx, by, "data", side.hasData) + 4;
        bx += drawPresenceBadge(c, bx, by, "stats", side.hasStats) + 4;
        drawPresenceBadge(c, bx, by, "adv", side.hasAdv);

        String statsLine = String.format(Locale.US, "Inv:%d Ender:%d · Min:%d · Adv:%d",
            side.invSlots, side.enderSlots, side.mined, side.advDoneCount);
        c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, statsLine, w - 16),
            x + 8, y + 52, AeternumTheme.TEXT_SECONDARY, false);
    }

    private int drawPresenceBadge(DrawContext c, int x, int y, String label, boolean present) {
        String txt = label + (present ? ": Sí" : ": No");
        int bg = present ? 0xFF1A2A22 : 0xFF1E1E24;
        int fg = present ? 0xFF8BE0B2 : AeternumTheme.TEXT_MUTED;
        AeternumTheme.drawBadge(c, textRenderer, x, y, txt, bg, fg);
        return textRenderer.getWidth(txt) + 10;
    }

    private void renderPlayerDetailInventory(
        DrawContext c, int mx, int my, float delta, int contentX, int lTop, int contentW, int lBottom
    ) {
        if (!inspectingPlayerDetail.hasPlayerdata) {
            c.drawCenteredTextWithShadow(textRenderer, "Sin archivo playerdata (.dat).",
                contentX + contentW / 2, lTop + 80, AeternumTheme.TEXT_SECONDARY);
            return;
        }
        int viewH = Math.max(1, lBottom - lTop);
        int maxL = maxPlayerDetailScrollPx();
        targetPlayerDetailScroll = Math.max(0, Math.min(targetPlayerDetailScroll, maxL));
        playerDetailScroll += (targetPlayerDetailScroll - playerDetailScroll) * Math.min(1.0, delta * 0.45 + 0.25);
        playerDetailScroll = Math.max(0, Math.min(playerDetailScroll, maxL));

        ItemSlotSnapshot hoveredSnap = null;
        int baseY = lTop - (int) Math.round(playerDetailScroll);
        c.enableScissor(panelX + SIDEBAR_W + 2, lTop, panelX + panelW - 8, lBottom);

        // Armor + Offhand row
        c.drawText(textRenderer, "Equipo (Casco · Pechera · Pantalón · Botas · Offhand)", contentX, baseY + 2, AeternumTheme.TEXT_SECONDARY, false);
        int[] eqSlots = {39, 38, 37, 36, 40};
        for (int i = 0; i < eqSlots.length; i++) {
            int sx = contentX + i * 20;
            int sy = baseY + 13;
            ItemSlotSnapshot found = drawSlotBox(c, sx, sy, eqSlots[i], inspectingInvStacks, inspectingPlayerDetail.inventory, mx, my, lTop, lBottom);
            if (found != null) hoveredSnap = found;
        }

        // Main inventory (9x3) + Hotbar (9x1)
        int invY = baseY + 38;
        c.drawText(textRenderer, "Inventario", contentX, invY, AeternumTheme.TEXT_SECONDARY, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = 9 + row * 9 + col;
                ItemSlotSnapshot found = drawSlotBox(c, contentX + col * 20, invY + 11 + row * 20, slot,
                    inspectingInvStacks, inspectingPlayerDetail.inventory, mx, my, lTop, lBottom);
                if (found != null) hoveredSnap = found;
            }
        }
        for (int col = 0; col < 9; col++) {
            ItemSlotSnapshot found = drawSlotBox(c, contentX + col * 20, invY + 75, col,
                inspectingInvStacks, inspectingPlayerDetail.inventory, mx, my, lTop, lBottom);
            if (found != null) hoveredSnap = found;
        }

        // Ender Chest (9x3) - to the right if wide enough, else below
        int enderX = (contentW >= 380) ? (contentX + 196) : contentX;
        int enderY = (contentW >= 380) ? invY : (invY + 102);
        c.drawText(textRenderer, "Ender Chest", enderX, enderY, AeternumTheme.TEXT_SECONDARY, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                ItemSlotSnapshot found = drawSlotBox(c, enderX + col * 20, enderY + 11 + row * 20, slot,
                    inspectingEnderStacks, inspectingPlayerDetail.enderChest, mx, my, lTop, lBottom);
                if (found != null) hoveredSnap = found;
            }
        }

        c.disableScissor();
        if (hoveredSnap != null && hoveredSnap.tooltip != null && !hoveredSnap.tooltip.isEmpty()) {
            List<Text> tipLines = new ArrayList<>(hoveredSnap.tooltip.size());
            for (String line : hoveredSnap.tooltip) {
                tipLines.add(Text.literal(line));
            }
            c.drawTooltip(textRenderer, tipLines, mx, my);
        }
    }

    private ItemSlotSnapshot drawSlotBox(
        DrawContext c, int sx, int sy, int slot,
        Map<Integer, ItemStack> stacks, List<ItemSlotSnapshot> snapshots,
        int mx, int my, int lTop, int lBottom
    ) {
        AeternumTheme.drawCard(c, sx, sy, 18, 18, 0xFF121218, 0xFF2D2D38);
        ItemStack stack = stacks.getOrDefault(slot, ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            c.drawItem(stack, sx + 1, sy + 1);
            c.drawStackOverlay(textRenderer, stack, sx + 1, sy + 1);
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18 && my >= lTop && my < lBottom) {
                if (snapshots != null) {
                    for (ItemSlotSnapshot snap : snapshots) {
                        if (snap.slot == slot) return snap;
                    }
                }
            }
        }
        return null;
    }

    private static ItemStack resolveStatIconStack(String category, String rawKey) {
        Identifier id = Identifier.tryParse(rawKey);
        if (id == null) return ItemStack.EMPTY;
        if ("mined".equals(category) && Registries.BLOCK.containsId(id)) {
            Item asItem = Registries.BLOCK.get(id).asItem();
            return asItem != Items.AIR ? new ItemStack(asItem) : ItemStack.EMPTY;
        }
        if (("used".equals(category) || "crafted".equals(category) || "broken".equals(category)
            || "picked_up".equals(category) || "dropped".equals(category)) && Registries.ITEM.containsId(id)) {
            Item item = Registries.ITEM.get(id);
            return item != Items.AIR ? new ItemStack(item) : ItemStack.EMPTY;
        }
        if (("killed".equals(category) || "killed_by".equals(category)) && Registries.ENTITY_TYPE.containsId(id)) {
            EntityType<?> et = Registries.ENTITY_TYPE.get(id);
            SpawnEggItem egg = SpawnEggItem.forEntity(et);
            if (egg != null) return new ItemStack(egg);
            if (et == EntityType.PLAYER) return new ItemStack(Items.PLAYER_HEAD);
            if (et == EntityType.ENDER_DRAGON) return new ItemStack(Items.DRAGON_HEAD);
            if (et == EntityType.WITHER) return new ItemStack(Items.WITHER_SKELETON_SKULL);
            return new ItemStack(Items.IRON_SWORD);
        }
        return ItemStack.EMPTY;
    }

    private static String resolveStatDisplayName(String category, String rawKey) {
        Identifier id = Identifier.tryParse(rawKey);
        if (id != null) {
            if ("mined".equals(category) && Registries.BLOCK.containsId(id)) {
                return Registries.BLOCK.get(id).getName().getString();
            }
            if (("used".equals(category) || "crafted".equals(category) || "broken".equals(category)
                || "picked_up".equals(category) || "dropped".equals(category)) && Registries.ITEM.containsId(id)) {
                return Registries.ITEM.get(id).getName().getString();
            }
            if (("killed".equals(category) || "killed_by".equals(category)) && Registries.ENTITY_TYPE.containsId(id)) {
                return Registries.ENTITY_TYPE.get(id).getName().getString();
            }
            if ("custom".equals(category)) {
                String transKey = "stat." + id.getNamespace() + "." + id.getPath();
                String translated = Text.translatable(transKey).getString();
                if (!translated.equals(transKey)) {
                    return translated;
                }
            }
        }
        return rawKey != null ? rawKey : "";
    }

    private static String formatStatValue(String category, String rawKey, int value) {
        if ("custom".equals(category) && rawKey != null) {
            String path = rawKey.contains(":") ? rawKey.substring(rawKey.indexOf(':') + 1) : rawKey;
            if ("play_time".equals(path) || "total_world_time".equals(path) || "time_since_death".equals(path)
                || "time_since_rest".equals(path) || "sneak_time".equals(path)) {
                long secs = Math.max(0L, value) / 20L;
                if (secs < 60L) return secs + "s";
                long mins = secs / 60L;
                if (mins < 60L) return mins + "m " + (secs % 60L) + "s";
                long hours = mins / 60L;
                if (hours < 24L) return hours + "h " + (mins % 60L) + "m";
                long days = hours / 24L;
                return days + "d " + (hours % 24L) + "h";
            }
            if (path.endsWith("_one_cm")) {
                double meters = value / 100.0;
                if (meters >= 1000.0) {
                    return String.format(Locale.US, "%.2f km", meters / 1000.0);
                }
                if (meters >= 1.0) {
                    return String.format(Locale.US, "%.1f m", meters);
                }
                return value + " cm";
            }
            if (path.startsWith("damage_")) {
                return String.format(Locale.US, "%.1f \u2764", value / 20.0);
            }
        }
        return String.format(Locale.US, "%,d", value);
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
            int sBottom = panelY + panelH - 36 - 60;
            int maxSb = Math.max(0, filteredObjectives().size() * SB_SIDEBAR_ROW_H - (sBottom - sTop));
            double frac = Math.max(0.0, Math.min(1.0, (my - sTop) / Math.max(1.0, sBottom - sTop)));
            targetSbSidebarScroll = sbSidebarScroll = frac * maxSb;
            return true;
        }
        if (draggingScrollbar) {
            if (activeTab == Tab.REGISTRY && inspectingPlayerUuid != null) {
                int lTop = tabBarY() + TAB_BAR_H + ("STATS".equals(playerDetailSubtab) ? 90 : 50);
                int lBottom = listBottom();
                int maxL = maxPlayerDetailScrollPx();
                double frac = Math.max(0.0, Math.min(1.0, (my - lTop) / Math.max(1.0, lBottom - lTop)));
                targetPlayerDetailScroll = playerDetailScroll = frac * maxL;
                return true;
            }
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
        if (activeTab == Tab.SCOREBOARDS && sbPanelMode != SbPanelMode.VIEW
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
            int sBottom = panelY + panelH - 36 - 60;
            if (my >= sTop && my < sBottom) {
                int maxSb = Math.max(0, filteredObjectives().size() * SB_SIDEBAR_ROW_H - (sBottom - sTop));
                targetSbSidebarScroll = Math.max(0, Math.min(maxSb, targetSbSidebarScroll - vAmt * 22.0));
                return true;
            }
        }
        if (activeTab == Tab.REGISTRY && inspectingPlayerUuid != null) {
            int lTop = tabBarY() + TAB_BAR_H + ("STATS".equals(playerDetailSubtab) ? 90 : 50);
            int lBottom = listBottom();
            if (my >= lTop && my < lBottom && mx >= panelX + SIDEBAR_W) {
                int maxDetail = maxPlayerDetailScrollPx();
                targetPlayerDetailScroll = Math.max(0.0, Math.min(maxDetail, targetPlayerDetailScroll - vAmt * 24.0));
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
