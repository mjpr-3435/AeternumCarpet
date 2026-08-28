package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.materials.MaterialEntry;
import smp.aeternum.carpet.materials.MaterialList;

public final class MaterialsHudOverlay {
	private static final Gson GSON = new Gson();
	private static final List<MaterialEntry> claimedEntries = new ArrayList<>();
	private static String currentPlayer;

	public static boolean hudEnabled = true;
	public static HudPos hudPosition = HudPos.NE;

	private MaterialsHudOverlay() {}

	public enum HudPos {
		NW, N, NE, E, SE, S, SW, W;
		public HudPos next() { return values()[(ordinal() + 1) % values().length]; }
		public int[] box(int sw, int sh, int w, int h, int m) {
			return switch (this) {
				case NW -> new int[]{m, m};
				case N  -> new int[]{(sw - w) / 2, m};
				case NE -> new int[]{sw - w - m, m};
				case E  -> new int[]{sw - w - m, (sh - h) / 2};
				case SE -> new int[]{sw - w - m, sh - h - m};
				case S  -> new int[]{(sw - w) / 2, sh - h - m};
				case SW -> new int[]{m, sh - h - m};
				case W  -> new int[]{m, (sh - h) / 2};
			};
		}
	}

	public static void clear() {
		claimedEntries.clear();
		currentPlayer = null;
	}

	public static void update(String player, String json) {
		currentPlayer = player;
		claimedEntries.clear();
		MaterialList[] lists = GSON.fromJson(json, MaterialList[].class);
		if (lists == null) return;
		for (MaterialList list : lists)
			for (MaterialEntry entry : list.entries)
				if (player.equals(entry.claimedBy) && entry.available < entry.total)
					claimedEntries.add(entry);
		claimedEntries.sort((a, b) -> Integer.compare(b.total, a.total));
	}

	public static void toggle() { hudEnabled = !hudEnabled; save(); }
	public static void cyclePosition() { hudPosition = hudPosition.next(); save(); }

	private static final Path CFG = FabricLoader.getInstance().getConfigDir().resolve("aeternum-carpet/hud.json");

	public static void load() {
		try {
			if (Files.exists(CFG)) {
				JsonObject j = GSON.fromJson(Files.readString(CFG), JsonObject.class);
				if (j != null) {
					if (j.has("enabled")) hudEnabled = j.get("enabled").getAsBoolean();
					if (j.has("position")) hudPosition = HudPos.valueOf(j.get("position").getAsString());
				}
			}
		} catch (Exception ignored) { }
	}

	public static void save() {
		try {
			Files.createDirectories(CFG.getParent());
			JsonObject j = new JsonObject();
			j.addProperty("enabled", hudEnabled);
			j.addProperty("position", hudPosition.name());
			Files.writeString(CFG, GSON.toJson(j));
		} catch (Exception ignored) { }
	}

	public static void render(DrawContext context) {
		if (!hudEnabled) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || claimedEntries.isEmpty()) return;

		int shown = Math.min(claimedEntries.size(), 7);
		int w = 103, h = 16 + shown * 18;
		int[] o = hudPosition.box(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), w, h, 4);
		int x = o[0] + 4, y = o[1] + 4;

		context.fill(x - 4, y - 4, x + 99, y + 12 + shown * 18, 0x80000000);
		context.drawText(client.textRenderer, "Item", x, y + 2, 0xFFFFAA00, false);
		context.drawText(client.textRenderer, "Falta", x + 30, y + 2, 0xFFFFAA00, false);

		y += 14;
		for (int i = 0; i < shown; i++) {
			MaterialEntry entry = claimedEntries.get(i);
			int rowY = y + i * 18;

			ItemStack icon = ItemStack.EMPTY;
			if (entry.itemId != null && !entry.itemId.isEmpty()) {
				var item = Registries.ITEM.get(Identifier.of(entry.itemId));
				if (item != Items.AIR) icon = new ItemStack(item);
			}
			if (!icon.isEmpty()) context.drawItem(icon, x, rowY);

			int missing = Math.max(0, entry.total - entry.available);
			context.drawText(client.textRenderer, formatStack(missing), x + 30, rowY + 5, 0xFFAAAAAA, false);
		}
	}

	private static String formatStack(int n) {
		if (n >= 93312) return String.format(java.util.Locale.ROOT, "SDC %.2f", n / 93312.0);
		if (n >= 1728) return String.format(java.util.Locale.ROOT, "SB %.2f", n / 1728.0);
		int stacks = n / 64, singles = n % 64;
		if (stacks > 0) return singles == 0 ? "64 x " + stacks : "64 x " + stacks + " + " + singles;
		return String.valueOf(n);
	}
}
