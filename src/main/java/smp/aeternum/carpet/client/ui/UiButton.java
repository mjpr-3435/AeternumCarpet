package smp.aeternum.carpet.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public final class UiButton extends ClickableWidget {
    public enum Variant {
        PRIMARY,
        SECONDARY,
        SUCCESS,
        DANGER,
        GHOST
    }

    private final Consumer<UiButton> onPress;
    private Variant variant;
    private float hoverAnim;
    private int customAccent = -1;

    public UiButton(int x, int y, int w, int h, String label, Variant variant, Consumer<UiButton> onPress) {
        super(x, y, w, h, Text.literal(label));
        this.variant = variant;
        this.onPress = onPress;
    }

    public static UiButton primary(int x, int y, int w, int h, String label, Consumer<UiButton> onPress) {
        return new UiButton(x, y, w, h, label, Variant.PRIMARY, onPress);
    }

    public static UiButton secondary(int x, int y, int w, int h, String label, Consumer<UiButton> onPress) {
        return new UiButton(x, y, w, h, label, Variant.SECONDARY, onPress);
    }

    public static UiButton success(int x, int y, int w, int h, String label, Consumer<UiButton> onPress) {
        return new UiButton(x, y, w, h, label, Variant.SUCCESS, onPress);
    }

    public static UiButton danger(int x, int y, int w, int h, String label, Consumer<UiButton> onPress) {
        return new UiButton(x, y, w, h, label, Variant.DANGER, onPress);
    }

    public static UiButton ghost(int x, int y, int w, int h, String label, Consumer<UiButton> onPress) {
        return new UiButton(x, y, w, h, label, Variant.GHOST, onPress);
    }

    public UiButton setVariant(Variant variant) {
        this.variant = variant;
        return this;
    }

    public UiButton setCustomAccent(int accent) {
        this.customAccent = accent;
        return this;
    }

    public void setLabel(String label) {
        setMessage(Text.literal(label));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.active && this.visible && this.onPress != null) {
            this.onPress.accept(this);
        }
    }

    @Override
    protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
        float target = (this.active && this.isHovered()) ? 1f : 0f;
        hoverAnim += (target - hoverAnim) * Math.min(1f, delta * 0.45f + 0.2f);

        int bg;
        int hoverBg;
        int border;
        int textColor;

        if (!this.active) {
            bg = 0xFF101014;
            hoverBg = bg;
            border = AeternumTheme.BORDER_SUBTLE;
            textColor = AeternumTheme.TEXT_MUTED;
        } else {
            switch (variant) {
                case PRIMARY -> {
                    int accent = customAccent != -1 ? customAccent : AeternumTheme.ACCENT_GOLD;
                    bg = AeternumTheme.lerpColor(0xFF1C1C22, accent, 0.16f);
                    hoverBg = AeternumTheme.lerpColor(0xFF1C1C22, accent, 0.30f);
                    border = AeternumTheme.withAlpha(accent, 0xCC);
                    textColor = AeternumTheme.TEXT_PRIMARY;
                }
                case SUCCESS -> {
                    bg = 0xFF1D1D24;
                    hoverBg = 0xFF2B2B35;
                    border = 0xFFD0D0DC;
                    textColor = AeternumTheme.TEXT_PRIMARY;
                }
                case DANGER -> {
                    bg = 0xFF18181D;
                    hoverBg = 0xFF282830;
                    border = 0xFF9898A6;
                    textColor = 0xFFE6E6EE;
                }
                case GHOST -> {
                    bg = 0x00000000;
                    hoverBg = 0x2EFFFFFF;
                    border = 0x00000000;
                    textColor = this.isHovered() ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY;
                }
                default -> {
                    bg = AeternumTheme.CARD_BG;
                    hoverBg = AeternumTheme.CARD_HOVER;
                    border = this.isHovered() ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE;
                    textColor = AeternumTheme.TEXT_PRIMARY;
                }
            }
        }

        int finalBg = AeternumTheme.lerpColor(bg, hoverBg, hoverAnim);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if ((finalBg >>> 24) > 0) {
            c.fill(x, y, x + w, y + h, finalBg);
        }
        if ((border >>> 24) > 0) {
            c.drawBorder(x, y, w, h, border);
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String label = getMessage().getString();
        int tw = tr.getWidth(label);
        int tx = x + Math.max(2, (w - tw) / 2);
        int ty = y + (h - 8) / 2;
        c.drawTextWithShadow(tr, label, tx, ty, textColor);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
