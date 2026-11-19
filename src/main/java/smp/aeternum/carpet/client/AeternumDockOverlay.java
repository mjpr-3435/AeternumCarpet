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
import smp.aeternum.carpet.network.TodoActionPayload;

public final class AeternumDockOverlay {
    private static final Gson GSON = new Gson();
    private static final Identifier LOGO_TEX = AeternumCarpet.id("textures/gui/aeternum_logo.png");
    private static final String[] SERVERS = {"SMP", "CMP", "MMP", "PMP"};
    private static final long JOIN_BANNER_DURATION_MS = 5_000L;
    private static final int TILE_W = 62;
    private static final int TILE_H = 42;
    private static final int TILE_GAP = 6;
    private static final int EDIT_BTN_W = 48;
    private static final int EDIT_BTN_H = 18;
    private static final int MAX_VISIBLE_TODOS = 8;
    private static final int TODO_LINE_H = 10;
    private static final int TODO_ITEM_GAP = 4;
    private static final int TODO_PILL_H = 18;
    private static final int DIM_TILE_W = 16;
    private static final int DIM_TILE_H = 16;
    private static final int DIM_TILE_GAP = 3;
    private static final int DIM_OFFSET_Y = 4;
    private static final int MICRO_SEP_W = 7;
    private static final Identifier CS_CAMERA_TEX = AeternumCarpet.id("textures/gui/cs_camera.png");
    private static final Identifier CB_OFF_TEX = AeternumCarpet.id("textures/gui/cb_off.png");
    private static final Identifier CB_SNEAK_TEX = AeternumCarpet.id("textures/gui/cb_sneak.png");
    private static final Identifier CB_ALWAYS_TEX = AeternumCarpet.id("textures/gui/cb_always.png");
    private static final DimButton[] DIMENSIONS = {
        new DimButton("Overworld", "minecraft:overworld", "overworld", AeternumCarpet.id("textures/gui/dim_overworld.png"), AeternumTheme.ACCENT_GREEN),
        new DimButton("Nether", "minecraft:the_nether", "nether", AeternumCarpet.id("textures/gui/dim_nether.png"), 0xFFD452FF),
        new DimButton("End", "minecraft:the_end", "end", AeternumCarpet.id("textures/gui/dim_end.png"), 0xFFE3E59C)
    };

    private static DockState cachedState = new DockState();
    private static boolean visible;
    private static boolean suppressUntilRelease;
    private static float openAnim;
    private static long joinBannerStartedMs;
    private static long joinBannerUntilMs;

    private AeternumDockOverlay() {}

    public static void updateState(String json) {
        try {
            DockState decoded = GSON.fromJson(json, DockState.class);
            if (decoded == null) return;
            cachedState = decoded;
            if (!decoded.joinMotd) {
                joinBannerUntilMs = 0L;
            } else if (decoded.showJoinBanner) {
                long now = System.currentTimeMillis();
                joinBannerStartedMs = now;
                joinBannerUntilMs = now + JOIN_BANNER_DURATION_MS;
            }
            if (!anySectionEnabled(MinecraftClient.getInstance()) && visible) {
                visible = false;
                openAnim = 0f;
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.currentScreen == null) client.mouse.lockCursor();
            }
        } catch (Exception ignored) {
        }
    }

    public static void clearState() {
        cachedState = new DockState();
        visible = false;
        suppressUntilRelease = false;
        openAnim = 0f;
        joinBannerStartedMs = 0L;
        joinBannerUntilMs = 0L;
    }

    public static void onWorldChange(MinecraftClient client) {
        clearState();
        if (client != null && client.player != null && client.getNetworkHandler() != null
            && ClientPlayNetworking.canSend(ScreenOpenRequestPayload.ID)) {
            ClientPlayNetworking.send(new ScreenOpenRequestPayload("dock"));
        }
    }

    private static boolean canShowCamera(MinecraftClient client) {
        return client != null
            && client.player != null
            && cachedState.cameraMode != null
            && !"off".equals(cachedState.cameraMode);
    }

    private static boolean canShowCarefulBreak(MinecraftClient client) {
        return client != null && client.player != null && cachedState.carefulBreak;
    }

    private static boolean canShowDimensions(MinecraftClient client) {
        return cachedState.dimension
            && client != null
            && client.player != null
            && (client.player.isCreative() || client.player.isSpectator());
    }

    private static boolean anySectionEnabled(MinecraftClient client) {
        return cachedState.joinMotd
            || cachedState.quickDock
            || cachedState.todoList
            || canShowCamera(client)
            || canShowCarefulBreak(client)
            || canShowDimensions(client);
    }

    /** True ONLY while Alt is held and cursor is unlocked for interactive clicks. */
    public static boolean isVisible() {
        MinecraftClient client = MinecraftClient.getInstance();
        return visible
            && anySectionEnabled(client)
            && client.currentScreen == null
            && client.player != null;
    }

    private static boolean isJoinBannerVisible() {
        MinecraftClient client = MinecraftClient.getInstance();
        return !visible
            && cachedState.joinMotd
            && System.currentTimeMillis() < joinBannerUntilMs
            && client.currentScreen == null
            && client.player != null;
    }

    private static boolean isPinnedTodoVisible() {
        MinecraftClient client = MinecraftClient.getInstance();
        return !visible
            && MaterialsHudOverlay.todoPinned
            && cachedState.todoList
            && cachedState.todos != null
            && !cachedState.todos.isEmpty()
            && client.currentScreen == null
            && client.player != null;
    }

    public static void tick(MinecraftClient client, boolean keyHeld) {
        if (client.player == null || client.getNetworkHandler() == null) {
            visible = false;
            suppressUntilRelease = false;
            joinBannerUntilMs = 0L;
            return;
        }
        if (client.currentScreen != null || !anySectionEnabled(client)) {
            if (visible) {
                visible = false;
                openAnim = 0f;
                if (client.currentScreen == null) client.mouse.lockCursor();
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
            joinBannerUntilMs = 0L;
            ClientPlayNetworking.send(new ScreenOpenRequestPayload("dock"));
            client.mouse.unlockCursor();
        }
    }

    private static List<DockAction> buildActions() {
        List<DockAction> out = new ArrayList<>();
        DockState s = cachedState;
        if (s.actions) out.add(new DockAction("Acciones", "actions", new ItemStack(Items.COMPARATOR), "Paquetes de comandos"));
        if (s.guide) out.add(new DockAction("Guías", "guide", new ItemStack(Items.BOOKSHELF), "Biblioteca de guías del servidor"));
        if (s.projects) out.add(new DockAction("Proyectos", "projects", new ItemStack(Items.SHULKER_BOX), "Proyectos, subregiones y materiales"));
        if (s.blockers) out.add(new DockAction("Blockers", "blockers", new ItemStack(Items.PISTON), "Escáner de bloqueadores"));
        if (s.exploration) out.add(new DockAction("Mapa", "exploration", new ItemStack(Items.FILLED_MAP), "Mapa topográfico y regiones"));
        if (s.isOp) out.add(new DockAction("Rules", "rules", new ItemStack(Items.COMMAND_BLOCK), "Editor universal de reglas Carpet"));
        return out;
    }

    private static List<MicroItem> buildMicroToolbar(MinecraftClient client) {
        boolean showCs = canShowCamera(client);
        boolean showCb = canShowCarefulBreak(client);
        boolean showDim = canShowDimensions(client);
        List<MicroItem> items = new ArrayList<>();
        int cursor = 0;

        if (showCs) {
            boolean active = client.player != null && client.player.isSpectator();
            items.add(new MicroItem(
                false, cursor, "cs", null, CS_CAMERA_TEX, AeternumTheme.ACCENT_CYAN, active,
                active ? "Salir de /cs (modo cámara activo)" : "Entrar a /cs (modo cámara)"
            ));
            cursor += DIM_TILE_W;
        }

        if (showCb) {
            if (!items.isEmpty()) {
                items.add(new MicroItem(true, cursor, null, null, null, 0, false, null));
                cursor += MICRO_SEP_W;
            }
            String mode = cachedState.carefulBreakMode == null ? "off" : cachedState.carefulBreakMode;
            Identifier tex = switch (mode) {
                case "sneak" -> CB_SNEAK_TEX;
                case "always" -> CB_ALWAYS_TEX;
                default -> CB_OFF_TEX;
            };
            int accent = switch (mode) {
                case "sneak" -> AeternumTheme.ACCENT_CYAN;
                case "always" -> AeternumTheme.ACCENT_GOLD;
                default -> 0;
            };
            boolean active = !"off".equals(mode);
            String nextMode = switch (mode) {
                case "off" -> "sneak";
                case "sneak" -> "always";
                default -> "off";
            };
            items.add(new MicroItem(
                false, cursor, "cb:" + nextMode, null, tex, accent, active,
                "CarefulBreak: " + mode.toUpperCase() + " (clic \u2192 " + nextMode + ")"
            ));
            cursor += DIM_TILE_W;
        }

        if (showDim && client.player != null) {
            if (!items.isEmpty()) {
                items.add(new MicroItem(true, cursor, null, null, null, 0, false, null));
                cursor += MICRO_SEP_W;
            }
            String currentDim = client.player.getWorld().getRegistryKey().getValue().toString();
            for (int i = 0; i < DIMENSIONS.length; i++) {
                DimButton dim = DIMENSIONS[i];
                if (i > 0) cursor += DIM_TILE_GAP;
                boolean current = dim.worldId.equals(currentDim);
                items.add(new MicroItem(
                    false, cursor, "dim:" + dim.command, dim.worldId, dim.texture, dim.accent, current,
                    current ? (dim.label + " (dimensión actual)") : ("Ir a " + dim.label + " (/" + dim.command + ")")
                ));
                cursor += DIM_TILE_W;
            }
        }
        return items;
    }

    private static int microToolbarWidth(List<MicroItem> items) {
        if (items.isEmpty()) return 0;
        MicroItem last = items.get(items.size() - 1);
        return last.xOffset + (last.separator ? MICRO_SEP_W : DIM_TILE_W);
    }

    private static Layout resolveLayout(TextRenderer tr, int width, int height, List<MicroItem> microItems, boolean showMotd, boolean showDock, boolean showTodo, int slideOffset, List<DockAction> actions) {
        boolean showMicro = !microItems.isEmpty();
        int microW = microToolbarWidth(microItems);

        int dockRowW = actions.isEmpty() ? 220 : actions.size() * TILE_W + (actions.size() - 1) * TILE_GAP;
        int dockBoxW = showDock ? Math.max(380, dockRowW + 20) : 0;
        int dockBoxH = showDock ? TILE_H + 14 : 0;

        List<String> tasks = cachedState.tasks != null ? cachedState.tasks : List.of();
        int shownTasks = Math.min(3, tasks.size());
        int motdH = showMotd ? 44 + (shownTasks > 0 ? 12 + shownTasks * 12 : 0) : 0;
        int motdW = showMotd ? (showDock ? Math.max(dockBoxW, 420) : 420) : 0;

        int centerW = Math.max(microW, Math.max(motdW, dockBoxW));
        boolean centerActive = showMicro || showMotd || showDock;

        List<String> todos = cachedState.todos != null ? cachedState.todos : List.of();
        boolean todoCompact = todos.isEmpty() || MaterialsHudOverlay.todoCollapsed;
        String compactLabel = todos.isEmpty() ? "+ Nota" : ("Notas (" + todos.size() + ")");

        int todoW = 0;
        boolean todoOnSide = false;
        if (showTodo) {
            if (todoCompact) {
                todoW = Math.max(54, tr.getWidth(compactLabel) + 16);
                todoOnSide = centerActive && width >= centerW + todoW + 24;
            } else if (centerActive && width >= centerW + 208) {
                todoOnSide = true;
                todoW = Math.min(240, Math.max(196, width - centerW - 24));
            } else if (centerActive) {
                // Narrow screen fallback: dock/motd stays centered, todo stacks above them.
                todoOnSide = false;
                todoW = Math.max(280, Math.min(centerW, 380));
            } else {
                todoOnSide = false;
                todoW = Math.min(340, width - 24);
            }
        }

        int baseBottom = height - 48 + slideOffset;
        int centerX = (width - centerW) / 2;
        if (todoOnSide && centerX + centerW + 8 + todoW > width - 8) {
            centerX = Math.max(8, width - 8 - todoW - 8 - centerW);
        }

        int dockX = centerX + (centerW - dockBoxW) / 2;
        int dockY = baseBottom - dockBoxH;
        int motdX = centerX + (centerW - motdW) / 2;
        int motdY = showDock ? (dockY - 8 - motdH) : (baseBottom - motdH);

        int dimX = showMotd ? motdX : (showDock ? dockX : (centerX + (centerW - microW) / 2));
        int dimAnchorBottom = showMotd ? (motdY - DIM_OFFSET_Y) : (showDock ? (dockY - DIM_OFFSET_Y) : baseBottom);
        int dimY = dimAnchorBottom - (showMicro ? DIM_TILE_H : 0);

        int stackTop = showMicro ? dimY : (showMotd ? motdY : dockY);
        int maxTodoH = todoOnSide ? Math.max(64, height - 64)
            : (centerActive ? Math.max(64, stackTop - 16) : Math.max(64, height - 64));
        TodoLayout todoLayout = (!showTodo || todoCompact)
            ? TodoLayout.EMPTY
            : measureTodos(tr, todos, todoW, true, maxTodoH);
        int todoH = showTodo ? (todoCompact ? TODO_PILL_H : todoLayout.height) : 0;

        int todoX;
        int todoY;
        if (todoOnSide) {
            todoX = centerX + centerW + 8;
            todoY = Math.max(8, baseBottom - todoH);
        } else if (centerActive) {
            todoX = (width - todoW) / 2;
            todoY = Math.max(8, stackTop - 8 - todoH);
        } else {
            todoX = (width - todoW) / 2;
            todoY = Math.max(8, baseBottom - todoH);
        }

        return new Layout(dimX, dimY, motdX, motdY, motdW, motdH, dockX, dockY, dockBoxW, dockBoxH, dockRowW, todoX, todoY, todoW, todoH, shownTasks, todoCompact, compactLabel, todoLayout);
    }

    private static TodoLayout measureTodos(TextRenderer tr, List<String> rawTodos, int w, boolean interactive, int maxHeight) {
        List<String> todos = rawTodos != null ? rawTodos : List.of();
        if (todos.isEmpty()) {
            return new TodoLayout(List.of(), 0, 24 + 18);
        }
        List<WrappedTodo> rows = new ArrayList<>();
        int usedBodyH = 0;
        int yCursor = 0;
        int shown = 0;
        for (int i = 0; i < todos.size() && shown < MAX_VISIBLE_TODOS; i++) {
            String note = todos.get(i);
            String prefix = (i + 1) + ".";
            int prefixW = tr.getWidth(prefix) + 4;
            int maxTextW = Math.max(60, w - (interactive ? 36 : 18) - prefixW);
            List<OrderedText> lines = tr.wrapLines(Text.literal(note), maxTextW);
            if (lines.isEmpty()) lines = List.of(OrderedText.EMPTY);
            int rowH = Math.max(12, lines.size() * TODO_LINE_H) + TODO_ITEM_GAP;
            int extraFooterReserve = (i + 1 < todos.size()) ? 12 : 0;
            if (!rows.isEmpty() && 28 + yCursor + rowH + extraFooterReserve > maxHeight) {
                break;
            }
            rows.add(new WrappedTodo(i, prefix, prefixW, lines, yCursor, rowH));
            yCursor += rowH;
            usedBodyH = yCursor;
            shown++;
        }
        int extra = Math.max(0, todos.size() - rows.size());
        int totalH = 26 + usedBodyH + (extra > 0 ? 12 : 2);
        return new TodoLayout(rows, extra, totalH);
    }

    public static void render(DrawContext c, float tickDelta) {
        boolean interactive = isVisible();
        boolean joinBanner = !interactive && isJoinBannerVisible();
        if (!interactive && !joinBanner) {
            if (isPinnedTodoVisible()) {
                renderPinnedTodo(c);
            }
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        List<MicroItem> microItems = interactive ? buildMicroToolbar(client) : List.of();
        boolean showMotd = interactive ? cachedState.joinMotd : true;
        boolean showDock = interactive && cachedState.quickDock;
        boolean showTodo = interactive && cachedState.todoList;
        if (microItems.isEmpty() && !showMotd && !showDock && !showTodo) return;

        int mx = interactive ? (int) Math.round(client.mouse.getScaledX(client.getWindow())) : -9999;
        int my = interactive ? (int) Math.round(client.mouse.getScaledY(client.getWindow())) : -9999;

        int slideOffset;
        if (interactive) {
            openAnim = Math.min(1f, openAnim + tickDelta * 0.35f + 0.14f);
            slideOffset = Math.round((1f - openAnim) * 12f);
        } else {
            long elapsed = Math.max(0L, System.currentTimeMillis() - joinBannerStartedMs);
            long remaining = Math.max(0L, joinBannerUntilMs - System.currentTimeMillis());
            if (elapsed < 180L) {
                slideOffset = Math.round((1f - elapsed / 180f) * 12f);
            } else if (remaining < 240L) {
                slideOffset = Math.round((1f - remaining / 240f) * 14f);
            } else {
                slideOffset = 0;
            }
        }

        List<DockAction> actions = showDock ? buildActions() : List.of();
        Layout l = resolveLayout(tr, width, height, microItems, showMotd, showDock, showTodo, slideOffset, actions);
        String hoveredTooltip = null;

        if (!microItems.isEmpty()) {
            hoveredTooltip = renderMicroToolbar(c, l, microItems, mx, my, hoveredTooltip);
        }
        if (showMotd) {
            hoveredTooltip = renderMotdCard(c, tr, l, interactive, joinBanner, mx, my, hoveredTooltip);
        }
        if (showDock) {
            hoveredTooltip = renderDockBar(c, tr, l, width, actions, mx, my, hoveredTooltip);
        }
        if (showTodo) {
            if (l.todoCompact) {
                hoveredTooltip = renderCompactTodoPill(c, tr, l.todoX, l.todoY, l.todoW, l.todoH, l.compactTodoLabel, mx, my, hoveredTooltip);
            } else {
                hoveredTooltip = renderTodoCard(c, tr, l.todoX, l.todoY, l.todoW, l.todoH, l.todoLayout, true, mx, my, hoveredTooltip);
            }
        }

        if (interactive && hoveredTooltip != null) {
            c.drawTooltip(tr, Text.literal(hoveredTooltip), mx, my);
        }
    }

    private static String renderMicroToolbar(DrawContext c, Layout l, List<MicroItem> items, int mx, int my, String hoveredTooltip) {
        int by = l.dimY;
        for (MicroItem item : items) {
            int bx = l.dimX + item.xOffset;
            if (item.separator) {
                c.fill(bx + 3, by + 2, bx + 4, by + DIM_TILE_H - 2, AeternumTheme.BORDER_BRIGHT);
                continue;
            }
            boolean over = mx >= bx && mx < bx + DIM_TILE_W && my >= by && my < by + DIM_TILE_H;
            c.fill(bx - 1, by + 1, bx + DIM_TILE_W + 1, by + DIM_TILE_H + 2, 0x40000000);
            int bg = item.active ? 0xE61F2029 : (over ? 0xE6282834 : 0xCC121216);
            int border = item.active ? item.accent : (over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE);
            AeternumTheme.drawCard(c, bx, by, DIM_TILE_W, DIM_TILE_H, bg, border);
            if (item.active) {
                c.fill(bx + 1, by + 1, bx + DIM_TILE_W - 1, by + 2, item.accent);
            } else if (over) {
                c.fill(bx + 1, by + 1, bx + DIM_TILE_W - 1, by + 2, AeternumTheme.TEXT_PRIMARY);
            }
            c.drawTexture(RenderPipelines.GUI_TEXTURED, item.texture, bx + 2, by + 2, 0f, 0f, 12, 12, 12, 12);
            if (over && item.tooltip != null) {
                hoveredTooltip = item.tooltip;
            }
        }
        return hoveredTooltip;
    }

    private static String renderMotdCard(DrawContext c, TextRenderer tr, Layout l, boolean interactive, boolean joinBanner, int mx, int my, String hoveredTooltip) {
        c.fill(l.motdX - 2, l.motdY + 2, l.motdX + l.motdW + 2, l.motdY + l.motdH + 3, 0x45000000);
        c.fill(l.motdX, l.motdY, l.motdX + l.motdW, l.motdY + l.motdH, 0xE60D0D10);
        c.drawBorder(l.motdX, l.motdY, l.motdW, l.motdH, AeternumTheme.BORDER_BRIGHT);
        c.fill(l.motdX + 1, l.motdY + 1, l.motdX + l.motdW - 1, l.motdY + 2, AeternumTheme.TOP_HIGHLIGHT);

        c.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO_TEX, l.motdX + 10, l.motdY + 8, 0f, 0f, 28, 28, 28, 28);
        c.drawTextWithShadow(tr, "Aeternum Network", l.motdX + 46, l.motdY + 11, AeternumTheme.TEXT_PRIMARY);
        String subLine = (cachedState.activeTime != null && !cachedState.activeTime.isEmpty())
            ? cachedState.activeTime
            : ("Mantén [" + ScreenKeybinds.quickDockKeyName() + "]");
        c.drawText(tr, subLine, l.motdX + 46, l.motdY + 24, AeternumTheme.TEXT_SECONDARY, false);

        int pillW = 34;
        int pillGap = 4;
        int pillsStartX = l.motdX + l.motdW - 10 - (SERVERS.length * pillW + (SERVERS.length - 1) * pillGap);
        int pillY = l.motdY + 13;

        if (interactive && cachedState.isOp) {
            int editX = pillsStartX - 6 - EDIT_BTN_W;
            boolean overEdit = mx >= editX && mx < editX + EDIT_BTN_W && my >= pillY && my < pillY + EDIT_BTN_H;
            AeternumTheme.drawCard(c, editX, pillY, EDIT_BTN_W, EDIT_BTN_H,
                overEdit ? 0xFF2C2C36 : 0xFF17171D,
                overEdit ? AeternumTheme.ACCENT_GOLD : AeternumTheme.BORDER_SUBTLE);
            String editLabel = "Tareas";
            c.drawTextWithShadow(tr, editLabel, editX + (EDIT_BTN_W - tr.getWidth(editLabel)) / 2, pillY + 5,
                overEdit ? AeternumTheme.ACCENT_GOLD : AeternumTheme.TEXT_SECONDARY);
            if (overEdit) hoveredTooltip = "Editar tareas del MOTD";
        }

        for (int i = 0; i < SERVERS.length; i++) {
            String srv = SERVERS[i];
            int px = pillsStartX + i * (pillW + pillGap);
            boolean over = interactive && mx >= px && mx < px + pillW && my >= pillY && my < pillY + 18;
            AeternumTheme.drawCard(c, px, pillY, pillW, 18, over ? 0xFF2C2C36 : 0xFF17171D, over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE);
            c.drawTextWithShadow(tr, srv, px + (pillW - tr.getWidth(srv)) / 2, pillY + 5,
                over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
            if (over) hoveredTooltip = "Conectar a " + srv;
        }

        List<String> tasks = cachedState.tasks != null ? cachedState.tasks : List.of();
        if (l.shownTasks > 0) {
            int divY = l.motdY + 42;
            c.fill(l.motdX + 10, divY, l.motdX + l.motdW - 10, divY + 1, AeternumTheme.BORDER_SUBTLE);
            for (int i = 0; i < l.shownTasks; i++) {
                String prefix = "[" + (i + 1) + "] ";
                int ty = divY + 6 + i * 12;
                c.drawText(tr, prefix, l.motdX + 12, ty, AeternumTheme.TEXT_MUTED, false);
                List<OrderedText> wrapped = tr.wrapLines(MotdMarkup.parse(tasks.get(i)), l.motdW - 44);
                if (!wrapped.isEmpty()) {
                    c.drawTextWithShadow(tr, wrapped.get(0), l.motdX + 12 + tr.getWidth(prefix), ty, AeternumTheme.TEXT_PRIMARY);
                }
            }
        }

        if (joinBanner) {
            float progress = Math.max(0f, Math.min(1f, (joinBannerUntilMs - System.currentTimeMillis()) / (float) JOIN_BANNER_DURATION_MS));
            int barW = Math.round((l.motdW - 2) * progress);
            if (barW > 0) {
                c.fill(l.motdX + 1, l.motdY + l.motdH - 2, l.motdX + 1 + barW, l.motdY + l.motdH - 1, AeternumTheme.ACCENT_GOLD);
            }
        }
        return hoveredTooltip;
    }

    private static String renderDockBar(DrawContext c, TextRenderer tr, Layout l, int width, List<DockAction> actions, int mx, int my, String hoveredTooltip) {
        c.fill(l.dockX - 2, l.dockY + 2, l.dockX + l.dockBoxW + 2, l.dockY + l.dockBoxH + 3, 0x45000000);
        c.fill(l.dockX, l.dockY, l.dockX + l.dockBoxW, l.dockY + l.dockBoxH, 0xE60D0D10);
        c.drawBorder(l.dockX, l.dockY, l.dockBoxW, l.dockBoxH, AeternumTheme.BORDER_BRIGHT);
        c.fill(l.dockX + 1, l.dockY + 1, l.dockX + l.dockBoxW - 1, l.dockY + 2, AeternumTheme.TOP_HIGHLIGHT);

        if (actions.isEmpty()) {
            c.drawCenteredTextWithShadow(tr, "Sin módulos activos en el servidor.", width / 2, l.dockY + (l.dockBoxH - 8) / 2, AeternumTheme.TEXT_MUTED);
            return hoveredTooltip;
        }

        int startX = l.dockX + (l.dockBoxW - l.dockRowW) / 2;
        int tileY = l.dockY + 7;
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
        return hoveredTooltip;
    }

    private static String renderCompactTodoPill(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String label, int mx, int my, String hoveredTooltip) {
        List<String> todos = cachedState.todos != null ? cachedState.todos : List.of();
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
        c.fill(x - 1, y + 1, x + w + 1, y + h + 2, 0x40000000);
        AeternumTheme.drawCard(c, x, y, w, h, over ? 0xFF262630 : 0xCC121216,
            over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE);
        if (!todos.isEmpty() && MaterialsHudOverlay.todoPinned) {
            c.fill(x + 1, y + 1, x + w - 1, y + 2, AeternumTheme.ACCENT_GOLD);
        }
        c.drawTextWithShadow(tr, label, x + (w - tr.getWidth(label)) / 2, y + 5,
            over ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
        if (over) {
            hoveredTooltip = todos.isEmpty()
                ? "Añadir nota rápida"
                : ("Expandir tus notas (" + todos.size() + ")");
        }
        return hoveredTooltip;
    }

    private static String renderTodoCard(DrawContext c, TextRenderer tr, int x, int y, int w, int h, TodoLayout todoLayout, boolean interactive, int mx, int my, String hoveredTooltip) {
        List<String> todos = cachedState.todos != null ? cachedState.todos : List.of();
        boolean pinned = MaterialsHudOverlay.todoPinned;
        c.fill(x - 2, y + 2, x + w + 2, y + h + 3, 0x45000000);
        c.fill(x, y, x + w, y + h, interactive ? 0xE60D0D10 : 0xB80D0D10);
        c.drawBorder(x, y, w, h, interactive ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, pinned ? AeternumTheme.ACCENT_GOLD : AeternumTheme.TOP_HIGHLIGHT);

        String header = todos.isEmpty() ? "Notas" : ("Notas (" + todos.size() + ")");
        c.drawTextWithShadow(tr, header, x + 8, y + 7, AeternumTheme.TEXT_PRIMARY);

        if (interactive) {
            int btnY = y + 5;
            int addX = x + w - 22;
            boolean overAdd = mx >= addX && mx < addX + 15 && my >= btnY && my < btnY + 13;
            AeternumTheme.drawCard(c, addX, btnY, 15, 13, overAdd ? 0xFF2C2C36 : 0xFF17171D,
                overAdd ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE);
            c.drawTextWithShadow(tr, "+", addX + 5, btnY + 3, overAdd ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
            if (overAdd) hoveredTooltip = "Añadir nota rápida";

            int pinX = addX - 18;
            boolean overPin = mx >= pinX && mx < pinX + 15 && my >= btnY && my < btnY + 13;
            AeternumTheme.drawCard(c, pinX, btnY, 15, 13,
                pinned ? 0xFF2A2416 : (overPin ? 0xFF2C2C36 : 0xFF17171D),
                pinned ? AeternumTheme.ACCENT_GOLD : (overPin ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE));
            c.drawTextWithShadow(tr, "\u2022", pinX + 5, btnY + 3, pinned ? AeternumTheme.ACCENT_GOLD : AeternumTheme.TEXT_SECONDARY);
            if (overPin) hoveredTooltip = pinned ? "Desfijar notas del HUD" : "Fijar notas en la esquina superior izquierda";

            int minX = pinX - 18;
            boolean overMin = mx >= minX && mx < minX + 15 && my >= btnY && my < btnY + 13;
            AeternumTheme.drawCard(c, minX, btnY, 15, 13, overMin ? 0xFF2C2C36 : 0xFF17171D,
                overMin ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE);
            c.drawTextWithShadow(tr, "-", minX + 5, btnY + 3, overMin ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
            if (overMin) hoveredTooltip = "Minimizar notas";
        }

        int sepY = y + 21;
        c.fill(x + 8, sepY, x + w - 8, sepY + 1, AeternumTheme.BORDER_SUBTLE);

        if (todoLayout.rows.isEmpty()) {
            c.drawText(tr, interactive ? "Sin notas. Pulsa + para añadir." : "Sin notas pendientes.", x + 8, sepY + 5, AeternumTheme.TEXT_MUTED, false);
            return hoveredTooltip;
        }

        int listTop = sepY + 4;
        for (WrappedTodo row : todoLayout.rows) {
            int ry = listTop + row.yOffset;
            c.drawText(tr, row.prefix, x + 8, ry + 1, AeternumTheme.TEXT_MUTED, false);
            for (int li = 0; li < row.lines.size(); li++) {
                c.drawText(tr, row.lines.get(li), x + 8 + row.prefixW, ry + 1 + li * TODO_LINE_H, AeternumTheme.TEXT_PRIMARY, false);
            }

            if (interactive) {
                int delX = x + w - 20;
                boolean overDel = mx >= delX && mx < delX + 12 && my >= ry && my < ry + 12;
                if (overDel) {
                    c.fill(delX, ry, delX + 12, ry + 12, 0x45E55353);
                    hoveredTooltip = "Completar / borrar nota";
                }
                c.drawText(tr, "\u00D7", delX + 3, ry + 1, overDel ? AeternumTheme.ACCENT_RED : AeternumTheme.TEXT_MUTED, false);
            }
        }
        if (todoLayout.extraCount > 0) {
            WrappedTodo last = todoLayout.rows.get(todoLayout.rows.size() - 1);
            c.drawText(tr, "+" + todoLayout.extraCount + " más", x + 8, listTop + last.yOffset + last.height, AeternumTheme.TEXT_MUTED, false);
        }
        return hoveredTooltip;
    }

    private static void renderPinnedTodo(DrawContext c) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int todoW = Math.min(230, Math.max(180, screenW / 3));
        TodoLayout layout = measureTodos(tr, cachedState.todos, todoW, false, Math.max(80, screenH - 24));
        renderTodoCard(c, tr, 10, 10, todoW, layout.height, layout, false, -9999, -9999, null);
    }

    public static void handleClick(double mx, double my) {
        if (!isVisible()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        List<MicroItem> microItems = buildMicroToolbar(client);
        boolean showMotd = cachedState.joinMotd;
        boolean showDock = cachedState.quickDock;
        boolean showTodo = cachedState.todoList;
        if (microItems.isEmpty() && !showMotd && !showDock && !showTodo) return;

        List<DockAction> actions = showDock ? buildActions() : List.of();
        Layout l = resolveLayout(client.textRenderer, width, height, microItems, showMotd, showDock, showTodo, 0, actions);

        if (!microItems.isEmpty()) {
            String currentDim = client.player.getWorld().getRegistryKey().getValue().toString();
            int by = l.dimY;
            for (MicroItem item : microItems) {
                if (item.separator) continue;
                int bx = l.dimX + item.xOffset;
                if (mx >= bx && mx < bx + DIM_TILE_W && my >= by && my < by + DIM_TILE_H) {
                    if (client.player.networkHandler == null) return;
                    if ("cs".equals(item.action)) {
                        client.player.networkHandler.sendChatCommand("cs");
                        closeAfterAction(client, true);
                    } else if (item.action != null && item.action.startsWith("cb:")) {
                        String nextMode = item.action.substring(3);
                        cachedState.carefulBreakMode = nextMode;
                        client.player.networkHandler.sendChatCommand("carefulbreak " + nextMode);
                    } else if (item.action != null && item.action.startsWith("dim:")) {
                        if (item.worldId == null || !item.worldId.equals(currentDim)) {
                            client.player.networkHandler.sendChatCommand(item.action.substring(4));
                            closeAfterAction(client, true);
                        }
                    }
                    return;
                }
            }
        }

        if (showMotd) {
            int pillW = 34;
            int pillGap = 4;
            int pillsStartX = l.motdX + l.motdW - 10 - (SERVERS.length * pillW + (SERVERS.length - 1) * pillGap);
            int pillY = l.motdY + 13;

            if (cachedState.isOp) {
                int editX = pillsStartX - 6 - EDIT_BTN_W;
                if (mx >= editX && mx < editX + EDIT_BTN_W && my >= pillY && my < pillY + EDIT_BTN_H) {
                    closeAfterAction(client, false);
                    client.setScreen(new MotdTasksScreen(null, cachedTasks(), cachedState.isOp));
                    return;
                }
            }

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
        }

        if (showDock && !actions.isEmpty()) {
            int startX = l.dockX + (l.dockBoxW - l.dockRowW) / 2;
            int tileY = l.dockY + 7;
            for (int i = 0; i < actions.size(); i++) {
                int tx = startX + i * (TILE_W + TILE_GAP);
                if (mx >= tx && mx < tx + TILE_W && my >= tileY && my < tileY + TILE_H) {
                    ClientPlayNetworking.send(new ScreenOpenRequestPayload(actions.get(i).screenId));
                    closeAfterAction(client, false);
                    return;
                }
            }
        }

        if (showTodo) {
            if (l.todoCompact) {
                if (mx >= l.todoX && mx < l.todoX + l.todoW && my >= l.todoY && my < l.todoY + l.todoH) {
                    if (cachedState.todos == null || cachedState.todos.isEmpty()) {
                        closeAfterAction(client, false);
                        client.setScreen(new TodoQuickInputScreen());
                    } else {
                        MaterialsHudOverlay.todoCollapsed = false;
                        MaterialsHudOverlay.save();
                    }
                    return;
                }
            } else {
                int btnY = l.todoY + 5;
                int addX = l.todoX + l.todoW - 22;
                if (mx >= addX && mx < addX + 15 && my >= btnY && my < btnY + 13) {
                    closeAfterAction(client, false);
                    client.setScreen(new TodoQuickInputScreen());
                    return;
                }
                int pinX = addX - 18;
                if (mx >= pinX && mx < pinX + 15 && my >= btnY && my < btnY + 13) {
                    MaterialsHudOverlay.todoPinned = !MaterialsHudOverlay.todoPinned;
                    MaterialsHudOverlay.save();
                    return;
                }
                int minX = pinX - 18;
                if (mx >= minX && mx < minX + 15 && my >= btnY && my < btnY + 13) {
                    MaterialsHudOverlay.todoCollapsed = true;
                    MaterialsHudOverlay.save();
                    return;
                }
                int listTop = l.todoY + 25;
                int delX = l.todoX + l.todoW - 20;
                for (WrappedTodo row : l.todoLayout.rows) {
                    int ry = listTop + row.yOffset;
                    if (mx >= delX && mx < delX + 12 && my >= ry && my < ry + 12) {
                        ClientPlayNetworking.send(new TodoActionPayload("delete", row.index, ""));
                        return;
                    }
                }
            }
        }
    }

    public static List<String> cachedTasks() {
        return cachedState.tasks != null ? cachedState.tasks : List.of();
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

    private record DimButton(String label, String worldId, String command, Identifier texture, int accent) {}

    private record MicroItem(boolean separator, int xOffset, String action, String worldId, Identifier texture, int accent, boolean active, String tooltip) {}

    private record WrappedTodo(int index, String prefix, int prefixW, List<OrderedText> lines, int yOffset, int height) {}

    private record TodoLayout(List<WrappedTodo> rows, int extraCount, int height) {
        static final TodoLayout EMPTY = new TodoLayout(List.of(), 0, 0);
    }

    private record Layout(
        int dimX, int dimY,
        int motdX, int motdY, int motdW, int motdH,
        int dockX, int dockY, int dockBoxW, int dockBoxH, int dockRowW,
        int todoX, int todoY, int todoW, int todoH,
        int shownTasks, boolean todoCompact, String compactTodoLabel, TodoLayout todoLayout
    ) {}
}
