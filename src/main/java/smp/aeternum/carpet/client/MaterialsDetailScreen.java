package smp.aeternum.carpet.client;

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
import smp.aeternum.carpet.materials.MaterialEntry;
import smp.aeternum.carpet.materials.MaterialList;
import smp.aeternum.carpet.network.MaterialsActionPayload;
import smp.aeternum.carpet.network.ProjectDownloadRequestPayload;
import smp.aeternum.carpet.network.ProjectPlacementPayload;
import smp.aeternum.carpet.network.ProjectSubregionActionPayload;

public final class MaterialsDetailScreen extends UiScreen {
	private enum MainTab { MATERIALS, SUBREGIONS, SYNC }
	private enum MatFilter { ALL, MISSING, MINE, DONE }
	private enum SubFilter { ALL, FREE, CLAIMED, DONE }

	private static final int MAT_ROW_H = 30, MAT_GAP = 3, MAT_STRIDE = MAT_ROW_H + MAT_GAP;
	private static final int SUB_ROW_H = 40, SUB_GAP = 4, SUB_STRIDE = SUB_ROW_H + SUB_GAP;
	private static final String[] ROTATIONS = {"NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90"};
	private static final String[] MIRRORS = {"NONE", "LEFT_RIGHT", "FRONT_BACK"};

	private final Screen parent;
	private MaterialList list;
	private MainTab activeTab;
	private MatFilter matFilter = MatFilter.ALL;
	private SubFilter subFilter = SubFilter.ALL;

	private UiTextField searchField;
	private UiButton tabMatBtn, tabSubBtn, tabSyncBtn;
	private UiButton f1Btn, f2Btn, f3Btn, f4Btn;

	// Sync tab widgets
	private UiTextField xField, yField, zField;
	private UiButton usePosBtn, rotBtn, mirrorBtn, savePlacementBtn;
	private UiButton loadLitematicaBtn, downloadBtn;
	private int editRotIdx, editMirrorIdx;
	private String editDim;

	private double scroll;
	private double targetScroll;
	private boolean draggingScrollbar;

	public MaterialsDetailScreen(Screen parent, MaterialList list) {
		super(Text.literal(list.name));
		this.parent = parent;
		this.list = list;
		this.activeTab = list.hasSchematic ? MainTab.SUBREGIONS : MainTab.MATERIALS;
		syncPlacementFieldsFromList();
	}

	public void replaceData(MaterialList updated) {
		this.list = updated;
		syncPlacementFieldsFromList();
	}

	public void replaceFromJson(String json) {
		var lists = MaterialsScreen.decodeLists(json);
		for (var serverList : lists) {
			if (!serverList.name.equals(this.list.name)) continue;
			this.list = serverList;
			return;
		}
	}

	private void syncPlacementFieldsFromList() {
		MaterialList.PlacementInfo pl = list.placement != null ? list.placement : new MaterialList.PlacementInfo();
		editDim = pl.dimension != null ? pl.dimension : "minecraft:overworld";
		editRotIdx = indexOf(ROTATIONS, pl.rotation);
		editMirrorIdx = indexOf(MIRRORS, pl.mirror);
	}

	private static int indexOf(String[] arr, String val) {
		for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
		return 0;
	}

	@Override
	protected void init() {
		String prevQuery = searchField != null ? searchField.getText() : "";
		clearChildren();
		computePanel(Math.min(720, (int) (width * 0.82)), Math.min(440, (int) (height * 0.82)));

		addDrawableChild(UiButton.ghost(panelX + 10, panelY + 10, 24, 24, "\u2190", b -> close()));

		if (list.hasSchematic) {
			int tx = panelX + panelW - 300;
			tabSubBtn = UiButton.secondary(tx, panelY + 11, 86, 22, "Subregiones", b -> switchTab(MainTab.SUBREGIONS));
			tabMatBtn = UiButton.secondary(tx + 90, panelY + 11, 82, 22, "Materiales", b -> switchTab(MainTab.MATERIALS));
			tabSyncBtn = UiButton.secondary(tx + 176, panelY + 11, 84, 22, "Plano / Sync", b -> switchTab(MainTab.SYNC));
			tabSubBtn.setVariant(activeTab == MainTab.SUBREGIONS ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
			tabMatBtn.setVariant(activeTab == MainTab.MATERIALS ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
			tabSyncBtn.setVariant(activeTab == MainTab.SYNC ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
			addDrawableChild(tabSubBtn);
			addDrawableChild(tabMatBtn);
			addDrawableChild(tabSyncBtn);
		} else {
			tabSubBtn = null;
			tabMatBtn = null;
			tabSyncBtn = null;
		}

		if (activeTab == MainTab.MATERIALS || activeTab == MainTab.SUBREGIONS) {
			int barY = panelY + 52;
			int searchW = Math.max(140, panelW - 294);
			String ph = activeTab == MainTab.MATERIALS ? "Buscar ítem…" : "Buscar subregión…";
			searchField = new UiTextField(textRenderer, panelX + 12, barY, searchW, 20, ph);
			searchField.setMaxLength(80);
			searchField.setText(prevQuery);
			searchField.setChangedListener(s -> { targetScroll = 0; scroll = 0; });
			addDrawableChild(searchField);

			int tabX = panelX + panelW - 270;
			if (activeTab == MainTab.MATERIALS) {
				f1Btn = UiButton.secondary(tabX, barY, 54, 20, "Todos", b -> setMatFilter(MatFilter.ALL));
				f2Btn = UiButton.secondary(tabX + 58, barY, 64, 20, "Faltan", b -> setMatFilter(MatFilter.MISSING));
				f3Btn = UiButton.secondary(tabX + 126, barY, 70, 20, "Míos", b -> setMatFilter(MatFilter.MINE));
				f4Btn = UiButton.secondary(tabX + 200, barY, 58, 20, "Listos", b -> setMatFilter(MatFilter.DONE));
			} else {
				f1Btn = UiButton.secondary(tabX, barY, 54, 20, "Todas", b -> setSubFilter(SubFilter.ALL));
				f2Btn = UiButton.secondary(tabX + 58, barY, 64, 20, "Libres", b -> setSubFilter(SubFilter.FREE));
				f3Btn = UiButton.secondary(tabX + 126, barY, 70, 20, "En obra", b -> setSubFilter(SubFilter.CLAIMED));
				f4Btn = UiButton.secondary(tabX + 200, barY, 58, 20, "Listas", b -> setSubFilter(SubFilter.DONE));
			}
			addDrawableChild(f1Btn);
			addDrawableChild(f2Btn);
			addDrawableChild(f3Btn);
			addDrawableChild(f4Btn);
			updateFilterStyles();

			xField = null; yField = null; zField = null;
			usePosBtn = null; rotBtn = null; mirrorBtn = null; savePlacementBtn = null;
			loadLitematicaBtn = null; downloadBtn = null;
		} else {
			searchField = null; f1Btn = null; f2Btn = null; f3Btn = null; f4Btn = null;
			initSyncTab();
		}
	}

	private void initSyncTab() {
		int cx = panelX + 20;
		int y = panelY + 64;
		loadLitematicaBtn = UiButton.primary(cx, y, 210, 24, "Cargar en Litematica (Auto)",
			b -> ClientPlayNetworking.send(new ProjectDownloadRequestPayload(list.name, true)));
		downloadBtn = UiButton.secondary(cx + 220, y, 160, 24, "Descargar .litematic",
			b -> ClientPlayNetworking.send(new ProjectDownloadRequestPayload(list.name, false)));
		addDrawableChild(loadLitematicaBtn);
		addDrawableChild(downloadBtn);

		MaterialList.PlacementInfo pl = list.placement != null ? list.placement : new MaterialList.PlacementInfo();
		boolean isOp = client.player != null && client.player.hasPermissionLevel(2);

		int fy = panelY + 178;
		xField = new UiTextField(textRenderer, cx, fy, 84, 20, "X");
		xField.setText(String.valueOf(pl.x));
		xField.setEditable(isOp);
		yField = new UiTextField(textRenderer, cx + 92, fy, 84, 20, "Y");
		yField.setText(String.valueOf(pl.y));
		yField.setEditable(isOp);
		zField = new UiTextField(textRenderer, cx + 184, fy, 84, 20, "Z");
		zField.setText(String.valueOf(pl.z));
		zField.setEditable(isOp);
		addDrawableChild(xField);
		addDrawableChild(yField);
		addDrawableChild(zField);

		usePosBtn = UiButton.secondary(cx + 278, fy, 130, 20, "Usar mi posición", b -> {
			if (client.player == null || client.world == null) return;
			BlockPos pos = client.player.getBlockPos();
			editDim = client.world.getRegistryKey().getValue().toString();
			xField.setText(String.valueOf(pos.getX()));
			yField.setText(String.valueOf(pos.getY()));
			zField.setText(String.valueOf(pos.getZ()));
		});
		usePosBtn.active = isOp;
		addDrawableChild(usePosBtn);

		int ry = panelY + 214;
		rotBtn = UiButton.secondary(cx, ry, 132, 20, "Rotación: " + rotLabel(ROTATIONS[editRotIdx]), b -> {
			editRotIdx = (editRotIdx + 1) % ROTATIONS.length;
			b.setLabel("Rotación: " + rotLabel(ROTATIONS[editRotIdx]));
		});
		rotBtn.active = isOp;

		mirrorBtn = UiButton.secondary(cx + 140, ry, 140, 20, "Espejo: " + MIRRORS[editMirrorIdx], b -> {
			editMirrorIdx = (editMirrorIdx + 1) % MIRRORS.length;
			b.setLabel("Espejo: " + MIRRORS[editMirrorIdx]);
		});
		mirrorBtn.active = isOp;

		savePlacementBtn = UiButton.primary(cx + 288, ry, 136, 20, "Guardar Placement", b -> {
			int px = parseCoord(xField.getText(), pl.x);
			int py = parseCoord(yField.getText(), pl.y);
			int pz = parseCoord(zField.getText(), pl.z);
			String dim = editDim != null ? editDim : "minecraft:overworld";
			ClientPlayNetworking.send(new ProjectPlacementPayload(
				list.name, dim, px, py, pz, ROTATIONS[editRotIdx], MIRRORS[editMirrorIdx]
			));
		});
		savePlacementBtn.active = isOp;
		addDrawableChild(rotBtn);
		addDrawableChild(mirrorBtn);
		addDrawableChild(savePlacementBtn);
	}

	private static int parseCoord(String text, int fallback) {
		try { return Integer.parseInt(text.trim()); } catch (NumberFormatException e) { return fallback; }
	}

	private void switchTab(MainTab tab) {
		this.activeTab = tab;
		this.scroll = 0;
		this.targetScroll = 0;
		init();
	}

	private void setMatFilter(MatFilter mode) {
		matFilter = mode;
		targetScroll = 0;
		scroll = 0;
		updateFilterStyles();
	}

	private void setSubFilter(SubFilter mode) {
		subFilter = mode;
		targetScroll = 0;
		scroll = 0;
		updateFilterStyles();
	}

	private void updateFilterStyles() {
		if (f1Btn == null) return;
		if (activeTab == MainTab.MATERIALS) {
			f1Btn.setVariant(matFilter == MatFilter.ALL ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
			f2Btn.setVariant(matFilter == MatFilter.MISSING ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
			f3Btn.setVariant(matFilter == MatFilter.MINE ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
			f4Btn.setVariant(matFilter == MatFilter.DONE ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
		} else if (activeTab == MainTab.SUBREGIONS) {
			f1Btn.setVariant(subFilter == SubFilter.ALL ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
			f2Btn.setVariant(subFilter == SubFilter.FREE ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
			f3Btn.setVariant(subFilter == SubFilter.CLAIMED ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
			f4Btn.setVariant(subFilter == SubFilter.DONE ? UiButton.Variant.PRIMARY : UiButton.Variant.SECONDARY);
		}
	}

	private List<MaterialEntry> activeMaterialSource() {
		return list.entries != null ? list.entries : List.of();
	}

	private List<MaterialEntry> filteredMaterials(String player) {
		String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
		List<MaterialEntry> out = new ArrayList<>();
		for (MaterialEntry e : activeMaterialSource()) {
			if (!q.isEmpty()) {
				String name = e.itemName != null ? e.itemName.toLowerCase() : "";
				String id = e.itemId != null ? e.itemId.toLowerCase() : "";
				if (!name.contains(q) && !id.contains(q)) continue;
			}
			int missing = e.missing();
			switch (matFilter) {
				case MISSING -> { if (missing == 0) continue; }
				case MINE -> { if (!player.equals(e.claimedBy)) continue; }
				case DONE -> { if (missing > 0) continue; }
				default -> {}
			}
			out.add(e);
		}
		out.sort(Comparator
			.comparingInt((MaterialEntry e) -> e.missing() > 0 ? 0 : 1)
			.thenComparing(e -> e.total, Comparator.reverseOrder()));
		return out;
	}

	private List<MaterialList.SubregionInfo> filteredSubregions() {
		if (list.subregions == null) return List.of();
		String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
		List<MaterialList.SubregionInfo> out = new ArrayList<>();
		for (var sub : list.subregions) {
			if (!q.isEmpty() && !sub.name.toLowerCase().contains(q)) continue;
			switch (subFilter) {
				case FREE -> { if (!"NONE".equals(sub.status)) continue; }
				case CLAIMED -> { if (!"CLAIMED".equals(sub.status)) continue; }
				case DONE -> { if (!"DONE".equals(sub.status)) continue; }
				default -> {}
			}
			out.add(sub);
		}
		out.sort(Comparator.comparing(s -> s.name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private int matListTop() { return panelY + 98; }
	private int subListTop() { return panelY + 80; }
	private int listBottom() { return panelY + panelH - 14; }

	private int maxScrollPx(int count, int stride, int gap, int top) {
		int contentH = count * stride - gap;
		int viewH = listBottom() - top;
		return Math.max(0, contentH - viewH);
	}

	@Override
	public void render(DrawContext c, int mx, int my, float d) {
		computePanel(Math.min(720, (int) (width * 0.82)), Math.min(440, (int) (height * 0.82)));
		renderBackground(c, mx, my, d);

		AeternumTheme.drawPanel(c, panelX, panelY, panelW, panelH);
		AeternumTheme.drawHeader(c, panelX, panelY, panelW, 44);
		c.fill(panelX + 1, panelY + 1, panelX + 4, panelY + 43, AeternumTheme.ACCENT_GOLD);

		String titleShown = AeternumTheme.ellipsize(textRenderer, list.name, panelW - (list.hasSchematic ? 350 : 90));
		c.drawTextWithShadow(textRenderer, titleShown, panelX + 38, panelY + 10, AeternumTheme.TEXT_PRIMARY);
		String sub = list.hasSchematic
			? (list.subregions.size() + " subregiones · " + list.totalBlocks + " bloques")
			: (list.entries.size() + " materiales");
		c.drawText(textRenderer, sub, panelX + 38, panelY + 25, AeternumTheme.TEXT_SECONDARY, false);
		drawCloseButton(c, mx, my);

		if (activeTab == MainTab.MATERIALS) renderMaterialsTab(c, mx, my, d);
		else if (activeTab == MainTab.SUBREGIONS) renderSubregionsTab(c, mx, my, d);
		else renderSyncTab(c, mx, my, d);

		if (searchField != null) {
			searchField.renderBox(c, panelX + 12, panelY + 52, Math.max(140, panelW - 294), 20, mx, my, d);
		}
		super.render(c, mx, my, d);
	}

	private void renderMaterialsTab(DrawContext c, int mx, int my, float d) {
		String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";
		List<MaterialEntry> entries = filteredMaterials(player);
		int lTop = matListTop();
		int lBottom = listBottom();
		int maxScroll = maxScrollPx(entries.size(), MAT_STRIDE, MAT_GAP, lTop);
		targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
		scroll += (targetScroll - scroll) * Math.min(1.0, d * 0.45 + 0.25);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		int colY = lTop - 18;
		c.fill(panelX + 12, colY, panelX + panelW - 18, colY + 15, AeternumTheme.CARD_ALT_BG);
		c.drawText(textRenderer, "ÍTEM", panelX + 20, colY + 4, AeternumTheme.TEXT_MUTED, false);
		int colTotalX = panelX + (int) (panelW * 0.43);
		int colMissingX = panelX + (int) (panelW * 0.58);
		int colClaimX = panelX + (int) (panelW * 0.72);
		c.drawText(textRenderer, "TOTAL", colTotalX, colY + 4, AeternumTheme.TEXT_MUTED, false);
		c.drawText(textRenderer, "FALTA", colMissingX, colY + 4, AeternumTheme.TEXT_MUTED, false);
		c.drawText(textRenderer, "CLAIM", colClaimX, colY + 4, AeternumTheme.TEXT_MUTED, false);

		int viewH = lBottom - lTop;
		int rowX = panelX + 12, rowW = panelW - 30;
		if (entries.isEmpty()) {
			c.drawCenteredTextWithShadow(textRenderer, "Sin resultados.", width / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
			return;
		}

		c.enableScissor(panelX + 6, lTop, panelX + panelW - 10, lBottom);
		for (int i = 0; i < entries.size(); i++) {
			int rowY = lTop + i * MAT_STRIDE - (int) Math.round(scroll);
			if (rowY + MAT_ROW_H < lTop || rowY > lBottom) continue;

			MaterialEntry entry = entries.get(i);
			int missing = entry.missing();
			int have = Math.min(entry.total, entry.placed + entry.available);
			float ratio = entry.total <= 0 ? 1f : Math.min(1f, (float) have / (float) entry.total);
			boolean mine = player.equals(entry.claimedBy);
			boolean hover = my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW && my >= rowY && my < rowY + MAT_ROW_H;

			int bg = mine ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : (i % 2 == 0 ? AeternumTheme.CARD_BG : AeternumTheme.CARD_ALT_BG));
			int border = mine ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE;
			AeternumTheme.drawCard(c, rowX, rowY, rowW, MAT_ROW_H, bg, border);

			AeternumTheme.drawItemIcon(c, entry.itemId, rowX + 6, rowY + 7);
			int nameMaxW = colTotalX - (rowX + 30);
			c.drawTextWithShadow(textRenderer, AeternumTheme.ellipsize(textRenderer, entry.itemName, nameMaxW), rowX + 28, rowY + 6, AeternumTheme.TEXT_PRIMARY);
			AeternumTheme.drawProgressBar(c, rowX + 28, rowY + 20, Math.max(40, nameMaxW - 6), 4, ratio, AeternumTheme.ACCENT_GOLD);

			c.drawText(textRenderer, formatStack(entry.total), colTotalX, rowY + 11, AeternumTheme.TEXT_SECONDARY, false);
			c.drawText(textRenderer, missing == 0 ? "\u2713 Listo" : formatStack(missing), colMissingX, rowY + 11,
				missing == 0 ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY, false);

			String claimed = (entry.claimedBy != null && !entry.claimedBy.isEmpty()) ? entry.claimedBy : "-";
			c.drawText(textRenderer, AeternumTheme.ellipsize(textRenderer, claimed, (rowX + rowW - 64) - colClaimX), colClaimX, rowY + 11,
				mine ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_MUTED, false);

			int btnX = rowX + rowW - 58, btnY = rowY + 6, btnW = 52, btnH = 18;
			boolean btnHover = my >= lTop && my < lBottom && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;
			if (entry.claimedBy == null || entry.claimedBy.isEmpty()) {
				AeternumTheme.drawCard(c, btnX, btnY, btnW, btnH, btnHover ? 0xFF2E2E38 : 0xFF1D1D24, 0xFFD4D4DF);
				c.drawTextWithShadow(textRenderer, "Claim", btnX + (btnW - textRenderer.getWidth("Claim")) / 2, btnY + 5, AeternumTheme.TEXT_PRIMARY);
			} else if (mine) {
				AeternumTheme.drawCard(c, btnX, btnY, btnW, btnH, btnHover ? 0xFF2B2B34 : 0xFF18181E, 0xFF8C8C9A);
				c.drawTextWithShadow(textRenderer, "Soltar", btnX + (btnW - textRenderer.getWidth("Soltar")) / 2, btnY + 5, AeternumTheme.TEXT_SECONDARY);
			}
		}
		c.disableScissor();

		AeternumTheme.drawScrollbar(c, panelX + panelW - 12, lTop, lBottom,
			entries.size() * MAT_STRIDE - MAT_GAP, viewH, scroll, draggingScrollbar);
	}

	private void renderSubregionsTab(DrawContext c, int mx, int my, float d) {
		List<MaterialList.SubregionInfo> subs = filteredSubregions();
		int lTop = subListTop();
		int lBottom = listBottom();
		int maxScroll = maxScrollPx(subs.size(), SUB_STRIDE, SUB_GAP, lTop);
		targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
		scroll += (targetScroll - scroll) * Math.min(1.0, d * 0.45 + 0.25);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		int viewH = lBottom - lTop;
		int rowX = panelX + 12, rowW = panelW - 30;
		if (subs.isEmpty()) {
			c.drawCenteredTextWithShadow(textRenderer, "No hay subregiones que coincidan.", width / 2, lTop + viewH / 2 - 4, AeternumTheme.TEXT_SECONDARY);
			return;
		}

		String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";
		boolean isOp = client.player != null && client.player.hasPermissionLevel(2);
		c.enableScissor(panelX + 6, lTop, panelX + panelW - 10, lBottom);
		for (int i = 0; i < subs.size(); i++) {
			int ry = lTop + i * SUB_STRIDE - (int) Math.round(scroll);
			if (ry + SUB_ROW_H < lTop || ry > lBottom) continue;

			MaterialList.SubregionInfo sub = subs.get(i);
			boolean isDone = "DONE".equals(sub.status);
			boolean hasClaimer = sub.claimedBy != null && !sub.claimedBy.isEmpty();
			boolean isMine = player.equals(sub.claimedBy);
			boolean isClaimed = "CLAIMED".equals(sub.status);
			boolean hover = my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW && my >= ry && my < ry + SUB_ROW_H;

			int bg = isDone ? 0xFF1D2220 : (isClaimed ? AeternumTheme.CARD_SELECTED : (hover ? AeternumTheme.CARD_HOVER : AeternumTheme.CARD_BG));
			int border = isDone ? AeternumTheme.TEXT_PRIMARY : (isClaimed ? AeternumTheme.BORDER_BRIGHT : AeternumTheme.BORDER_SUBTLE);
			AeternumTheme.drawCard(c, rowX, ry, rowW, SUB_ROW_H, bg, border);

			int doneBtnX = rowX + rowW - 64;
			int claimBtnX = doneBtnX - 62;
			int btnY = ry + 10;

			String nameShown = AeternumTheme.ellipsize(textRenderer, sub.name, claimBtnX - rowX - 130);
			c.drawTextWithShadow(textRenderer, nameShown, rowX + 10, ry + 7, AeternumTheme.TEXT_PRIMARY);

			String statusText = switch (sub.status) {
				case "DONE" -> "\u2713 Lista" + (hasClaimer ? " (" + sub.claimedBy + ")" : "");
				case "CLAIMED" -> "Claim: " + (hasClaimer ? sub.claimedBy : "?");
				default -> "Libre";
			};
			AeternumTheme.drawBadge(c, textRenderer, rowX + 16 + textRenderer.getWidth(nameShown), ry + 5,
				statusText, isDone ? 0xFF282830 : 0xFF1A1A20, isDone ? AeternumTheme.TEXT_PRIMARY : AeternumTheme.TEXT_SECONDARY);

			float ratio = sub.totalBlocks <= 0 ? 1f : Math.min(1f, (float) sub.placedBlocks / (float) sub.totalBlocks);
			String blockLabel = sub.placedBlocks + " / " + sub.totalBlocks + " (" + Math.round(ratio * 100f) + "%)";
			c.drawText(textRenderer, blockLabel, rowX + 10, ry + 24, AeternumTheme.TEXT_MUTED, false);
			int barX = rowX + 18 + textRenderer.getWidth(blockLabel);
			int barW = Math.max(40, claimBtnX - barX - 10);
			AeternumTheme.drawProgressBar(c, barX, ry + 26, barW, 4, ratio, AeternumTheme.ACCENT_CYAN);

			// Button 1: Claim / Soltar
			boolean canUnclaim = hasClaimer && (isMine || isOp);
			boolean claimActive = !hasClaimer || canUnclaim;
			String claimLabel = !hasClaimer ? "Claim" : (canUnclaim ? "Soltar" : "Ocupada");
			boolean overClaim = claimActive && my >= lTop && my < lBottom && mx >= claimBtnX && mx < claimBtnX + 56 && my >= btnY && my < btnY + 20;
			int cBg = !claimActive ? 0xFF121216 : (overClaim ? 0xFF2E2E38 : 0xFF1D1D24);
			int cBorder = !claimActive ? AeternumTheme.BORDER_SUBTLE : (hasClaimer ? 0xFF8C8C9A : 0xFFD0D0DC);
			int cFg = !claimActive ? AeternumTheme.TEXT_MUTED : (hasClaimer ? AeternumTheme.TEXT_SECONDARY : AeternumTheme.TEXT_PRIMARY);
			AeternumTheme.drawCard(c, claimBtnX, btnY, 56, 20, cBg, cBorder);
			c.drawTextWithShadow(textRenderer, claimLabel, claimBtnX + (56 - textRenderer.getWidth(claimLabel)) / 2, btnY + 6, cFg);

			// Button 2: Listo / Reabrir
			String doneLabel = isDone ? "Reabrir" : "Listo \u2713";
			boolean overDone = my >= lTop && my < lBottom && mx >= doneBtnX && mx < doneBtnX + 56 && my >= btnY && my < btnY + 20;
			int dBg = overDone ? 0xFF2E2E38 : (isDone ? 0xFF262630 : 0xFF1D1D24);
			int dBorder = isDone ? AeternumTheme.TEXT_PRIMARY : 0xFFD0D0DC;
			AeternumTheme.drawCard(c, doneBtnX, btnY, 56, 20, dBg, dBorder);
			c.drawTextWithShadow(textRenderer, doneLabel, doneBtnX + (56 - textRenderer.getWidth(doneLabel)) / 2, btnY + 6, AeternumTheme.TEXT_PRIMARY);
		}
		c.disableScissor();

		AeternumTheme.drawScrollbar(c, panelX + panelW - 12, lTop, lBottom,
			subs.size() * SUB_STRIDE - SUB_GAP, viewH, scroll, draggingScrollbar);
	}

	private void renderSyncTab(DrawContext c, int mx, int my, float d) {
		int cx = panelX + 20;
		c.drawTextWithShadow(textRenderer, "SINCRONIZACIÓN CON LITEMATICA", cx, panelY + 50, AeternumTheme.TEXT_PRIMARY);

		MaterialList.PlacementInfo pl = list.placement != null ? list.placement : new MaterialList.PlacementInfo();
		int cardY = panelY + 98;
		AeternumTheme.drawCard(c, cx, cardY, panelW - 40, 52, AeternumTheme.CARD_BG, AeternumTheme.BORDER_SUBTLE);
		String uploader = list.uploadedBy != null && !list.uploadedBy.isEmpty() ? list.uploadedBy : "Servidor";
		c.drawText(textRenderer, "Subido por: " + uploader + "   ·   Bloques colocados: " + list.placedBlocks + " / " + list.totalBlocks,
			cx + 12, cardY + 10, AeternumTheme.TEXT_SECONDARY, false);
		String plText = pl.configured
			? ("Placement: " + pl.dimension + " @ " + pl.x + ", " + pl.y + ", " + pl.z + "  (" + rotLabel(pl.rotation) + ", " + pl.mirror + ")")
			: "Placement sin configurar todavía";
		c.drawTextWithShadow(textRenderer, plText, cx + 12, cardY + 29, AeternumTheme.TEXT_PRIMARY);

		c.drawText(textRenderer, "CONFIGURAR PLACEMENT (SOLO OP) · Dimensión: " + (editDim != null ? editDim : ""), cx, panelY + 164, AeternumTheme.TEXT_MUTED, false);
		if (xField != null) xField.renderBox(c, cx, panelY + 178, 84, 20, mx, my, d);
		if (yField != null) yField.renderBox(c, cx + 92, panelY + 178, 84, 20, mx, my, d);
		if (zField != null) zField.renderBox(c, cx + 184, panelY + 178, 84, 20, mx, my, d);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int b) {
		if (b == 0 && isCloseButtonHit(mx, my)) {
			client.setScreen(null);
			return true;
		}
		if (super.mouseClicked(mx, my, b)) return true;
		if ((b != 0 && b != 1) || activeTab == MainTab.SYNC) return false;

		int lTop = activeTab == MainTab.MATERIALS ? matListTop() : subListTop();
		int lBottom = listBottom();
		int viewH = lBottom - lTop;
		int rowX = panelX + 12, rowW = panelW - 30;

		if (activeTab == MainTab.MATERIALS) {
			String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";
			List<MaterialEntry> entries = filteredMaterials(player);
			int maxScroll = maxScrollPx(entries.size(), MAT_STRIDE, MAT_GAP, lTop);
			if (maxScroll > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
				draggingScrollbar = true;
				updateScrollFromMouse(my, lTop, viewH, maxScroll, entries.size(), MAT_STRIDE, MAT_GAP);
				return true;
			}
			if (my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW) {
				int relY = (int) (my - lTop + scroll);
				int idx = relY / MAT_STRIDE;
				if (idx >= 0 && idx < entries.size() && (relY % MAT_STRIDE) < MAT_ROW_H) {
					int rowY = lTop + idx * MAT_STRIDE - (int) Math.round(scroll);
					int btnX = rowX + rowW - 58, btnY = rowY + 6;
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
		} else {
			List<MaterialList.SubregionInfo> subs = filteredSubregions();
			int maxScroll = maxScrollPx(subs.size(), SUB_STRIDE, SUB_GAP, lTop);
			if (maxScroll > 0 && mx >= panelX + panelW - 16 && mx <= panelX + panelW - 4 && my >= lTop && my <= lBottom) {
				draggingScrollbar = true;
				updateScrollFromMouse(my, lTop, viewH, maxScroll, subs.size(), SUB_STRIDE, SUB_GAP);
				return true;
			}
			if (my >= lTop && my < lBottom && mx >= rowX && mx < rowX + rowW) {
				int relY = (int) (my - lTop + scroll);
				int idx = relY / SUB_STRIDE;
				if (idx >= 0 && idx < subs.size() && (relY % SUB_STRIDE) < SUB_ROW_H) {
					MaterialList.SubregionInfo sub = subs.get(idx);
					int ry = lTop + idx * SUB_STRIDE - (int) Math.round(scroll);
					int doneBtnX = rowX + rowW - 64;
					int claimBtnX = doneBtnX - 62;
					int btnY = ry + 10;
					if (b == 0 && mx >= doneBtnX && mx < doneBtnX + 56 && my >= btnY && my < btnY + 20) {
						String nextAction = "DONE".equals(sub.status) ? "reopen" : "done";
						ClientPlayNetworking.send(new ProjectSubregionActionPayload(list.name, sub.name, nextAction));
						return true;
					}
					if (b == 0 && mx >= claimBtnX && mx < claimBtnX + 56 && my >= btnY && my < btnY + 20) {
						String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";
						boolean isOp = client.player != null && client.player.hasPermissionLevel(2);
						boolean hasClaimer = sub.claimedBy != null && !sub.claimedBy.isEmpty();
						if (!hasClaimer) {
							ClientPlayNetworking.send(new ProjectSubregionActionPayload(list.name, sub.name, "claim"));
						} else if (player.equals(sub.claimedBy) || isOp) {
							ClientPlayNetworking.send(new ProjectSubregionActionPayload(list.name, sub.name, "unclaim"));
						}
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (draggingScrollbar && button == 0 && activeTab != MainTab.SYNC) {
			int lTop = activeTab == MainTab.MATERIALS ? matListTop() : subListTop();
			int stride = activeTab == MainTab.MATERIALS ? MAT_STRIDE : SUB_STRIDE;
			int gap = activeTab == MainTab.MATERIALS ? MAT_GAP : SUB_GAP;
			String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";
			int count = activeTab == MainTab.MATERIALS ? filteredMaterials(player).size() : filteredSubregions().size();
			updateScrollFromMouse(my, lTop, listBottom() - lTop, maxScrollPx(count, stride, gap, lTop), count, stride, gap);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int b) {
		if (b == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
		return super.mouseReleased(mx, my, b);
	}

	private void updateScrollFromMouse(double my, int top, int viewH, int maxScroll, int count, int stride, int gap) {
		if (maxScroll == 0) return;
		int totalH = count * stride - gap;
		int barH = Math.max(22, viewH * viewH / Math.max(1, totalH));
		double f = (my - top - barH / 2.0) / Math.max(1.0, viewH - barH);
		targetScroll = Math.max(0, Math.min(1, f)) * maxScroll;
		scroll = targetScroll;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (v != 0 && activeTab != MainTab.SYNC) {
			int lTop = activeTab == MainTab.MATERIALS ? matListTop() : subListTop();
			int stride = activeTab == MainTab.MATERIALS ? MAT_STRIDE : SUB_STRIDE;
			int gap = activeTab == MainTab.MATERIALS ? MAT_GAP : SUB_GAP;
			String player = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";
			int count = activeTab == MainTab.MATERIALS ? filteredMaterials(player).size() : filteredSubregions().size();
			int maxScroll = maxScrollPx(count, stride, gap, lTop);
			targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - v * 28));
			return true;
		}
		return super.mouseScrolled(mx, my, h, v);
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

	private static String formatStack(int n) {
		if (n >= 93312) return String.format(java.util.Locale.ROOT, "SDC %.2f", n / 93312.0);
		if (n >= 1728) return String.format(java.util.Locale.ROOT, "SB %.2f", n / 1728.0);
		int stacks = n / 64, singles = n % 64;
		if (stacks > 0) return singles == 0 ? "64 x " + stacks : "64 x " + stacks + " + " + singles;
		return String.valueOf(n);
	}
}
