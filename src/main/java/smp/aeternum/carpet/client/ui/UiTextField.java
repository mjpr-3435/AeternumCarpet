package smp.aeternum.carpet.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class UiTextField extends TextFieldWidget {
    private final String placeholder;

    public UiTextField(TextRenderer tr, int x, int y, int w, int h, String placeholder) {
        super(tr, x + 6, y + Math.max(2, (h - 8) / 2), Math.max(16, w - 12), Math.max(10, h - 4), Text.literal(placeholder));
        this.placeholder = placeholder;
        setDrawsBackground(false);
        setEditableColor(AeternumTheme.TEXT_PRIMARY);
        setUneditableColor(AeternumTheme.TEXT_SECONDARY);
    }

    public int boxX() {
        return getX() - 6;
    }

    public int boxY() {
        return getY() - Math.max(2, (getHeight() + 4 - 8) / 2);
    }

    public void setBoxBounds(int x, int y, int w, int h) {
        setPosition(x + 6, y + Math.max(2, (h - 8) / 2));
        setWidth(Math.max(16, w - 12));
    }

    public void renderBox(DrawContext c, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int border = isFocused()
            ? AeternumTheme.INPUT_FOCUS_BORDER
            : (hovered ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.INPUT_BORDER);
        c.fill(x, y, x + w, y + h, AeternumTheme.INPUT_BG);
        c.drawBorder(x, y, w, h, border);
        super.renderWidget(c, mouseX, mouseY, delta);
        if (getText().isEmpty() && !isFocused() && placeholder != null && !placeholder.isEmpty()) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            String shown = AeternumTheme.ellipsize(tr, placeholder, w - 14);
            c.drawText(tr, shown, x + 6, y + (h - 8) / 2, AeternumTheme.TEXT_MUTED, false);
        }
    }
}
