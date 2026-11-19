package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import smp.aeternum.carpet.client.ui.AeternumTheme;
import smp.aeternum.carpet.client.ui.UiButton;
import smp.aeternum.carpet.client.ui.UiScreen;
import smp.aeternum.carpet.materials.MaterialEntry;
import smp.aeternum.carpet.materials.MaterialList;
import smp.aeternum.carpet.network.MaterialsDeletePayload;
import smp.aeternum.carpet.network.MaterialsRefreshPayload;
import smp.aeternum.carpet.network.ProjectDownloadRequestPayload;

public final class MaterialsScreen extends UiScreen {
	private static final Gson GSON = new Gson();
	private static final int CARD_H = 44;
	private static final int CARD_GAP = 4;
	private static final int ROW_STRIDE = CARD_H + CARD_GAP;

	private List<MaterialList> lists;
	private UiButton refreshButton;
	private UiButton uploadButton;
	private UiButton hudToggleButton;
	private UiButton hudPosButton;
	private long refreshEnableAt;
	private double scroll;
	private double targetScroll;
	private boolean draggingScrollbar;

	public MaterialsScreen(String json) {
		super(Text.literal("Proyectos"));
		lists = decode(json);
	}

	public void replaceData(String json) {
		lists = decode(json);
		clampScroll();
	}

	@Override
	protected void init() {
		clearChildren();
		computePanel(Math.min(640, (int) (width * 0.75)), Math.min(420, (int) (height * 0.78)));
		int footerY = panelY + panelH - 28;
		hudToggleButton = UiButton.secondary(panelX + 44, footerY, 48, 20,
			MaterialsHudOverlay.hudEnabled ? "ON" : "OFF",
			b -> {
				MaterialsHudOverlay.toggle();
				b.setLabel(MaterialsHudOverlay.hudEnabled ? "ON" : "OFF");
				b.setVariant(MaterialsHudOverlay.hudEnabled ? UiButton.Variant.SUCCESS : UiButton.Variant.DANGER);
			});
		hudToggleButton.setVariant(MaterialsHudOverlay.hudEnabled ? UiButton.Variant.SUCCESS : UiButton.Variant.DANGER);
		addDrawableChild(hudToggleButton);

		hudPosButton = UiButton.secondary(panelX + 98, footerY, 50, 20,
			MaterialsHudOverlay.hudPosition.name(),
			b -> {
				MaterialsHudOverlay.cyclePosition();
				b.setLabel(MaterialsHudOverlay.hudPosition.name());
			});
		addDrawableChild(hudPosButton);

		boolean isOp = client.player != null && client.player.hasPermissionLevel(2);
		uploadButton = UiButton.primary(panelX + panelW - 224, footerY, 128, 20, "+ Subir Proyecto",
			b -> client.setScreen(new LitematicPickerScreen(this)));
		uploadButton.active = isOp;
		addDrawableChild(uploadButton);

		refreshButton = UiButton.secondary(panelX + panelW - 90, footerY, 78, 20, "Refrescar", b -> {
			b.active = false;
			b.setFocused(false);
			refreshEnableAt = System.currentTimeMillis() + 300;
			ClientPlayNetworking.send(new MaterialsRefreshPayload());
		});
		addDrawableChild(refreshButton);
	}

	private int listTop() { return panelY + 54; }
	private int listBottom() { return panelY + panelH - 42; }

	private int maxScrollPx() {
		int contentH = lists.size() * ROW_STRIDE - CARD_GAP;
		int viewH = listBottom() - listTop();
		return Math.max(0, contentH - viewH);
	}

	private void clampScroll() {
		int max = maxScrollPx();
		targetScroll = Math.max(0, Math.min(targetScroll, max));
		scroll = Math.max(0, Math.min(scroll, max));
	}

	@Override
	public void render(DrawContext c, int mx, int my, float d) {
		computePanel(Math.min(640, (int) (width * 0.75)), Math.min(420, (int) (height * 0.78)));
		scroll += (targetScroll - scroll) * Math.min(1.0, d * 0.45 + 0.25);
		clampScroll();

		renderBackground(c, mx, my, d);
		drawWindowFrame(c, "PROYECTOS", lists.size() + (lists.size() == 1 ? " proyecto registrado" : " proyectos registrados"));
		drawCloseButton(c, mx, my);

		int lTop = listTop();
		int lBottom = listBottom();
		int viewH = lBottom - lTop;
		int cardX = panelX + 12;
		int cardW = panelW - 30;

		if (lists.isEmpty()) {
			c.drawCenteredTextWithShadow(textRenderer, "No hay proyectos registrados.", width / 2, lTop + viewH / 2 - 12, AeternumTheme.TEXT_SECONDARY);
			c.drawCenteredTextWithShadow(textRenderer, "Coloca un plano en Litematica y súbelo con '+ Subir Proyecto'.", width / 2, lTop + viewH / 2 + 4, AeternumTheme.TEXT_MUTED);
		} else {
			c.enableScissor(panelX + 6, lTop, panelX + panelW - 10, lBottom);
			for (int i = 0; i < lists.size(); i++) {
				int cardY = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
				if (cardY + CARD_H < lTop || cardY > lBottom) continue;

				MaterialList list = lists.get(i);
				int delX = cardX + cardW - 34;
				int loadX = delX - 60;
				int btnY = cardY + (CARD_H - 20) / 2;
				boolean inListArea = my >= lTop && my < lBottom;
				boolean overCard = inListArea && mx >= cardX && mx < cardX + cardW && my >= cardY && my < cardY + CARD_H;
				boolean overDelete = inListArea && mx >= delX && mx < delX + 26 && my >= btnY && my < btnY + 20;
				boolean overLoad = list.hasSchematic && inListArea && mx >= loadX && mx < loadX + 54 && my >= btnY && my < btnY + 20;

				int bg = (overCard && !overDelete && !overLoad) ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG;
				int border = (overCard && !overDelete && !overLoad) ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE;
				AeternumTheme.drawCard(c, cardX, cardY, cardW, CARD_H, bg, border);

				ProgressStats matStats = computeMaterialStats(list);
				int matPct = Math.round(matStats.ratio * 100f);

				int rightBound = list.hasSchematic ? loadX : delX;
				String title = AeternumTheme.ellipsize(textRenderer, list.name, rightBound - cardX - 130);
				c.drawTextWithShadow(textRenderer, title, cardX + 12, cardY + 7, AeternumTheme.TEXT_PRIMARY);

				int badgeX = cardX + 16 + textRenderer.getWidth(title);
				if (list.hasSchematic) {
					int subCount = list.subregions != null ? list.subregions.size() : 0;
					AeternumTheme.drawBadge(c, textRenderer, badgeX, cardY + 5, subCount + " sec", 0xFF202028, AeternumTheme.TEXT_PRIMARY);
				}

				if (list.hasSchematic && list.totalBlocks > 0) {
					int halfW = (rightBound - cardX - 28) / 2;
					float buildRatio = Math.min(1f, (float) list.placedBlocks / (float) list.totalBlocks);
					int buildPct = Math.round(buildRatio * 100f);

					c.drawText(textRenderer, "Mat " + matPct + "%", cardX + 12, cardY + 22, AeternumTheme.TEXT_SECONDARY, false);
					AeternumTheme.drawProgressBar(c, cardX + 12, cardY + 33, halfW, 4, matStats.ratio, AeternumTheme.ACCENT_GOLD);

					int buildX = cardX + 18 + halfW;
					c.drawText(textRenderer, "Obra " + buildPct + "%", buildX, cardY + 22, AeternumTheme.TEXT_SECONDARY, false);
					AeternumTheme.drawProgressBar(c, buildX, cardY + 33, halfW, 4, buildRatio, AeternumTheme.ACCENT_CYAN);
				} else {
					String pctLabel = matPct + "%  (" + matStats.completed + "/" + list.entries.size() + " completos)";
					int pctW = textRenderer.getWidth(pctLabel);
					c.drawText(textRenderer, pctLabel, rightBound - pctW - 12, cardY + 9, AeternumTheme.TEXT_SECONDARY, false);
					AeternumTheme.drawProgressBar(c, cardX + 12, cardY + 29, rightBound - cardX - 24, 5, matStats.ratio, AeternumTheme.ACCENT_GOLD);
				}

				if (list.hasSchematic) {
					AeternumTheme.drawCard(c, loadX, btnY, 54, 20, overLoad ? 0xFF2E2E38 : 0xFF1D1D24, 0xFFD0D0DC);
					c.drawTextWithShadow(textRenderer, "Cargar", loadX + (54 - textRenderer.getWidth("Cargar")) / 2, btnY + 6, AeternumTheme.TEXT_PRIMARY);
				}

				boolean isOp = client.player != null && client.player.hasPermissionLevel(2);
				boolean shift = isOp && hasShiftDown();
				int delBg = overDelete ? (shift ? 0xFF3A2024 : 0xFF212128) : (shift ? 0xFF2B181B : 0xFF141418);
				int delBorder = shift ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.BORDER_SUBTLE;
				int delFg = shift ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_MUTED;
				AeternumTheme.drawCard(c, delX, btnY, 26, 20, delBg, delBorder);
				c.drawTextWithShadow(textRenderer, "\u00D7", delX + 10, btnY + 6, delFg);
			}
			c.disableScissor();

			AeternumTheme.drawScrollbar(
				c, panelX + panelW - 12, lTop, lBottom,
				lists.size() * ROW_STRIDE - CARD_GAP, viewH, scroll, draggingScrollbar
			);
		}

		AeternumTheme.drawFooter(c, panelX, panelY + panelH - 36, panelW, panelY + panelH);
		c.drawText(textRenderer, "HUD", panelX + 14, panelY + panelH - 22, AeternumTheme.TEXT_SECONDARY, false);
		super.render(c, mx, my, d);

		if (isCloseButtonHit(mx, my)) {
			c.drawTooltip(textRenderer, Text.literal("Cerrar"), mx, my);
		} else if (uploadButton != null && uploadButton.isMouseOver(mx, my)) {
			boolean isOp = client.player != null && client.player.hasPermissionLevel(2);
			c.drawTooltip(textRenderer, Text.literal(isOp ? "Subir desde un placement activo de Litematica" : "Solo operadores pueden subir proyectos"), mx, my);
		} else if (refreshButton != null && refreshButton.isMouseOver(mx, my)) {
			c.drawTooltip(textRenderer, Text.literal("Recargar proyectos desde el servidor"), mx, my);
		} else if (hudPosButton != null && hudPosButton.isMouseOver(mx, my)) {
			c.drawTooltip(textRenderer, Text.literal("Cambiar posición del HUD en pantalla"), mx, my);
		} else if (!lists.isEmpty() && my >= lTop && my < lBottom) {
			for (int i = 0; i < lists.size(); i++) {
				int cardY = lTop + i * ROW_STRIDE - (int) Math.round(scroll);
				int delX = cardX + cardW - 34;
				int loadX = delX - 60;
				int btnY = cardY + (CARD_H - 20) / 2;
				MaterialList item = lists.get(i);
				if (item.hasSchematic && mx >= loadX && mx < loadX + 54 && my >= btnY && my < btnY + 20) {
					c.drawTooltip(textRenderer, Text.literal("Cargar y alinear plano en Litematica"), mx, my);
					break;
				}
				if (mx >= delX && mx < delX + 26 && my >= btnY && my < btnY + 20) {
					boolean isOp = client.player != null && client.player.hasPermissionLevel(2);
					c.drawTooltip(textRenderer, Text.literal(isOp ? (hasShiftDown() ? "Eliminar proyecto" : "Mantén Shift para eliminar") : "Solo operadores"), mx, my);
					break;
				}
			}
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int b) {
		if (b == 0 && isCloseButtonHit(mx, my)) {
			close();
			return true;
		}
		if (super.mouseClicked(mx, my, b)) return true;
		if (b != 0) return false;

		int lTop = listTop();
		int lBottom = listBottom();
		int viewH = lBottom - lTop;
		int maxScroll = maxScrollPx();
		if (maxScroll > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
			draggingScrollbar = true;
			updateScrollFromMouse(my, lTop, viewH, maxScroll);
			return true;
		}

		int cardX = panelX + 12;
		int cardW = panelW - 30;
		if (my >= lTop && my < lBottom && mx >= cardX && mx < cardX + cardW) {
			int relY = (int) (my - lTop + scroll);
			int idx = relY / ROW_STRIDE;
			if (idx >= 0 && idx < lists.size() && (relY % ROW_STRIDE) < CARD_H) {
				int cardY = lTop + idx * ROW_STRIDE - (int) Math.round(scroll);
				int delX = cardX + cardW - 34;
				int loadX = delX - 60;
				int btnY = cardY + (CARD_H - 20) / 2;
				MaterialList target = lists.get(idx);
				if (target.hasSchematic && mx >= loadX && mx < loadX + 54 && my >= btnY && my < btnY + 20) {
					ClientPlayNetworking.send(new ProjectDownloadRequestPayload(target.name, true));
					return true;
				}
				if (mx >= delX && mx < delX + 26 && my >= btnY && my < btnY + 20) {
					boolean isOp = client.player != null && client.player.hasPermissionLevel(2);
					if (isOp && hasShiftDown()) {
						ClientPlayNetworking.send(new MaterialsDeletePayload(target.name));
					}
					return true;
				}
				client.setScreen(new MaterialsDetailScreen(this, target));
				return true;
			}
		}
		return false;
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
		if (b == 0 && draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(mx, my, b);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (v != 0 && maxScrollPx() > 0) {
			targetScroll = Math.max(0, Math.min(maxScrollPx(), targetScroll - v * 28));
			return true;
		}
		return super.mouseScrolled(mx, my, h, v);
	}

	private void updateScrollFromMouse(double my, int top, int viewH, int maxScroll) {
		int totalH = lists.size() * ROW_STRIDE - CARD_GAP;
		int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
		double frac = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
		targetScroll = Math.max(0, Math.min(1, frac)) * maxScroll;
		scroll = targetScroll;
	}

	@Override
	public void tick() {
		if (refreshButton != null && !refreshButton.active && System.currentTimeMillis() >= refreshEnableAt) {
			refreshButton.active = true;
		}
	}

	@Override
	public void close() {
		client.setScreen(null);
	}

	private static ProgressStats computeMaterialStats(MaterialList list) {
		if (list.entries.isEmpty()) return new ProgressStats(0f, 0);
		long totalUnits = 0;
		long haveUnits = 0;
		int completed = 0;
		for (MaterialEntry e : list.entries) {
			int t = Math.max(0, e.total);
			int h = Math.max(0, Math.min(t, e.placed + e.available));
			totalUnits += t;
			haveUnits += h;
			if (h >= t) completed++;
		}
		float ratio = totalUnits == 0 ? 1f : (float) haveUnits / (float) totalUnits;
		return new ProgressStats(ratio, completed);
	}

	private record ProgressStats(float ratio, int completed) {}

	static List<MaterialList> decodeLists(String json) {
		MaterialList[] arr = GSON.fromJson(json, MaterialList[].class);
		return arr == null ? new ArrayList<>() : new ArrayList<>(List.of(arr));
	}

	private static List<MaterialList> decode(String json) {
		return decodeLists(json);
	}
}
