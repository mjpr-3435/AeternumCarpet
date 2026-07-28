package smp.aeternum.carpet.materials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.finder.FinderItemNames;
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

	private MaterialsStore() {}

	public static void load(MinecraftServer server) {
		dataDir = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_materials");
		csvDir = dataDir;
		claimsFile = dataDir.resolve("claims.json");
		regionsFile = dataDir.resolve("regions.json");
		lists.clear();
		loadCsvs();
		loadClaims();
		loadRegions();
	}

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
		String resolved = FinderItemNames.resolve(name);
		if (resolved != null) return resolved;
		String key = name.toLowerCase().replace(' ', '_').replaceAll("[^a-z0-9/._-]", "");
		return "minecraft:" + key;
	}

	public static void reload() {
		lists.clear();
		loadCsvs();
		loadClaims();
		mergeClaims();
	}

	public static MaterialList get(String name) { return lists.get(name); }
	public static Collection<MaterialList> all() { mergeClaims(); return lists.values(); }

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
		for (int x = minX; x <= maxX; x++)
			for (int y = minY; y <= maxY; y++)
				for (int z = minZ; z <= maxZ; z++) {
					if (!(world.getBlockEntity(new BlockPos(x, y, z)) instanceof net.minecraft.inventory.Inventory inv)) continue;
					for (int s = 0; s < inv.size(); s++) {
						net.minecraft.item.ItemStack stack = inv.getStack(s);
						if (stack.isEmpty()) continue;
						String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
						counts.merge(id, stack.getCount(), Integer::sum);
					}
				}
		for (MaterialEntry entry : list.entries) entry.available = counts.getOrDefault(entry.itemId, 0);
		list.scannedAvailable = counts.values().stream().mapToInt(Integer::intValue).sum();
	}

	public static void addStorageRegion(String listName, String dimension, BlockPos pos1, BlockPos pos2) {
		regions.computeIfAbsent(listName, k -> new ArrayList<>()).add(new StorageRegion(dimension, pos1, pos2));
		saveRegions();
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
		public BlockPos pos1, pos2;
		public StorageRegion() {}
		public StorageRegion(String dimension, BlockPos pos1, BlockPos pos2) { this.dimension = dimension; this.pos1 = pos1; this.pos2 = pos2; }
	}
}
