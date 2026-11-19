package smp.aeternum.carpet.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.network.ProjectUploadPayload;

public final class LitematicPickerScreen extends UiScreen {
    private static final int CARD_H = 26;
    private static final int CARD_GAP = 3;
    private static final int ROW_STRIDE = CARD_H + CARD_GAP;
    private static final int MAX_UPLOAD_BYTES = 1_000_000;

    private final Screen parent;
    private final Path rootDir = LitematicaBridge.schematicsRoot();
    private Path currentDir = rootDir;
    private final List<Path> allSchematics = new ArrayList<>();
    private final List<BrowserEntry> visibleEntries = new ArrayList<>();

    private Path selectedFile;
    private String errorMsg = "";

    private double scroll, targetScroll;
    private boolean draggingScrollbar;

    private UiTextField searchField;
    private UiTextField nameField;
    private UiButton upDirBtn, uploadBtn;

    public LitematicPickerScreen(Screen parent) {
        super(Text.literal("Subir Litematic"));
        this.parent = parent;
        scanAllFiles();
        rebuildEntries();
    }

    private void scanAllFiles() {
        allSchematics.clear();
        if (!Files.isDirectory(rootDir)) return;
        try (var stream = Files.walk(rootDir, 6)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic"))
                .sorted(Comparator.comparing(p -> rootDir.relativize(p).toString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(allSchematics::add);
        } catch (IOException ignored) {
        }
    }

    private void rebuildEntries() {
        visibleEntries.clear();
        String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        if (!q.isEmpty()) {
            for (Path p : allSchematics) {
                String rel = rootDir.relativize(p).toString().replace('\\', '/');
                if (rel.toLowerCase().contains(q)) {
                    visibleEntries.add(new BrowserEntry(false, p, rel, fileSizeKb(p)));
                }
            }
            return;
        }

        if (!Files.isDirectory(currentDir)) {
            currentDir = rootDir;
        }
        List<Path> dirs = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(currentDir)) {
            stream.forEach(p -> {
                if (Files.isDirectory(p)) dirs.add(p);
                else if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".litematic")) files.add(p);
            });
        } catch (IOException ignored) {
        }

        dirs.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        files.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));

        if (!currentDir.equals(rootDir)) {
            visibleEntries.add(new BrowserEntry(true, currentDir.getParent(), "..", 0));
        }
        for (Path d : dirs) {
            visibleEntries.add(new BrowserEntry(true, d, d.getFileName().toString(), countInside(d)));
        }
        for (Path f : files) {
            visibleEntries.add(new BrowserEntry(false, f, f.getFileName().toString(), fileSizeKb(f)));
        }
    }

    private int countInside(Path dir) {
        String prefix = dir.toAbsolutePath().normalize().toString();
        int count = 0;
        for (Path p : allSchematics) {
            if (p.toAbsolutePath().normalize().toString().startsWith(prefix)) count++;
        }
        return count;
    }

    private static long fileSizeKb(Path file) {
        try {
            return Math.max(1, Files.size(file) / 1024);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    protected void init() {
        String prevSearch = searchField != null ? searchField.getText() : "";
        String prevName = nameField != null ? nameField.getText() : "";
        clearChildren();
        computePanel(Math.min(640, (int) (width * 0.76)), Math.min(430, (int) (height * 0.80)));

        addDrawableChild(UiButton.ghost(panelX + 10, panelY + 10, 24, 24, "\u2190", b -> close()));

        upDirBtn = UiButton.secondary(panelX + 12, panelY + 52, 60, 20, "\u2191 Atrás", b -> {
            if (!currentDir.equals(rootDir) && currentDir.getParent() != null) {
                currentDir = currentDir.getParent();
                targetScroll = 0;
                scroll = 0;
                rebuildEntries();
            }
        });
        addDrawableChild(upDirBtn);

        searchField = new UiTextField(textRenderer, panelX + 78, panelY + 52, panelW - 90, 20, "Buscar .litematic en todas las carpetas…");
        searchField.setMaxLength(80);
        searchField.setText(prevSearch);
        searchField.setChangedListener(s -> {
            targetScroll = 0;
            scroll = 0;
            rebuildEntries();
        });
        addDrawableChild(searchField);

        int nameY = panelY + panelH - 64;
        nameField = new UiTextField(textRenderer, panelX + 12, nameY, panelW - 24, 20, "Nombre del proyecto (obligatorio)");
        nameField.setMaxLength(48);
        nameField.setText(prevName);
        addDrawableChild(nameField);

        int footerY = panelY + panelH - 28;
        addDrawableChild(UiButton.secondary(panelX + 12, footerY, 82, 20, "Reescanear", b -> {
            scanAllFiles();
            rebuildEntries();
        }));
        uploadBtn = UiButton.primary(panelX + panelW - 132, footerY, 120, 20, "Subir Proyecto", b -> submitUpload());
        addDrawableChild(uploadBtn);

        rebuildEntries();
    }

    private void submitUpload() {
        if (selectedFile == null || !Files.isRegularFile(selectedFile)) {
            errorMsg = "Selecciona un archivo .litematic de la lista.";
            return;
        }
        String projectName = nameField != null ? nameField.getText().trim() : "";
        if (projectName.isEmpty()) {
            errorMsg = "Escribe un nombre para el proyecto.";
            return;
        }
        try {
            long size = Files.size(selectedFile);
            if (size > MAX_UPLOAD_BYTES) {
                errorMsg = "Archivo demasiado grande (" + (size / 1024) + " KB, máx 976 KB).";
                return;
            }
            byte[] bytes = Files.readAllBytes(selectedFile);
            MinecraftClient mc = MinecraftClient.getInstance();
            String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "minecraft:overworld";
            BlockPos pos = mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN;
            ClientPlayNetworking.send(new ProjectUploadPayload(
                projectName,
                bytes,
                true,
                dim,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                "NONE",
                "NONE"
            ));
            close();
        } catch (IOException e) {
            errorMsg = "Error leyendo archivo: " + e.getMessage();
        }
    }

    private int listTop() { return panelY + 94; }
    private int listBottom() { return panelY + panelH - 72; }

    private int maxScrollPx() {
        int contentH = visibleEntries.size() * ROW_STRIDE - CARD_GAP;
        int viewH = listBottom() - listTop();
        return Math.max(0, contentH - viewH);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        computePanel(Math.min(640, (int) (width * 0.76)), Math.min(430, (int) (height * 0.80)));
        int maxScroll = maxScrollPx();
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        scroll += (targetScroll - scroll) * Math.min(1.0, d * 0.45 + 0.25);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        if (upDirBtn != null) {
            boolean searching = searchField != null && !searchField.getText().trim().isEmpty();
            upDirBtn.active = !searching && !currentDir.equals(rootDir);
        }

        renderBackground(c, mx, my, d);
        AeternumTheme.drawPanel(c, panelX, panelY, panelW, panelH);
        AeternumTheme.drawHeader(c, panelX, panelY, panelW, 44);
        c.fill(panelX + 1, panelY + 1, panelX + 4, panelY + 43, AeternumTheme.ACCENT_GOLD);
        c.drawTextWithShadow(textRenderer, "SUBIR .LITEMATIC", panelX + 38, panelY + 10, AeternumTheme.TEXT_PRIMARY);
        c.drawText(textRenderer, allSchematics.size() + " archivos en schematics/", panelX + 38, panelY + 25, AeternumTheme.TEXT_SECONDARY, false);
        drawCloseButton(c, mx, my);

        // Breadcrumb bar
        String relPath = currentDir.equals(rootDir) ? "schematics/" : "schematics/" + rootDir.relativize(currentDir).toString().replace('\\', '/') + "/";
        boolean searching = searchField != null && !searchField.getText().trim().isEmpty();
        String pathTxt = searching ? ("Resultados de búsqueda (" + visibleEntries.size() + ")") : relPath;
        c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, pathTxt, panelW - 28), panelX + 14, panelY + 79, AeternumTheme.TEXT_SECONDARY, false);

        int lTop = listTop(), lBottom = listBottom(), viewH = lBottom - lTop;
        int rowX = panelX + 12, rowW = panelW - 30;

        if (visibleEntries.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, searching ? "Sin coincidencias." : "Carpeta vacía.", width / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
        } else {
            c.enableScissor(panelX + 6, lTop, panelX + panelW - 10, lBottom);
            for (int i = 0; i < visibleEntries.size(); i++) {
                int ry = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
                if (ry + CARD_H < lTop || ry > lBottom) continue;

                BrowserEntry entry = visibleEntries.get(i);
                boolean isSel = !entry.isDir && entry.path.equals(selectedFile);
                boolean hover = my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW && my >= ry && my < ry + CARD_H;

                int bg = isSel ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : (entry.isDir ? AeternumTheme.CARD_ALT_BG : AeternumTheme.CARD_BG));
                int border = isSel ? AeternumTheme.TEXT_PRIMARY : (entry.isDir ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE);
                AeternumTheme.drawCard(c, rowX, ry, rowW, CARD_H, bg, border);

                if (entry.isDir) {
                    int tagW = AeternumTheme.drawBadge(c, textRenderer, rowX + 8, ry + 6, "..".equals(entry.label) ? "SUBIR" : "DIR", 0xFF202028, AeternumTheme.TEXT_PRIMARY);
                    c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, entry.label, rowW - tagW - 84), rowX + 14 + tagW, ry + 9, AeternumTheme.TEXT_PRIMARY);
                    if (!"..".equals(entry.label)) {
                        String cnt = entry.meta + " archivos";
                        c.drawText(textRenderer, cnt, rowX + rowW - textRenderer.getWidth(cnt) - 10, ry + 9, AeternumTheme.TEXT_MUTED, false);
                    }
                } else {
                    c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, entry.label, rowW - 84), rowX + 10, ry + 9, AeternumTheme.TEXT_PRIMARY);
                    String kb = entry.meta + " KB";
                    c.drawText(textRenderer, kb, rowX + rowW - textRenderer.getWidth(kb) - 10, ry + 9, AeternumTheme.TEXT_MUTED, false);
                }
            }
            c.disableScissor();

            AeternumTheme.drawScrollbar(c, panelX + panelW - 12, lTop, lBottom, visibleEntries.size() * ROW_STRIDE - CARD_GAP, viewH, scroll, draggingScrollbar);
        }

        if (uploadBtn != null) uploadBtn.active = selectedFile != null && nameField != null && !nameField.getText().trim().isEmpty();

        AeternumTheme.drawFooter(c, panelX, panelY + panelH - 36, panelW, panelY + panelH);
        if (!errorMsg.isEmpty()) {
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, errorMsg, panelW - 240), panelX + 102, panelY + panelH - 22, AeternumTheme.TEXT_PRIMARY, false);
        } else if (selectedFile != null) {
            String selTxt = "Seleccionado: " + selectedFile.getFileName().toString();
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, selTxt, panelW - 240), panelX + 102, panelY + panelH - 22, AeternumTheme.TEXT_SECONDARY, false);
        }

        if (searchField != null) searchField.renderBox(c, panelX + 78, panelY + 52, panelW - 90, 20, mx, my, d);
        if (nameField != null) nameField.renderBox(c, panelX + 12, panelY + panelH - 64, panelW - 24, 20, mx, my, d);
        super.render(c, mx, my, d);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0 && isCloseButtonHit(mx, my)) {
            close();
            return true;
        }
        if (super.mouseClicked(mx, my, b)) return true;
        if (b != 0) return false;

        int lTop = listTop(), lBottom = listBottom(), viewH = lBottom - lTop;
        int maxScroll = maxScrollPx();
        if (maxScroll > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(my, lTop, viewH, maxScroll);
            return true;
        }

        int rowX = panelX + 12, rowW = panelW - 30;
        if (my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW) {
            int relY = (int) (my - lTop + scroll);
            int idx = relY / ROW_STRIDE;
            if (idx >= 0 && idx < visibleEntries.size() && (relY % ROW_STRIDE) < CARD_H) {
                BrowserEntry entry = visibleEntries.get(idx);
                if (entry.isDir) {
                    currentDir = entry.path != null ? entry.path : rootDir;
                    targetScroll = 0;
                    scroll = 0;
                    rebuildEntries();
                } else {
                    selectedFile = entry.path;
                    errorMsg = "";
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v != 0) {
            int maxScroll = maxScrollPx();
            targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - v * 28));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(my, listTop(), listBottom() - listTop(), maxScrollPx());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        if (b == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mx, my, b);
    }

    private void updateScrollFromMouse(double my, int top, int viewH, int maxScroll) {
        if (maxScroll == 0) return;
        int totalH = visibleEntries.size() * ROW_STRIDE - CARD_GAP;
        int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
        double f = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
        targetScroll = Math.max(0, Math.min(1, f)) * maxScroll;
        scroll = targetScroll;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private record BrowserEntry(boolean isDir, Path path, String label, long meta) {}
}
