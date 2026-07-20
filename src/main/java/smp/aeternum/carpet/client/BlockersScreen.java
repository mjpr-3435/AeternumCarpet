package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import smp.aeternum.carpet.blockers.BlockerScan;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

/** Landing page: scans first, then an explicit detail screen. */
public final class BlockersScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final Type SCANS = new TypeToken<List<BlockerScan>>() {}.getType();
    private List<BlockerScan> scans;
    private boolean canDelete;
    private final List<ButtonWidget> deleteButtons = new ArrayList<>();

    public BlockersScreen(String json, boolean canDelete) {
        super(Text.literal("Blocker scans"));
        this.canDelete = canDelete;
        scans = decode(json);
    }

    public void replaceData(String json, boolean canDelete) {
        this.canDelete = canDelete;
        scans = decode(json);
        reloadDeleteButtons();
    }

    @Override
    protected void init() {
        createDeleteButtons();
    }

    private void reloadDeleteButtons() {
        clearChildren();
        deleteButtons.clear();
        createDeleteButtons();
    }

    private void createDeleteButtons() {
        int count = Math.min(14, scans.size());
        for (int i = 0; i < count; i++) {
            String id = scans.get(i).id;
            ButtonWidget button = ButtonWidget.builder(Text.literal("X"), ignored -> BlockerDeleteRequest.send(id))
                .dimensions(0, 0, 38, 18).build();
            deleteButtons.add(button);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int panelW = Math.max(480, (int) (width * 0.70)), panelH = Math.max(320, (int) (height * 0.70));
        panelW = Math.min(panelW, width - 32);
        panelH = Math.min(panelH, height - 32);
        int left = (width - panelW) / 2, top = (height - panelH) / 2;
        context.fill(left, top, left + panelW, top + panelH, 0xE0101010);
        context.drawBorder(left, top, panelW, panelH, 0xFF909090);
        context.fill(left + 1, top + 1, left + panelW - 1, top + 43, 0xCC242424);
        context.drawTextWithShadow(textRenderer, "BLOCKER SCANS", left + 14, top + 12, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Escoge un análisis para ver sus bloqueadores", left + 14, top + 27, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, "×", left + panelW - 18, top + 10, 0xFFFFFFFF);
        if (scans.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No hay análisis guardados.", width / 2, top + panelH / 2 - 8, 0xFFAAAAAA);
            context.drawCenteredTextWithShadow(textRenderer, "Usa /blockers <coord1> <coord2> <id>.", width / 2, top + panelH / 2 + 8, 0xFF888888);
            return;
        }
        int rowY = top + 52;
        for (int i = 0; i < Math.min(14, scans.size()); i++, rowY += 20) {
            BlockerScan scan = scans.get(i);
            boolean hover = mouseX >= left + 9 && mouseX < left + panelW - 9 && mouseY >= rowY - 2 && mouseY < rowY + 16;
            if (i % 2 == 0) context.fill(left + 9, rowY - 2, left + panelW - 9, rowY + 16, 0x40202020);
            if (hover) context.fill(left + 8, rowY - 2, left + panelW - 8, rowY + 16, 0xFF30465A);
            String state = scan.complete ? "Terminado" : scan.scannedChunks + "/" + scan.totalChunks + " chunks";
            context.drawTextWithShadow(textRenderer, scan.id, left + 14, rowY + 1, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, scan.entries.size() + " bloqueadores  ·  " + state, left + 170, rowY + 1, 0xFFB0B0B0);
            ButtonWidget delete = deleteButtons.get(i);
            delete.setPosition(left + panelW - 54, rowY - 2);
            delete.active = canDelete && hasShiftDown();
            delete.render(context, mouseX, mouseY, delta);
        }
        if (mouseX >= left + panelW - 60 && mouseY >= top + 52 && mouseY < top + 332) {
            context.drawTooltip(textRenderer, Text.literal(canDelete ? "Mantén Shift y haz clic para borrar el scan." : "Solo operadores pueden borrar scans."), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = Math.max(480, (int) (width * 0.70)), panelH = Math.max(320, (int) (height * 0.70));
        panelW = Math.min(panelW, width - 32);
        panelH = Math.min(panelH, height - 32);
        int left = (width - panelW) / 2, top = (height - panelH) / 2;
        if (button == 0 && mouseX >= left + panelW - 25 && mouseY >= top && mouseY < top + 30) { close(); return true; }
        int index = (int) ((mouseY - (top + 52)) / 20);
        if (button == 0 && index >= 0 && index < Math.min(14, scans.size())) {
            BlockerScan scan = scans.get(index);
            if (mouseX >= left + panelW - 60) return deleteButtons.get(index).mouseClicked(mouseX, mouseY, button);
            client.setScreen(new BlockerScanScreen(this, scan));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static List<BlockerScan> decode(String json) {
        List<BlockerScan> decoded = GSON.fromJson(json, SCANS);
        if (decoded == null) return List.of();
        return decoded.stream().sorted(Comparator.comparingLong((BlockerScan scan) -> scan.createdAt).reversed()).toList();
    }
}
