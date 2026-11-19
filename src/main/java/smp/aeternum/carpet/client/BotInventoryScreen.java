package smp.aeternum.carpet.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import smp.aeternum.carpet.bot.BotInventoryScreenHandler;
import smp.aeternum.carpet.client.ui.AeternumTheme;

/** Vanilla-styled player inventory screen for editing a Carpet fake player's equipment and inventory. */
public final class BotInventoryScreen extends HandledScreen<BotInventoryScreenHandler> {
    private static final int HOTBAR_BTN_X = 86;
    private static final int HOTBAR_BTN_Y = 58;
    private static final int HOTBAR_BTN_W = 9;
    private static final int HOTBAR_BTN_H = 12;

    public BotInventoryScreen(BotInventoryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 254;
        this.playerInventoryTitleX = 8;
        this.playerInventoryTitleY = 160;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0xFF404040, false);
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;
        int w = this.backgroundWidth;
        int h = this.backgroundHeight;

        // Vanilla-style 3D beveled gray container panel
        c.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        c.drawBorder(x, y, w, h, 0xFF000000);
        c.fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);
        c.fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);
        c.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
        c.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, 0xFF555555);

        // Recessed 3D entity box
        int entX1 = x + 30;
        int entY1 = y + 8;
        int entX2 = x + 79;
        int entY2 = y + 78;
        drawBevelBox(c, entX1, entY1, entX2 - entX1, entY2 - entY1, 0xFF000000);

        // Bot name + offhand/main-hand labels
        String botName = AeternumTheme.ellipsize(this.textRenderer, this.title.getString(), 80);
        c.drawText(this.textRenderer, botName, x + 86, y + 10, 0xFF404040, false);
        c.drawText(this.textRenderer, "Offhand", x + 107, y + 31, 0xFF555555, false);
        c.drawText(this.textRenderer, "Mano principal", x + 86, y + 48, 0xFF555555, false);

        // Draw every 18x18 vanilla slot recess
        for (Slot slot : this.handler.slots) {
            drawBevelBox(c, x + slot.x - 1, y + slot.y - 1, 18, 18, 0xFF8B8B8B);
        }

        // Highlight active hotbar slot on bot's hotbar (handler slots 32..40)
        int selected = this.handler.getSelectedHotbarSlot();
        int activeSlotX = x + 8 + selected * 18 - 1;
        int activeSlotY = y + 139 - 1;
        c.drawBorder(activeSlotX, activeSlotY, 18, 18, 0xFFFFCC33);

        // 1..9 Main-hand hotbar slot selector
        for (int i = 0; i < 9; i++) {
            int bx = x + HOTBAR_BTN_X + i * HOTBAR_BTN_W;
            int by = y + HOTBAR_BTN_Y;
            boolean active = (i == selected);
            boolean hover = mouseX >= bx && mouseX < bx + HOTBAR_BTN_W && mouseY >= by && mouseY < by + HOTBAR_BTN_H;
            int bg = active ? 0xFFE0A95B : (hover ? 0xFFAFAFAF : 0xFF8B8B8B);
            c.fill(bx, by, bx + HOTBAR_BTN_W, by + HOTBAR_BTN_H, bg);
            c.drawBorder(bx, by, HOTBAR_BTN_W, HOTBAR_BTN_H, active ? 0xFF373737 : 0xFF555555);
            String num = String.valueOf(i + 1);
            c.drawText(this.textRenderer, num, bx + 2, by + 2, active ? 0xFF1A1A1A : 0xFFFFFFFF, false);
        }

        // Live 3D bot model looking at cursor
        LivingEntity bot = this.handler.getClientBot();
        if (bot != null) {
            InventoryScreen.drawEntity(c, entX1, entY1, entX2, entY2, 30, 0.0625f, (float) mouseX, (float) mouseY, bot);
        }
    }

    private static void drawBevelBox(DrawContext c, int x, int y, int w, int h, int fill) {
        c.fill(x, y, x + w, y + h, fill);
        c.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        c.fill(x, y, x + 1, y + h - 1, 0xFF373737);
        c.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        c.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int bxStart = this.x + HOTBAR_BTN_X;
            int by = this.y + HOTBAR_BTN_Y;
            if (mouseY >= by && mouseY < by + HOTBAR_BTN_H && mouseX >= bxStart && mouseX < bxStart + 9 * HOTBAR_BTN_W) {
                int idx = (int) ((mouseX - bxStart) / HOTBAR_BTN_W);
                if (idx >= 0 && idx < 9 && this.client != null && this.client.interactionManager != null) {
                    this.client.interactionManager.clickButton(this.handler.syncId, idx);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
