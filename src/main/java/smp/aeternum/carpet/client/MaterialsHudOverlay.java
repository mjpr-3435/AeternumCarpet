package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
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

	private MaterialsHudOverlay() {}

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

	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || claimedEntries.isEmpty()) return;

		int x = client.getWindow().getScaledWidth() - 100, y = 8;
		int shown = Math.min(claimedEntries.size(), 7);

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
