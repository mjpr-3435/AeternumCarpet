package smp.aeternum.carpet.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class UiScreen extends Screen {
    protected int panelX;
    protected int panelY;
    protected int panelW;
    protected int panelH;

    protected UiScreen(Text title) {
        super(title);
    }

    protected void computePanel(int preferredW, int preferredH) {
        panelW = Math.min(Math.max(420, preferredW), Math.max(320, width - 28));
        panelH = Math.min(Math.max(280, preferredH), Math.max(220, height - 28));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        try {
            applyBlur(context);
        } catch (RuntimeException ignored) {
        }
        context.fillGradient(0, 0, width, height, AeternumTheme.BACKDROP_TOP, AeternumTheme.BACKDROP_BOTTOM);
    }

    protected void drawWindowFrame(DrawContext c, String title, String subtitle) {
        AeternumTheme.drawPanel(c, panelX, panelY, panelW, panelH);
        AeternumTheme.drawHeader(c, panelX, panelY, panelW, 44);
        c.fill(panelX + 1, panelY + 1, panelX + 4, panelY + 43, AeternumTheme.ACCENT_GOLD);
        c.drawTextWithShadow(textRenderer, title, panelX + 14, panelY + 10, AeternumTheme.TEXT_PRIMARY);
        if (subtitle != null && !subtitle.isEmpty()) {
            c.drawText(textRenderer, subtitle, panelX + 14, panelY + 25, AeternumTheme.TEXT_SECONDARY, false);
        }
    }

    protected void drawCloseButton(DrawContext c, int mouseX, int mouseY) {
        int bx = panelX + panelW - 28;
        int by = panelY + 10;
        boolean hover = mouseX >= bx && mouseX < bx + 20 && mouseY >= by && mouseY < by + 20;
        if (hover) {
            c.fill(bx, by, bx + 20, by + 20, 0x40E55353);
            c.drawBorder(bx, by, 20, 20, AeternumTheme.withAlpha(AeternumTheme.ACCENT_RED, 0x90));
        }
        c.drawTextWithShadow(textRenderer, "\u00D7", bx + 7, by + 6, hover ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
    }

    protected boolean isCloseButtonHit(double mouseX, double mouseY) {
        int bx = panelX + panelW - 28;
        int by = panelY + 10;
        return mouseX >= bx && mouseX < bx + 20 && mouseY >= by && mouseY < by + 20;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
