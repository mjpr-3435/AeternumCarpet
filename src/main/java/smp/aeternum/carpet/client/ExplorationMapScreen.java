package smp.aeternum.carpet.client;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.network.*;

/** Server-authoritative, one-pixel-per-block map. Textures are temporary and freed on close. */
final class ExplorationMapScreen extends Screen {
    private static final int TILE = 128;
    private static final int HEADER_H = 38;
    private static final int FOOTER_H = 44;

    private List<ExplorationMapOpenPayload.Area> areas;
    private final Map<Key, Identifier> tiles = new HashMap<>();
    private final Set<Key> requested = new HashSet<>();
    private String dimension;
    private double centerX, centerZ, zoom = 1;
    private int maxRequests;
    private float mspt;
    private long lastStatusRequest;
    private boolean dragging, showChunkGrid, showRegionGrid, formOpen, selecting;
    private double lastX, lastY;
    private int selStartChunkX, selStartChunkZ, selEndChunkX, selEndChunkZ;

    private int panelX, panelY, panelW, panelH;
    private int mapL, mapT, mapR, mapB;
    private int formX, formY, formW, formH;

    private UiButton dimButton, chunkGridButton, regionGridButton, maxReqButton;
    private UiButton clearVisibleButton, clearAllButton, closeFooterButton;
    private UiButton submitButton, cancelButton, deleteButton;
    private UiTextField nameField;
    private int blinkMaxReq, blinkClearAll, blinkClearVisible;
    private ExplorationMapOpenPayload.Area editingArea;

    ExplorationMapScreen(ExplorationMapOpenPayload data) {
        super(Text.literal("Mapa de exploración"));
        dimension = data.dimension();
        centerX = data.centerChunkX() * 16.0;
        centerZ = data.centerChunkZ() * 16.0;
        maxRequests = data.maxRequests();
        mspt = data.mspt();
        areas = data.areas();
    }

    /** A server save sends a fresh area list; keep the map viewport and loaded textures intact. */
    void replaceData(ExplorationMapOpenPayload data) {
        areas = data.areas();
        maxRequests = data.maxRequests();
        mspt = data.mspt();
        if (maxReqButton != null) maxReqButton.setMessage(Text.literal("Req: " + maxRequests));
    }

    void acceptMspt(float value) {
        mspt = value;
    }

    private void computeLayout() {
        panelX = 10;
        panelY = 8;
        panelW = Math.max(320, width - 20);
        panelH = Math.max(220, height - 16);
        mapL = panelX + 8;
        mapT = panelY + HEADER_H + 4;
        mapR = panelX + panelW - 8;
        mapB = panelY + panelH - FOOTER_H - 4;
        formW = Math.min(264, panelW - 24);
        formH = 112;
        formX = (width - formW) / 2;
        formY = (height - formH) / 2;
    }

    @Override
    protected void init() {
        clearChildren();
        computeLayout();

        int btnY = panelY + panelH - 26;
        int x = panelX + 10;

        dimButton = UiButton.secondary(x, btnY, 92, 19, "Dim: " + shortDim(), b -> {
            String[] dims = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
            for (int i = 0; i < dims.length; i++) {
                if (dims[i].equals(dimension)) {
                    dimension(dims[(i + 1) % dims.length]);
                    break;
                }
            }
            updateDimButton();
        });
        dimButton.setTooltip(Tooltip.of(Text.literal("Cambiar dimensión vista en el mapa")));
        addDrawableChild(dimButton);
        updateDimButton();
        x += 96;

        chunkGridButton = UiButton.secondary(x, btnY, 64, 19, "Chunks", b -> {
            showChunkGrid = !showChunkGrid;
            updateGridButtons();
        });
        chunkGridButton.setTooltip(Tooltip.of(Text.literal("Mostrar / ocultar cuadrícula de chunks (16×16)")));
        addDrawableChild(chunkGridButton);
        x += 68;

        regionGridButton = UiButton.secondary(x, btnY, 72, 19, "Regiones", b -> {
            showRegionGrid = !showRegionGrid;
            updateGridButtons();
        });
        regionGridButton.setTooltip(Tooltip.of(Text.literal("Mostrar / ocultar cuadrícula de regiones (.mca 512×512)")));
        addDrawableChild(regionGridButton);
        updateGridButtons();
        x += 76;

        maxReqButton = UiButton.secondary(x, btnY, 54, 19, "Req: " + maxRequests, b -> {
            int[] vals = {4, 8, 16, 32, 48, 64};
            int next = 4;
            for (int v : vals) {
                if (v > maxRequests) {
                    next = v;
                    break;
                }
            }
            maxRequests = next;
            b.setMessage(Text.literal("Req: " + maxRequests));
            blinkMaxReq = 10;
            b.active = false;
            ClientPlayNetworking.send(new ExplorationMapMaxRequestsPayload(maxRequests));
        });
        maxReqButton.setTooltip(Tooltip.of(Text.literal("Máximo de tiles solicitados simultáneamente al servidor")));
        addDrawableChild(maxReqButton);
        x += 58;

        clearVisibleButton = UiButton.secondary(x, btnY, 84, 19, "Limpiar vista", b -> {
            blinkClearVisible = 10;
            b.active = false;
            List<ExplorationMapClearCachePayload.TileCoord> coords = new ArrayList<>();
            if (client != null) {
                for (var e : tiles.entrySet()) {
                    coords.add(new ExplorationMapClearCachePayload.TileCoord(e.getKey().x, e.getKey().z));
                    client.getTextureManager().destroyTexture(e.getValue());
                }
            }
            tiles.clear();
            ClientPlayNetworking.send(new ExplorationMapClearCachePayload(dimension, true, coords));
        });
        clearVisibleButton.setTooltip(Tooltip.of(Text.literal("Recargar los tiles actualmente cargados")));
        addDrawableChild(clearVisibleButton);
        x += 88;

        clearAllButton = UiButton.danger(x, btnY, 82, 19, "Limpiar todo", b -> {
            blinkClearAll = 10;
            b.active = false;
            if (client != null) {
                for (Identifier id : tiles.values()) client.getTextureManager().destroyTexture(id);
            }
            tiles.clear();
            requested.clear();
            ClientPlayNetworking.send(new ExplorationMapClearCachePayload(dimension, false, List.of()));
        });
        clearAllButton.setTooltip(Tooltip.of(Text.literal("Vaciar toda la caché del mapa en esta dimensión")));
        addDrawableChild(clearAllButton);

        closeFooterButton = UiButton.ghost(panelX + panelW - 72, btnY, 62, 19, "Cerrar", b -> close());
        addDrawableChild(closeFooterButton);

        nameField = new UiTextField(textRenderer, formX + 12, formY + 48, formW - 24, 19, "Nombre de la región");
        nameField.setMaxLength(64);
        submitButton = UiButton.primary(formX + formW - 116, formY + formH - 28, 104, 19, "Nueva región", b -> submit());
        deleteButton = UiButton.danger(formX + 12, formY + formH - 28, 76, 19, "Eliminar", b -> delete());
        cancelButton = UiButton.ghost(formX + formW - 28, formY + 6, 20, 18, "\u00D7", b -> toggleForm(false));
        addDrawableChild(nameField);
        addDrawableChild(submitButton);
        addDrawableChild(deleteButton);
        addDrawableChild(cancelButton);
        toggleForm(formOpen);
    }

    private void toggleForm(boolean open) {
        formOpen = open;
        if (nameField != null) {
            nameField.visible = open;
            submitButton.visible = open;
            cancelButton.visible = open;
            deleteButton.visible = open && editingArea != null;
            boolean bar = !open;
            if (dimButton != null) dimButton.visible = bar;
            if (chunkGridButton != null) chunkGridButton.visible = bar;
            if (regionGridButton != null) regionGridButton.visible = bar;
            if (maxReqButton != null) maxReqButton.visible = bar;
            if (clearAllButton != null) clearAllButton.visible = bar;
            if (clearVisibleButton != null) clearVisibleButton.visible = bar;
            if (closeFooterButton != null) closeFooterButton.visible = bar;
        }
    }

    private void openCreate() {
        editingArea = null;
        nameField.setText("");
        submitButton.setMessage(Text.literal("Nueva región"));
        toggleForm(true);
        setFocused(nameField);
        nameField.setFocused(true);
    }

    private void openEdit(ExplorationMapOpenPayload.Area area) {
        editingArea = area;
        nameField.setText(area.name());
        selStartChunkX = area.minChunkX();
        selStartChunkZ = area.minChunkZ();
        selEndChunkX = area.maxChunkX();
        selEndChunkZ = area.maxChunkZ();
        submitButton.setMessage(Text.literal("Aplicar cambios"));
        toggleForm(true);
        setFocused(nameField);
        nameField.setFocused(true);
    }

    private void submit() {
        String name = nameField.getText().trim();
        if (name.isBlank() || selStartChunkX > selEndChunkX || selStartChunkZ > selEndChunkZ) return;
        int minCX = Math.min(selStartChunkX, selEndChunkX);
        int maxCX = Math.max(selStartChunkX, selEndChunkX);
        int minCZ = Math.min(selStartChunkZ, selEndChunkZ);
        int maxCZ = Math.max(selStartChunkZ, selEndChunkZ);
        if (editingArea == null) {
            ClientPlayNetworking.send(new ExplorationMapCreateRegionPayload(name, dimension, minCX, minCZ, maxCX, maxCZ));
        } else {
            ClientPlayNetworking.send(new ExplorationMapUpdateRegionPayload(editingArea.name(), name, editingArea.dimension(), minCX, minCZ, maxCX, maxCZ, false));
        }
        toggleForm(false);
    }

    private void delete() {
        if (editingArea == null) return;
        ClientPlayNetworking.send(new ExplorationMapUpdateRegionPayload(editingArea.name(), "", dimension, 0, 0, 0, 0, true));
        toggleForm(false);
    }

    private void updateDimButton() {
        if (dimButton == null) return;
        dimButton.setMessage(Text.literal("Dim: " + shortDim()));
        int accent = switch (dimension) {
            case "minecraft:the_nether" -> 0xFFD452FF;
            case "minecraft:the_end" -> AeternumTheme.ACCENT_GOLD;
            default -> AeternumTheme.ACCENT_GREEN;
        };
        dimButton.setCustomAccent(accent);
    }

    private void updateGridButtons() {
        if (chunkGridButton != null) {
            chunkGridButton.setVariant(showChunkGrid ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
            chunkGridButton.setCustomAccent(showChunkGrid ? AeternumTheme.ACCENT_CYAN : -1);
        }
        if (regionGridButton != null) {
            regionGridButton.setVariant(showRegionGrid ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
            regionGridButton.setCustomAccent(showRegionGrid ? AeternumTheme.ACCENT_GOLD : -1);
        }
    }

    private void dimension(String next) {
        if (dimension.equals(next)) return;
        clear();
        dimension = next;
        centerX = centerZ = 0;
        zoom = 1;
        selecting = false;
    }

    private String shortDim() {
        return switch (dimension) {
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> "Overworld";
        };
    }

    void accept(ExplorationMapTilePayload p) {
        if (!p.dimension().equals(dimension) || p.colors().length != TILE * TILE) return;
        Key key = new Key(p.dimension(), p.regionX(), p.regionZ());
        Identifier old = tiles.remove(key);
        if (old != null) client.getTextureManager().destroyTexture(old);
        NativeImage image = new NativeImage(TILE, TILE, false);
        for (int z = 0; z < TILE; z++) {
            for (int x = 0; x < TILE; x++) {
                image.setColorArgb(x, z, p.colors()[z * TILE + x]);
            }
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "aeternum-map", image);
        texture.setFilter(false, false);
        texture.setClamp(true);
        // A record hash is not an identifier: e.g. coordinates offset by 31 can collide.
        // Use the actual tile address so streaming one tile can never replace another's GPU texture.
        Identifier id = Identifier.of("aeternum-carpet", "map/" + key.dimension.replace(':', '_') + "/" + key.x + "_" + key.z);
        client.getTextureManager().registerTexture(id, texture);
        tiles.put(key, id);
        requested.remove(key);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, AeternumTheme.BACKDROP_TOP, AeternumTheme.BACKDROP_BOTTOM);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        computeLayout();
        if (blinkMaxReq > 0 && --blinkMaxReq == 0 && maxReqButton != null) maxReqButton.active = true;
        if (blinkClearAll > 0 && --blinkClearAll == 0 && clearAllButton != null) clearAllButton.active = true;
        if (blinkClearVisible > 0 && --blinkClearVisible == 0 && clearVisibleButton != null) clearVisibleButton.active = true;
        long now = System.currentTimeMillis();
        if (now - lastStatusRequest >= 1000) {
            lastStatusRequest = now;
            ClientPlayNetworking.send(new ExplorationMapStatusRequestPayload());
        }

        renderBackground(c, mx, my, d);
        AeternumTheme.drawPanel(c, panelX, panelY, panelW, panelH);
        AeternumTheme.drawHeader(c, panelX, panelY, panelW, HEADER_H);
        c.fill(panelX + 1, panelY + 1, panelX + 4, panelY + HEADER_H - 1, AeternumTheme.ACCENT_GOLD);

        int l = mapL;
        int t = mapT;
        int r = mapR;
        int b = mapB;
        int cx = (l + r) / 2;
        int cy = (t + b) / 2;

        // Header left: title + dimension + MSPT
        String title = "Mapa de exploración";
        c.drawTextWithShadow(textRenderer, title, panelX + 14, panelY + 8, AeternumTheme.TEXT_PRIMARY);
        int dimTagColor = switch (dimension) {
            case "minecraft:the_nether" -> 0xFFD452FF;
            case "minecraft:the_end" -> AeternumTheme.ACCENT_GOLD;
            default -> AeternumTheme.ACCENT_GREEN;
        };
        int msptColor = mspt <= 35f ? AeternumTheme.ACCENT_GREEN : (mspt <= 48f ? AeternumTheme.ACCENT_GOLD : AeternumTheme.ACCENT_RED);
        String msptStr = String.format(Locale.ROOT, "%.1f mspt", mspt);
        int dimX = panelX + 14 + textRenderer.getWidth(title) + 8;
        String dimStr = shortDim().toUpperCase(Locale.ROOT);
        c.drawText(textRenderer, dimStr, dimX, panelY + 8, dimTagColor, false);
        int msptX = dimX + textRenderer.getWidth(dimStr) + 8;
        c.drawText(textRenderer, "· " + msptStr, msptX, panelY + 8, msptColor, false);
        if (!requested.isEmpty()) {
            c.drawText(textRenderer, "· Cargando " + requested.size(), msptX + textRenderer.getWidth("· " + msptStr) + 6, panelY + 8, AeternumTheme.ACCENT_CYAN, false);
        }

        // Header second line: cursor coordinates, chunk, region & zoom
        int clampedMx = Math.max(l, Math.min(r - 1, mx));
        int clampedMy = Math.max(t, Math.min(b - 1, my));
        int wx = (int) Math.floor(centerX + (clampedMx - cx) / zoom);
        int wz = (int) Math.floor(centerZ + (clampedMy - cy) / zoom);
        int chunkX = Math.floorDiv(wx, 16);
        int chunkZ = Math.floorDiv(wz, 16);
        String coordsLine = "Bloque " + wx + ", " + wz + "  ·  Chunk " + chunkX + ", " + chunkZ
            + "  ·  Región " + Math.floorDiv(chunkX, 32) + ", " + Math.floorDiv(chunkZ, 32)
            + "  ·  Zoom " + String.format(Locale.ROOT, "%.2fx", zoom);
        c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, coordsLine, panelW - 56), panelX + 14, panelY + 23, AeternumTheme.TEXT_SECONDARY, false);

        // Header close X
        drawHeaderCloseButton(c, mx, my);

        // Map viewport
        c.fill(l, t, r, b, 0xFF0E1015);
        c.enableScissor(l, t, r, b);
        request(l, t, r, b, cx, cy);
        for (var entry : tiles.entrySet()) {
            Key key = entry.getKey();
            if (!key.dimension.equals(dimension)) continue;
            int x = (int) Math.floor(cx + (key.x * TILE - centerX) * zoom);
            int y = (int) Math.floor(cy + (key.z * TILE - centerZ) * zoom);
            int x2 = (int) Math.ceil(x + TILE * zoom);
            int y2 = (int) Math.ceil(y + TILE * zoom);
            if (x2 > l && x < r && y2 > t && y < b) {
                c.drawTexture(RenderPipelines.GUI_TEXTURED, entry.getValue(), x, y, 0, 0, x2 - x, y2 - y, TILE, TILE, TILE, TILE);
            }
        }
        grid(c, l, t, r, b, cx, cy);
        areas(c, l, t, r, b, cx, cy);
        playerCursor(c, cx, cy);
        c.disableScissor();
        c.drawBorder(l, t, r - l, b - t, AeternumTheme.BORDER_BRIGHT);

        // Footer bar & subtle shortcut hint
        AeternumTheme.drawFooter(c, panelX, panelY + panelH - FOOTER_H, panelW, panelY + panelH);
        String hint = "Arrastra izq: mover  ·  Rueda: zoom  ·  Click der: región (OP)  ·  Espacio: tu pos  ·  F: 0,0";
        c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, hint, panelW - 24), panelX + 12, panelY + panelH - FOOTER_H + 5, AeternumTheme.TEXT_MUTED, false);

        if (formOpen) {
            c.fill(0, 0, width, height, 0x80000000);
            drawRegionFormPanel(c, mx, my, d);
        }

        super.render(c, mx, my, d);

        if (!formOpen && isHeaderCloseHit(mx, my)) {
            c.drawTooltip(textRenderer, Text.literal("Cerrar"), mx, my);
        }
    }

    private void drawHeaderCloseButton(DrawContext c, int mx, int my) {
        int bx = panelX + panelW - 28;
        int by = panelY + 9;
        boolean hover = !formOpen && isHeaderCloseHit(mx, my);
        if (hover) {
            c.fill(bx, by, bx + 20, by + 20, 0x40E55353);
            c.drawBorder(bx, by, 20, 20, AeternumTheme.withAlpha(AeternumTheme.ACCENT_RED, 0x90));
        }
        c.drawTextWithShadow(textRenderer, "\u00D7", bx + 7, by + 6, hover ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);
    }

    private boolean isHeaderCloseHit(double mx, double my) {
        int bx = panelX + panelW - 28;
        int by = panelY + 9;
        return mx >= bx && mx < bx + 20 && my >= by && my < by + 20;
    }

    private void drawRegionFormPanel(DrawContext c, int mx, int my, float d) {
        AeternumTheme.drawPanel(c, formX, formY, formW, formH);
        AeternumTheme.drawHeader(c, formX, formY, formW, 32);
        c.fill(formX + 1, formY + 1, formX + 4, formY + 31, AeternumTheme.ACCENT_GOLD);

        String title = editingArea == null ? "Nueva región" : ("Editar · " + editingArea.name());
        c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, title, formW - 44), formX + 12, formY + 6, AeternumTheme.TEXT_PRIMARY);
        int minCX = Math.min(selStartChunkX, selEndChunkX);
        int maxCX = Math.max(selStartChunkX, selEndChunkX);
        int minCZ = Math.min(selStartChunkZ, selEndChunkZ);
        int maxCZ = Math.max(selStartChunkZ, selEndChunkZ);
        String bounds = minCX + "," + minCZ + " \u2192 " + maxCX + "," + maxCZ + " (" + (maxCX - minCX + 1) + "×" + (maxCZ - minCZ + 1) + " chunks)";
        c.drawText(textRenderer, bounds, formX + 12, formY + 19, AeternumTheme.TEXT_SECONDARY, false);

        if (nameField != null) {
            nameField.renderBox(c, formX + 12, formY + 48, formW - 24, 19, mx, my, d);
        }
        AeternumTheme.drawFooter(c, formX, formY + formH - 36, formW, formY + formH);
    }

    private void request(int l, int t, int r, int b, int cx, int cy) {
        if (requested.size() >= maxRequests) return;
        int minX = (int) Math.floor(centerX + (l - cx) / zoom);
        int maxX = (int) Math.ceil(centerX + (r - cx) / zoom);
        int minZ = (int) Math.floor(centerZ + (t - cy) / zoom);
        int maxZ = (int) Math.ceil(centerZ + (b - cy) / zoom);
        int crx = Math.floorDiv((int) centerX, TILE);
        int crz = Math.floorDiv((int) centerZ, TILE);
        List<Key> todo = new ArrayList<>();
        for (int z = Math.floorDiv(minZ, TILE); z <= Math.floorDiv(maxZ, TILE); z++) {
            for (int x = Math.floorDiv(minX, TILE); x <= Math.floorDiv(maxX, TILE); x++) {
                Key key = new Key(dimension, x, z);
                if (!tiles.containsKey(key) && !requested.contains(key)) todo.add(key);
            }
        }
        todo.sort(Comparator.comparingInt(key -> Math.abs(key.x - crx) + Math.abs(key.z - crz)));
        for (Key key : todo) {
            if (requested.size() >= maxRequests) break;
            requested.add(key);
            ClientPlayNetworking.send(new ExplorationMapTileRequestPayload(dimension, key.x, key.z));
        }
    }

    private void areas(DrawContext c, int l, int t, int r, int b, int cx, int cy) {
        if (formOpen || selecting) highlight(c, l, t, r, b, cx, cy);
        for (var area : areas) {
            if (!area.dimension().equals(dimension)) continue;
            int x = Math.max(l, (int) (cx + (area.minChunkX() * 16 - centerX) * zoom));
            int y = Math.max(t, (int) (cy + (area.minChunkZ() * 16 - centerZ) * zoom));
            int x2 = Math.min(r, (int) (cx + ((area.maxChunkX() + 1) * 16 - centerX) * zoom));
            int y2 = Math.min(b, (int) (cy + ((area.maxChunkZ() + 1) * 16 - centerZ) * zoom));
            if (x2 <= x || y2 <= y) continue;
            c.fill(x, y, x2, y2, 0x3CE0A95B);
            c.drawBorder(x, y, x2 - x, y2 - y, AeternumTheme.ACCENT_GOLD);
            String label = area.name() + " · " + (area.maxChunkX() - area.minChunkX() + 1) + "×" + (area.maxChunkZ() - area.minChunkZ() + 1) + "c";
            int labelX = x2 + 4;
            int labelWidth = textRenderer.getWidth(label);
            if (labelX + labelWidth > r) labelX = Math.max(l + 2, x - labelWidth - 4);
            int labelY = Math.max(t + 2, Math.min(b - 12, y + 2));
            c.fill(labelX - 2, labelY - 1, labelX + labelWidth + 2, labelY + 10, 0xB00E1015);
            c.drawTextWithShadow(textRenderer, label, labelX, labelY, 0xFFFFD584);
        }
    }

    private void highlight(DrawContext c, int l, int t, int r, int b, int cx, int cy) {
        int minCX = Math.min(selStartChunkX, selEndChunkX);
        int maxCX = Math.max(selStartChunkX, selEndChunkX);
        int minCZ = Math.min(selStartChunkZ, selEndChunkZ);
        int maxCZ = Math.max(selStartChunkZ, selEndChunkZ);
        int x = (int) (cx + (minCX * 16 - centerX) * zoom);
        int y = (int) (cy + (minCZ * 16 - centerZ) * zoom);
        int x2 = (int) (x + (maxCX - minCX + 1) * 16 * zoom);
        int y2 = (int) (y + (maxCZ - minCZ + 1) * 16 * zoom);
        int clippedX = Math.max(l, x);
        int clippedY = Math.max(t, y);
        int clippedX2 = Math.min(r, x2);
        int clippedY2 = Math.min(b, y2);
        c.fill(clippedX, clippedY, clippedX2, clippedY2, 0x55E55353);
        c.drawBorder(clippedX, clippedY, Math.max(0, clippedX2 - clippedX), Math.max(0, clippedY2 - clippedY), AeternumTheme.ACCENT_RED);
    }

    private void grid(DrawContext c, int l, int t, int r, int b, int cx, int cy) {
        if (showChunkGrid && 16 * zoom >= 4) gridLines(c, l, t, r, b, cx, cy, 16, 0x30FFFFFF);
        if (showRegionGrid && 512 * zoom >= 4) gridLines(c, l, t, r, b, cx, cy, 512, 0x90E0E0E0);
    }

    private void playerCursor(DrawContext c, int cx, int cy) {
        if (client.player == null) return;
        if (!client.player.getWorld().getRegistryKey().getValue().toString().equals(dimension)) return;
        double px = cx + (client.player.getX() - centerX) * zoom;
        double py = cy + (client.player.getZ() - centerZ) * zoom;
        int ix = (int) Math.floor(px);
        int iy = (int) Math.floor(py);
        c.fill(ix - 4, iy - 4, ix + 4, iy + 4, AeternumTheme.ACCENT_RED);
        c.fill(ix - 2, iy - 2, ix + 2, iy + 2, AeternumTheme.TEXT_PRIMARY);
    }

    private void gridLines(DrawContext c, int l, int t, int r, int b, int cx, int cy, int spacing, int color) {
        int minX = (int) Math.floor(centerX + (l - cx) / zoom);
        int maxX = (int) Math.ceil(centerX + (r - cx) / zoom);
        int minZ = (int) Math.floor(centerZ + (t - cy) / zoom);
        int maxZ = (int) Math.ceil(centerZ + (b - cy) / zoom);
        for (int x = Math.floorDiv(minX, spacing) * spacing; x <= maxX; x += spacing) {
            int sx = (int) Math.floor(cx + (x - centerX) * zoom);
            if (sx >= l && sx < r) c.fill(sx, t, sx + 1, b, color);
        }
        for (int z = Math.floorDiv(minZ, spacing) * spacing; z <= maxZ; z += spacing) {
            int sy = (int) Math.floor(cy + (z - centerZ) * zoom);
            if (sy >= t && sy < b) c.fill(l, sy, r, sy + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (formOpen) {
            super.mouseClicked(x, y, button);
            return true;
        }
        if (button == 0 && isHeaderCloseHit(x, y)) {
            close();
            return true;
        }
        boolean inMap = x >= mapL && x < mapR && y >= mapT && y < mapB;
        int cx = (mapL + mapR) / 2;
        int cy = (mapT + mapB) / 2;
        if (button == 1 && inMap && client.player != null && client.player.hasPermissionLevel(2)) {
            selStartChunkX = selEndChunkX = Math.floorDiv((int) Math.floor(centerX + (x - cx) / zoom), 16);
            selStartChunkZ = selEndChunkZ = Math.floorDiv((int) Math.floor(centerZ + (y - cy) / zoom), 16);
            selecting = true;
            return true;
        }
        if (button == 0 && inMap) {
            dragging = true;
            lastX = x;
            lastY = y;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (formOpen) {
            super.mouseDragged(x, y, button, dx, dy);
            return true;
        }
        int cx = (mapL + mapR) / 2;
        int cy = (mapT + mapB) / 2;
        if (selecting && button == 1) {
            selEndChunkX = Math.floorDiv((int) Math.floor(centerX + (x - cx) / zoom), 16);
            selEndChunkZ = Math.floorDiv((int) Math.floor(centerZ + (y - cy) / zoom), 16);
            return true;
        }
        if (dragging && button == 0) {
            centerX -= (x - lastX) / zoom;
            centerZ -= (y - lastY) / zoom;
            lastX = x;
            lastY = y;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (formOpen) {
            super.mouseReleased(x, y, button);
            return true;
        }
        if (selecting && button == 1) {
            selecting = false;
            int minCX = Math.min(selStartChunkX, selEndChunkX);
            int maxCX = Math.max(selStartChunkX, selEndChunkX);
            int minCZ = Math.min(selStartChunkZ, selEndChunkZ);
            int maxCZ = Math.max(selStartChunkZ, selEndChunkZ);
            if (selStartChunkX == selEndChunkX && selStartChunkZ == selEndChunkZ) {
                for (int i = areas.size() - 1; i >= 0; i--) {
                    var area = areas.get(i);
                    if (area.dimension().equals(dimension)
                        && selStartChunkX >= area.minChunkX() && selStartChunkX <= area.maxChunkX()
                        && selStartChunkZ >= area.minChunkZ() && selStartChunkZ <= area.maxChunkZ()) {
                        openEdit(area);
                        return true;
                    }
                }
            }
            for (int i = areas.size() - 1; i >= 0; i--) {
                var area = areas.get(i);
                if (area.dimension().equals(dimension)
                    && area.minChunkX() == minCX && area.minChunkZ() == minCZ
                    && area.maxChunkX() == maxCX && area.maxChunkZ() == maxCZ) {
                    openEdit(area);
                    return true;
                }
            }
            openCreate();
            return true;
        }
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (formOpen || selecting) return true;
        if (v == 0) return false;
        int cx = (mapL + mapR) / 2;
        int cy = (mapT + mapB) / 2;
        double old = zoom;
        zoom = Math.max(.0625, Math.min(16, zoom * Math.pow(1.18, v)));
        centerX += (x - cx) * (1 / old - 1 / zoom);
        centerZ += (y - cy) * (1 / old - 1 / zoom);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (formOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                toggleForm(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submit();
                return true;
            }
            // Let the focused input edit itself, then consume every key so map
            // shortcuts such as Space and F cannot run behind the dialog.
            super.keyPressed(key, scan, modifiers);
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE && client.player != null) {
            centerX = client.player.getBlockX();
            centerZ = client.player.getBlockZ();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F) {
            centerX = centerZ = 0;
            zoom = 1;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        clear();
        client.setScreen(null);
    }

    private void clear() {
        if (client != null) {
            for (Identifier id : tiles.values()) client.getTextureManager().destroyTexture(id);
        }
        tiles.clear();
        requested.clear();
    }

    private record Key(String dimension, int x, int z) { }
}
