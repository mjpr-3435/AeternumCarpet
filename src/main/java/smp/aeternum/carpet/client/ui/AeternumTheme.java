package smp.aeternum.carpet.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class AeternumTheme {
    public static final int BACKDROP_TOP = 0x8C050506;
    public static final int BACKDROP_BOTTOM = 0xC008080A;

    public static final int PANEL_BG = 0xEE0D0D10;
    public static final int HEADER_BG = 0xFF131317;
    public static final int FOOTER_BG = 0xFF101014;
    public static final int SIDEBAR_BG = 0xFF0A0A0D;

    public static final int CARD_BG = 0xFF16161B;
    public static final int CARD_ALT_BG = 0xFF111115;
    public static final int CARD_HOVER = 0xFF212128;
    public static final int CARD_SELECTED = 0xFF282830;

    public static final int INPUT_BG = 0xFF0A0A0C;
    public static final int INPUT_BORDER = 0xFF2B2B33;
    public static final int INPUT_FOCUS_BORDER = 0xFFFFFFFF;

    public static final int BORDER_SUBTLE = 0xFF26262D;
    public static final int BORDER_BRIGHT = 0xFF454552;
    public static final int TOP_HIGHLIGHT = 0x33FFFFFF;

    public static final int ACCENT_GOLD = 0xFFFFFFFF;
    public static final int ACCENT_CYAN = 0xFFE2E2EA;
    public static final int ACCENT_BLUE = 0xFFC8C8D2;
    public static final int ACCENT_GREEN = 0xFFF2F2F7;
    public static final int ACCENT_RED = 0xFFB0B0BA;

    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFB5B5C0;
    public static final int TEXT_MUTED = 0xFF70707C;

    public static final int SCROLLBAR_TRACK = 0x401A1A20;
    public static final int SCROLLBAR_THUMB = 0xFF52525E;
    public static final int SCROLLBAR_THUMB_ACTIVE = 0xFF9E9EAE;

    private AeternumTheme() {
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ca = Math.round(aa + (ba - aa) * t);
        int cr = Math.round(ar + (br - ar) * t);
        int cg = Math.round(ag + (bg - ag) * t);
        int cb = Math.round(ab + (bb - ab) * t);
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }

    public static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    public static void drawPanel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x - 2, y + 2, x + w + 2, y + h + 4, 0x55000000);
        c.fill(x, y, x + w, y + h, PANEL_BG);
        c.drawBorder(x, y, w, h, BORDER_BRIGHT);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, TOP_HIGHLIGHT);
    }

    public static void drawCard(DrawContext c, int x, int y, int w, int h, int bg, int border) {
        c.fill(x, y, x + w, y + h, bg);
        c.drawBorder(x, y, w, h, border);
    }

    public static void drawHeader(DrawContext c, int x, int y, int w, int h) {
        c.fill(x + 1, y + 1, x + w - 1, y + h, HEADER_BG);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, BORDER_SUBTLE);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, TOP_HIGHLIGHT);
    }

    public static void drawFooter(DrawContext c, int x, int y, int w, int bottomY) {
        c.fill(x + 1, y, x + w - 1, bottomY - 1, FOOTER_BG);
        c.fill(x + 1, y, x + w - 1, y + 1, BORDER_SUBTLE);
    }

    public static void drawProgressBar(DrawContext c, int x, int y, int w, int h, float ratio, int fillColor) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        c.fill(x, y, x + w, y + h, 0xFF08080A);
        c.drawBorder(x, y, w, h, 0xFF24242B);
        int fillW = Math.round(w * clamped);
        if (fillW > 0) {
            c.fill(x, y, x + fillW, y + h, fillColor);
        }
    }

    public static int drawBadge(DrawContext c, TextRenderer tr, int x, int y, String text, int bg, int fg) {
        int tw = tr.getWidth(text);
        int w = tw + 10;
        int h = 13;
        c.fill(x, y, x + w, y + h, bg);
        c.drawBorder(x, y, w, h, withAlpha(fg, 0x60));
        c.drawText(tr, text, x + 5, y + 3, fg, false);
        return w;
    }

    public static void drawItemIcon(DrawContext c, String itemId, int x, int y) {
        if (itemId == null || itemId.isEmpty()) return;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return;
        Item item = Registries.ITEM.get(id);
        if (item != Items.AIR) {
            c.drawItem(new ItemStack(item), x, y);
        }
    }

    public static void drawScrollbar(
        DrawContext c,
        int x,
        int top,
        int bottom,
        int totalItems,
        int visibleItems,
        double scrollOffset,
        boolean active
    ) {
        if (totalItems <= visibleItems || visibleItems <= 0) return;
        int trackH = bottom - top;
        if (trackH <= 0) return;
        int barH = Math.max(22, trackH * visibleItems / totalItems);
        int maxScroll = Math.max(1, totalItems - visibleItems);
        double frac = Math.max(0.0, Math.min(1.0, scrollOffset / maxScroll));
        int barY = top + (int) Math.round((trackH - barH) * frac);
        c.fill(x, top, x + 4, bottom, SCROLLBAR_TRACK);
        c.fill(x, barY, x + 4, barY + barH, active ? SCROLLBAR_THUMB_ACTIVE : SCROLLBAR_THUMB);
    }

    public static String ellipsize(TextRenderer tr, String text, int maxWidth) {
        if (text == null) return "";
        if (tr.getWidth(text) <= maxWidth) return text;
        String ellipsis = "\u2026";
        int ew = tr.getWidth(ellipsis);
        return tr.trimToWidth(text, Math.max(0, maxWidth - ew)) + ellipsis;
    }
}
