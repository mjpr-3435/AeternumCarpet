package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import smp.aeternum.carpet.guides.GuideDocument;
import smp.aeternum.carpet.motd.MotdMarkup;

/** Personal formatted-text library. The guide markup is the same as the join MOTD. */
public final class GuideScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<List<GuideDocument>>() {}.getType();
    private List<GuideDocument> guides;
    private boolean canEdit, editing, helpOpen, dirty;
    private int selected, scroll, readerScroll; private boolean draggingReaderScrollbar;
    private EditBoxWidget editor;
    private TextFieldWidget titleField;
    private ButtonWidget renameButton, refreshButton;
    private long saveAt, refreshEnableAt;

    public GuideScreen(String json, boolean canEdit) {
        super(Text.literal("Guias"));
        guides = decode(json);
        this.canEdit = canEdit;
    }

    public void replaceData(String json, boolean canEdit) {
        if (editing && dirty) return;
        guides = decode(json);
        this.canEdit = canEdit;
        selected = Math.min(selected, Math.max(0, guides.size() - 1));
    }

    @Override protected void init() { build(); }

    private void build() {
        clearChildren(); editor = null; titleField = null; renameButton = null; refreshButton = null;
        int l = width / 2 - 260, t = height / 2 - 170;
        if (editing && canEdit) {
            if (current() != null) {
                titleField = new TextFieldWidget(textRenderer, l + 126, t + 42, 348, 18, Text.literal("Nombre de archivo"));
                titleField.setText(current().title); titleField.setMaxLength(64); addDrawableChild(titleField);
                editor = EditBoxWidget.builder().x(l + 126).y(t + 68).hasOverlay(false)
                    .build(textRenderer, 348, 230, Text.literal("Texto de guia"));
                editor.setText(current().markdown); editor.setMaxLength(100_000);
                editor.setChangeListener(v -> { dirty = true; saveAt = System.currentTimeMillis() + 700; });
                addDrawableChild(editor);
                renameButton = ButtonWidget.builder(Text.literal("\u2713"), b -> {
                    if (!titleField.getText().trim().equals(current().title))
                        GuideRequest.send("rename", current().title, titleField.getText().trim());
                }).dimensions(l + 36, t + 302, 20, 20).build();
                renameButton.active = false; addDrawableChild(renameButton);
                addDrawableChild(ButtonWidget.builder(Text.literal("?"), b -> helpOpen = !helpOpen)
                    .dimensions(l + 60, t + 302, 20, 20).build());
                ButtonWidget delete = ButtonWidget.builder(Text.literal("\u00D7"), b -> {
                    if (hasShiftDown()) GuideRequest.send("delete", current().title, "");
                }).dimensions(l + 84, t + 302, 20, 20).build();
                delete.active = false; addDrawableChild(delete);
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> GuideRequest.send("create", "Guia nueva", ""))
                .dimensions(l + 12, t + 302, 20, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Guias"), b -> {
                editing = false; helpOpen = false; build();
            }).dimensions(l + 400, t + 302, 74, 20).build());
        } else if (canEdit) {
            refreshButton = ButtonWidget.builder(Text.literal("Refrescar"), b -> {
                b.active = false; b.setFocused(false); refreshEnableAt = System.currentTimeMillis() + 150;
                GuideRequest.send("reload", "", "");
            }).dimensions(l + 320, t + 302, 74, 20).build();
            addDrawableChild(refreshButton);
            addDrawableChild(ButtonWidget.builder(Text.literal("Editar"), b -> { editing = true; build(); })
                .dimensions(l + 400, t + 302, 74, 20).build());
        }
    }

    private GuideDocument current() { return guides.isEmpty() ? null : guides.get(Math.max(0, Math.min(selected, guides.size() - 1))); }

    @Override public void tick() {
        if (refreshButton != null && !refreshButton.active && System.currentTimeMillis() >= refreshEnableAt) refreshButton.active = true;
        if (dirty && editor != null && System.currentTimeMillis() >= saveAt && current() != null) {
            GuideRequest.send("save", current().title, editor.getText()); dirty = false;
        }
    }

    @Override public void renderBackground(DrawContext c, int x, int y, float d) {}

    @Override public void render(DrawContext c, int mx, int my, float d) {
        int l = width / 2 - 260, t = height / 2 - 170;
        c.fill(0, 0, width, height, 0x90000000); c.fill(l, t, l + 520, t + 340, 0xE0101010); c.drawBorder(l, t, 520, 340, 0xFF909090);
        c.drawTextWithShadow(textRenderer, editing ? "EDITOR DE GUIAS" : "GUIAS", l + 14, t + 14, 0xFFFFFFFF);
        c.drawTextWithShadow(textRenderer, "\u00D7", l + 502, t + 14, 0xFFFFFFFF);
        int visible = 13; scroll = Math.max(0, Math.min(scroll, Math.max(0, guides.size() - visible)));
        for (int row = 0; row < visible && row + scroll < guides.size(); row++) {
            int i = row + scroll, y = t + 42 + row * 19;
            if (i == selected) c.fill(l + 9, y - 2, l + 112, y + 16, 0xFF30465A);
            c.drawTextWithShadow(textRenderer, shortTitle(guides.get(i).title), l + 14, y + 1, i == selected ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
        if (guides.isEmpty()) c.drawTextWithShadow(textRenderer, "No hay guias.", l + 14, t + 48, 0xFFAAAAAA);
        if (!editing && current() != null) { renderGuideText(c, current().markdown, l + 126, t + 43, 338); renderReaderScrollbar(c,l,t); }
        if (editing && canEdit) {
            if (renameButton != null && titleField != null && current() != null)
                renameButton.active = !titleField.getText().trim().isEmpty() && !titleField.getText().trim().equals(current().title);
            for (var child : children()) if (child instanceof ButtonWidget button && button.getMessage().getString().equals("\u00D7") && button.getY() == t + 302)
                button.active = hasShiftDown();
            c.drawTextWithShadow(textRenderer, dirty ? "Guardando..." : "Guardado", l + 126, t + 306, dirty ? 0xFFFFD080 : 0xFF55FF55);
        }
        super.render(c, mx, my, d);
        if (editing && canEdit && helpOpen) renderHelp(c, l, t);
        renderButtonTooltips(c, mx, my, l, t);
    }

    private void renderHelp(DrawContext c, int l, int t) {
        c.fill(l + 137, t + 76, l + 465, t + 286, 0xF0181818); c.drawBorder(l + 137, t + 76, 328, 210, 0xFF909090);
        String[] help = {"FORMATO DE GUIAS", "Colores: \\black \\dark_blue \\dark_green", "\\dark_aqua \\dark_red \\dark_purple \\gold", "\\gray \\dark_gray \\blue \\green \\aqua", "\\red \\light_purple \\yellow \\white", "Estilos: \\bold \\italic \\underline", "\\strikethrough \\obfuscated \\reset", "Ejemplo: \\redTexto rojo \\boldImportante"};
        for (int i = 0; i < help.length; i++) c.drawTextWithShadow(textRenderer, help[i], l + 147, t + 88 + i * 18, i == 0 ? 0xFFFFFFFF : 0xFFCCCCCC);
    }

    private void renderButtonTooltips(DrawContext c, int mx, int my, int l, int t) {
        if (!canEdit) return;
        if (editing) {
            if (mx >= l + 12 && mx < l + 32 && my >= t + 302 && my < t + 322) c.drawTooltip(textRenderer, Text.literal("Crear una guía"), mx, my);
            else if (mx >= l + 36 && mx < l + 56 && my >= t + 302 && my < t + 322) c.drawTooltip(textRenderer, Text.literal(renameButton != null && renameButton.active ? "Confirmar nuevo nombre" : "Escribe un nombre distinto para renombrar"), mx, my);
            else if (mx >= l + 60 && mx < l + 80 && my >= t + 302 && my < t + 322) c.drawTooltip(textRenderer, Text.literal("Ver sintaxis de formato"), mx, my);
            else if (mx >= l + 84 && mx < l + 104 && my >= t + 302 && my < t + 322) c.drawTooltip(textRenderer, Text.literal(hasShiftDown() ? "Eliminar esta guía" : "Mantén Shift para eliminar"), mx, my);
            else if (mx >= l + 400 && mx < l + 474 && my >= t + 302 && my < t + 322) c.drawTooltip(textRenderer, Text.literal("Volver al lector de guías"), mx, my);
        } else if (mx >= l + 320 && mx < l + 394 && my >= t + 302 && my < t + 322) c.drawTooltip(textRenderer, Text.literal("Recargar archivos de guías desde el servidor"), mx, my);
        else if (mx >= l + 400 && mx < l + 474 && my >= t + 302 && my < t + 322) c.drawTooltip(textRenderer, Text.literal("Editar la guía seleccionada"), mx, my);
    }

    private String shortTitle(String title) { String cut=textRenderer.trimToWidth(title,92); return cut.length()<title.length()?textRenderer.trimToWidth(cut,84)+"…":cut; }
    private void renderGuideText(DrawContext c, String content, int x, int y, int w) {
        int lineY = y;
        int skipped = readerScroll;
        for (String raw : content.split("\\R")) {
            boolean rendered = false;
            for (var wrapped : textRenderer.wrapLines(MotdMarkup.parse(raw), w)) {
                if (skipped-- > 0) continue;
                if (lineY > height / 2 + 112) return;
                c.drawTextWithShadow(textRenderer, wrapped, x, lineY, 0xFFCCCCCC); lineY += 10; rendered = true;
            }
            if (rendered) lineY += 2;
        }
    }
    private int maxReaderScroll(){if(current()==null)return 0;int lines=0;for(String raw:current().markdown.split("\\R"))lines+=textRenderer.wrapLines(MotdMarkup.parse(raw),338).size();return Math.max(0,lines-20);}
    private void renderReaderScrollbar(DrawContext c,int l,int t){int max=maxReaderScroll();readerScroll=Math.max(0,Math.min(readerScroll,max));if(max==0)return;int top=t+42,bottom=t+282,track=bottom-top,bar=Math.max(20,track*20/(max+20)),y=top+(track-bar)*readerScroll/max;c.fill(l+466,top,l+472,bottom,0xFF333333);c.fill(l+466,y,l+472,y+bar,0xFFAAAAAA);}

    @Override public boolean mouseClicked(double mx, double my, int b) {
        int l = width / 2 - 260, t = height / 2 - 170;
        if (b == 0 && mx >= l + 500 && mx < l + 520 && my >= t && my < t + 32) { close(); return true; }
        if (!editing && b==0 && maxReaderScroll()>0 && mx>=l+462&&mx<=l+476&&my>=t+42&&my<=t+282){draggingReaderScrollbar=true;updateReaderScroll(my);return true;}
        if (b == 0 && mx >= l + 9 && mx < l + 112 && my >= t + 42 && my < t + 289) {
            selected = Math.min(guides.size() - 1, scroll + (int) ((my - (t + 42)) / 19)); readerScroll = 0;
            if (editing) { dirty = false; helpOpen = false; build(); }
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        int l = width / 2 - 260, t = height / 2 - 170;
        if (mx >= l && mx < l + 117 && my >= t + 40 && my < t + 290) { scroll -= Math.signum(v); return true; }
        if (!editing && mx >= l + 126 && mx < l + 474 && my >= t + 40 && my < t + 290 && current()!=null) { readerScroll=Math.max(0,Math.min(maxReaderScroll(),readerScroll-(int)Math.signum(v))); return true; }
        return super.mouseScrolled(mx, my, h, v);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(draggingReaderScrollbar&&button==0){updateReaderScroll(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(button==0&&draggingReaderScrollbar){draggingReaderScrollbar=false;return true;}return super.mouseReleased(mx,my,button);}
    private void updateReaderScroll(double my){int max=maxReaderScroll();if(max==0)return;int t=height/2-170,top=t+42,bottom=t+282,track=bottom-top,bar=Math.max(20,track*20/(max+20));double f=(my-top-bar/2.0)/Math.max(1,track-bar);readerScroll=(int)Math.round(Math.max(0,Math.min(1,f))*max);}
    @Override public boolean shouldPause() { return false; }
    @Override public void close() {
        if (editing) { editing = false; helpOpen = false; build(); }
        else client.setScreen(null);
    }
    private static List<GuideDocument> decode(String json) { List<GuideDocument> result = GSON.fromJson(json, TYPE); return result == null ? new ArrayList<>() : result; }
}
