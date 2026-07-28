package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import smp.aeternum.carpet.materials.MaterialList;
import smp.aeternum.carpet.network.MaterialsDeletePayload;
import smp.aeternum.carpet.network.MaterialsRefreshPayload;

public final class MaterialsScreen extends Screen {
	private static final Gson GSON = new Gson();
	private static final int ROW_HEIGHT = 20, VISIBLE = 13;
	private List<MaterialList> lists;
	private final List<ButtonWidget> deleteButtons = new ArrayList<>();
	private ButtonWidget refreshButton;
	private long refreshEnableAt;
	private int scroll;
	private boolean draggingScrollbar;

	public MaterialsScreen(String json) {
		super(Text.literal("Materiales"));
		lists = decode(json);
	}

	public void replaceData(String json) {
		lists = decode(json);
		reloadDeleteButtons();
	}

	@Override protected void init() { createButtons(); }

	private void reloadDeleteButtons() { clearChildren(); deleteButtons.clear(); refreshButton = null; createButtons(); }

	private void createButtons() {
		refreshButton = ButtonWidget.builder(Text.literal("Refrescar"), b -> { b.active = false; b.setFocused(false); refreshEnableAt = System.currentTimeMillis() + 300; ClientPlayNetworking.send(new MaterialsRefreshPayload()); }).dimensions(0, 0, 72, 20).build();
		int count = Math.min(VISIBLE + 4, lists.size());
		for (int i = 0; i < count; i++) {
			final int idx = i;
			ButtonWidget button = ButtonWidget.builder(Text.literal("X"), b -> ClientPlayNetworking.send(new MaterialsDeletePayload(lists.get(idx).name)))
				.dimensions(0, 0, 38, 18).build();
			deleteButtons.add(button);
		}
	}

	@Override public void renderBackground(DrawContext c, int x, int y, float d) {}

	@Override public void render(DrawContext c, int mx, int my, float d) {
		int l = width / 2 - 260, t = height / 2 - 170;
		c.fill(0, 0, width, height, 0x90000000);
		c.fill(l, t, l + 520, t + 340, 0xE0101010);
		c.drawBorder(l, t, 520, 340, 0xFF909090);
		c.drawTextWithShadow(textRenderer, "MATERIALES", l + 14, t + 14, 0xFFFFFFFF);
		c.drawTextWithShadow(textRenderer, "\u00D7", l + 502, t + 14, 0xFFFFFFFF);

		if (lists.isEmpty()) {
			c.drawCenteredTextWithShadow(textRenderer, "No hay listas de materiales.", width / 2, t + 160, 0xFFAAAAAA);
			c.drawCenteredTextWithShadow(textRenderer, "Pon archivos .csv en world/data/aeternum_materials.", width / 2, t + 180, 0xFF888888);
		} else {
			int listAreaTop = t + 48, listAreaBottom = t + 308;
			scroll = Math.max(0, Math.min(scroll, Math.max(0, lists.size() - VISIBLE)));

			int rowY = listAreaTop;
			for (int i = 0; i < VISIBLE && i + scroll < lists.size() && i < deleteButtons.size(); i++, rowY += ROW_HEIGHT) {
				int idx = i + scroll;
				MaterialList list = lists.get(idx);
				boolean hover = mx >= l + 9 && mx < l + 510 && my >= rowY && my < rowY + 18;
				if (idx % 2 == 0) c.fill(l + 9, rowY, l + 510, rowY + 18, 0x40202020);
				if (hover) c.fill(l + 8, rowY, l + 510, rowY + 18, 0xFF30465A);
				c.drawTextWithShadow(textRenderer, list.name, l + 14, rowY + 2, 0xFFFFFFFF);
				c.drawTextWithShadow(textRenderer, list.entries.size() + " items", l + 260, rowY + 2, 0xFFB0B0B0);

				ButtonWidget del = deleteButtons.get(i);
				del.setPosition(l + 470, rowY);
				del.active = hasShiftDown();
				del.render(c, mx, my, d);
			}

			renderScrollbar(c, l, t, listAreaTop, listAreaBottom, mx, my);

		if (mx >= l + 470 && mx < l + 508 && my >= listAreaTop && my < listAreaBottom)
			c.drawTooltip(textRenderer, Text.literal(hasShiftDown() ? "Eliminar esta lista" : "Mantén Shift para eliminar"), mx, my);
		}

		if (refreshButton != null) { refreshButton.setPosition(l + 430, t + 312); refreshButton.render(c, mx, my, d); }

		if (mx >= l + 430 && mx < l + 502 && my >= t + 312 && my < t + 332)
			c.drawTooltip(textRenderer, Text.literal("Recargar listas desde los archivos CSV"), mx, my);

		super.render(c, mx, my, d);
	}

	private void renderScrollbar(DrawContext c, int l, int t, int top, int bottom, int mx, int my) {
		int max = Math.max(0, lists.size() - VISIBLE);
		if (max == 0) return;
		int trackH = bottom - top;
		int barH = Math.max(20, trackH * VISIBLE / (max + VISIBLE));
		int barY = top + (trackH - barH) * scroll / max;
		c.fill(l + 510, top, l + 516, bottom, 0xFF333333);
		c.fill(l + 510, barY, l + 516, barY + barH, 0xFFAAAAAA);
	}

	@Override public boolean mouseClicked(double mx, double my, int b) {
		int l = width / 2 - 260, t = height / 2 - 170;
		if (b == 0 && mx >= l + 500 && mx < l + 520 && my >= t && my < t + 32) { close(); return true; }
		if (b == 0 && refreshButton != null && refreshButton.isMouseOver(mx, my)) { refreshButton.mouseClicked(mx, my, b); return true; }
		int listAreaTop = t + 48;
		int maxScroll = Math.max(0, lists.size() - VISIBLE);
		if (maxScroll > 0 && b == 0 && mx >= l + 508 && mx <= l + 518 && my >= listAreaTop && my <= t + 308) { draggingScrollbar = true; updateScroll(my); return true; }
		int index = (int) ((my - listAreaTop) / ROW_HEIGHT) + scroll;
		if (b == 0 && my >= listAreaTop && my < t + 308 && index >= 0 && index < lists.size()) {
			if (hasShiftDown() && mx >= l + 470 && mx < l + 508) {
				int localIdx = index - scroll;
				if (localIdx >= 0 && localIdx < deleteButtons.size()) deleteButtons.get(localIdx).mouseClicked(mx, my, b);
				return true;
			}
			if (mx >= l + 9 && mx < l + 470) { client.setScreen(new MaterialsDetailScreen(this, lists.get(index))); return true; }
		}
		return super.mouseClicked(mx, my, b);
	}

	@Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (draggingScrollbar && button == 0) { updateScroll(my); return true; }
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override public boolean mouseReleased(double mx, double my, int b) {
		if (b == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
		return super.mouseReleased(mx, my, b);
	}

	@Override public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (v != 0) { scroll -= Math.signum(v); return true; }
		return super.mouseScrolled(mx, my, h, v);
	}

	private void updateScroll(double my) {
		int t = height / 2 - 170;
		int max = Math.max(0, lists.size() - VISIBLE);
		if (max == 0) return;
		int top = t + 48, bottom = t + 308;
		int trackH = bottom - top;
		int barH = Math.max(20, trackH * VISIBLE / (max + VISIBLE));
		double f = (my - top - barH / 2.0) / Math.max(1, trackH - barH);
		scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * max);
	}

	@Override public boolean shouldPause() { return false; }
	@Override public void tick() { if (refreshButton != null && !refreshButton.active && System.currentTimeMillis() >= refreshEnableAt) refreshButton.active = true; }
	@Override public void close() { client.setScreen(null); }

	static List<MaterialList> decodeLists(String json) {
		MaterialList[] arr = GSON.fromJson(json, MaterialList[].class);
		return arr == null ? new ArrayList<>() : new ArrayList<>(List.of(arr));
	}

	private static List<MaterialList> decode(String json) { return decodeLists(json); }
}
