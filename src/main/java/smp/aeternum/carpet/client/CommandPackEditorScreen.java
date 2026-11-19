package smp.aeternum.carpet.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import smp.aeternum.carpet.actions.CommandPack;
import smp.aeternum.carpet.actions.CommandStep;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;

/** The second screen works as both an OP editor and a safe read-only runner. */
final class CommandPackEditorScreen extends UiScreen {
    private static final int STEP_ROW_H = 24;

    private final Screen parent;
    private final List<CommandPack> packs;
    private final CommandPack pack;
    private final boolean canEdit;
    private UiTextField name, description;
    private final List<UiTextField> stepFields = new ArrayList<>();
    private UiButton deleteButton, saveButton;
    private String runFeedback = "";
    private int feedbackColor = AeternumTheme.ACCENT_GREEN;
    private String savedName, savedDescription;
    private List<CommandStep> savedCommands;
    private int commandScroll;
    private boolean draggingCommandScrollbar;
    private boolean dirty;

    CommandPackEditorScreen(Screen parent, List<CommandPack> packs, CommandPack pack, boolean canEdit) {
        super(Text.literal("Editar paquete"));
        this.parent = parent;
        this.packs = packs;
        this.pack = pack;
        this.canEdit = canEdit;
        captureSavedState();
    }

    @Override
    protected void init() {
        clearChildren();
        stepFields.clear();
        computePanel(Math.min(640, (int) (width * 0.76)), Math.min(430, (int) (height * 0.80)));

        addDrawableChild(UiButton.ghost(panelX + 10, panelY + 10, 24, 24, "\u2190", b -> close()));

        int fieldW = panelW - 28;
        name = field(panelX + 14, panelY + 62, fieldW, pack.name, 80, "Nombre del paquete");
        description = field(panelX + 14, panelY + 98, fieldW, pack.description, 240, "Descripción opcional");

        int visibleRows = visibleStepRows();
        commandScroll = Math.max(0, Math.min(commandScroll, maxCommandScroll()));
        int stepListTop = panelY + 142;

        for (int i = 0; i < visibleRows && i + commandScroll < pack.commands.size(); i++) {
            int index = i + commandScroll;
            int y = stepListTop + i * STEP_ROW_H;
            CommandStep step = pack.commands.get(index);

            boolean isCmd = step.type == CommandStep.Type.COMMAND;
            UiButton type = UiButton.secondary(panelX + 14, y, 64, 19, isCmd ? "CMD" : "ESPERA", b -> {
                commit();
                CommandStep current = pack.commands.get(index);
                current.type = current.type == CommandStep.Type.COMMAND ? CommandStep.Type.WAIT : CommandStep.Type.COMMAND;
                current.value = current.type == CommandStep.Type.WAIT ? "1" : "say ";
                dirty = true;
                init();
            });
            type.setCustomAccent(isCmd ? AeternumTheme.ACCENT_CYAN : AeternumTheme.ACCENT_GOLD);
            type.setVariant(UiButton.Variant.PRIMARY);
            type.setTooltip(Tooltip.of(Text.literal("Alternar entre comando y espera")));
            type.active = canEdit;
            addDrawableChild(type);

            int inputW = panelW - 134;
            if (isCmd) {
                stepFields.add(field(panelX + 84, y, inputW, step.value, 2048, "Comando de servidor"));
            } else {
                stepFields.add(field(panelX + 84, y, inputW, step.value, 8, "Segundos de espera"));
            }

            UiButton remove = UiButton.danger(panelX + panelW - 44, y, 24, 19, "\u00D7", b -> {
                commit();
                if (index < pack.commands.size()) {
                    pack.commands.remove(index);
                    dirty = true;
                }
                init();
            });
            remove.setTooltip(Tooltip.of(Text.literal("Eliminar esta línea")));
            remove.active = canEdit;
            addDrawableChild(remove);
        }

        int footerY = panelY + panelH - 28;
        UiButton newLine = UiButton.secondary(panelX + 14, footerY, 108, 20, "+ Nueva línea", b -> {
            commit();
            pack.commands.add(new CommandStep(CommandStep.Type.COMMAND, "say "));
            dirty = true;
            commandScroll = maxCommandScroll();
            init();
        });
        newLine.active = canEdit;
        addDrawableChild(newLine);

        deleteButton = UiButton.danger(panelX + panelW - 260, footerY, 72, 20, "Eliminar", b -> {
            if (!canEdit || !hasShiftDown()) return;
            packs.remove(pack);
            sortAndSave();
            client.setScreen(parent);
        });
        deleteButton.active = false;
        addDrawableChild(deleteButton);

        addDrawableChild(UiButton.success(panelX + panelW - 182, footerY, 80, 20, "Ejecutar", b -> {
            commit();
            CommandPacksRequest.run(pack);
            runFeedback = "Ejecutando paquete…";
            feedbackColor = AeternumTheme.ACCENT_GREEN;
        }));

        saveButton = UiButton.primary(panelX + panelW - 96, footerY, 82, 20, "Guardar", b -> {
            if (!hasUniqueName(name.getText())) return;
            commit();
            sortAndSave();
            captureSavedState();
            dirty = false;
            runFeedback = "Guardado.";
            feedbackColor = AeternumTheme.ACCENT_GREEN;
        });
        saveButton.active = false;
        addDrawableChild(saveButton);
    }

    private int visibleStepRows() {
        return Math.max(3, (panelH - 200) / STEP_ROW_H);
    }

    private UiTextField field(int x, int y, int w, String v, int max, String hint) {
        UiTextField f = new UiTextField(textRenderer, x, y, w, 19, hint);
        f.setMaxLength(max);
        f.setText(v == null ? "" : v);
        f.setEditable(canEdit);
        if (canEdit) f.setChangedListener(value -> dirty = true);
        addDrawableChild(f);
        return f;
    }

    private void commit() {
        if (name == null) return;
        pack.name = name.getText().trim();
        pack.description = description.getText().trim();
        int visible = Math.min(stepFields.size(), pack.commands.size() - commandScroll);
        for (int i = 0; i < visible; i++) {
            pack.commands.get(commandScroll + i).value = stepFields.get(i).getText().trim();
        }
    }

    private void sortAndSave() {
        packs.sort(Comparator.comparing(p -> p.name == null ? "" : p.name, String.CASE_INSENSITIVE_ORDER));
        CommandPacksRequest.save(packs);
    }

    private boolean hasUniqueName(String proposed) {
        String candidate = proposed == null ? "" : proposed.strip();
        if (candidate.isEmpty()) {
            runFeedback = "El paquete necesita un nombre.";
            feedbackColor = AeternumTheme.ACCENT_RED;
            return false;
        }
        String key = candidate.toLowerCase(Locale.ROOT);
        for (CommandPack other : packs) {
            if (other != pack && other.name != null && other.name.strip().toLowerCase(Locale.ROOT).equals(key)) {
                runFeedback = "Ya existe un paquete llamado '" + other.name + "'.";
                feedbackColor = AeternumTheme.ACCENT_RED;
                return false;
            }
        }
        return true;
    }

    private void captureSavedState() {
        savedName = pack.name;
        savedDescription = pack.description;
        savedCommands = copySteps(pack.commands);
    }

    private void restoreSavedState() {
        pack.name = savedName;
        pack.description = savedDescription;
        pack.commands.clear();
        pack.commands.addAll(copySteps(savedCommands));
    }

    private static List<CommandStep> copySteps(List<CommandStep> source) {
        List<CommandStep> copy = new ArrayList<>();
        if (source != null) {
            for (CommandStep step : source) {
                if (step != null) copy.add(new CommandStep(step.type, step.value));
            }
        }
        return copy;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        computePanel(Math.min(640, (int) (width * 0.76)), Math.min(430, (int) (height * 0.80)));
        if (deleteButton != null) deleteButton.active = canEdit && hasShiftDown();
        if (saveButton != null) saveButton.active = canEdit && dirty;

        renderBackground(c, mx, my, d);
        AeternumTheme.drawPanel(c, panelX, panelY, panelW, panelH);
        AeternumTheme.drawHeader(c, panelX, panelY, panelW, 44);
        c.fill(panelX + 1, panelY + 1, panelX + 4, panelY + 43, AeternumTheme.ACCENT_GOLD);

        String titleTxt = (pack.name == null || pack.name.isBlank()) ? "NUEVO PAQUETE" : pack.name;
        c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, titleTxt, panelW - 180), panelX + 38, panelY + 10, AeternumTheme.TEXT_PRIMARY);
        c.drawText(textRenderer, canEdit ? "Modo edición" : "Solo lectura", panelX + 38, panelY + 25,
            canEdit ? AeternumTheme.ACCENT_CYAN : AeternumTheme.ACCENT_GOLD, false);
        drawCloseButton(c, mx, my);

        c.drawText(textRenderer, "NOMBRE", panelX + 14, panelY + 51, AeternumTheme.TEXT_MUTED, false);
        c.drawText(textRenderer, "DESCRIPCIÓN", panelX + 14, panelY + 87, AeternumTheme.TEXT_MUTED, false);
        c.drawText(textRenderer, "PASOS  ·  CMD se ejecuta como servidor  ·  ESPERA usa segundos", panelX + 14, panelY + 128, AeternumTheme.TEXT_SECONDARY, false);

        int fieldW = panelW - 28;
        if (name != null) name.renderBox(c, panelX + 14, panelY + 62, fieldW, 19, mx, my, d);
        if (description != null) description.renderBox(c, panelX + 14, panelY + 98, fieldW, 19, mx, my, d);

        int stepListTop = panelY + 142;
        int visibleRows = visibleStepRows();
        int stepListBottom = stepListTop + visibleRows * STEP_ROW_H;
        int inputW = panelW - 134;

        if (pack.commands.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, "Añade una línea; usa CMD para alternar a espera.", width / 2, stepListTop + 40, AeternumTheme.TEXT_MUTED);
        } else {
            for (int i = 0; i < stepFields.size(); i++) {
                int y = stepListTop + i * STEP_ROW_H;
                stepFields.get(i).renderBox(c, panelX + 84, y, inputW, 19, mx, my, d);
            }
            if (maxCommandScroll() > 0) {
                AeternumTheme.drawScrollbar(c, panelX + panelW - 12, stepListTop, stepListBottom,
                    pack.commands.size(), visibleRows, commandScroll, draggingCommandScrollbar);
            }
        }

        AeternumTheme.drawFooter(c, panelX, panelY + panelH - 36, panelW, panelY + panelH);
        if (!runFeedback.isEmpty()) {
            c.drawText(textRenderer, runFeedback, panelX + 14, panelY + panelH - 50, feedbackColor, false);
        }

        super.render(c, mx, my, d);

        if (isCloseButtonHit(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Cerrar"), mx, my);
        } else if (deleteButton != null && deleteButton.isMouseOver(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal(!canEdit ? "Solo OP" : (hasShiftDown() ? "Clic para eliminar este paquete" : "Mantén Shift para habilitar Eliminar")), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && isCloseButtonHit(mx, my)) {
            close();
            return true;
        }
        int stepListTop = panelY + 142;
        int stepListBottom = stepListTop + visibleStepRows() * STEP_ROW_H;
        if (button == 0 && maxCommandScroll() > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= stepListTop && my <= stepListBottom) {
            commit();
            draggingCommandScrollbar = true;
            updateCommandScroll(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int stepListTop = panelY + 142;
        int stepListBottom = stepListTop + visibleStepRows() * STEP_ROW_H;
        if (mx >= panelX + 14 && mx < panelX + panelW - 10 && my >= stepListTop && my < stepListBottom && maxCommandScroll() > 0) {
            commit();
            commandScroll = Math.max(0, Math.min(maxCommandScroll(), commandScroll - (int) Math.signum(v)));
            init();
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingCommandScrollbar && button == 0) {
            updateCommandScroll(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingCommandScrollbar) {
            draggingCommandScrollbar = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private int maxCommandScroll() {
        return Math.max(0, pack.commands.size() - visibleStepRows());
    }

    private void updateCommandScroll(double mouseY) {
        int max = maxCommandScroll();
        if (max == 0) return;
        int listTop = panelY + 142;
        int visibleRows = visibleStepRows();
        int listBottom = listTop + visibleRows * STEP_ROW_H;
        int track = listBottom - listTop;
        int barH = Math.max(22, track * visibleRows / pack.commands.size());
        double fraction = (mouseY - listTop - barH / 2.0) / Math.max(1.0, track - barH);
        commandScroll = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * max);
        init();
    }

    @Override
    public void close() {
        if (canEdit) restoreSavedState();
        client.setScreen(parent);
    }
}
