package smp.aeternum.carpet.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.client.ui.UiTextField;
import smp.aeternum.carpet.materials.MaterialEntry;
import smp.aeternum.carpet.materials.MaterialList;
import smp.aeternum.carpet.network.MaterialsActionPayload;

public final class MaterialsDetailScreen extends UiScreen {
	private enum FilterMode { ALL, MISSING, MINE, DONE }

	private static final int ROW_H = 30;
	private static final int ROW_GAP = 3;
	private static final int ROW_STRIDE = ROW_H + ROW_GAP;

	private final Screen parent;
	private MaterialList list;
	private FilterMode filterMode = FilterMode.ALL;
	private UiTextField searchField;
	private UiButton backButton;
	private UiButton tabAll, tabMissing, tabMine, tabDone;
	private double scroll;
	private double targetScroll;
	private boolean draggingScrollbar;

	public MaterialsDetailScreen(Screen parent, MaterialList list) {
		super(Text.literal(list.name));
		this.parent = parent;
		this.list = list;
	}

	public void replaceData(MaterialList updated) {
		this.list = updated;
	}

	public void replaceFromJson(String json) {
		var lists = MaterialsScreen.decodeLists(json);
		for (var serverList : lists) {
			if (!serverList.name.equals(this.list.name)) continue;
			for (MaterialEntry entry : this.list.entries) {
				for (var se : serverList.entries) {
					if (se.itemId.equals(entry.itemId)) {
						entry.claimedBy = se.claimedBy;
						entry.available = se.available;
						break;
					}
				}
			}
			this.list.nextScanAt = serverList.nextScanAt;
			this.list.scannedAvailable = serverList.scannedAvailable;
			return;
		}
	}

	@Override
	protected void init() {
		String prevQuery = searchField != null ? searchField.getText() : "";
		clearChildren();
		computePanel(Math.min(680, (int) (width * 0.78)), Math.min(430, (int) (height * 0.80)));

		backButton = UiButton.ghost(panelX + 10, panelY + 10, 24, 24, "\u2190", b -> close());
		addDrawableChild(backButton);

		int barY = panelY + 52;
		searchField = new UiTextField(textRenderer, panelX + 12, barY, Math.max(140, panelW - 294), 20, "Buscar ítem…");
		searchField.setMaxLength(80);
		searchField.setText(prevQuery);
		searchField.setChangedListener(s -> {
			targetScroll = 0;
			scroll = 0;
		});
		addDrawableChild(searchField);

		int tabX = panelX + panelW - 270;
		tabAll = UiButton.secondary(tabX, barY, 54, 20, "Todos", b -> setFilter(FilterMode.ALL));
		tabMissing = UiButton.secondary(tabX + 58, barY, 64, 20, "Faltan", b -> setFilter(FilterMode.MISSING));
		tabMine = UiButton.secondary(tabX + 126, barY, 70, 20, "Míos", b -> setFilter(FilterMode.MINE));
		tabDone = UiButton.secondary(tabX + 200, barY, 58, 20, "Listos", b -> setFilter(FilterMode.DONE));
		addDrawableChild(tabAll);
		addDrawableChild(tabMissing);
		addDrawableChild(tabMine);
		addDrawableChild(tabDone);
		updateTabStyles();
	}

	private void setFilter(FilterMode mode) {
		filterMode = mode;
		targetScroll = 0;
		scroll = 0;
		updateTabStyles();
	}

	private void updateTabStyles() {
		if (tabAll == null) return;
		tabAll.setVariant(filterMode == FilterMode.ALL ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
		tabMissing.setVariant(filterMode == FilterMode.MISSING ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
		tabMine.setVariant(filterMode == FilterMode.MINE ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
		tabDone.setVariant(filterMode == FilterMode.DONE ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
	}

	private List<MaterialEntry> filteredEntries(String player) {
		String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
		List<MaterialEntry> out = new ArrayList<>();
		for (MaterialEntry e : list.entries) {
			if (!q.isEmpty()) {
				String name = e.itemName != null ? e.itemName.toLowerCase() : "";
				String id = e.itemId != null ? e.itemId.toLowerCase() : "";
				if (!name.contains(q) && !id.contains(q)) continue;
			}
			int missing = Math.max(0, e.total - e.available);
			switch (filterMode) {
				case MISSING -> { if (missing == 0) continue; }
				case MINE -> { if (!player.equals(e.claimedBy)) continue; }
				case DONE -> { if (missing > 0) continue; }
				default -> {}
			}
			out.add(e);
		}
		out.sort(Comparator
			.comparingInt((MaterialEntry e) -> e.total - e.available > 0 ? 0 : 1)
			.thenComparing(e -> e.total, Comparator.reverseOrder()));
		return out;
	}

	private int listTop() { return panelY + 98; }
	private int listBottom() { return panelY + panelH - 14; }

	private int maxScrollPx(int count) {
		int contentH = count * ROW_STRIDE - ROW_GAP;
		int viewH = listBottom() - listTop();
		return Math.max(0, contentH - viewH);
	}

	@Override
	public void render(DrawContext c, int mx, int my, float d) {
		computePanel(Math.min(680, (int) (width * 0.78)), Math.min(430, (int) (height * 0.80)));
		String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";
		List<MaterialEntry> entries = filteredEntries(player);

		int maxScroll = maxScrollPx(entries.size());
		targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
		scroll += (targetScroll - scroll) * Math.min(1.0, d * 0.45 + 0.25);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		renderBackground(c, mx, my, d);

		AeternumTheme.drawPanel(c, panelX, panelY, panelW, panelH);
		AeternumTheme.drawHeader(c, panelX, panelY, panelW, 44);
		c.fill(panelX + 1, panelY + 1, panelX + 4, panelY + 43, AeternumTheme.ACCENT_GOLD);
		c.drawTextWithShadow(textRenderer, list.name, panelX + 38, panelY + 10, AeternumTheme.TEXT_PRIMARY);
		c.drawText(textRenderer, list.entries.size() + " materiales", panelX + 38, panelY + 25, AeternumTheme.TEXT_SECONDARY, false);
		drawCloseButton(c, mx, my);

		// Column header
		int colY = panelY + 80;
		c.fill(panelX + 12, colY, panelX + panelW - 18, colY + 15, AeternumTheme.CARD_ALT_BG);
		c.drawText(textRenderer, "ÍTEM", panelX + 20, colY + 4, AeternumTheme.TEXT_MUTED, false);
		int colTotalX = panelX + (int) (panelW * 0.43);
		int colMissingX = panelX + (int) (panelW * 0.58);
		int colClaimX = panelX + (int) (panelW * 0.72);
		c.drawText(textRenderer, "TOTAL", colTotalX, colY + 4, AeternumTheme.TEXT_MUTED, false);
		c.drawText(textRenderer, "FALTA", colMissingX, colY + 4, AeternumTheme.TEXT_MUTED, false);
		c.drawText(textRenderer, "CLAIM", colClaimX, colY + 4, AeternumTheme.TEXT_MUTED, false);

		int lTop = listTop();
		int lBottom = listBottom();
		int viewH = lBottom - lTop;
		int rowX = panelX + 12;
		int rowW = panelW - 30;

		if (entries.isEmpty()) {
			c.drawCenteredTextWithShadow(textRenderer, list.entries.isEmpty() ? "Lista vacía." : "Sin resultados con ese filtro.", width / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
		} else {
			c.enableScissor(panelX + 6, lTop, panelX + panelW - 10, lBottom);
			for (int i = 0; i < entries.size(); i++) {
				int rowY = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
				if (rowY + ROW_H < lTop || rowY > lBottom) continue;

				MaterialEntry entry = entries.get(i);
				int missing = Math.max(0, entry.total - entry.available);
				float ratio = entry.total <= 0 ? 1f : Math.min(1f, (float) entry.available / (float) entry.total);
				boolean mine = player.equals(entry.claimedBy);
				boolean hover = my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW && my >= rowY && my < rowY + ROW_H;

				int bg = mine ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : (i % 2 == 0 ? AeternumTheme.CARD_BG : AeternumTheme.CARD_ALT_BG));
				int border = mine ? AeternumTheme.withAlpha(AeternumTheme.ACCENT_CYAN, 0x80) : AeternumTheme.BORDER_SUBTLE;
				AeternumTheme.drawCard(c, rowX, rowY, rowW, ROW_H, bg, border);

				AeternumTheme.drawItemIcon(c, entry.itemId, rowX + 6, rowY + 7);

				int nameMaxW = colTotalX - (rowX + 30);
				String shownName = AeternumTheme.ellipsize(textRenderer, entry.itemName, nameMaxW);
				c.drawTextWithShadow(textRenderer, shownName, rowX + 28, rowY + 6, AeternumTheme.TEXT_PRIMARY);

				int barColor = missing == 0 ? AeternumTheme.ACCENT_GREEN : (ratio >= 0.5f ? AeternumTheme.ACCENT_CYAN : AeternumTheme.ACCENT_GOLD);
				AeternumTheme.drawProgressBar(c, rowX + 28, rowY + 20, Math.max(40, nameMaxW - 6), 4, ratio, barColor);

				c.drawText(textRenderer, formatStack(entry.total), colTotalX, rowY + 11, AeternumTheme.TEXT_SECONDARY, false);
				c.drawText(textRenderer, missing == 0 ? "\u2713 Listo" : formatStack(missing), colMissingX, rowY + 11,
					missing == 0 ? AeternumTheme.ACCENT_GREEN : AeternumTheme.ACCENT_RED, false);

				String claimed = (entry.claimedBy != null && !entry.claimedBy.isEmpty()) ? entry.claimedBy : "-";
				String shortClaim = AeternumTheme.ellipsize(textRenderer, claimed, (rowX + rowW - 64) - colClaimX);
				c.drawText(textRenderer, shortClaim, colClaimX, rowY + 11,
					mine ? AeternumTheme.ACCENT_CYAN : (entry.claimedBy != null ? AeternumTheme.ACCENT_GOLD : AeternumTheme.TEXT_MUTED), false);

				// Claim/Unclaim button
				int btnX = rowX + rowW - 58;
				int btnY = rowY + 6;
				int btnW = 52;
				int btnH = 18;
				boolean btnHover = my >= lTop && my < lBottom && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;
				if (entry.claimedBy == null || entry.claimedBy.isEmpty()) {
					int bBg = btnHover ? 0xFF2E2E38 : 0xFF1D1D24;
					AeternumTheme.drawCard(c, btnX, btnY, btnW, btnH, bBg, 0xFFD4D4DF);
					c.drawTextWithShadow(textRenderer, "Claim", btnX + (btnW - textRenderer.getWidth("Claim")) / 2, btnY + 5, AeternumTheme.TEXT_PRIMARY);
				} else if (mine) {
					int bBg = btnHover ? 0xFF2B2B34 : 0xFF18181E;
					AeternumTheme.drawCard(c, btnX, btnY, btnW, btnH, bBg, 0xFF8C8C9A);
					c.drawTextWithShadow(textRenderer, "Soltar", btnX + (btnW - textRenderer.getWidth("Soltar")) / 2, btnY + 5, AeternumTheme.TEXT_SECONDARY);
				}
			}
			c.disableScissor();

			AeternumTheme.drawScrollbar(
				c, panelX + panelW - 12, lTop, lBottom,
				entries.size() * ROW_STRIDE - ROW_GAP, viewH, scroll, draggingScrollbar
			);
		}

		if (searchField != null) {
			searchField.renderBox(c, searchField.boxX(), searchField.boxY(), Math.max(140, panelW - 294), 20, mx, my, d);
		}
		super.render(c, mx, my, d);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int b) {
		if (b == 0 && isCloseButtonHit(mx, my)) {
			client.setScreen(null);
			return true;
		}
		if (super.mouseClicked(mx, my, b)) return true;
		if (b != 0) return false;

		String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";
		List<MaterialEntry> entries = filteredEntries(player);
		int lTop = listTop();
		int lBottom = listBottom();
		int viewH = lBottom - lTop;
		int maxScroll = maxScrollPx(entries.size());

		if (maxScroll > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
			draggingScrollbar = true;
			updateScrollFromMouse(my, lTop, viewH, maxScroll, entries.size());
			return true;
		}

		int rowX = panelX + 12;
		int rowW = panelW - 30;
		if (my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW) {
			int relY = (int) (my - lTop + scroll);
			int idx = relY / ROW_STRIDE;
			if (idx >= 0 && idx < entries.size() && (relY % ROW_STRIDE) < ROW_H) {
				int rowY = lTop + idx * ROW_STRIDE - (int) Math.round(scroll);
				int btnX = rowX + rowW - 58;
				int btnY = rowY + 6;
				if (mx >= btnX && mx < btnX + 52 && my >= btnY && my < btnY + 18) {
					MaterialEntry entry = entries.get(idx);
					if (entry.claimedBy == null || entry.claimedBy.isEmpty()) {
						ClientPlayNetworking.send(new MaterialsActionPayload(list.name, entry.itemId, player, true));
						entry.claimedBy = player;
					} else if (entry.claimedBy.equals(player)) {
						ClientPlayNetworking.send(new MaterialsActionPayload(list.name, entry.itemId, player, false));
						entry.claimedBy = null;
					}
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (draggingScrollbar && button == 0) {
			String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";
			List<MaterialEntry> entries = filteredEntries(player);
			updateScrollFromMouse(my, listTop(), listBottom() - listTop(), maxScrollPx(entries.size()), entries.size());
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int b) {
		if (b == 0 && draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(mx, my, b);
	}

	private void updateScrollFromMouse(double my, int top, int viewH, int maxScroll, int count) {
		if (maxScroll == 0) return;
		int totalH = count * ROW_STRIDE - ROW_GAP;
		int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
		double frac = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
		targetScroll = Math.max(0, Math.min(1, frac)) * maxScroll;
		scroll = targetScroll;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (v != 0) {
			String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";
			int maxScroll = maxScrollPx(filteredEntries(player).size());
			targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - v * 28));
			return true;
		}
		return super.mouseScrolled(mx, my, h, v);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	private static String formatStack(int n) {
		if (n >= 93312) return String.format(java.util.Locale.ROOT, "SDC %.2f", n / 93312.0);
		if (n >= 1728) return String.format(java.util.Locale.ROOT, "SB %.2f", n / 1728.0);
		int stacks = n / 64, singles = n % 64;
		if (stacks > 0) return singles == 0 ? "64 x " + stacks : "64 x " + stacks + " + " + singles;
		return String.valueOf(n);
	}
}
