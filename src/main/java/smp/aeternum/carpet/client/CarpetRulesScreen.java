package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.network.CarpetRuleSetPayload;
import smp.aeternum.carpet.network.CarpetRulesNetworking.RuleSnapshot;

public final class CarpetRulesScreen extends UiScreen {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<List<RuleSnapshot>>() {}.getType();
    private static final int SIDEBAR_W = 156;
    private static final int SIDEBAR_ROW_H = 20;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 4;
    private static final int ROW_STRIDE = CARD_H + CARD_GAP;

    private List<RuleSnapshot> rules;
    private String selectedScope = "ALL"; // ALL, MODIFIED, MGR:<id>, SUB:<id>/<folder>
    private final Set<String> expandedManagers = new HashSet<>();
    private boolean saveDefault = true;
    private String statusMessage = "";
    private boolean statusError;

    private double sidebarScroll, targetSidebarScroll;
    private double listScroll, targetListScroll;
    private boolean draggingScrollbar;

    private UiTextField searchField;
    private UiButton defaultToggleButton;

    // Inline editor for non-boolean / custom value rules
    private RuleSnapshot editingRule;
    private UiTextField valueField;
    private UiButton applyValueButton, cancelValueButton;

    public CarpetRulesScreen(String json) {
        super(Text.literal("Reglas de Carpet"));
        this.rules = decode(json);
    }

    public void replaceData(String json, String status, boolean error) {
        this.rules = decode(json);
        if (status != null && !status.isEmpty()) {
            this.statusMessage = status;
            this.statusError = error;
        }
        if (editingRule != null) {
            boolean stillExists = rules.stream().anyMatch(r -> r.managerId.equals(editingRule.managerId) && r.name.equals(editingRule.name));
            if (!stillExists) editingRule = null;
        }
    }

    @Override
    protected void init() {
        String prevQuery = searchField != null ? searchField.getText() : "";
        String prevEditVal = valueField != null ? valueField.getText() : null;
        clearChildren();
        computePanel(Math.min(760, (int) (width * 0.86)), Math.min(460, (int) (height * 0.84)));

        defaultToggleButton = UiButton.secondary(panelX + panelW - 186, panelY + 11, 148, 22,
            saveDefault ? "Guardar Default: SÍ" : "Guardar Default: NO",
            b -> {
                saveDefault = !saveDefault;
                b.setLabel(saveDefault ? "Guardar Default: SÍ" : "Guardar Default: NO");
                b.setVariant(saveDefault ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
            });
        defaultToggleButton.setVariant(saveDefault ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
        addDrawableChild(defaultToggleButton);

        int contentX = panelX + SIDEBAR_W + 10;
        int contentW = panelW - SIDEBAR_W - 22;
        searchField = new UiTextField(textRenderer, contentX, panelY + 52, contentW, 20, "Buscar regla o descripción…");
        searchField.setMaxLength(80);
        searchField.setText(prevQuery);
        searchField.setChangedListener(s -> {
            targetListScroll = 0;
            listScroll = 0;
        });
        addDrawableChild(searchField);

        if (editingRule != null) {
            int barY = panelY + panelH - 64;
            valueField = new UiTextField(textRenderer, contentX, barY, Math.max(120, contentW - 134), 20, "Nuevo valor…");
            valueField.setMaxLength(128);
            valueField.setText(prevEditVal != null ? prevEditVal : editingRule.value);
            valueField.setFocused(true);
            addDrawableChild(valueField);

            applyValueButton = UiButton.primary(contentX + contentW - 128, barY, 62, 20, "Aplicar", b -> submitInlineEdit());
            cancelValueButton = UiButton.secondary(contentX + contentW - 62, barY, 62, 20, "Cancelar", b -> closeInlineEdit());
            addDrawableChild(applyValueButton);
            addDrawableChild(cancelValueButton);
        } else {
            valueField = null;
            applyValueButton = null;
            cancelValueButton = null;
        }
    }

    private void openInlineEdit(RuleSnapshot rule) {
        editingRule = rule;
        init();
    }

    private void closeInlineEdit() {
        editingRule = null;
        init();
    }

    private void submitInlineEdit() {
        if (editingRule == null || valueField == null) return;
        String val = valueField.getText().trim();
        if (val.isEmpty()) return;
        ClientPlayNetworking.send(new CarpetRuleSetPayload(editingRule.managerId, editingRule.name, val, saveDefault, false));
        editingRule = null;
        init();
    }

    private static final Map<String, String> AETERNUM_FOLDERS = Map.ofEntries(
        Map.entry("reloadFakePlayers", "Bots"),
        Map.entry("editBotInventory", "Bots"),
        Map.entry("todoList", "HUD & Dock"),
        Map.entry("joinMotd", "HUD & Dock"),
        Map.entry("quickDock", "HUD & Dock"),
        Map.entry("explorationManager", "HUD & Dock"),
        Map.entry("allowPlayerExploration", "HUD & Dock"),
        Map.entry("commandPerimeters", "Comandos"),
        Map.entry("commandFinder", "Comandos"),
        Map.entry("commandProtect", "Comandos"),
        Map.entry("commandActions", "Comandos"),
        Map.entry("commandGuide", "Comandos"),
        Map.entry("commandProjects", "Comandos"),
        Map.entry("commandDimension", "Comandos"),
        Map.entry("commandHat", "Comandos"),
        Map.entry("commandViewInventory", "Comandos"),
        Map.entry("commandStats", "Comandos"),
        Map.entry("commandWaypoints", "Comandos"),
        Map.entry("phantomSpawning", "Mundo & Mecánicas"),
        Map.entry("spectatorPortalTravel", "Mundo & Mecánicas"),
        Map.entry("openShulkerInHand", "Mundo & Mecánicas"),
        Map.entry("aeStackableShulkersFixes", "Mundo & Mecánicas"),
        Map.entry("openEnderChestInHand", "Mundo & Mecánicas"),
        Map.entry("protectedPots", "Mundo & Mecánicas"),
        Map.entry("endermanGriefing", "Mundo & Mecánicas"),
        Map.entry("cameraMode", "Mundo & Mecánicas"),
        Map.entry("carefulBreak", "Mundo & Mecánicas"),
        Map.entry("playerSit", "Mundo & Mecánicas"),
        Map.entry("oldFlintAndSteelBehavior", "Mundo & Mecánicas"),
        Map.entry("updateSuppressionCrashFix", "Mundo & Mecánicas")
    );

    private static String resolveSource(RuleSnapshot r) {
        if (r.sourceMod != null && !r.sourceMod.isBlank()) return r.sourceMod;
        return r.managerId != null ? r.managerId : "Carpet";
    }

    private static String resolveSubfolder(RuleSnapshot r) {
        String source = resolveSource(r);
        if ("Aeternum".equalsIgnoreCase(source) && r.name != null) {
            String mapped = AETERNUM_FOLDERS.get(r.name);
            if (mapped != null) return mapped;
        }
        if (r.categories == null || r.categories.isEmpty()) return "General";
        String fallback = null;
        for (String cat : r.categories) {
            if (cat == null || cat.isBlank()) continue;
            String low = cat.toLowerCase(Locale.ROOT);
            if (low.equalsIgnoreCase(source) || low.equalsIgnoreCase(r.managerId)
                || low.equals("aeternum") || low.equals("chronos") || low.equals("tis")
                || low.equals("ams") || low.equals("extra") || low.equals("org")) {
                continue;
            }
            if (low.equals("feature") || low.equals("command") || low.equals("experimental")) {
                if (fallback == null) fallback = formatFolderName(cat);
                continue;
            }
            return formatFolderName(cat);
        }
        return fallback != null ? fallback : "General";
    }

    private static String formatFolderName(String raw) {
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.equals("tnt")) return "TNT";
        if (low.equals("hud")) return "HUD";
        return Character.toUpperCase(low.charAt(0)) + low.substring(1);
    }

    private List<ScopeItem> buildScopes() {
        List<ScopeItem> scopes = new ArrayList<>();
        long modifiedCount = rules.stream().filter(r -> !r.isDefault).count();
        scopes.add(new ScopeItem("ALL", "Todas (" + rules.size() + ")", 0, false, false, null));
        scopes.add(new ScopeItem("MODIFIED", "Modificadas (" + modifiedCount + ")", 0, false, false, null));

        Map<String, Integer> managerTotals = new LinkedHashMap<>();
        Map<String, Integer> managerModified = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> managerSubs = new LinkedHashMap<>();
        for (RuleSnapshot r : rules) {
            String src = resolveSource(r);
            managerTotals.merge(src, 1, Integer::sum);
            if (!r.isDefault) {
                managerModified.merge(src, 1, Integer::sum);
            }
            String sub = resolveSubfolder(r);
            managerSubs.computeIfAbsent(src, k -> new LinkedHashMap<>()).merge(sub, 1, Integer::sum);
        }

        List<String> sortedManagers = new ArrayList<>(managerTotals.keySet());
        sortedManagers.sort((a, b) -> {
            if (a.equals("Aeternum")) return -1;
            if (b.equals("Aeternum")) return 1;
            if (a.equals("Carpet")) return -1;
            if (b.equals("Carpet")) return 1;
            return a.compareToIgnoreCase(b);
        });

        for (String mgr : sortedManagers) {
            int total = managerTotals.get(mgr);
            int mod = managerModified.getOrDefault(mgr, 0);
            boolean expanded = expandedManagers.contains(mgr);
            String mgrLabel = mgr + " (" + total + ")";
            scopes.add(new ScopeItem("MGR:" + mgr, mgrLabel, 0, true, expanded, mgr));
            if (expanded) {
                scopes.add(new ScopeItem("MGR:" + mgr, "Todas (" + total + ")", 1, false, false, mgr));
                scopes.add(new ScopeItem("MGR_MOD:" + mgr, "Modificadas (" + mod + ")", 1, false, false, mgr));
                Map<String, Integer> subs = managerSubs.getOrDefault(mgr, Map.of());
                List<String> sortedSubs = new ArrayList<>(subs.keySet());
                sortedSubs.sort((a, b) -> {
                    if (a.equals("General")) return 1;
                    if (b.equals("General")) return -1;
                    return a.compareToIgnoreCase(b);
                });
                for (String sub : sortedSubs) {
                    scopes.add(new ScopeItem("SUB:" + mgr + "/" + sub, sub + " (" + subs.get(sub) + ")", 1, false, false, mgr));
                }
            }
        }
        return scopes;
    }

    private List<RuleSnapshot> filteredRules() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<RuleSnapshot> out = new ArrayList<>();
        for (RuleSnapshot r : rules) {
            String src = resolveSource(r);
            if ("MODIFIED".equals(selectedScope) && r.isDefault) continue;
            if (selectedScope.startsWith("MGR:") && !src.equals(selectedScope.substring(4))) continue;
            if (selectedScope.startsWith("MGR_MOD:") && (r.isDefault || !src.equals(selectedScope.substring(8)))) continue;
            if (selectedScope.startsWith("SUB:")) {
                String rest = selectedScope.substring(4);
                int slash = rest.indexOf('/');
                if (slash > 0) {
                    String mgr = rest.substring(0, slash);
                    String sub = rest.substring(slash + 1);
                    if (!src.equals(mgr) || !resolveSubfolder(r).equals(sub)) continue;
                }
            }

            if (!q.isEmpty()) {
                String n = r.name != null ? r.name.toLowerCase(Locale.ROOT) : "";
                String dn = r.displayName != null ? r.displayName.toLowerCase(Locale.ROOT) : "";
                String desc = r.description != null ? r.description.toLowerCase(Locale.ROOT) : "";
                if (!n.contains(q) && !dn.contains(q) && !desc.contains(q)) continue;
            }
            out.add(r);
        }
        return out;
    }

    private int sidebarTop() { return panelY + 52; }
    private int sidebarBottom() { return panelY + panelH - 40; }
    private int listTop() { return panelY + 78; }
    private int listBottom() { return editingRule != null ? panelY + panelH - 72 : panelY + panelH - 42; }

    private int maxListScrollPx(int count) {
        int contentH = count * ROW_STRIDE - CARD_GAP;
        int viewH = listBottom() - listTop();
        return Math.max(0, contentH - viewH);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        computePanel(Math.min(760, (int) (width * 0.86)), Math.min(460, (int) (height * 0.84)));
        List<ScopeItem> scopes = buildScopes();
        List<RuleSnapshot> shown = filteredRules();

        int sTop = sidebarTop(), sBottom = sidebarBottom();
        int maxS = Math.max(0, scopes.size() * SIDEBAR_ROW_H - (sBottom - sTop));
        targetSidebarScroll = Math.max(0, Math.min(targetSidebarScroll, maxS));
        sidebarScroll += (targetSidebarScroll - sidebarScroll) * Math.min(1.0, delta * 0.45 + 0.25);

        int lTop = listTop(), lBottom = listBottom();
        int viewH = lBottom - lTop;
        int maxL = maxListScrollPx(shown.size());
        targetListScroll = Math.max(0, Math.min(targetListScroll, maxL));
        listScroll += (targetListScroll - listScroll) * Math.min(1.0, delta * 0.45 + 0.25);
        listScroll = Math.max(0, Math.min(listScroll, maxL));

        renderBackground(c, mx, my, delta);
        long modCount = rules.stream().filter(r -> !r.isDefault).count();
        drawWindowFrame(c, "CARPET RULES", rules.size() + " reglas  ·  " + modCount + " modificadas");
        drawCloseButton(c, mx, my);

        // Sidebar
        c.fill(panelX + 1, panelY + 44, panelX + SIDEBAR_W, panelY + panelH - 36, AeternumTheme.SIDEBAR_BG);
        c.fill(panelX + SIDEBAR_W, panelY + 44, panelX + SIDEBAR_W + 1, panelY + panelH - 36, AeternumTheme.BORDER_SUBTLE);

        c.enableScissor(panelX + 2, sTop, panelX + SIDEBAR_W - 1, sBottom);
        for (int i = 0; i < scopes.size(); i++) {
            ScopeItem item = scopes.get(i);
            int ry = sTop + i * SIDEBAR_ROW_H - (int) Math.round(sidebarScroll);
            if (ry + SIDEBAR_ROW_H < sTop || ry > sBottom) continue;

            boolean isSel = !item.isManager && item.id.equals(selectedScope);
            boolean hover = my >= sTop && my < sBottom && mx >= panelX + 4 && mx < panelX + SIDEBAR_W - 4 && my >= ry && my < ry + SIDEBAR_ROW_H - 2;
            if (isSel) {
                c.fill(panelX + 4, ry, panelX + SIDEBAR_W - 4, ry + SIDEBAR_ROW_H - 2, AeternumTheme.CARD_SELECTED);
                c.fill(panelX + 4, ry, panelX + 7, ry + SIDEBAR_ROW_H - 2, AeternumTheme.ACCENT_GOLD);
            } else if (hover) {
                c.fill(panelX + 4, ry, panelX + SIDEBAR_W - 4, ry + SIDEBAR_ROW_H - 2, AeternumTheme.CARD_HOVER);
            }
            int indent = item.depth * 10;
            if (item.depth > 0) {
                c.fill(panelX + 10, ry, panelX + 11, ry + SIDEBAR_ROW_H - 2, AeternumTheme.BORDER_SUBTLE);
            }
            int textX = panelX + 10 + indent;
            if (item.isManager) {
                String arrow = item.expanded ? "\u25BE" : "\u25B8";
                c.drawText(textRenderer, arrow, textX, ry + 5, AeternumTheme.TEXT_SECONDARY, false);
                textX += 10;
            }
            String lbl = AeternumTheme.ellipsize(textRenderer, item.label, panelX + SIDEBAR_W - 8 - textX);
            c.drawTextWithShadow(textRenderer, lbl, textX, ry + 5,
                isSel ? AeternumTheme.TEXT_PRIMARY : (item.isManager ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY));
        }
        c.disableScissor();

        // Rules list
        int cardX = panelX + SIDEBAR_W + 10;
        int cardW = panelW - SIDEBAR_W - 26;
        RuleSnapshot hoveredRule = null;

        if (shown.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, "No hay reglas que coincidan.", cardX + cardW / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
        } else {
            c.enableScissor(panelX + SIDEBAR_W + 2, lTop, panelX + panelW - 8, lBottom);
            for (int i = 0; i < shown.size(); i++) {
                int cy = lTop + i * ROW_STRIDE - (int) Math.round(listScroll);
                if (cy + CARD_H < lTop || cy > lBottom) continue;

                RuleSnapshot r = shown.get(i);
                boolean inArea = my >= lTop && my < lBottom;
                boolean overCard = inArea && mx >= cardX && mx < cardX + cardW && my >= cy && my < cy + CARD_H;
                if (overCard) hoveredRule = r;

                int bg = !r.isDefault ? AeternumTheme.CARD_SELECTED : (overCard ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG);
                int border = !r.isDefault ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE;
                AeternumTheme.drawCard(c, cardX, cy, cardW, CARD_H, bg, border);

                int resetW = !r.isDefault ? 22 : 0;
                int valW = 84;
                int valX = cardX + cardW - valW - (resetW > 0 ? resetW + 12 : 8);
                int valY = cy + (CARD_H - 20) / 2;

                int titleMaxW = valX - cardX - 20;
                String title = r.name;
                String titleShown = AeternumTheme.ellipsize(textRenderer, title, titleMaxW);
                c.drawTextWithShadow(textRenderer, titleShown, cardX + 10, cy + 7, AeternumTheme.TEXT_PRIMARY);

                if ("ALL".equals(selectedScope) || "MODIFIED".equals(selectedScope)) {
                    String sideTag = resolveSource(r);
                    if (!sideTag.isEmpty()) {
                        int techX = cardX + 16 + textRenderer.getWidth(titleShown);
                        int techAvail = valX - techX - 10;
                        if (techAvail > 24) {
                            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, sideTag, techAvail), techX, cy + 8, AeternumTheme.TEXT_MUTED, false);
                        }
                    }
                }

                String desc = r.description != null ? r.description : "";
                c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, desc, valX - cardX - 18), cardX + 10, cy + 25, AeternumTheme.TEXT_SECONDARY, false);

                // Value control
                boolean overVal = inArea && mx >= valX && mx < valX + valW && my >= valY && my < valY + 20;
                if (r.isBoolean) {
                    boolean isTrue = "true".equalsIgnoreCase(r.value);
                    int bBg = overVal ? (isTrue ? 0xFF3A3A46 : 0xFF24242C) : (isTrue ? 0xFF2B2B35 : 0xFF15151A);
                    int bBorder = isTrue ? 0xFFFFFFFF : AeternumTheme.BORDER_SUBTLE;
                    AeternumTheme.drawCard(c, valX, valY, valW, 20, bBg, bBorder);
                    String lbl = isTrue ? "TRUE" : "FALSE";
                    c.drawTextWithShadow(textRenderer, lbl, valX + (valW - textRenderer.getWidth(lbl)) / 2, valY + 6,
                        isTrue ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_MUTED);
                } else {
                    int bBg = overVal ? 0xFF282832 : 0xFF18181E;
                    int bBorder = overVal ? 0xFFFFFFFF : AeternumTheme.BORDER_BRIGHT;
                    AeternumTheme.drawCard(c, valX, valY, valW, 20, bBg, bBorder);
                    String valTxt = AeternumTheme.ellipsize(textRenderer, r.value, valW - 10);
                    c.drawTextWithShadow(textRenderer, valTxt, valX + (valW - textRenderer.getWidth(valTxt)) / 2, valY + 6, AeternumTheme.TEXT_PRIMARY);
                }

                // Reset to default button
                if (!r.isDefault) {
                    int rx = cardX + cardW - 28;
                    boolean overReset = inArea && mx >= rx && mx < rx + 20 && my >= valY && my < valY + 20;
                    AeternumTheme.drawCard(c, rx, valY, 20, 20, overReset ? 0xFF34343F : 0xFF1C1C24, AeternumTheme.BORDER_BRIGHT);
                    c.drawTextWithShadow(textRenderer, "\u21BA", rx + 7, valY + 6, AeternumTheme.TEXT_PRIMARY);
                }
            }
            c.disableScissor();

            AeternumTheme.drawScrollbar(c, panelX + panelW - 12, lTop, lBottom,
                shown.size() * ROW_STRIDE - CARD_GAP, viewH, listScroll, draggingScrollbar);
        }

        if (editingRule != null && valueField != null) {
            int barY = panelY + panelH - 64;
            int contentW = panelW - SIDEBAR_W - 22;
            valueField.renderBox(c, cardX, barY, Math.max(120, contentW - 134), 20, mx, my, delta);
        }

        AeternumTheme.drawFooter(c, panelX, panelY + panelH - 36, panelW, panelY + panelH);
        if (!statusMessage.isEmpty()) {
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, statusMessage, panelW - 28),
                panelX + 14, panelY + panelH - 22,
                statusError ? AeternumTheme.TEXT_SECONDARY : AeternumTheme.TEXT_PRIMARY, false);
        } else {
            c.drawText(textRenderer, "Clic en una regla booleana o valor para editar · \u21BA restaura default",
                panelX + 14, panelY + panelH - 22, AeternumTheme.TEXT_MUTED, false);
        }

        if (searchField != null) {
            searchField.renderBox(c, cardX, panelY + 52, panelW - SIDEBAR_W - 22, 20, mx, my, delta);
        }
        super.render(c, mx, my, delta);

        // Tooltips
        if (isCloseButtonHit(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Cerrar"), mx, my);
        } else if (defaultToggleButton != null && defaultToggleButton.isMouseOver(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal(saveDefault ? "Los cambios también se guardan en carpet.conf" : "Los cambios aplican solo hasta reiniciar el servidor"), mx, my);
        } else if (hoveredRule != null) {
            int resetW = !hoveredRule.isDefault ? 22 : 0;
            int valW = 84;
            int valX = cardX + cardW - valW - (resetW > 0 ? resetW + 12 : 8);
            int rx = cardX + cardW - 28;
            if (!hoveredRule.isDefault && mx >= rx && mx < rx + 20) {
                c.drawTooltip(textRenderer, Text.literal("Restaurar valor por defecto (" + hoveredRule.defaultValue + ")"), mx, my);
            } else if (mx >= valX && mx < valX + valW) {
                String hint = hoveredRule.isBoolean ? "Clic para alternar"
                    : (!hoveredRule.suggestions.isEmpty() && hoveredRule.suggestions.size() <= 6 && hoveredRule.strict
                        ? "Clic para rotar opciones"
                        : "Clic para editar valor");
                c.drawTooltip(textRenderer, Text.literal(hint), mx, my);
            } else if (hoveredRule.description != null && !hoveredRule.description.isEmpty()) {
                List<Text> tip = new ArrayList<>();
                tip.add(Text.literal(hoveredRule.name + " (" + hoveredRule.managerId + ")"));
                tip.add(Text.literal(hoveredRule.description));
                tip.add(Text.literal("Default: " + hoveredRule.defaultValue));
                c.drawTooltip(textRenderer, tip, mx, my);
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

        int sTop = sidebarTop(), sBottom = sidebarBottom();
        if (mx >= panelX + 2 && mx < panelX + SIDEBAR_W && my >= sTop && my < sBottom) {
            List<ScopeItem> scopes = buildScopes();
            int row = (int) ((my - sTop + sidebarScroll) / SIDEBAR_ROW_H);
            if (row >= 0 && row < scopes.size()) {
                ScopeItem clicked = scopes.get(row);
                if (clicked.isManager) {
                    if (mx < panelX + 22 || clicked.id.equals(selectedScope)) {
                        if (!expandedManagers.remove(clicked.managerId)) {
                            expandedManagers.add(clicked.managerId);
                        }
                    } else {
                        expandedManagers.add(clicked.managerId);
                    }
                }
                selectedScope = clicked.id;
                targetListScroll = 0;
                listScroll = 0;
                return true;
            }
        }

        List<RuleSnapshot> shown = filteredRules();
        int lTop = listTop(), lBottom = listBottom(), viewH = lBottom - lTop;
        int maxL = maxListScrollPx(shown.size());
        if (maxL > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
            draggingScrollbar = true;
            updateListScrollFromMouse(my, lTop, viewH, maxL, shown.size());
            return true;
        }

        int cardX = panelX + SIDEBAR_W + 10;
        int cardW = panelW - SIDEBAR_W - 26;
        if (my >= lTop && my < lBottom && mx >= cardX && mx < cardX + cardW) {
            int relY = (int) (my - lTop + listScroll);
            int idx = relY / ROW_STRIDE;
            if (idx >= 0 && idx < shown.size() && (relY % ROW_STRIDE) < CARD_H) {
                RuleSnapshot r = shown.get(idx);
                int cy = lTop + idx * ROW_STRIDE - (int) Math.round(listScroll);
                int resetW = !r.isDefault ? 22 : 0;
                int valW = 84;
                int valX = cardX + cardW - valW - (resetW > 0 ? resetW + 12 : 8);
                int valY = cy + (CARD_H - 20) / 2;
                int rx = cardX + cardW - 28;

                if (!r.isDefault && mx >= rx && mx < rx + 20 && my >= valY && my < valY + 20) {
                    ClientPlayNetworking.send(new CarpetRuleSetPayload(r.managerId, r.name, r.defaultValue, saveDefault, true));
                    return true;
                }
                if (mx >= valX && mx < valX + valW && my >= valY && my < valY + 20) {
                    handleRuleValueClick(r);
                    return true;
                }
            }
        }
        return false;
    }

    private void handleRuleValueClick(RuleSnapshot r) {
        if (r.isBoolean) {
            String next = "true".equalsIgnoreCase(r.value) ? "false" : "true";
            ClientPlayNetworking.send(new CarpetRuleSetPayload(r.managerId, r.name, next, saveDefault, false));
        } else if (!r.suggestions.isEmpty() && r.suggestions.size() <= 6 && r.strict) {
            int curIdx = r.suggestions.indexOf(r.value);
            String next = r.suggestions.get((curIdx + 1) % r.suggestions.size());
            ClientPlayNetworking.send(new CarpetRuleSetPayload(r.managerId, r.name, next, saveDefault, false));
        } else {
            openInlineEdit(r);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingRule != null && (keyCode == 257 || keyCode == 335)) { // Enter / Numpad Enter
            submitInlineEdit();
            return true;
        }
        if (editingRule != null && keyCode == 256) { // Escape closes inline edit first
            closeInlineEdit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v == 0) return super.mouseScrolled(mx, my, h, v);
        if (mx >= panelX && mx < panelX + SIDEBAR_W && my >= sidebarTop() && my < sidebarBottom()) {
            int max = Math.max(0, buildScopes().size() * SIDEBAR_ROW_H - (sidebarBottom() - sidebarTop()));
            targetSidebarScroll = Math.max(0, Math.min(max, targetSidebarScroll - v * 20));
            return true;
        }
        if (mx >= panelX + SIDEBAR_W && mx < panelX + panelW && my >= listTop() && my < listBottom()) {
            int max = maxListScrollPx(filteredRules().size());
            targetListScroll = Math.max(0, Math.min(max, targetListScroll - v * 28));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            List<RuleSnapshot> shown = filteredRules();
            updateListScrollFromMouse(my, listTop(), listBottom() - listTop(), maxListScrollPx(shown.size()), shown.size());
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

    private void updateListScrollFromMouse(double my, int top, int viewH, int maxScroll, int count) {
        if (maxScroll == 0) return;
        int totalH = count * ROW_STRIDE - CARD_GAP;
        int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
        double frac = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
        targetListScroll = Math.max(0, Math.min(1, frac)) * maxScroll;
        listScroll = targetListScroll;
    }

    private record ScopeItem(String id, String label, int depth, boolean isManager, boolean expanded, String managerId) {}

    private static List<RuleSnapshot> decode(String json) {
        List<RuleSnapshot> res = GSON.fromJson(json, TYPE);
        return res == null ? new ArrayList<>() : res;
    }
}
