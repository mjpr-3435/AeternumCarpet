package smp.aeternum.carpet.materials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.network.MaterialsNetworking;

public final class MaterialsStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type CLAIMS_TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
	private static final Type REGIONS_TYPE = new TypeToken<Map<String, List<StorageRegion>>>() {}.getType();
	private static final long SCAN_INTERVAL_MS = 1_000;
	private static final int WORLD_BLOCKS_PER_TICK = 2_048;

	private static Path csvDir, dataDir, projectsDir;
	private static Path claimsFile, regionsFile;
	private static final Map<String, MaterialList> lists = new LinkedHashMap<>();
	private static final Map<String, LitematicParser.ParsedSchematic> schematics = new LinkedHashMap<>();
	private static Map<String, Map<String, String>> claims = new LinkedHashMap<>();
	private static Map<String, List<StorageRegion>> regions = new LinkedHashMap<>();
	private static long lastTickTime;
	private static MinecraftServer server;

	private MaterialsStore() {}

	public static void load(MinecraftServer srv) {
		server = srv;
		dataDir = srv.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_materials");
		projectsDir = srv.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_projects");
		csvDir = dataDir;
		claimsFile = dataDir.resolve("claims.json");
		regionsFile = dataDir.resolve("regions.json");
		reload();
	}

	public static void reload() {
		lists.clear();
		schematics.clear();
		loadCsvs();
		loadProjects();
		loadClaims();
		loadRegions();
		mergeClaims();
		lastTickTime = System.currentTimeMillis();
	}

	public static void tick() {
		if (server == null) return;
		boolean worldDirty = stepWorldVerification();
		if (System.currentTimeMillis() - lastTickTime < SCAN_INTERVAL_MS) return;
		lastTickTime = System.currentTimeMillis();

		boolean scanned = worldDirty;
		for (var entry : regions.entrySet()) {
			String listName = entry.getKey();
			MaterialList list = lists.get(listName);
			if (list == null) continue;
			for (StorageRegion region : entry.getValue()) {
				ServerWorld world = findWorld(region.dimension);
				if (world == null) continue;
				scan(world, listName, region.pos1(), region.pos2());
				scanned = true;
			}
		}
		if (scanned) MaterialsNetworking.broadcast(server);
	}

	private static boolean stepWorldVerification() {
		boolean updatedAny = false;
		int budget = WORLD_BLOCKS_PER_TICK;
		for (var entry : lists.entrySet()) {
			if (budget <= 0) break;
			MaterialList list = entry.getValue();
			if (!list.hasSchematic || list.placement == null || !list.placement.configured) continue;
			LitematicParser.ParsedSchematic parsed = schematics.get(list.name);
			if (parsed == null) continue;
			ServerWorld world = findWorld(list.placement.dimension);
			if (world == null) continue;

			boolean projectChanged = false;
			for (LitematicParser.ParsedSubregion sub : parsed.subregions()) {
				if (budget <= 0) break;
				int size = sub.templateBlocks.size();
				if (size == 0) continue;
				int checks = Math.min(budget, size);
				for (int i = 0; i < checks; i++) {
					int idx = sub.cursor;
					sub.cursor = (idx + 1) % size;
					LitematicParser.TemplateBlock tb = sub.templateBlocks.get(idx);
					BlockPos worldPos = LitematicParser.transform(tb.relX(), tb.relY(), tb.relZ(), list.placement);
					if (!world.isChunkLoaded(worldPos.getX() >> 4, worldPos.getZ() >> 4)) continue;
					boolean matches = world.getBlockState(worldPos).isOf(tb.expectedBlock());
					if (sub.matchedBits.get(idx) != matches) {
						sub.matchedBits.set(idx, matches);
						projectChanged = true;
					}
				}
				budget -= checks;
			}
			if (projectChanged) {
				recomputePlacedCounts(list, parsed);
				updatedAny = true;
			}
		}
		return updatedAny;
	}

	private static void recomputePlacedCounts(MaterialList list, LitematicParser.ParsedSchematic parsed) {
		int totalPlaced = 0;
		Map<String, Integer> globalPlacedByItem = new HashMap<>();

		for (int s = 0; s < parsed.subregions().size() && s < list.subregions.size(); s++) {
			LitematicParser.ParsedSubregion pSub = parsed.subregions().get(s);
			MaterialList.SubregionInfo info = list.subregions.get(s);
			Map<String, Integer> subPlacedByItem = new HashMap<>();
			int subPlaced = pSub.matchedBits.cardinality();

			for (int idx = pSub.matchedBits.nextSetBit(0); idx >= 0; idx = pSub.matchedBits.nextSetBit(idx + 1)) {
				String itemId = pSub.templateBlocks.get(idx).itemId();
				if (!itemId.isEmpty()) {
					subPlacedByItem.merge(itemId, 1, Integer::sum);
					globalPlacedByItem.merge(itemId, 1, Integer::sum);
				}
			}

			info.placedBlocks = subPlaced;
			totalPlaced += subPlaced;
			for (MaterialEntry e : info.entries) {
				e.placed = subPlacedByItem.getOrDefault(e.itemId, 0);
			}
		}

		list.placedBlocks = totalPlaced;
		for (MaterialEntry e : list.entries) {
			e.placed = globalPlacedByItem.getOrDefault(e.itemId, 0);
		}
	}

	private static ServerWorld findWorld(String dimension) {
		if (server == null || dimension == null) return null;
		for (ServerWorld w : server.getWorlds()) {
			if (w.getRegistryKey().getValue().toString().equals(dimension)) return w;
		}
		return null;
	}

	public static long millisUntilNextScan() {
		return Math.max(0, SCAN_INTERVAL_MS - (System.currentTimeMillis() - lastTickTime));
	}

	public static void forceScan() {
		lastTickTime = 0;
		tick();
	}

	public static String uploadProject(String rawName, byte[] bytes, String uploadedBy, MaterialList.PlacementInfo placement) throws IOException {
		String name = sanitizeName(rawName);
		if (name.isEmpty()) throw new IOException("Nombre de proyecto inválido.");
		LitematicParser.ParsedSchematic parsed = LitematicParser.parse(bytes);

		Files.createDirectories(projectsDir);
		Files.write(projectsDir.resolve(name + ".litematic"), bytes);

		ProjectMeta meta = loadMeta(projectsDir.resolve(name + ".meta.json"));
		meta.uploadedBy = uploadedBy;
		if (placement != null && placement.configured) {
			meta.placement = placement;
		}
		saveMeta(projectsDir.resolve(name + ".meta.json"), meta);

		MaterialList list = buildProjectList(name, parsed, meta);
		lists.put(name, list);
		schematics.put(name, parsed);
		mergeClaims();
		return name;
	}

	public static boolean updatePlacement(String projectName, MaterialList.PlacementInfo placement) {
		MaterialList list = lists.get(projectName);
		if (list == null || !list.hasSchematic) return false;
		list.placement = placement;
		list.placedBlocks = 0;
		for (MaterialEntry e : list.entries) e.placed = 0;
		for (var sub : list.subregions) {
			sub.placedBlocks = 0;
			for (MaterialEntry e : sub.entries) e.placed = 0;
		}
		LitematicParser.ParsedSchematic parsed = schematics.get(projectName);
		if (parsed != null) {
			for (var sub : parsed.subregions()) sub.resetMatchState();
		}
		ProjectMeta meta = loadMeta(projectsDir.resolve(projectName + ".meta.json"));
		meta.placement = placement;
		saveMeta(projectsDir.resolve(projectName + ".meta.json"), meta);
		return true;
	}

	public static boolean subregionAction(String projectName, String subregionName, String action, String player) {
		MaterialList list = lists.get(projectName);
		if (list == null || !list.hasSchematic) return false;
		MaterialList.SubregionInfo target = null;
		for (var sub : list.subregions) {
			if (sub.name.equals(subregionName)) {
				target = sub;
				break;
			}
		}
		if (target == null) return false;

		switch (action) {
			case "claim" -> {
				target.claimedBy = player;
				if ("NONE".equals(target.status)) target.status = "CLAIMED";
			}
			case "done" -> {
				target.status = "DONE";
				if (target.claimedBy == null || target.claimedBy.isEmpty()) target.claimedBy = player;
			}
			case "reopen" -> {
				target.status = (target.claimedBy != null && !target.claimedBy.isEmpty()) ? "CLAIMED" : "NONE";
			}
			case "unclaim" -> {
				target.claimedBy = null;
				if ("CLAIMED".equals(target.status)) target.status = "NONE";
			}
			default -> {
				return false;
			}
		}

		ProjectMeta meta = loadMeta(projectsDir.resolve(projectName + ".meta.json"));
		SubregionMeta sm = new SubregionMeta();
		sm.status = target.status;
		sm.claimedBy = target.claimedBy;
		meta.subregions.put(subregionName, sm);
		saveMeta(projectsDir.resolve(projectName + ".meta.json"), meta);
		return true;
	}

	public static byte[] readSchematicBytes(String projectName) {
		if (projectsDir == null) return null;
		Path p = projectsDir.resolve(projectName + ".litematic");
		if (!Files.isRegularFile(p)) return null;
		try {
			return Files.readAllBytes(p);
		} catch (IOException e) {
			AeternumCarpet.LOGGER.error("Failed to read schematic {}", p, e);
			return null;
		}
	}

	private static void loadProjects() {
		try { Files.createDirectories(projectsDir); } catch (IOException e) { return; }
		try (var files = Files.list(projectsDir)) {
			files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic")).forEach(p -> {
				String fileName = p.getFileName().toString();
				String name = fileName.substring(0, fileName.length() - ".litematic".length());
				try {
					LitematicParser.ParsedSchematic parsed = LitematicParser.parse(p);
					ProjectMeta meta = loadMeta(projectsDir.resolve(name + ".meta.json"));
					lists.put(name, buildProjectList(name, parsed, meta));
					schematics.put(name, parsed);
				} catch (Exception e) {
					AeternumCarpet.LOGGER.error("Could not load project schematic {}", p, e);
				}
			});
		} catch (IOException e) {
			AeternumCarpet.LOGGER.error("Could not list project schematics", e);
		}
	}

	private static MaterialList buildProjectList(String name, LitematicParser.ParsedSchematic parsed, ProjectMeta meta) {
		MaterialList list = new MaterialList(name, copyEntries(parsed.entries()));
		list.hasSchematic = true;
		list.uploadedBy = meta.uploadedBy;
		list.totalBlocks = parsed.totalBlocks();
		list.placement = meta.placement != null ? meta.placement : new MaterialList.PlacementInfo();
		list.subregions = new ArrayList<>();
		for (LitematicParser.ParsedSubregion pSub : parsed.subregions()) {
			MaterialList.SubregionInfo info = new MaterialList.SubregionInfo(pSub.name, pSub.totalBlocks, List.of());
			SubregionMeta sm = meta.subregions.get(pSub.name);
			if (sm != null) {
				info.status = sm.status != null ? sm.status : "NONE";
				info.claimedBy = sm.claimedBy;
			}
			list.subregions.add(info);
		}
		return list;
	}

	private static List<MaterialEntry> copyEntries(List<MaterialEntry> src) {
		List<MaterialEntry> out = new ArrayList<>(src.size());
		for (MaterialEntry e : src) {
			MaterialEntry copy = new MaterialEntry(e.itemName, e.itemId, e.total, e.available);
			copy.placed = e.placed;
			copy.claimedBy = e.claimedBy;
			out.add(copy);
		}
		return out;
	}

	private static String sanitizeName(String raw) {
		if (raw == null) return "";
		String s = raw.trim().replaceAll("(?i)\\.litematic$", "");
		return s.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("^_+|_+$", "");
	}

	private static void loadCsvs() {
		try { Files.createDirectories(csvDir); } catch (IOException e) { return; }
		try (var files = Files.list(csvDir)) {
			files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv")).forEach(p -> {
				String name = p.getFileName().toString().replace(".csv", "");
				if (lists.containsKey(name)) return;
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

	public static MaterialList get(String name) { return lists.get(name); }
	public static Collection<MaterialList> all() {
		mergeClaims();
		long nextAt = System.currentTimeMillis() + millisUntilNextScan();
		for (var l : lists.values()) l.nextScanAt = nextAt;
		return lists.values();
	}

	public static String overlayJsonFor(ServerPlayerEntity player) {
		Map<String, Integer> carried = new HashMap<>();
		Inventory inv = player.getInventory();
		for (int s = 0; s < inv.size(); s++) countStack(inv.getStack(s), carried);
		List<MaterialList> result = new ArrayList<>();
		for (MaterialList list : all()) {
			MaterialList copy = new MaterialList(list.name, new ArrayList<>());
			copy.nextScanAt = list.nextScanAt;
			for (MaterialEntry e : list.entries) {
				if (e.claimedBy == null || !e.claimedBy.equals(player.getName().getString())) continue;
				MaterialEntry ce = new MaterialEntry(e.itemName, e.itemId, e.total, e.available + carried.getOrDefault(e.itemId, 0));
				ce.placed = e.placed;
				ce.claimedBy = e.claimedBy;
				copy.entries.add(ce);
			}
			if (!copy.entries.isEmpty()) result.add(copy);
		}
		return GSON.toJson(result);
	}

	private static void mergeClaims() {
		for (var list : lists.values()) {
			Map<String, String> listClaims = claims.get(list.name);
			if (listClaims == null) continue;
			for (MaterialEntry entry : list.entries) entry.claimedBy = listClaims.get(entry.itemId);
			if (list.subregions != null) {
				for (var sub : list.subregions) {
					for (MaterialEntry entry : sub.entries) entry.claimedBy = listClaims.get(entry.itemId);
				}
			}
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
							if (!(world.getBlockEntity(new BlockPos(x, y, z)) instanceof Inventory inv)) continue;
							for (int s = 0; s < inv.size(); s++) countStack(inv.getStack(s), counts);
						}
			}
		for (MaterialEntry entry : list.entries) entry.available = counts.getOrDefault(entry.itemId, 0);
		if (list.subregions != null) {
			for (var sub : list.subregions) {
				for (MaterialEntry entry : sub.entries) entry.available = counts.getOrDefault(entry.itemId, 0);
			}
		}
		list.scannedAvailable = counts.values().stream().mapToInt(Integer::intValue).sum();
	}

	private static void countStack(ItemStack stack, Map<String, Integer> counts) {
		if (stack.isEmpty()) return;
		String id = Registries.ITEM.getId(stack.getItem()).toString();
		counts.merge(id, stack.getCount(), Integer::sum);
		ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
		if (container != null) for (ItemStack nested : container.iterateNonEmpty()) countStack(nested, counts);
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
		updateEntryClaim(lists.get(listName), itemId, player);
		saveClaims();
	}

	public static void unclaim(String listName, String itemId) {
		Map<String, String> listClaims = claims.get(listName);
		if (listClaims != null) { listClaims.remove(itemId); saveClaims(); }
		updateEntryClaim(lists.get(listName), itemId, null);
	}

	private static void updateEntryClaim(MaterialList list, String itemId, String player) {
		if (list == null) return;
		for (MaterialEntry e : list.entries) if (e.itemId.equals(itemId)) e.claimedBy = player;
		if (list.subregions != null) {
			for (var sub : list.subregions) {
				for (MaterialEntry e : sub.entries) if (e.itemId.equals(itemId)) e.claimedBy = player;
			}
		}
	}

	public static void delete(String listName) {
		lists.remove(listName);
		schematics.remove(listName);
		claims.remove(listName);
		regions.remove(listName);
		try { Files.deleteIfExists(dataDir.resolve(listName + ".csv")); } catch (IOException ignored) {}
		if (projectsDir != null) {
			try { Files.deleteIfExists(projectsDir.resolve(listName + ".litematic")); } catch (IOException ignored) {}
			try { Files.deleteIfExists(projectsDir.resolve(listName + ".meta.json")); } catch (IOException ignored) {}
		}
		saveClaims();
		saveRegions();
	}

	private static ProjectMeta loadMeta(Path metaPath) {
		if (!Files.isRegularFile(metaPath)) return new ProjectMeta();
		try {
			ProjectMeta meta = GSON.fromJson(Files.readString(metaPath), ProjectMeta.class);
			return meta != null ? meta : new ProjectMeta();
		} catch (Exception e) {
			return new ProjectMeta();
		}
	}

	private static void saveMeta(Path metaPath, ProjectMeta meta) {
		try {
			Files.createDirectories(metaPath.getParent());
			Files.writeString(metaPath, GSON.toJson(meta));
		} catch (IOException e) {
			AeternumCarpet.LOGGER.error("Could not save project meta {}", metaPath, e);
		}
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

	public static class ProjectMeta {
		public String uploadedBy;
		public MaterialList.PlacementInfo placement = new MaterialList.PlacementInfo();
		public Map<String, SubregionMeta> subregions = new LinkedHashMap<>();
	}

	public static class SubregionMeta {
		public String status = "NONE";
		public String claimedBy;
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
