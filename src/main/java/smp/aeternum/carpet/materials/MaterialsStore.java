package smp.aeternum.carpet.materials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.network.MaterialsNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.nio.file.*;
import java.util.*;

public final class MaterialsStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type CLAIMS_TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
	private static final Type REGIONS_TYPE = new TypeToken<Map<String, List<StorageRegion>>>() {}.getType();
	private static Path csvDir, dataDir;
	private static Path claimsFile, regionsFile;
	private static final Map<String, MaterialList> lists = new LinkedHashMap<>();
	private static Map<String, Map<String, String>> claims = new LinkedHashMap<>();
	private static Map<String, List<StorageRegion>> regions = new LinkedHashMap<>();
	private static long lastTickTime;
	private static final long SCAN_INTERVAL_MS = 5_000;
	private static MinecraftServer server;

	private MaterialsStore() {}

	public static void load(MinecraftServer srv) {
		server = srv;
		dataDir = srv.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_materials");
		csvDir = dataDir;
		claimsFile = dataDir.resolve("claims.json");
		regionsFile = dataDir.resolve("regions.json");
		lists.clear();
		loadCsvs();
		loadClaims();
		loadRegions();
		mergeClaims();
		lastTickTime = System.currentTimeMillis();
	}

	public static void tick() {
		if (server == null || System.currentTimeMillis() - lastTickTime < SCAN_INTERVAL_MS) return;
		lastTickTime = System.currentTimeMillis();
		boolean scanned = false;
		for (var entry : regions.entrySet()) {
			String listName = entry.getKey();
			MaterialList list = lists.get(listName);
			if (list == null) continue;
			for (StorageRegion region : entry.getValue()) {
				ServerWorld world = null;
				for (ServerWorld w : server.getWorlds()) {
					if (w.getRegistryKey().getValue().toString().equals(region.dimension)) { world = w; break; }
				}
				if (world == null) continue;
				scan(world, listName, region.pos1(), region.pos2());
				scanned = true;
			}
		}
		if (scanned) MaterialsNetworking.broadcast(server);
	}

	public static long millisUntilNextScan() { return Math.max(0, SCAN_INTERVAL_MS - (System.currentTimeMillis() - lastTickTime)); }

	public static void forceScan() { lastTickTime = 0; tick(); }

	private static void loadCsvs() {
		try { Files.createDirectories(csvDir); } catch (IOException e) { return; }
		try (var files = Files.list(csvDir)) {
			files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv")).forEach(p -> {
				String name = p.getFileName().toString().replace(".csv", "");
				List<MaterialEntry> entries = parseCsv(p);
				if (!entries.isEmpty()) lists.put(name, new MaterialList(name, entries));
			});
		} catch (IOException e) { AeternumCarpet.LOGGER.error("Could not load material CSVs", e); }
	}

	private static List<MaterialEntry> parseCsv(Path file) {
		List<MaterialEntry> entries = new ArrayList<>();
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String line = reader.readLine(); if (line == null) return entries;
			while ((line = reader.readLine()) != null) {
				line = line.trim(); if (line.isEmpty()) continue;
				String[] parts = parseCsvLine(line);
				if (parts.length < 4) continue;
				String itemName = unquote(parts[0]);
				int total = parseInt(parts[1]);
				String itemId = resolveItemId(itemName);
				entries.add(new MaterialEntry(itemName, itemId, total, 0));
			}
		} catch (IOException e) { AeternumCarpet.LOGGER.error("Could not parse CSV {}", file, e); }
		return entries;
	}

	private static String[] parseCsvLine(String line) {
		List<String> parts = new ArrayList<>();
		boolean inQuotes = false; StringBuilder current = new StringBuilder();
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') { inQuotes = !inQuotes; }
			else if (c == ',' && !inQuotes) { parts.add(current.toString().trim()); current.setLength(0); }
			else current.append(c);
		}
		parts.add(current.toString().trim());
		return parts.toArray(new String[0]);
	}

	private static String unquote(String s) { return s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s; }
	private static int parseInt(String s) { try { return Integer.parseInt(unquote(s).trim()); } catch (NumberFormatException e) { return 0; } }

	private static String resolveItemId(String name) {
		String resolved = MaterialItemResolver.resolve(name);
		if (resolved != null) return resolved;
		String key = name.toLowerCase().replace(' ', '_').replaceAll("[^a-z0-9/._-]", "");
		return "minecraft:" + key;
	}

	public static void reload() {
		lists.clear();
		loadCsvs();
		loadClaims();
		loadRegions();
		mergeClaims();
	}

	public static MaterialList get(String name) { return lists.get(name); }
	public static Collection<MaterialList> all() { mergeClaims(); long nextAt = System.currentTimeMillis() + millisUntilNextScan(); for (var l : lists.values()) l.nextScanAt = nextAt; return lists.values(); }

	private static void mergeClaims() {
		for (var list : lists.values()) {
			Map<String, String> listClaims = claims.get(list.name);
			if (listClaims == null) continue;
			for (MaterialEntry entry : list.entries) entry.claimedBy = listClaims.get(entry.itemId);
		}
	}

	public static void scan(ServerWorld world, String listName, BlockPos pos1, BlockPos pos2) {
		MaterialList list = lists.get(listName);
		if (list == null) return;
		Map<String, Integer> counts = new HashMap<>();
		int minX = Math.min(pos1.getX(), pos2.getX()), maxX = Math.max(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY()), maxY = Math.max(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ()), maxZ = Math.max(pos1.getZ(), pos2.getZ());
		int minCX = minX >> 4, maxCX = maxX >> 4, minCZ = minZ >> 4, maxCZ = maxZ >> 4;
		for (int cx = minCX; cx <= maxCX; cx++)
			for (int cz = minCZ; cz <= maxCZ; cz++) {
				if (!world.isChunkLoaded(cx, cz)) continue;
				int bx0 = Math.max(minX, cx << 4), bx1 = Math.min(maxX, (cx << 4) + 15);
				int bz0 = Math.max(minZ, cz << 4), bz1 = Math.min(maxZ, (cz << 4) + 15);
				for (int x = bx0; x <= bx1; x++)
					for (int y = minY; y <= maxY; y++)
						for (int z = bz0; z <= bz1; z++) {
							if (!(world.getBlockEntity(new BlockPos(x, y, z)) instanceof net.minecraft.inventory.Inventory inv)) continue;
							for (int s = 0; s < inv.size(); s++) countStack(inv.getStack(s), counts);
						}
			}
		for (MaterialEntry entry : list.entries) entry.available = counts.getOrDefault(entry.itemId, 0);
		list.scannedAvailable = counts.values().stream().mapToInt(Integer::intValue).sum();
	}

	private static void countStack(net.minecraft.item.ItemStack stack, Map<String, Integer> counts) {
		if (stack.isEmpty()) return;
		String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
		counts.merge(id, stack.getCount(), Integer::sum);
		ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
		if (container != null) for (net.minecraft.item.ItemStack nested : container.iterateNonEmpty()) countStack(nested, counts);
	}

	public static void addStorageRegion(String listName, String dimension, BlockPos pos1, BlockPos pos2) {
		regions.computeIfAbsent(listName, k -> new ArrayList<>()).add(new StorageRegion(dimension, pos1, pos2));
		saveRegions();
	}

	public static void removeStorageRegion(String listName, int index) {
		List<StorageRegion> listRegions = regions.get(listName);
		if (listRegions != null && index >= 0 && index < listRegions.size()) {
			listRegions.remove(index);
			if (listRegions.isEmpty()) regions.remove(listName);
			saveRegions();
		}
	}

	public static List<StorageRegion> getRegions(String listName) { return regions.getOrDefault(listName, List.of()); }

	public static String getClaim(String listName, String itemId) {
		Map<String, String> listClaims = claims.get(listName);
		return listClaims == null ? null : listClaims.get(itemId);
	}

	public static void claim(String listName, String itemId, String player) {
		claims.computeIfAbsent(listName, k -> new LinkedHashMap<>()).put(itemId, player);
		MaterialList list = lists.get(listName);
		if (list != null) for (MaterialEntry e : list.entries) if (e.itemId.equals(itemId)) { e.claimedBy = player; break; }
		saveClaims();
	}

	public static void unclaim(String listName, String itemId) {
		Map<String, String> listClaims = claims.get(listName);
		if (listClaims != null) { listClaims.remove(itemId); saveClaims(); }
		MaterialList list = lists.get(listName);
		if (list != null) for (MaterialEntry e : list.entries) if (e.itemId.equals(itemId)) { e.claimedBy = null; break; }
	}

	public static void delete(String listName) {
		lists.remove(listName);
		claims.remove(listName);
		regions.remove(listName);
		try { Files.deleteIfExists(dataDir.resolve(listName + ".csv")); } catch (IOException ignored) {}
		saveClaims();
		saveRegions();
	}

	@SuppressWarnings("unchecked")
	private static void loadClaims() {
		if (!Files.isRegularFile(claimsFile)) { claims = new LinkedHashMap<>(); return; }
		try { claims = new LinkedHashMap<>(GSON.fromJson(Files.readString(claimsFile), CLAIMS_TYPE)); }
		catch (Exception e) { claims = new LinkedHashMap<>(); }
	}

	private static void saveClaims() {
		try { Files.createDirectories(dataDir); Files.writeString(claimsFile, GSON.toJson(claims)); }
		catch (IOException e) { AeternumCarpet.LOGGER.error("Could not save material claims", e); }
	}

	@SuppressWarnings("unchecked")
	private static void loadRegions() {
		if (!Files.isRegularFile(regionsFile)) { regions = new LinkedHashMap<>(); return; }
		try { regions = new LinkedHashMap<>(GSON.fromJson(Files.readString(regionsFile), REGIONS_TYPE)); }
		catch (Exception e) { regions = new LinkedHashMap<>(); }
	}

	private static void saveRegions() {
		try { Files.createDirectories(dataDir); Files.writeString(regionsFile, GSON.toJson(regions)); }
		catch (IOException e) { AeternumCarpet.LOGGER.error("Could not save material regions", e); }
	}

	public static class StorageRegion {
		public String dimension;
		public int x1, y1, z1, x2, y2, z2;
		public StorageRegion() {}
		public StorageRegion(String dimension, BlockPos pos1, BlockPos pos2) {
			this.dimension = dimension; this.x1 = pos1.getX(); this.y1 = pos1.getY(); this.z1 = pos1.getZ();
			this.x2 = pos2.getX(); this.y2 = pos2.getY(); this.z2 = pos2.getZ();
		}
		BlockPos pos1() { return new BlockPos(x1, y1, z1); }
		BlockPos pos2() { return new BlockPos(x2, y2, z2); }
	}
}
