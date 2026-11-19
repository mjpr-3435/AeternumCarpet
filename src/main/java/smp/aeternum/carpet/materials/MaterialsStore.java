package smp.aeternum.carpet.materials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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
import net.minecraft.block.entity.BlockEntity;
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
import net.minecraft.world.chunk.WorldChunk;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.network.MaterialsNetworking;

public final class MaterialsStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type CLAIMS_TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
	private static final Type REGIONS_TYPE = new TypeToken<Map<String, List<StorageRegion>>>() {}.getType();
	private static final Type STORAGE_CACHE_TYPE = new TypeToken<Map<String, Map<String, Map<String, Integer>>>>() {}.getType();
	private static final long SCAN_INTERVAL_MS = 1_000;
	private static final int WORLD_BLOCKS_PER_TICK = 2_048;

	private static Path dataDir, projectsDir;
	private static Path claimsFile, regionsFile, storageCacheFile;
	private static final Map<String, MaterialList> lists = new LinkedHashMap<>();
	private static final Map<String, LitematicParser.ParsedSchematic> schematics = new LinkedHashMap<>();
	private static Map<String, Map<String, String>> claims = new LinkedHashMap<>();
	private static Map<String, List<StorageRegion>> regions = new LinkedHashMap<>();
	private static Map<String, Map<String, Map<String, Integer>>> storageCache = new LinkedHashMap<>();
	private static long lastTickTime;
	private static boolean pendingWorldBroadcast;
	private static MinecraftServer server;

	private MaterialsStore() {}

	public static void load(MinecraftServer srv) {
		server = srv;
		dataDir = srv.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_materials");
		projectsDir = srv.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_projects");
		claimsFile = dataDir.resolve("claims.json");
		regionsFile = dataDir.resolve("regions.json");
		storageCacheFile = dataDir.resolve("storage-cache.json");
		reload();
	}

	public static void reload() {
		lists.clear();
		schematics.clear();
		loadProjects();
		loadClaims();
		loadRegions();
		loadStorageCache();
		mergeClaims();
		recomputeAllAvailable();
		lastTickTime = System.currentTimeMillis();
	}

	public static void tick() {
		if (server == null) return;
		if (stepWorldVerification()) pendingWorldBroadcast = true;
		if (System.currentTimeMillis() - lastTickTime < SCAN_INTERVAL_MS) return;
		lastTickTime = System.currentTimeMillis();

		boolean shouldBroadcast = pendingWorldBroadcast;
		pendingWorldBroadcast = false;
		boolean cacheDirty = false;
		for (var entry : regions.entrySet()) {
			String listName = entry.getKey();
			MaterialList list = lists.get(listName);
			if (list == null) continue;
			if (scanListStorage(listName, entry.getValue())) {
				cacheDirty = true;
				shouldBroadcast = true;
			}
		}
		if (cacheDirty) saveStorageCache();
		if (shouldBroadcast) MaterialsNetworking.broadcast(server);
	}

	private static boolean stepWorldVerification() {
		boolean updatedAny = false;
		int budget = WORLD_BLOCKS_PER_TICK;
		for (var entry : lists.entrySet()) {
			if (budget <= 0) break;
			MaterialList list = entry.getValue();
			if (!list.hasSchematic || list.placement == null || !list.placement.configured) continue;
			LitematicParser.ParsedSchematic parsed = schematics.get(list.name);
			if (parsed == null || parsed.subregions().isEmpty()) continue;
			ServerWorld world = findWorld(list.placement.dimension);
			if (world == null) continue;

			boolean projectChanged = false;
			int subCount = parsed.subregions().size();
			int visitedSubs = 0;
			while (budget > 0 && visitedSubs < subCount) {
				if (parsed.subregionCursor >= subCount) parsed.subregionCursor = 0;
				LitematicParser.ParsedSubregion sub = parsed.subregions().get(parsed.subregionCursor);
				int size = sub.templateBlocks.size();
				if (size == 0) {
					parsed.subregionCursor = (parsed.subregionCursor + 1) % subCount;
					visitedSubs++;
					continue;
				}
				int remainingInSub = size - sub.cursor;
				int checks = Math.min(budget, remainingInSub);
				for (int i = 0; i < checks; i++) {
					int idx = sub.cursor++;
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
				if (sub.cursor >= size) {
					sub.cursor = 0;
					parsed.subregionCursor = (parsed.subregionCursor + 1) % subCount;
					visitedSubs++;
				}
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
			parsed.subregionCursor = 0;
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

	private static boolean scanListStorage(String listName, List<StorageRegion> listRegions) {
		if (listRegions == null || listRegions.isEmpty()) return false;
		Map<String, List<StorageRegion>> byDimension = new LinkedHashMap<>();
		for (StorageRegion region : listRegions) {
			if (region.dimension != null) {
				byDimension.computeIfAbsent(region.dimension, k -> new ArrayList<>()).add(region);
			}
		}
		Map<String, Map<String, Integer>> listCache = storageCache.computeIfAbsent(listName, k -> new LinkedHashMap<>());
		boolean changed = false;

		for (var dimEntry : byDimension.entrySet()) {
			String dim = dimEntry.getKey();
			ServerWorld world = findWorld(dim);
			if (world == null) continue;
			List<StorageRegion> dimRegions = dimEntry.getValue();

			// Collect unique chunk coords covered by any storage region in this dimension.
			Map<Long, int[]> coveredChunks = new LinkedHashMap<>();
			for (StorageRegion region : dimRegions) {
				int minCX = Math.min(region.x1, region.x2) >> 4;
				int maxCX = Math.max(region.x1, region.x2) >> 4;
				int minCZ = Math.min(region.z1, region.z2) >> 4;
				int maxCZ = Math.max(region.z1, region.z2) >> 4;
				for (int cx = minCX; cx <= maxCX; cx++) {
					for (int cz = minCZ; cz <= maxCZ; cz++) {
						long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
						coveredChunks.putIfAbsent(key, new int[]{cx, cz});
					}
				}
			}

			for (int[] coords : coveredChunks.values()) {
				int cx = coords[0], cz = coords[1];
				// Keep cached counts intact when a chunk is unloaded so HUD doesn't reset to 0.
				if (!world.isChunkLoaded(cx, cz)) continue;
				WorldChunk chunk = world.getChunk(cx, cz);
				Map<String, Integer> fresh = new LinkedHashMap<>();
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof Inventory inv)) continue;
					BlockPos pos = be.getPos();
					if (!isInsideAny(pos, dimRegions)) continue;
					for (int s = 0; s < inv.size(); s++) countStack(inv.getStack(s), fresh);
				}
				String cKey = chunkKey(dim, cx, cz);
				Map<String, Integer> prev = listCache.get(cKey);
				if (fresh.isEmpty()) {
					if (prev != null) {
						listCache.remove(cKey);
						changed = true;
					}
				} else if (!fresh.equals(prev)) {
					listCache.put(cKey, fresh);
					changed = true;
				}
			}
		}

		if (changed) recomputeAvailable(listName);
		return changed;
	}

	private static boolean isInsideAny(BlockPos pos, List<StorageRegion> dimRegions) {
		int x = pos.getX(), y = pos.getY(), z = pos.getZ();
		for (StorageRegion r : dimRegions) {
			int minX = Math.min(r.x1, r.x2), maxX = Math.max(r.x1, r.x2);
			int minY = Math.min(r.y1, r.y2), maxY = Math.max(r.y1, r.y2);
			int minZ = Math.min(r.z1, r.z2), maxZ = Math.max(r.z1, r.z2);
			if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
				return true;
			}
		}
		return false;
	}

	private static String chunkKey(String dim, int cx, int cz) {
		return dim + "|" + cx + "|" + cz;
	}

	private static void pruneStorageCache(String listName) {
		List<StorageRegion> listRegions = regions.get(listName);
		if (listRegions == null || listRegions.isEmpty()) {
			storageCache.remove(listName);
			return;
		}
		Map<String, Map<String, Integer>> listCache = storageCache.get(listName);
		if (listCache == null) return;
		var it = listCache.keySet().iterator();
		while (it.hasNext()) {
			String key = it.next();
			if (!isChunkKeyCovered(key, listRegions)) it.remove();
		}
		if (listCache.isEmpty()) storageCache.remove(listName);
	}

	private static boolean isChunkKeyCovered(String key, List<StorageRegion> listRegions) {
		int first = key.indexOf('|');
		int second = first < 0 ? -1 : key.indexOf('|', first + 1);
		if (first < 0 || second < 0) return false;
		String dim = key.substring(0, first);
		int cx, cz;
		try {
			cx = Integer.parseInt(key.substring(first + 1, second));
			cz = Integer.parseInt(key.substring(second + 1));
		} catch (NumberFormatException e) {
			return false;
		}
		for (StorageRegion r : listRegions) {
			if (!dim.equals(r.dimension)) continue;
			int minCX = Math.min(r.x1, r.x2) >> 4, maxCX = Math.max(r.x1, r.x2) >> 4;
			int minCZ = Math.min(r.z1, r.z2) >> 4, maxCZ = Math.max(r.z1, r.z2) >> 4;
			if (cx >= minCX && cx <= maxCX && cz >= minCZ && cz <= maxCZ) return true;
		}
		return false;
	}

	private static void recomputeAllAvailable() {
		for (String listName : lists.keySet()) {
			recomputeAvailable(listName);
		}
	}

	private static void recomputeAvailable(String listName) {
		MaterialList list = lists.get(listName);
		if (list == null) return;
		Map<String, Integer> totals = new HashMap<>();
		Map<String, Map<String, Integer>> listCache = storageCache.get(listName);
		if (listCache != null) {
			for (Map<String, Integer> chunkCounts : listCache.values()) {
				for (var e : chunkCounts.entrySet()) {
					totals.merge(e.getKey(), e.getValue(), Integer::sum);
				}
			}
		}
		for (MaterialEntry entry : list.entries) entry.available = totals.getOrDefault(entry.itemId, 0);
		if (list.subregions != null) {
			for (var sub : list.subregions) {
				for (MaterialEntry entry : sub.entries) entry.available = totals.getOrDefault(entry.itemId, 0);
			}
		}
		list.scannedAvailable = totals.values().stream().mapToInt(Integer::intValue).sum();
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
		if (scanListStorage(listName, regions.get(listName))) saveStorageCache();
		if (server != null) MaterialsNetworking.broadcast(server);
	}

	public static void removeStorageRegion(String listName, int index) {
		List<StorageRegion> listRegions = regions.get(listName);
		if (listRegions != null && index >= 0 && index < listRegions.size()) {
			listRegions.remove(index);
			if (listRegions.isEmpty()) regions.remove(listName);
			saveRegions();
			pruneStorageCache(listName);
			if (regions.containsKey(listName)) scanListStorage(listName, regions.get(listName));
			recomputeAvailable(listName);
			saveStorageCache();
			if (server != null) MaterialsNetworking.broadcast(server);
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
		storageCache.remove(listName);
		if (projectsDir != null) {
			try { Files.deleteIfExists(projectsDir.resolve(listName + ".litematic")); } catch (IOException ignored) {}
			try { Files.deleteIfExists(projectsDir.resolve(listName + ".meta.json")); } catch (IOException ignored) {}
		}
		saveClaims();
		saveRegions();
		saveStorageCache();
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

	private static void loadStorageCache() {
		if (storageCacheFile == null || !Files.isRegularFile(storageCacheFile)) { storageCache = new LinkedHashMap<>(); return; }
		try {
			Map<String, Map<String, Map<String, Integer>>> loaded = GSON.fromJson(Files.readString(storageCacheFile), STORAGE_CACHE_TYPE);
			storageCache = loaded != null ? new LinkedHashMap<>(loaded) : new LinkedHashMap<>();
		} catch (Exception e) {
			storageCache = new LinkedHashMap<>();
		}
	}

	private static void saveStorageCache() {
		if (storageCacheFile == null) return;
		try { Files.createDirectories(dataDir); Files.writeString(storageCacheFile, GSON.toJson(storageCache)); }
		catch (IOException e) { AeternumCarpet.LOGGER.error("Could not save storage cache", e); }
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
