package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.motd.MotdMarkup;
import smp.aeternum.carpet.network.ScreenOpenNetworking.DockState;
import smp.aeternum.carpet.network.ScreenOpenRequestPayload;

public final class AeternumDockOverlay {
    private static final Gson GSON = new Gson();
    private static final Identifier LOGO_TEX = AeternumCarpet.id("textures/gui/aeternum_logo.png");
    private static final String[] SERVERS = {"SMP", "CMP", "MMP", "PMP"};
    private static final int TILE_W = 62;
    private static final int TILE_H = 42;
    private static final int TILE_GAP = 6;

    private static DockState cachedState = new DockState();
    private static boolean visible;
    private static boolean suppressUntilRelease;
    private static float openAnim;

    private AeternumDockOverlay() {}

    public static void updateState(String json) {
        try {
            DockState decoded = GSON.fromJson(json, DockState.class);
            if (decoded != null) cachedState = decoded;
        } catch (Exception ignored) {
        }
    }

    public static void clearState() {
        cachedState = new DockState();
        visible = false;
        suppressUntilRelease = false;
        openAnim = 0f;
    }

    public static boolean isVisible() {
        MinecraftClient client = MinecraftClient.getInstance();
        return visible && client.currentScreen == null && client.player != null;
    }

    public static void tick(MinecraftClient client, boolean keyHeld) {
        if (client.player == null || client.getNetworkHandler() == null) {
            visible = false;
            suppressUntilRelease = false;
            return;
        }
        if (client.currentScreen != null) {
            if (visible) {
                visible = false;
                openAnim = 0f;
            }
            if (!keyHeld) suppressUntilRelease = false;
            return;
        }
        if (!keyHeld) {
            suppressUntilRelease = false;
            if (visible) {
                visible = false;
                openAnim = 0f;
                client.mouse.lockCursor();
            }
            return;
        }
        if (!suppressUntilRelease && !visible) {
            visible = true;
            openAnim = 0f;
            ClientPlayNetworking.send(new ScreenOpenRequestPayload("dock"));
            client.mouse.unlockCursor();
        }
    }

    private static List<DockAction> buildActions() {
        List<DockAction> out = new ArrayList<>();
        DockState s = cachedState;
        if (s.projects) out.add(new DockAction("Proyectos", "projects", new ItemStack(Items.SHULKER_BOX), "Proyectos, subregiones y materiales"));
        if (s.guide) out.add(new DockAction("Guías", "guide", new ItemStack(Items.BOOKSHELF), "Biblioteca de guías del servidor"));
        if (s.actions) out.add(new DockAction("Acciones", "actions", new ItemStack(Items.COMPARATOR), "Paquetes de comandos"));
        if (s.blockers) out.add(new DockAction("Blockers", "blockers", new ItemStack(Items.PISTON), "Escáner de bloqueadores"));
        if (s.exploration) out.add(new DockAction("Mapa", "exploration", new ItemStack(Items.FILLED_MAP), "Mapa topográfico y regiones"));
        if (s.isOp) out.add(new DockAction("Rules", "rules", new ItemStack(Items.COMMAND_BLOCK), "Editor universal de reglas Carpet"));
        return out;
    }

    public static void render(DrawContext c, float tickDelta) {
        if (!isVisible()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int mx = (int) Math.round(client.mouse.getScaledX(client.getWindow()));
        int my = (int) Math.round(client.mouse.getScaledY(client.getWindow()));

        openAnim = Math.min(1f, openAnim + tickDelta * 0.35f + 0.14f);
        int slideOffset = Math.round((1f - openAnim) * 12f);

        List<DockAction> actions = buildActions();
        int dockRowW = actions.isEmpty() ? 220 : actions.size() * TILE_W + (actions.size() - 1) * TILE_GAP;
        int dockBoxW = Math.max(380, dockRowW + 20);
        int dockBoxH = TILE_H + 14;

        List<String> tasks = cachedState.tasks != null ? cachedState.tasks : List.of();
        int shownTasks = Math.min(3, tasks.size());
        int motdH = 44 + (shownTasks > 0 ? 12 + shownTasks * 12 : 0);
        int motdW = Math.max(dockBoxW, 420);

        int baseBottom = height - 44 + slideOffset;
        int dockX = (width - dockBoxW) / 2;
        int dockY = baseBottom - dockBoxH;
        int motdX = (width - motdW) / 2;
        int motdY = dockY - 8 - motdH;

        // 1. MOTD Card
        c.fill(motdX - 2, motdY + 2, motdX + motdW + 2, motdY + motdH + 3, 0x45000000);
        c.fill(motdX, motdY, motdX + motdW, motdY + motdH, 0xE60D0D10);
        c.drawBorder(motdX, motdY, motdW, motdH, AeternumTheme.BORDER_BRIGHT);
        c.fill(motdX + 1, motdY + 1, motdX + motdW - 1, motdY + 2, AeternumTheme.TOP_HIGHLIGHT);

        c.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO_TEX, motdX + 10, motdY + 8, 0f, 0f, 28, 28, 28, 28);
        c.drawTextWithShadow(tr, "Aeternum Network", motdX + 46, motdY + 11, AeternumTheme.TEXT_PRIMARY);
        String timeStr = (cachedState.activeTime != null && !cachedState.activeTime.isEmpty())
            ? cachedState.activeTime
            : ("Mantén [" + ScreenKeybinds.quickDockKeyName() + "]");
        c.drawText(tr, timeStr, motdX + 46, motdY + 24, AeternumTheme.TEXT_SECONDARY, false);

        int pillW = 34;
        int pillGap = 4;
        int pillsStartX = motdX + motdW - 10 - (SERVERS.length * pillW + (SERVERS.length - 1) * pillGap);
        int pillY = motdY + 13;
        String hoveredTooltip = null;
        for (int i = 0; i < SERVERS.length; i++) {
            String srv = SERVERS[i];
            int px = pillsStartX + i * (pillW + pillGap);
            boolean over = mx >= px && mx < px + pillW && my >= pillY && my < pillY + 18;
            AeternumTheme.drawCard(c, px, pillY, pillW, 18, over ? 0xFF2C2C36 : 0xFF17171D, over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE);
            c.drawTextWithShadow(tr, srv, px + (pillW - tr.getWidth(srv)) / 2, pillY + 5,
                over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
            if (over) hoveredTooltip = "Conectar a " + srv;
        }

        if (shownTasks > 0) {
            int divY = motdY + 42;
            c.fill(motdX + 10, divY, motdX + motdW - 10, divY + 1, AeternumTheme.BORDER_SUBTLE);
            for (int i = 0; i < shownTasks; i++) {
                String prefix = "[" + (i + 1) + "] ";
                int ty = divY + 6 + i * 12;
                c.drawText(tr, prefix, motdX + 12, ty, AeternumTheme.TEXT_MUTED, false);
                List<OrderedText> wrapped = tr.wrapLines(MotdMarkup.parse(tasks.get(i)), motdW - 44);
                if (!wrapped.isEmpty()) {
                    c.drawTextWithShadow(tr, wrapped.get(0), motdX + 12 + tr.getWidth(prefix), ty, AeternumTheme.TEXT_PRIMARY);
                }
            }
        }

        // 2. Quick Dock Bar
        c.fill(dockX - 2, dockY + 2, dockX + dockBoxW + 2, dockY + dockBoxH + 3, 0x45000000);
        c.fill(dockX, dockY, dockX + dockBoxW, dockY + dockBoxH, 0xE60D0D10);
        c.drawBorder(dockX, dockY, dockBoxW, dockBoxH, AeternumTheme.BORDER_BRIGHT);
        c.fill(dockX + 1, dockY + 1, dockX + dockBoxW - 1, dockY + 2, AeternumTheme.TOP_HIGHLIGHT);

        if (actions.isEmpty()) {
            c.drawCenteredTextWithShadow(tr, "Sin módulos activos en el servidor.", width / 2, dockY + (dockBoxH - 8) / 2, AeternumTheme.TEXT_MUTED);
        } else {
            int startX = dockX + (dockBoxW - dockRowW) / 2;
            int tileY = dockY + 7;
            for (int i = 0; i < actions.size(); i++) {
                DockAction act = actions.get(i);
                int tx = startX + i * (TILE_W + TILE_GAP);
                boolean over = mx >= tx && mx < tx + TILE_W && my >= tileY && my < tileY + TILE_H;
                int bg = over ? AeternumTheme.CARD_SELECTED : AeternumTheme.CARD_BG;
                int border = over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE;
                AeternumTheme.drawCard(c, tx, tileY, TILE_W, TILE_H, bg, border);
                if (over) {
                    c.fill(tx + 1, tileY + 1, tx + TILE_W - 1, tileY + 3, AeternumTheme.TEXT_PRIMARY);
                    hoveredTooltip = act.tooltip;
                }

                c.drawItem(act.icon, tx + (TILE_W - 16) / 2, tileY + 6);
                c.drawTextWithShadow(tr, act.label, tx + (TILE_W - tr.getWidth(act.label)) / 2, tileY + 27,
                    over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
            }
        }

        if (hoveredTooltip != null) {
            c.drawTooltip(tr, Text.literal(hoveredTooltip), mx, my);
        }
    }

    public static void handleClick(double mx, double my) {
        if (!isVisible()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        List<DockAction> actions = buildActions();
        int dockRowW = actions.isEmpty() ? 220 : actions.size() * TILE_W + (actions.size() - 1) * TILE_GAP;
        int dockBoxW = Math.max(380, dockRowW + 20);
        int dockBoxH = TILE_H + 14;
        List<String> tasks = cachedState.tasks != null ? cachedState.tasks : List.of();
        int shownTasks = Math.min(3, tasks.size());
        int motdH = 44 + (shownTasks > 0 ? 12 + shownTasks * 12 : 0);
        int motdW = Math.max(dockBoxW, 420);

        int baseBottom = height - 44;
        int dockX = (width - dockBoxW) / 2;
        int dockY = baseBottom - dockBoxH;
        int motdX = (width - motdW) / 2;
        int motdY = dockY - 8 - motdH;

        int pillW = 34;
        int pillGap = 4;
        int pillsStartX = motdX + motdW - 10 - (SERVERS.length * pillW + (SERVERS.length - 1) * pillGap);
        int pillY = motdY + 13;
        for (int i = 0; i < SERVERS.length; i++) {
            int px = pillsStartX + i * (pillW + pillGap);
            if (mx >= px && mx < px + pillW && my >= pillY && my < pillY + 18) {
                if (client.player != null && client.player.networkHandler != null) {
                    client.player.networkHandler.sendChatCommand("server " + SERVERS[i]);
                }
                closeAfterAction(client, true);
                return;
            }
        }

        if (!actions.isEmpty()) {
            int startX = dockX + (dockBoxW - dockRowW) / 2;
            int tileY = dockY + 7;
            for (int i = 0; i < actions.size(); i++) {
                int tx = startX + i * (TILE_W + TILE_GAP);
                if (mx >= tx && mx < tx + TILE_W && my >= tileY && my < tileY + TILE_H) {
                    ClientPlayNetworking.send(new ScreenOpenRequestPayload(actions.get(i).screenId));
                    closeAfterAction(client, false);
                    return;
                }
            }
        }
    }

    private static void closeAfterAction(MinecraftClient client, boolean relockCursor) {
        visible = false;
        suppressUntilRelease = true;
        openAnim = 0f;
        if (relockCursor && client.currentScreen == null) {
            client.mouse.lockCursor();
        }
    }

    private record DockAction(String label, String screenId, ItemStack icon, String tooltip) {}
}
