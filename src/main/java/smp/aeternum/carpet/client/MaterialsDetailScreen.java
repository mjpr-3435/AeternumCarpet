package smp.aeternum.carpet.client;

import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.materials.MaterialEntry;
import smp.aeternum.carpet.materials.MaterialList;
import smp.aeternum.carpet.network.MaterialsActionPayload;
import smp.aeternum.carpet.network.MaterialsScanPayload;
import smp.aeternum.carpet.network.MaterialsRefreshPayload;

public final class MaterialsDetailScreen extends Screen {
	private final Screen parent;
	private MaterialList list;
	private int scroll;
	private boolean draggingScrollbar;
	private static final int VISIBLE = 12;

	public MaterialsDetailScreen(Screen parent, MaterialList list) {
		super(Text.literal(list.name));
		this.parent = parent;
		this.list = list;
	}

	public void replaceData(MaterialList updated) { this.list = updated; clearChildren(); }

	public void replaceFromJson(String json) {
		var lists = MaterialsScreen.decodeLists(json);
		for (var serverList : lists) {
			if (!serverList.name.equals(this.list.name)) continue;
			for (MaterialEntry entry : this.list.entries) {
				for (var se : serverList.entries) {
					if (se.itemId.equals(entry.itemId)) { entry.claimedBy = se.claimedBy; entry.available = se.available; break; }
				}
			}
			this.list.nextScanAt = serverList.nextScanAt;
			this.list.scannedAvailable = serverList.scannedAvailable;
			return;
		}
	}

	@Override protected void init() {}

	@Override public void renderBackground(DrawContext c, int x, int y, float d) {}

	@Override public void render(DrawContext c, int mx, int my, float d) {
		clearChildren();
		int l = width / 2 - 260, t = height / 2 - 170;
		c.fill(0, 0, width, height, 0x90000000);
		c.fill(l, t, l + 520, t + 340, 0xE0101010);
		c.drawBorder(l, t, 520, 340, 0xFF909090);

		c.drawTextWithShadow(textRenderer, "\u2190 " + list.name, l + 14, t + 14, 0xFFFFFFFF);
		c.drawTextWithShadow(textRenderer, "\u00D7", l + 502, t + 14, 0xFFFFFFFF);

		if (list.nextScanAt > 0) {
			int secs = Math.max(0, (int) ((list.nextScanAt - System.currentTimeMillis()) / 1000));
			c.drawTextWithShadow(textRenderer, "Escaneo en " + secs + "s", l + 320, t + 14, 0xFFAAAAAA);
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("Refrescar"), b -> { b.active = false; ClientPlayNetworking.send(new MaterialsScanPayload(list.name)); })
			.dimensions(l + 400, t + 10, 64, 18).build());

		List<MaterialEntry> entries = list.entries;
		if (entries.isEmpty()) {
			c.drawCenteredTextWithShadow(textRenderer, "Lista vacía.", width / 2, t + 160, 0xFFAAAAAA);
			super.render(c, mx, my, d);
			return;
		}

		int visible = Math.min(entries.size(), VISIBLE);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visible)));

		int headerY = t + 42;
		c.fill(l + 10, headerY, l + 496, headerY + 14, 0xFF2A2A2A);
		c.drawTextWithShadow(textRenderer, "Item", l + 14, headerY + 1, 0xFFAAAAAA);
		c.drawTextWithShadow(textRenderer, "Total", l + 200, headerY + 1, 0xFFAAAAAA);
		c.drawTextWithShadow(textRenderer, "Falta", l + 280, headerY + 1, 0xFFAAAAAA);
		c.drawTextWithShadow(textRenderer, "Claim", l + 350, headerY + 1, 0xFFAAAAAA);

		String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";

		for (int i = 0; i < visible; i++) {
			int idx = i + scroll;
			MaterialEntry entry = entries.get(idx);
			int rowY = headerY + 18 + i * 22;
			if (idx % 2 == 0) c.fill(l + 10, rowY, l + 496, rowY + 20, 0x40202020);

			ItemStack icon = ItemStack.EMPTY;
			if (entry.itemId != null && !entry.itemId.isEmpty()) {
				var item = Registries.ITEM.get(Identifier.of(entry.itemId));
				if (item != Items.AIR) icon = new ItemStack(item);
			}
			if (!icon.isEmpty()) c.drawItem(icon, l + 14, rowY + 2);

			c.drawTextWithShadow(textRenderer, entry.itemName, l + 34, rowY + 3, 0xFFFFFFFF);
			c.drawTextWithShadow(textRenderer, formatStack(entry.total), l + 200, rowY + 3, 0xFFCCCCCC);
			int missing = Math.max(0, entry.total - entry.available);
			c.drawTextWithShadow(textRenderer, formatStack(missing), l + 280, rowY + 3, missing > 0 ? 0xFFFF5555 : 0xFF55FF55);

			String claimed = entry.claimedBy != null ? entry.claimedBy : "-";
			c.drawTextWithShadow(textRenderer, claimed.length() > 12 ? claimed.substring(0, 10) + "\u2026" : claimed, l + 350, rowY + 3, entry.claimedBy != null ? 0xFFFFFF55 : 0xFF666666);

			if (entry.claimedBy == null || entry.claimedBy.isEmpty()) {
				addDrawableChild(ButtonWidget.builder(Text.literal("Claim"), b -> {
					ClientPlayNetworking.send(new MaterialsActionPayload(list.name, entry.itemId, player, true));
					entry.claimedBy = player;
				}).dimensions(l + 430, rowY, 40, 18).build());
			} else if (entry.claimedBy.equals(player)) {
				addDrawableChild(ButtonWidget.builder(Text.literal("Unclaim"), b -> {
					ClientPlayNetworking.send(new MaterialsActionPayload(list.name, entry.itemId, player, false));
					entry.claimedBy = null;
				}).dimensions(l + 430, rowY, 44, 18).build());
			}
		}
		renderScrollbar(c, l, t, headerY + 18, t + 308);
		super.render(c, mx, my, d);
	}

	private void renderScrollbar(DrawContext c, int l, int t, int top, int bottom) {
		int max = Math.max(0, list.entries.size() - VISIBLE);
		if (max == 0) return;
		int trackH = bottom - top;
		int barH = Math.max(20, trackH * VISIBLE / (max + VISIBLE));
		int barY = top + (trackH - barH) * scroll / max;
		c.fill(l + 510, top, l + 516, bottom, 0xFF333333);
		c.fill(l + 510, barY, l + 516, barY + barH, 0xFFAAAAAA);
	}

	@Override public boolean mouseClicked(double mx, double my, int b) {
		int l = width / 2 - 260, t = height / 2 - 170;
		if (b == 0 && mx >= l && mx < l + 100 && my >= t && my < t + 30) { close(); return true; }
		if (b == 0 && mx >= l + 500 && mx < l + 520 && my >= t && my < t + 32) { client.setScreen(parent); return true; }
		int maxScroll = Math.max(0, list.entries.size() - VISIBLE);
		if (maxScroll > 0 && b == 0 && mx >= l + 508 && mx <= l + 518 && my >= t + 60 && my <= t + 308) { draggingScrollbar = true; updateScroll(my); return true; }
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

	private void updateScroll(double my) {
		int t = height / 2 - 170;
		int max = Math.max(0, list.entries.size() - VISIBLE);
		if (max == 0) return;
		int top = t + 60, bottom = t + 308;
		int trackH = bottom - top;
		int barH = Math.max(20, trackH * VISIBLE / (max + VISIBLE));
		double f = (my - top - barH / 2.0) / Math.max(1, trackH - barH);
		scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * max);
	}

	@Override public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (v != 0) { scroll -= Math.signum(v); return true; }
		return super.mouseScrolled(mx, my, h, v);
	}

	@Override public boolean shouldPause() { return false; }
	@Override public void close() { client.setScreen(parent); }

	private static String formatStack(int n) {
		if (n >= 1728) return String.format(java.util.Locale.ROOT, "SB %.2f", n / 1728.0);
		int stacks = n / 64, singles = n % 64;
		if (stacks > 0) return singles == 0 ? "64 x " + stacks : "64 x " + stacks + " + " + singles;
		return String.valueOf(n);
	}
}
