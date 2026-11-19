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
    private static final int CARD_H = 28;
    private static final int CARD_GAP = 3;
    private static final int ROW_STRIDE = CARD_H + CARD_GAP;
    private static final int MAX_UPLOAD_BYTES = 1_000_000;
    private static final String[] ROTATIONS = {"NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90"};

    private final Screen parent;
    private final List<Path> files = new ArrayList<>();
    private Path selectedFile;
    private boolean includePlacement = true;
    private int rotationIdx = 0;
    private String errorMsg = "";

    private double scroll, targetScroll;
    private boolean draggingScrollbar;

    private UiTextField searchField;
    private UiTextField nameField;
    private UiButton placementBtn, rotBtn, uploadBtn;

    public LitematicPickerScreen(Screen parent) {
        super(Text.literal("Subir Litematic"));
        this.parent = parent;
        scanFiles();
    }

    private void scanFiles() {
        files.clear();
        Path root = LitematicaBridge.schematicsRoot();
        if (!Files.isDirectory(root)) return;
        try (var stream = Files.walk(root, 4)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic"))
                .sorted(Comparator.comparing(p -> root.relativize(p).toString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(files::add);
        } catch (IOException ignored) {
        }
    }

    @Override
    protected void init() {
        String prevSearch = searchField != null ? searchField.getText() : "";
        String prevName = nameField != null ? nameField.getText() : "";
        clearChildren();
        computePanel(Math.min(640, (int) (width * 0.76)), Math.min(430, (int) (height * 0.80)));

        addDrawableChild(UiButton.ghost(panelX + 10, panelY + 10, 24, 24, "\u2190", b -> close()));

        searchField = new UiTextField(textRenderer, panelX + 12, panelY + 52, panelW - 24, 20, "Filtrar archivos en .minecraft/schematics/…");
        searchField.setMaxLength(80);
        searchField.setText(prevSearch);
        searchField.setChangedListener(s -> { targetScroll = 0; scroll = 0; });
        addDrawableChild(searchField);

        int configY = panelY + panelH - 64;
        nameField = new UiTextField(textRenderer, panelX + 12, configY, Math.max(140, panelW - 320), 20, "Nombre del proyecto");
        nameField.setMaxLength(48);
        nameField.setText(prevName);
        addDrawableChild(nameField);

        placementBtn = UiButton.secondary(panelX + panelW - 298, configY, 150, 20,
            includePlacement ? "Placement: Mi pos" : "Placement: Ninguno",
            b -> {
                includePlacement = !includePlacement;
                b.setLabel(includePlacement ? "Placement: Mi pos" : "Placement: Ninguno");
                b.setVariant(includePlacement ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
                if (rotBtn != null) rotBtn.active = includePlacement;
            });
        placementBtn.setVariant(includePlacement ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
        addDrawableChild(placementBtn);

        rotBtn = UiButton.secondary(panelX + panelW - 142, configY, 130, 20, "Rotación: " + rotLabel(ROTATIONS[rotationIdx]), b -> {
            rotationIdx = (rotationIdx + 1) % ROTATIONS.length;
            b.setLabel("Rotación: " + rotLabel(ROTATIONS[rotationIdx]));
        });
        rotBtn.active = includePlacement;
        addDrawableChild(rotBtn);

        int footerY = panelY + panelH - 28;
        addDrawableChild(UiButton.secondary(panelX + 12, footerY, 82, 20, "Reescanear", b -> scanFiles()));
        uploadBtn = UiButton.primary(panelX + panelW - 132, footerY, 120, 20, "Subir Proyecto", b -> submitUpload());
        addDrawableChild(uploadBtn);
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
                includePlacement,
                dim,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                ROTATIONS[rotationIdx],
                "NONE"
            ));
            close();
        } catch (IOException e) {
            errorMsg = "Error leyendo archivo: " + e.getMessage();
        }
    }

    private List<Path> filtered() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        if (q.isEmpty()) return files;
        List<Path> out = new ArrayList<>();
        Path root = LitematicaBridge.schematicsRoot();
        for (Path p : files) {
            if (root.relativize(p).toString().toLowerCase().contains(q)) out.add(p);
        }
        return out;
    }

    private int listTop() { return panelY + 78; }
    private int listBottom() { return panelY + panelH - 74; }

    private int maxScrollPx(int count) {
        int contentH = count * ROW_STRIDE - CARD_GAP;
        int viewH = listBottom() - listTop();
        return Math.max(0, contentH - viewH);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        computePanel(Math.min(640, (int) (width * 0.76)), Math.min(430, (int) (height * 0.80)));
        List<Path> shown = filtered();
        int maxScroll = maxScrollPx(shown.size());
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        scroll += (targetScroll - scroll) * Math.min(1.0, d * 0.45 + 0.25);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        renderBackground(c, mx, my, d);
        AeternumTheme.drawPanel(c, panelX, panelY, panelW, panelH);
        AeternumTheme.drawHeader(c, panelX, panelY, panelW, 44);
        c.fill(panelX + 1, panelY + 1, panelX + 4, panelY + 43, AeternumTheme.ACCENT_GOLD);
        c.drawTextWithShadow(textRenderer, "SUBIR .LITEMATIC", panelX + 38, panelY + 10, AeternumTheme.TEXT_PRIMARY);
        c.drawText(textRenderer, shown.size() + " archivos encontrados en schematics/", panelX + 38, panelY + 25, AeternumTheme.TEXT_SECONDARY, false);
        drawCloseButton(c, mx, my);

        int lTop = listTop(), lBottom = listBottom(), viewH = lBottom - lTop;
        int rowX = panelX + 12, rowW = panelW - 30;
        Path root = LitematicaBridge.schematicsRoot();

        if (shown.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, "No se encontraron archivos .litematic.", width / 2, lTop + viewH / 2 - 8, AeternumTheme.TEXT_SECONDARY);
            c.drawCenteredTextWithShadow(textRenderer, "Colócalos en .minecraft/schematics/", width / 2, lTop + viewH / 2 + 8, AeternumTheme.TEXT_MUTED);
        } else {
            c.enableScissor(panelX + 6, lTop, panelX + panelW - 10, lBottom);
            for (int i = 0; i < shown.size(); i++) {
                int ry = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
                if (ry + CARD_H < lTop || ry > lBottom) continue;

                Path p = shown.get(i);
                boolean isSel = p.equals(selectedFile);
                boolean hover = my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW && my >= ry && my < ry + CARD_H;
                int bg = isSel ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG);
                int border = isSel ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE;
                AeternumTheme.drawCard(c, rowX, ry, rowW, CARD_H, bg, border);

                String rel = root.relativize(p).toString().replace('\\', '/');
                c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, rel, rowW - 84), rowX + 10, ry + 10, AeternumTheme.TEXT_PRIMARY);

                try {
                    String kb = Math.max(1, Files.size(p) / 1024) + " KB";
                    int kw = textRenderer.getWidth(kb);
                    c.drawText(textRenderer, kb, rowX + rowW - kw - 10, ry + 10, AeternumTheme.TEXT_MUTED, false);
                } catch (IOException ignored) {}
            }
            c.disableScissor();

            AeternumTheme.drawScrollbar(c, panelX + panelW - 12, lTop, lBottom, shown.size() * ROW_STRIDE - CARD_GAP, viewH, scroll, draggingScrollbar);
        }

        if (uploadBtn != null) uploadBtn.active = selectedFile != null && nameField != null && !nameField.getText().trim().isEmpty();

        AeternumTheme.drawFooter(c, panelX, panelY + panelH - 36, panelW, panelY + panelH);
        if (!errorMsg.isEmpty()) {
            c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, errorMsg, panelW - 240), panelX + 102, panelY + panelH - 22, AeternumTheme.TEXT_PRIMARY, false);
        }

        if (searchField != null) searchField.renderBox(c, panelX + 12, panelY + 52, panelW - 24, 20, mx, my, d);
        if (nameField != null) nameField.renderBox(c, panelX + 12, panelY + panelH - 64, Math.max(140, panelW - 320), 20, mx, my, d);
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

        List<Path> shown = filtered();
        int lTop = listTop(), lBottom = listBottom(), viewH = lBottom - lTop;
        int maxScroll = maxScrollPx(shown.size());
        if (maxScroll > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(my, lTop, viewH, maxScroll, shown.size());
            return true;
        }

        int rowX = panelX + 12, rowW = panelW - 30;
        if (my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW) {
            int relY = (int) (my - lTop + scroll);
            int idx = relY / ROW_STRIDE;
            if (idx >= 0 && idx < shown.size() && (relY % ROW_STRIDE) < CARD_H) {
                selectedFile = shown.get(idx);
                errorMsg = "";
                if (nameField != null && nameField.getText().trim().isEmpty()) {
                    String fn = selectedFile.getFileName().toString().replaceAll("(?i)\\.litematic$", "");
                    nameField.setText(fn.replaceAll("[^a-zA-Z0-9_-]", "_"));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v != 0) {
            int maxScroll = maxScrollPx(filtered().size());
            targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - v * 28));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            List<Path> shown = filtered();
            updateScrollFromMouse(my, listTop(), listBottom() - listTop(), maxScrollPx(shown.size()), shown.size());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        if (b == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mx, my, b);
    }

    private void updateScrollFromMouse(double my, int top, int viewH, int maxScroll, int count) {
        if (maxScroll == 0) return;
        int totalH = count * ROW_STRIDE - CARD_GAP;
        int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
        double f = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
        targetScroll = Math.max(0, Math.min(1, f)) * maxScroll;
        scroll = targetScroll;
    }

    private static String rotLabel(String rot) {
        return switch (rot) {
            case "CLOCKWISE_90" -> "90°";
            case "CLOCKWISE_180" -> "180°";
            case "COUNTERCLOCKWISE_90" -> "270°";
            default -> "0°";
        };
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
