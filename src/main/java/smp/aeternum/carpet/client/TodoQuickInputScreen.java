package smp.aeternum.carpet.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.network.TodoActionPayload;

/** Minimalist 1-line floating prompt for quickly adding a personal to-do note. */
public final class TodoQuickInputScreen extends Screen {
    private static final int BOX_W = 360;
    private static final int BOX_H = 44;

    private UiTextField input;
    private int boxX;
    private int boxY;

    public TodoQuickInputScreen() {
        super(Text.literal("Nueva nota"));
    }

    @Override
    protected void init() {
        clearChildren();
        boxX = (width - BOX_W) / 2;
        boxY = Math.max(24, height - 96);

        input = new UiTextField(textRenderer, boxX + 8, boxY + 18, BOX_W - 76, 19, "Escribe una nota rápida y pulsa Enter...");
        input.setMaxLength(140);
        addDrawableChild(input);
        setFocused(input);
        input.setFocused(true);

        addDrawableChild(UiButton.primary(boxX + BOX_W - 62, boxY + 18, 54, 19, "Añadir", b -> submit()));
    }

    private void submit() {
        if (input != null) {
            String note = input.getText().strip();
            if (!note.isEmpty()) {
                ClientPlayNetworking.send(new TodoActionPayload("add", 0, note));
            }
        }
        close();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep world crisp with no full-screen blur or dark backdrop for quick inline entry.
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        c.fill(boxX - 2, boxY + 2, boxX + BOX_W + 2, boxY + BOX_H + 3, 0x55000000);
        c.fill(boxX, boxY, boxX + BOX_W, boxY + BOX_H, 0xEE0D0D10);
        c.drawBorder(boxX, boxY, BOX_W, BOX_H, AeternumTheme.BORDER_BRIGHT);
        c.fill(boxX + 1, boxY + 1, boxX + BOX_W - 1, boxY + 2, AeternumTheme.ACCENT_GOLD);

        c.drawTextWithShadow(textRenderer, "Nueva nota", boxX + 8, boxY + 6, AeternumTheme.TEXT_PRIMARY);
        c.drawText(textRenderer, "Enter: guardar · Esc: cancelar", boxX + BOX_W - 8 - textRenderer.getWidth("Enter: guardar · Esc: cancelar"), boxY + 6, AeternumTheme.TEXT_MUTED, false);

        if (input != null) {
            input.renderBox(c, boxX + 8, boxY + 18, BOX_W - 76, 19, mx, my, d);
        }
        super.render(c, mx, my, d);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && (mx < boxX || mx >= boxX + BOX_W || my < boxY || my >= boxY + BOX_H)) {
            close();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        client.setScreen(null);
    }
}
