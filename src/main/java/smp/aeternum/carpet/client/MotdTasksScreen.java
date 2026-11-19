package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.motd.MotdMarkup;
import smp.aeternum.carpet.network.MotdTasksSavePayload;

public final class MotdTasksScreen extends UiScreen {
    private static final Gson GSON = new Gson();
    private static final int MAX_TASKS = 64;
    private static final int ROW_H = 42;
    private static final int LIST_TOP_OFFSET = 58;
    private static final int FOOTER_HEIGHT = 40;

    private final Screen parent;
    private final boolean canEdit;
    private final List<String> tasks = new ArrayList<>();
    private final List<UiTextField> fields = new ArrayList<>();
    private UiButton addButton;
    private UiButton saveButton;
    private UiButton syntaxButton;
    private int scroll;
    private boolean draggingScrollbar;
    private boolean dirty;
    private boolean showSyntax;
    private String feedback = "";
    private int feedbackColor = AeternumTheme.ACCENT_GREEN;

    public MotdTasksScreen(Screen parent, List<String> initialTasks, boolean canEdit) {
        super(Text.literal("Tareas del MOTD"));
        this.parent = parent;
        this.canEdit = canEdit;
        if (initialTasks != null) {
            for (String task : initialTasks) {
                if (task != null && !task.isBlank() && tasks.size() < MAX_TASKS) {
                    tasks.add(task.strip());
                }
            }
        }
    }

    public void syncFromServer(List<String> updatedTasks) {
        if (dirty) return;
        tasks.clear();
        if (updatedTasks != null) {
            for (String task : updatedTasks) {
                if (task != null && !task.isBlank() && tasks.size() < MAX_TASKS) {
                    tasks.add(task.strip());
                }
            }
        }
        init();
    }

    @Override
    protected void init() {
        clearChildren();
        fields.clear();
        computePanel(Math.min(640, (int) (width * 0.80)), Math.min(440, (int) (height * 0.82)));

        syntaxButton = UiButton.ghost(panelX + panelW - 56, panelY + 10, 22, 20, "?", b -> {
            commit();
            showSyntax = !showSyntax;
        });
        syntaxButton.setTooltip(Tooltip.of(Text.literal("Ver códigos de color y formato")));
        addDrawableChild(syntaxButton);

        int visibleRows = visibleRows();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        int listTop = panelY + LIST_TOP_OFFSET;
        int fieldW = panelW - 156;

        for (int i = 0; i < visibleRows && i + scroll < tasks.size(); i++) {
            int index = i + scroll;
            int y = listTop + i * ROW_H;

            UiTextField field = new UiTextField(textRenderer, panelX + 38, y + 2, fieldW, 19, "Texto de la tarea (usa \\gold, \\bold, etc.)");
            field.setMaxLength(180);
            field.setText(tasks.get(index));
            field.setEditable(canEdit);
            if (canEdit) {
                field.setChangedListener(value -> {
                    if (index < tasks.size()) {
                        tasks.set(index, value);
                        dirty = true;
                        feedback = "";
                    }
                });
            }
            addDrawableChild(field);
            fields.add(field);

            int btnX = panelX + 38 + fieldW + 6;
            UiButton up = UiButton.ghost(btnX, y + 2, 20, 19, "\u25B2", b -> {
                commit();
                if (index > 0) {
                    String moved = tasks.remove(index);
                    tasks.add(index - 1, moved);
                    dirty = true;
                    feedback = "";
                    if (index - 1 < scroll) scroll = index - 1;
                    init();
                }
            });
            up.active = canEdit && index > 0;
            up.setTooltip(Tooltip.of(Text.literal("Subir")));
            addDrawableChild(up);

            UiButton down = UiButton.ghost(btnX + 23, y + 2, 20, 19, "\u25BC", b -> {
                commit();
                if (index + 1 < tasks.size()) {
                    String moved = tasks.remove(index);
                    tasks.add(index + 1, moved);
                    dirty = true;
                    feedback = "";
                    if (index + 1 >= scroll + visibleRows()) scroll = Math.min(maxScroll(), scroll + 1);
                    init();
                }
            });
            down.active = canEdit && index + 1 < tasks.size();
            down.setTooltip(Tooltip.of(Text.literal("Bajar")));
            addDrawableChild(down);

            UiButton remove = UiButton.danger(btnX + 46, y + 2, 22, 19, "\u00D7", b -> {
                commit();
                if (index < tasks.size()) {
                    tasks.remove(index);
                    dirty = true;
                    feedback = "";
                    init();
                }
            });
            remove.active = canEdit;
            remove.setTooltip(Tooltip.of(Text.literal("Eliminar tarea")));
            addDrawableChild(remove);
        }

        int footerY = panelY + panelH - 28;
        addButton = UiButton.secondary(panelX + 14, footerY, 114, 20, "+ Nueva tarea", b -> {
            commit();
            if (tasks.size() < MAX_TASKS) {
                tasks.add("");
                dirty = true;
                feedback = "";
                scroll = maxScroll();
                init();
            }
        });
        addButton.active = canEdit && tasks.size() < MAX_TASKS;
        addDrawableChild(addButton);

        saveButton = UiButton.primary(panelX + panelW - 96, footerY, 82, 20, "Guardar", b -> saveTasks());
        saveButton.active = canEdit && dirty;
        addDrawableChild(saveButton);
    }

    private void saveTasks() {
        commit();
        List<String> cleaned = new ArrayList<>();
        for (String task : tasks) {
            String stripped = task == null ? "" : task.strip();
            if (!stripped.isEmpty()) cleaned.add(stripped);
            if (cleaned.size() >= MAX_TASKS) break;
        }
        tasks.clear();
        tasks.addAll(cleaned);
        ClientPlayNetworking.send(new MotdTasksSavePayload(GSON.toJson(cleaned)));
        dirty = false;
        feedback = "Guardado y sincronizado.";
        feedbackColor = AeternumTheme.ACCENT_GREEN;
        init();
    }

    private void commit() {
        int visible = Math.min(fields.size(), tasks.size() - scroll);
        for (int i = 0; i < visible; i++) {
            tasks.set(scroll + i, fields.get(i).getText());
        }
    }

    private int visibleRows() {
        return Math.max(2, (panelH - LIST_TOP_OFFSET - FOOTER_HEIGHT - 14) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, tasks.size() - visibleRows());
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        computePanel(Math.min(640, (int) (width * 0.80)), Math.min(440, (int) (height * 0.82)));
        if (addButton != null) addButton.active = canEdit && tasks.size() < MAX_TASKS;
        if (saveButton != null) saveButton.active = canEdit && dirty;

        renderBackground(c, mx, my, d);
        drawWindowFrame(c, "Tareas del MOTD", "Se muestran en el banner de bienvenida y el overlay de Alt  ·  Top 3 visibles arriba");
        drawCloseButton(c, mx, my);

        int listTop = panelY + LIST_TOP_OFFSET;
        int visibleRows = visibleRows();
        int listBottom = listTop + visibleRows * ROW_H;
        int fieldW = panelW - 156;

        if (tasks.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, "No hay tareas configuradas.", width / 2, listTop + 34, AeternumTheme.TEXT_MUTED);
            c.drawCenteredTextWithShadow(textRenderer, "Pulsa '+ Nueva tarea' para añadir la primera.", width / 2, listTop + 48, AeternumTheme.TEXT_SECONDARY);
        } else {
            for (int i = 0; i < fields.size(); i++) {
                int index = i + scroll;
                int y = listTop + i * ROW_H;
                boolean topHighlighted = index < 3;
                int numColor = topHighlighted ? AeternumTheme.ACCENT_GOLD : AeternumTheme.TEXT_MUTED;
                c.drawText(textRenderer, "[" + (index + 1) + "]", panelX + 14, y + 7, numColor, false);

                fields.get(i).renderBox(c, panelX + 38, y + 2, fieldW, 19, mx, my, d);

                String raw = fields.get(i).getText().strip();
                c.drawText(textRenderer, "Vista:", panelX + 38, y + 25, AeternumTheme.TEXT_MUTED, false);
                if (raw.isEmpty()) {
                    c.drawText(textRenderer, "(vacía — se omitirá al guardar)", panelX + 74, y + 25, AeternumTheme.TEXT_MUTED, false);
                } else {
                    List<OrderedText> preview = textRenderer.wrapLines(MotdMarkup.parse(raw), panelW - 102);
                    if (!preview.isEmpty()) {
                        c.drawTextWithShadow(textRenderer, preview.get(0), panelX + 74, y + 25, AeternumTheme.TEXT_PRIMARY);
                    }
                }
                if (i + 1 < fields.size()) {
                    c.fill(panelX + 14, y + ROW_H - 3, panelX + panelW - 18, y + ROW_H - 2, AeternumTheme.BORDER_SUBTLE);
                }
            }
            if (maxScroll() > 0) {
                AeternumTheme.drawScrollbar(c, panelX + panelW - 12, listTop, listBottom,
                    tasks.size(), visibleRows, scroll, draggingScrollbar);
            }
        }

        AeternumTheme.drawFooter(c, panelX, panelY + panelH - FOOTER_HEIGHT, panelW, panelY + panelH);
        String countTxt = tasks.size() + " / " + MAX_TASKS + " tareas";
        c.drawText(textRenderer, countTxt, panelX + 136, panelY + panelH - 22, AeternumTheme.TEXT_SECONDARY, false);
        if (!feedback.isEmpty()) {
            c.drawText(textRenderer, feedback, panelX + 14, panelY + panelH - FOOTER_HEIGHT - 12, feedbackColor, false);
        }

        super.render(c, mx, my, d);

        if (showSyntax) {
            drawSyntaxPopover(c);
        }
        if (isCloseButtonHit(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Cerrar"), mx, my);
        }
    }

    private void drawSyntaxPopover(DrawContext c) {
        int pw = Math.min(340, panelW - 28);
        int ph = 112;
        int px = panelX + panelW - pw - 14;
        int py = panelY + 38;
        c.fill(px - 2, py + 2, px + pw + 2, py + ph + 3, 0x60000000);
        c.fill(px, py, px + pw, py + ph, 0xF5121216);
        c.drawBorder(px, py, pw, ph, AeternumTheme.BORDER_BRIGHT);
        c.drawTextWithShadow(textRenderer, "Formato soportado (MotdMarkup)", px + 10, py + 8, AeternumTheme.ACCENT_GOLD);
        c.drawText(textRenderer, "Colores: \\red \\gold \\yellow \\green \\aqua \\blue", px + 10, py + 24, AeternumTheme.TEXT_PRIMARY, false);
        c.drawText(textRenderer, "         \\light_purple \\dark_aqua \\gray \\white", px + 10, py + 36, AeternumTheme.TEXT_PRIMARY, false);
        c.drawText(textRenderer, "Estilos: \\bold \\italic \\underline \\strikethrough", px + 10, py + 52, AeternumTheme.TEXT_SECONDARY, false);
        c.drawText(textRenderer, "Reset:   \\reset", px + 10, py + 66, AeternumTheme.TEXT_SECONDARY, false);
        c.drawTextWithShadow(textRenderer, MotdMarkup.parse("\\gold\\boldEjemplo\\reset: \\aquaGranja activa"), px + 10, py + 86, AeternumTheme.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && isCloseButtonHit(mx, my)) {
            close();
            return true;
        }
        int listTop = panelY + LIST_TOP_OFFSET;
        int listBottom = listTop + visibleRows() * ROW_H;
        if (button == 0 && maxScroll() > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= listTop && my <= listBottom) {
            commit();
            draggingScrollbar = true;
            updateScroll(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int listTop = panelY + LIST_TOP_OFFSET;
        int listBottom = listTop + visibleRows() * ROW_H;
        if (mx >= panelX + 14 && mx < panelX + panelW - 10 && my >= listTop && my < listBottom && maxScroll() > 0) {
            commit();
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(v)));
            init();
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            updateScroll(my);
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

    private void updateScroll(double mouseY) {
        int max = maxScroll();
        if (max == 0) return;
        int listTop = panelY + LIST_TOP_OFFSET;
        int visible = visibleRows();
        int listBottom = listTop + visible * ROW_H;
        int track = listBottom - listTop;
        int barH = Math.max(22, track * visible / tasks.size());
        double fraction = (mouseY - listTop - barH / 2.0) / Math.max(1.0, track - barH);
        scroll = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * max);
        init();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
