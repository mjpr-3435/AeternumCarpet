package smp.aeternum.carpet.client;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.network.*;

/** Server-authoritative, one-pixel-per-block map. Textures are temporary and freed on close. */
final class ExplorationMapScreen extends Screen {
    private static final int TILE = 128;
    private List<ExplorationMapOpenPayload.Area> areas;
    private final Map<Key, Identifier> tiles = new HashMap<>();
    private final Set<Key> requested = new HashSet<>();
    private String dimension;
    private double centerX, centerZ, zoom = 1;
    private int maxRequests;
    private boolean dragging, showChunkGrid, showRegionGrid, formOpen;
    private double lastX, lastY;
    private ButtonWidget chunkGridButton, regionGridButton, submitButton, cancelButton, deleteButton;
    private ButtonWidget maxReqButton, clearAllButton, clearVisibleButton;
    private int blinkMaxReq, blinkClearAll, blinkClearVisible;
    private TextFieldWidget nameField, sizeField;
    private int selectedChunkX, selectedChunkZ;
    private ExplorationMapOpenPayload.Area editingArea;

    ExplorationMapScreen(ExplorationMapOpenPayload data) {
        super(Text.literal("Mapa de exploración"));
        dimension = data.dimension();
        centerX = data.centerChunkX() * 16.0;
        centerZ = data.centerChunkZ() * 16.0;
        maxRequests = data.maxRequests();
        areas = data.areas();
    }

    /** A server save sends a fresh area list; keep the map viewport and loaded textures intact. */
    void replaceData(ExplorationMapOpenPayload data) { areas = data.areas(); maxRequests = data.maxRequests(); if (maxReqButton != null) maxReqButton.setMessage(Text.literal("Req: " + maxRequests)); }

    @Override protected void init() {
        int y = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Dim: " + shortDim()), b -> {
            String[] dims = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
            for (int i = 0; i < dims.length; i++) if (dims[i].equals(dimension)) { dimension(dims[(i + 1) % dims.length]); break; }
            b.setMessage(Text.literal("Dim: " + shortDim()));
        }).dimensions(12, y, 80, 20).build());
        chunkGridButton = ButtonWidget.builder(Text.literal("Chunks: no"), b -> { showChunkGrid = !showChunkGrid; updateGridButtons(); }).dimensions(96, y, 88, 20).build();
        regionGridButton = ButtonWidget.builder(Text.literal("Regiones: no"), b -> { showRegionGrid = !showRegionGrid; updateGridButtons(); }).dimensions(188, y, 96, 20).build();
        addDrawableChild(chunkGridButton); addDrawableChild(regionGridButton);

        maxReqButton = ButtonWidget.builder(Text.literal("Req: " + maxRequests), b -> {
            int[] vals = {4, 8, 16, 32, 48, 64}; int next = 4;
            for (int v : vals) if (v > maxRequests) { next = v; break; }
            maxRequests = next; b.setMessage(Text.literal("Req: " + maxRequests));
            blinkMaxReq = 10; b.active = false;
            ClientPlayNetworking.send(new ExplorationMapMaxRequestsPayload(maxRequests));
        }).dimensions(288, y, 60, 20).build();
        clearAllButton = ButtonWidget.builder(Text.literal("Limpiar todo"), b -> {
            blinkClearAll = 10; b.active = false;
            if (client != null) for (Identifier id : tiles.values()) client.getTextureManager().destroyTexture(id);
            tiles.clear(); requested.clear();
            ClientPlayNetworking.send(new ExplorationMapClearCachePayload(dimension, false, List.of()));
        }).dimensions(352, y, 72, 20).build();
        clearVisibleButton = ButtonWidget.builder(Text.literal("Limpiar pantalla"), b -> {
            blinkClearVisible = 10; b.active = false;
            List<ExplorationMapClearCachePayload.TileCoord> coords = new ArrayList<>();
            if (client != null) for (var e : tiles.entrySet()) { coords.add(new ExplorationMapClearCachePayload.TileCoord(e.getKey().x, e.getKey().z)); client.getTextureManager().destroyTexture(e.getValue()); }
            tiles.clear();
            ClientPlayNetworking.send(new ExplorationMapClearCachePayload(dimension, true, coords));
        }).dimensions(428, y, 82, 20).build();
        addDrawableChild(maxReqButton); addDrawableChild(clearAllButton); addDrawableChild(clearVisibleButton);
        addDrawableChild(ButtonWidget.builder(Text.literal("Cerrar"), b -> close()).dimensions(width - 82, y, 70, 20).build());

        nameField = new TextFieldWidget(textRenderer, width / 2 - 100, height / 2 - 34, 176, 18, Text.literal("Nombre"));
        sizeField = new TextFieldWidget(textRenderer, width / 2 - 100, height / 2 - 10, 80, 18, Text.literal("Size"));
        sizeField.setMaxLength(8);
        submitButton = ButtonWidget.builder(Text.literal("Nueva región"), b -> submit()).dimensions(width / 2 - 24, height / 2 + 14, 124, 20).build();
        deleteButton = ButtonWidget.builder(Text.literal("Eliminar"), b -> delete()).dimensions(width / 2 - 100, height / 2 + 14, 70, 20).build();
        cancelButton = ButtonWidget.builder(Text.literal("X"), b -> toggleForm(false)).dimensions(width / 2 + 90, height / 2 - 52, 20, 18).build();
        addDrawableChild(nameField); addDrawableChild(sizeField); addDrawableChild(submitButton); addDrawableChild(deleteButton); addDrawableChild(cancelButton);
        toggleForm(false);
    }

    private void toggleForm(boolean open) {
        formOpen = open;
        if (nameField != null) {
            nameField.visible = open; sizeField.visible = open; submitButton.visible = open; cancelButton.visible = open;
            deleteButton.visible = open && editingArea != null;
            if (maxReqButton != null) maxReqButton.visible = !open;
            if (clearAllButton != null) clearAllButton.visible = !open;
            if (clearVisibleButton != null) clearVisibleButton.visible = !open;
        }
    }

    private void openCreate() {
        editingArea = null; nameField.setText(""); sizeField.setText("0"); submitButton.setMessage(Text.literal("Nueva región"));
        toggleForm(true); setFocused(nameField);
    }

    private void openEdit(ExplorationMapOpenPayload.Area area) {
        editingArea = area;
        nameField.setText(area.name());
        sizeField.setText(Integer.toString((area.maxChunkX() - area.minChunkX()) / 2));
        submitButton.setMessage(Text.literal("Aplicar cambios"));
        toggleForm(true); setFocused(nameField);
    }

    private void submit() {
        try {
            int size = Integer.parseInt(sizeField.getText());
            String name = nameField.getText().trim();
            if (name.isBlank() || size < 0) return;
            if (editingArea == null) {
                ClientPlayNetworking.send(new ExplorationMapCreateRegionPayload(name, dimension, selectedChunkX, selectedChunkZ, size));
            } else {
                ClientPlayNetworking.send(new ExplorationMapUpdateRegionPayload(editingArea.name(), name, dimension, selectedChunkX, selectedChunkZ, size, false));
            }
            toggleForm(false);
        } catch (NumberFormatException ignored) { }
    }

    private void delete() {
        if (editingArea == null) return;
        ClientPlayNetworking.send(new ExplorationMapUpdateRegionPayload(editingArea.name(), "", dimension, 0, 0, 0, true));
        toggleForm(false);
    }

    private void updateGridButtons() {
        chunkGridButton.setMessage(Text.literal("Chunks: " + (showChunkGrid ? "sí" : "no")));
        regionGridButton.setMessage(Text.literal("Regiones: " + (showRegionGrid ? "sí" : "no")));
    }

    private void dimension(String next) {
        if (dimension.equals(next)) return;
        clear(); dimension = next; centerX = centerZ = 0; zoom = 1;
    }

    private String shortDim() { return switch (dimension) { case "minecraft:the_nether" -> "Nether"; case "minecraft:the_end" -> "End"; default -> "Overworld"; }; }

    void accept(ExplorationMapTilePayload p) {
        if (!p.dimension().equals(dimension) || p.colors().length != TILE * TILE) return;
        Key key = new Key(p.dimension(), p.regionX(), p.regionZ());
        Identifier old = tiles.remove(key);
        if (old != null) client.getTextureManager().destroyTexture(old);
        NativeImage image = new NativeImage(TILE, TILE, false);
        for (int z = 0; z < TILE; z++) for (int x = 0; x < TILE; x++) image.setColorArgb(x, z, p.colors()[z * TILE + x]);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "aeternum-map", image);
        texture.setFilter(false, false); texture.setClamp(true);
        // A record hash is not an identifier: e.g. coordinates offset by 31 can collide.
        // Use the actual tile address so streaming one tile can never replace another's GPU texture.
        Identifier id = Identifier.of("aeternum-carpet", "map/" + key.dimension.replace(':', '_') + "/" + key.x + "_" + key.z);
        client.getTextureManager().registerTexture(id, texture); tiles.put(key, id); requested.remove(key);
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override public void render(DrawContext c, int mx, int my, float d) {
        if (blinkMaxReq > 0 && --blinkMaxReq == 0 && maxReqButton != null) maxReqButton.active = true;
        if (blinkClearAll > 0 && --blinkClearAll == 0 && clearAllButton != null) clearAllButton.active = true;
        if (blinkClearVisible > 0 && --blinkClearVisible == 0 && clearVisibleButton != null) clearVisibleButton.active = true;
        c.fill(0, 0, width, height, 0xE0101010);
        int l = 10, t = 28, r = width - 10, b = height - 36, cx = (l + r) / 2, cy = (t + b) / 2;
        c.fill(l, t, r, b, 0xFF20242A); c.enableScissor(l, t, r, b); request(l, t, r, b, cx, cy);
        for (var entry : tiles.entrySet()) {
            Key key = entry.getKey(); if (!key.dimension.equals(dimension)) continue;
            int x = (int) Math.floor(cx + (key.x * TILE - centerX) * zoom), y = (int) Math.floor(cy + (key.z * TILE - centerZ) * zoom);
            int x2 = (int) Math.ceil(x + TILE * zoom), y2 = (int) Math.ceil(y + TILE * zoom);
            if (x2 > l && x < r && y2 > t && y < b) c.drawTexture(RenderPipelines.GUI_TEXTURED, entry.getValue(), x, y, 0, 0, x2 - x, y2 - y, TILE, TILE, TILE, TILE);
        }
        grid(c, l, t, r, b, cx, cy); areas(c, l, t, r, b, cx, cy); c.disableScissor();
        c.drawBorder(l, t, r - l, b - t, 0xFF909090);
        int wx = (int) Math.floor(centerX + (mx - cx) / zoom), wz = (int) Math.floor(centerZ + (my - cy) / zoom), chunkX = Math.floorDiv(wx, 16), chunkZ = Math.floorDiv(wz, 16);
        c.drawTextWithShadow(textRenderer, "MAPA DE EXPLORACIÓN · " + dimension.substring(dimension.indexOf(':') + 1).toUpperCase(), 16, 10, 0xFFFFFFFF);
        c.drawTextWithShadow(textRenderer, "Bloque " + wx + ", " + wz + " · Chunk " + chunkX + ", " + chunkZ + " · Región " + Math.floorDiv(chunkX, 32) + ", " + Math.floorDiv(chunkZ, 32) + " | " + String.format(Locale.ROOT, "%.3fx", zoom), width - 430, 10, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "Arrastra · rueda: zoom · Espacio: 0,0 · F: tu posición", 16, height - 51, 0xFFAAAAAA);
        super.render(c, mx, my, d);
    }

    private void request(int l, int t, int r, int b, int cx, int cy) {
        if (requested.size() >= maxRequests) return;
        int minX = (int) Math.floor(centerX + (l - cx) / zoom), maxX = (int) Math.ceil(centerX + (r - cx) / zoom), minZ = (int) Math.floor(centerZ + (t - cy) / zoom), maxZ = (int) Math.ceil(centerZ + (b - cy) / zoom);
        int crx = Math.floorDiv((int) centerX, TILE), crz = Math.floorDiv((int) centerZ, TILE); List<Key> todo = new ArrayList<>();
        for (int z = Math.floorDiv(minZ, TILE); z <= Math.floorDiv(maxZ, TILE); z++) for (int x = Math.floorDiv(minX, TILE); x <= Math.floorDiv(maxX, TILE); x++) { Key key = new Key(dimension, x, z); if (!tiles.containsKey(key) && !requested.contains(key)) todo.add(key); }
        todo.sort(Comparator.comparingInt(key -> Math.abs(key.x - crx) + Math.abs(key.z - crz)));
        for (Key key : todo) { if (requested.size() >= maxRequests) break; requested.add(key); ClientPlayNetworking.send(new ExplorationMapTileRequestPayload(dimension, key.x, key.z)); }
    }

    private void areas(DrawContext c, int l, int t, int r, int b, int cx, int cy) {
        if (formOpen) highlight(c, l, t, r, b, cx, cy);
        for (var area : areas) {
            if (!area.dimension().equals(dimension)) continue;
            int x = Math.max(l, (int) (cx + (area.minChunkX() * 16 - centerX) * zoom)), y = Math.max(t, (int) (cy + (area.minChunkZ() * 16 - centerZ) * zoom));
            int x2 = Math.min(r, (int) (cx + ((area.maxChunkX() + 1) * 16 - centerX) * zoom)), y2 = Math.min(b, (int) (cy + ((area.maxChunkZ() + 1) * 16 - centerZ) * zoom));
            if (x2 <= x || y2 <= y) continue;
            c.fill(x, y, x2, y2, 0x48FF8A00); c.drawBorder(x, y, x2 - x, y2 - y, 0xFFFF9900);
            String label = area.name() + " · " + (area.maxChunkX() - area.minChunkX() + 1) + "×" + (area.maxChunkZ() - area.minChunkZ() + 1) + " chunks";
            int labelX = x2 + 4, labelWidth = textRenderer.getWidth(label); if (labelX + labelWidth > r) labelX = Math.max(l, x - labelWidth - 4);
            c.drawTextWithShadow(textRenderer, label, labelX, Math.max(t, y + 2), 0xFFFFB347);
        }
        if (formOpen) {
            c.fill(width / 2 - 112, height / 2 - 54, width / 2 + 112, height / 2 + 42, 0xF0101010);
            c.drawBorder(width / 2 - 112, height / 2 - 54, 224, 96, 0xFF909090);
            String title = editingArea == null ? "Nueva región" : "Editar región · " + editingArea.name();
            c.drawTextWithShadow(textRenderer, title + " · chunk " + selectedChunkX + ", " + selectedChunkZ, width / 2 - 100, height / 2 - 50, 0xFFFFFFFF);
        }
    }

    private void highlight(DrawContext c, int l, int t, int r, int b, int cx, int cy) {
        int x = (int) (cx + (selectedChunkX * 16 - centerX) * zoom), y = (int) (cy + (selectedChunkZ * 16 - centerZ) * zoom), x2 = (int) (x + 16 * zoom), y2 = (int) (y + 16 * zoom);
        c.fill(Math.max(l, x), Math.max(t, y), Math.min(r, x2), Math.min(b, y2), 0x66FF3030);
        c.drawBorder(Math.max(l, x), Math.max(t, y), Math.max(0, Math.min(r, x2) - Math.max(l, x)), Math.max(0, Math.min(b, y2) - Math.max(t, y)), 0xFFFF5555);
    }

    private void grid(DrawContext c, int l, int t, int r, int b, int cx, int cy) { if (showChunkGrid && 16 * zoom >= 4) gridLines(c, l, t, r, b, cx, cy, 16, 0x30303030); if (showRegionGrid && 512 * zoom >= 4) gridLines(c, l, t, r, b, cx, cy, 512, 0xA0E0E0E0); }
    private void gridLines(DrawContext c, int l, int t, int r, int b, int cx, int cy, int spacing, int color) { int minX = (int) Math.floor(centerX + (l - cx) / zoom), maxX = (int) Math.ceil(centerX + (r - cx) / zoom), minZ = (int) Math.floor(centerZ + (t - cy) / zoom), maxZ = (int) Math.ceil(centerZ + (b - cy) / zoom); for (int x = Math.floorDiv(minX, spacing) * spacing; x <= maxX; x += spacing) { int sx = (int) Math.floor(cx + (x - centerX) * zoom); if (sx >= l && sx < r) c.fill(sx, t, sx + 1, b, color); } for (int z = Math.floorDiv(minZ, spacing) * spacing; z <= maxZ; z += spacing) { int sy = (int) Math.floor(cy + (z - centerZ) * zoom); if (sy >= t && sy < b) c.fill(l, sy, r, sy + 1, color); } }

    @Override public boolean mouseClicked(double x, double y, int button) {
        // The region form is modal: no pointer event may fall through to the map.
        if (formOpen) {
            super.mouseClicked(x, y, button);
            return true;
        }
        if (button == 1 && x >= 10 && x < width - 10 && y >= 28 && y < height - 36 && client.player != null && client.player.hasPermissionLevel(2)) {
            int cx = width / 2, cy = (28 + height - 36) / 2; selectedChunkX = Math.floorDiv((int) Math.floor(centerX + (x - cx) / zoom), 16); selectedChunkZ = Math.floorDiv((int) Math.floor(centerZ + (y - cy) / zoom), 16);
            for (int i = areas.size() - 1; i >= 0; i--) { var area = areas.get(i); if (area.dimension().equals(dimension) && selectedChunkX >= area.minChunkX() && selectedChunkX <= area.maxChunkX() && selectedChunkZ >= area.minChunkZ() && selectedChunkZ <= area.maxChunkZ()) { openEdit(area); return true; } }
            openCreate(); return true;
        }
        if (button == 0 && x >= 10 && x < width - 10 && y >= 28 && y < height - 36) { dragging = true; lastX = x; lastY = y; return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { if (formOpen) { super.mouseDragged(x, y, button, dx, dy); return true; } if (dragging && button == 0) { centerX -= (x - lastX) / zoom; centerZ -= (y - lastY) / zoom; lastX = x; lastY = y; return true; } return super.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { if (formOpen) { super.mouseReleased(x, y, button); return true; } if (button == 0 && dragging) { dragging = false; return true; } return super.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) { if (formOpen) return true; if (v == 0) return false; int cx = width / 2, cy = (28 + height - 36) / 2; double old = zoom; zoom = Math.max(.0625, Math.min(16, zoom * Math.pow(1.18, v))); centerX += (x - cx) * (1 / old - 1 / zoom); centerZ += (y - cy) * (1 / old - 1 / zoom); return true; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (formOpen) {
            if (key == 256) { toggleForm(false); return true; }
            if (key == 257 || key == 335) { submit(); return true; }
            // Let the focused input edit itself, then consume every key so map
            // shortcuts such as Space and F cannot run behind the dialog.
            super.keyPressed(key, scan, modifiers);
            return true;
        }
        if (key == 32) { centerX = centerZ = 0; zoom = 1; return true; }
        if (key == 70 && client.player != null) { centerX = client.player.getBlockX(); centerZ = client.player.getBlockZ(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { clear(); client.setScreen(null); }
    private void clear() { if (client != null) for (Identifier id : tiles.values()) client.getTextureManager().destroyTexture(id); tiles.clear(); requested.clear(); }
    private record Key(String dimension, int x, int z) { }
}
