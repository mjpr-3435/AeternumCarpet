package smp.aeternum.carpet.materials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.network.MaterialsNetworking;

public final class MaterialsStore {
	private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Gson COMPACT_GSON = new Gson();
	private static final Type CLAIMS_TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
	private static final Type REGIONS_TYPE = new TypeToken<Map<String, List<StorageRegion>>>() {}.getType();
	private static final Type STORAGE_CACHE_TYPE = new TypeToken<Map<String, Map<String, Map<String, Integer>>>>() {}.getType();

	private static final long DATA_BROADCAST_COALESCE_MS = 250L;
	private static final long OVERLAY_SCAN_INTERVAL_MS = 500L;
	private static final long STORAGE_CYCLE_INTERVAL_MS = 1_000L;
	private static final long PROGRESS_SAVE_INTERVAL_MS = 5_000L;
	private static final long META_SAVE_INTERVAL_MS = 3_000L;
	private static final long STORAGE_SAVE_INTERVAL_MS = 5_000L;
	private static final long REFRESH_COOLDOWN_MS = 2_000L;

	private static final int TIER1_MAX_BLOCKS_PER_TICK = 16_384;
	private static final long TIER1_MAX_NANOS_PER_TICK = 500_000L; // 0.5 ms
	private static final int TIER2_INTERVAL_TICKS = 15;
	private static final int STORAGE_MAX_CHUNKS_PER_TICK = 3;
	private static final long STORAGE_MAX_NANOS_PER_TICK = 250_000L; // 0.25 ms

	private static Path dataDir, projectsDir;
	private static Path claimsFile, regionsFile, storageCacheFile;

	private static final Map<String, MaterialList> lists = new LinkedHashMap<>();
	private static final Map<String, ProjectRuntime> runtimes = new LinkedHashMap<>();
	private static final Map<String, ProjectMeta> metas = new LinkedHashMap<>();
	private static final Map<String, FileStamp> fileStamps = new LinkedHashMap<>();

	// Global O(1) lookup: World -> ChunkPos.toLong -> overlapping project chunk bindings
	private static final Map<RegistryKey<World>, Long2ObjectOpenHashMap<ChunkBinding[]>> worldChunkIndex = new IdentityHashMap<>();

	// Tier 1 priority verification queues (loaded unverified chunks)
	private static final Map<String, LongArrayFIFOQueue> verifyQueues = new LinkedHashMap<>();
	private static final Map<String, LongOpenHashSet> verifyQueuedSet = new HashMap<>();
	private static final List<String> activeProjectNames = new ArrayList<>();
	private static int tier1ProjectCursor;
	private static int tier2ProjectCursor;
	private static long tickCounter;

	// Auto-DONE manual reopen guard
	private static final Map<String, Set<String>> manuallyReopenedAtFull = new HashMap<>();

	// Storage scanning
	private static Map<String, List<StorageRegion>> regions = new LinkedHashMap<>();
	private static Map<String, Map<String, Map<String, Integer>>> storageCache = new LinkedHashMap<>();
	private static final Map<String, StorageIndex> storageIndices = new LinkedHashMap<>();
	private static final List<StorageChunkTarget> storageScanList = new ArrayList<>();
	private static int storageScanCursor;
	private static long storageCycleStartedAt;
	private static boolean storageCycleIdle;

	// Claims & reactive HUD overlay
	private static Map<String, Map<String, String>> claims = new LinkedHashMap<>();
	private static final Map<String, ReferenceOpenHashSet<Item>> claimedItemsByPlayer = new HashMap<>();
	private static final Map<UUID, int[]> lastOverlaySignature = new HashMap<>();
	private static final Set<String> dirtyOverlayPlayerNames = new HashSet<>();

	// Dirty persistence flags
	private static final Set<String> dirtyProgressProjects = new LinkedHashSet<>();
	private static final Set<String> dirtyMetaProjects = new LinkedHashSet<>();
	private static boolean dirtyStorageCache;

	// Timers & lifecycle
	private static long lastBroadcastTime;
	private static long lastDirtyOverlayFlushTime;
	private static long lastOverlayScanTime;
	private static long lastProgressSaveTime;
	private static long lastMetaSaveTime;
	private static long lastStorageSaveTime;
	private static long lastRefreshDiskCheckTime;
	private static boolean pendingDataBroadcast;
	private static boolean wasCommandProjectsEnabled;
	private static MinecraftServer server;

	private MaterialsStore() {}

	public static void load(MinecraftServer srv) {
		server = srv;
		dataDir = srv.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_materials");
		projectsDir = srv.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_projects");
		claimsFile = dataDir.resolve("claims.json");
		regionsFile = dataDir.resolve("regions.json");
		storageCacheFile = dataDir.resolve("storage-cache.json");
		reloadSync();
	}

	private static void clearMemoryState() {
		lists.clear();
		runtimes.clear();
		metas.clear();
		fileStamps.clear();
		worldChunkIndex.clear();
		verifyQueues.clear();
		verifyQueuedSet.clear();
		activeProjectNames.clear();
		manuallyReopenedAtFull.clear();
		storageIndices.clear();
		storageScanList.clear();
		claimedItemsByPlayer.clear();
		lastOverlaySignature.clear();
		dirtyOverlayPlayerNames.clear();
		dirtyProgressProjects.clear();
		dirtyMetaProjects.clear();
		dirtyStorageCache = false;
		pendingDataBroadcast = false;
		tier1ProjectCursor = 0;
		tier2ProjectCursor = 0;
		storageScanCursor = 0;
		storageCycleIdle = false;
	}

	/** Synchronous full reload used on server startup (before world ticks start). */
	private static void reloadSync() {
		clearMemoryState();
		loadClaims();
		loadRegions();
		loadStorageCache();
		loadProjectsSync();
		rebuildWorldChunkIndex();
		rebuildClaimedItemsIndex();
		rebuildAllStorageIndices();
		recomputeAllAvailable();
		enqueueAllLoadedUnverifiedChunks();
		long now = System.currentTimeMillis();
		lastBroadcastTime = now;
		lastOverlayScanTime = now;
		lastProgressSaveTime = now;
		lastMetaSaveTime = now;
		lastStorageSaveTime = now;
		storageCycleStartedAt = now;
		wasCommandProjectsEnabled = false;
	}

	/** Async full reload for OP `/projects reload`. */
	public static void reloadAsync(Consumer<String> onComplete) {
		if (server == null || projectsDir == null) return;
		MinecraftServer srv = server;
		Path pDir = projectsDir;
		flushDirtyStateNow();

		MaterialsAsyncIO.execute(() -> {
			List<LoadedRawFile> loaded = new ArrayList<>();
			try {
				Files.createDirectories(pDir);
				try (var stream = Files.list(pDir)) {
					List<Path> files = stream
						.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic"))
						.toList();
					for (Path p : files) {
						String fn = p.getFileName().toString();
						String name = fn.substring(0, fn.length() - ".litematic".length());
						try {
							BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
							FileStamp stamp = new FileStamp(attrs.lastModifiedTime().toMillis(), attrs.size());
							LitematicParser.RawSchematic raw = LitematicParser.parseRaw(p);
							ProjectMeta meta = loadMetaFromDisk(pDir.resolve(name + ".meta.json"));
							ProgressCacheV2 progress = loadProgressCacheV2FromDisk(pDir.resolve(name + ".progress.json"));
							loaded.add(new LoadedRawFile(name, stamp, raw, meta, progress));
						} catch (Exception e) {
							AeternumCarpet.LOGGER.error("Could not load project schematic {}", p, e);
						}
					}
				}
			} catch (IOException e) {
				AeternumCarpet.LOGGER.error("Could not list project schematics", e);
			}

			srv.execute(() -> {
				clearMemoryState();
				loadClaims();
				loadRegions();
				loadStorageCache();
				for (LoadedRawFile lf : loaded) {
					installCompiledProject(lf.name, lf.stamp, lf.raw, lf.meta, lf.progress);
				}
				rebuildWorldChunkIndex();
				rebuildClaimedItemsIndex();
				rebuildAllStorageIndices();
				recomputeAllAvailable();
				enqueueAllLoadedUnverifiedChunks();
				MaterialsNetworking.broadcastData(srv);
				syncAllPlayerOverlays(srv, true);
				if (onComplete != null) {
					onComplete.accept("Recargados " + lists.size() + " proyecto(s) desde disco.");
				}
			});
		});
	}

	/**
	 * Lightweight refresh triggered by client's "Refrescar" button (MaterialsRefreshPayload).
	 * Rate-limited on disk/storage scans; never wipes or re-parses unchanged .litematic files.
	 */
	public static void refresh(ServerPlayerEntity requester) {
		if (server == null) return;
		if (!AeternumCarpetSettings.commandProjects) return;

		long now = System.currentTimeMillis();
		if (now - lastRefreshDiskCheckTime < REFRESH_COOLDOWN_MS) {
			MaterialsNetworking.sendSnapshot(requester, false);
			syncPlayerOverlay(requester, true);
			return;
		}
		lastRefreshDiskCheckTime = now;

		// Quick bounded storage refresh for loaded storage chunks
		boolean storageChanged = runImmediateStoragePass();

		// Detect only added / modified / deleted .litematic files on disk asynchronously
		MinecraftServer srv = server;
		Path pDir = projectsDir;
		Map<String, FileStamp> knownStamps = new HashMap<>(fileStamps);

		MaterialsAsyncIO.execute(() -> {
			Set<String> onDisk = new HashSet<>();
			List<LoadedRawFile> toLoad = new ArrayList<>();
			try {
				Files.createDirectories(pDir);
				try (var stream = Files.list(pDir)) {
					List<Path> files = stream
						.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic"))
						.toList();
					for (Path p : files) {
						String fn = p.getFileName().toString();
						String name = fn.substring(0, fn.length() - ".litematic".length());
						onDisk.add(name);
						try {
							BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
							FileStamp stamp = new FileStamp(attrs.lastModifiedTime().toMillis(), attrs.size());
							FileStamp prev = knownStamps.get(name);
							if (prev != null && prev.equals(stamp)) continue;
							LitematicParser.RawSchematic raw = LitematicParser.parseRaw(p);
							ProjectMeta meta = loadMetaFromDisk(pDir.resolve(name + ".meta.json"));
							ProgressCacheV2 progress = loadProgressCacheV2FromDisk(pDir.resolve(name + ".progress.json"));
							toLoad.add(new LoadedRawFile(name, stamp, raw, meta, progress));
						} catch (Exception e) {
							AeternumCarpet.LOGGER.error("Could not refresh schematic {}", p, e);
						}
					}
				}
			} catch (IOException e) {
				AeternumCarpet.LOGGER.error("Could not scan projects directory", e);
			}

			List<String> removed = new ArrayList<>();
			for (String existing : knownStamps.keySet()) {
				if (!onDisk.contains(existing)) removed.add(existing);
			}

			srv.execute(() -> {
				boolean changed = storageChanged;
				for (String rem : removed) {
					removeProjectFromMemory(rem);
					changed = true;
				}
				for (LoadedRawFile lf : toLoad) {
					installCompiledProject(lf.name, lf.stamp, lf.raw, lf.meta, lf.progress);
					changed = true;
				}
				if (!removed.isEmpty() || !toLoad.isEmpty()) {
					rebuildWorldChunkIndex();
					rebuildClaimedItemsIndex();
					rebuildAllStorageIndices();
					recomputeAllAvailable();
					enqueueAllLoadedUnverifiedChunks();
				}
				if (changed) {
					MaterialsNetworking.broadcastData(srv);
					syncAllPlayerOverlays(srv, false);
				} else if (!requester.isDisconnected()) {
					MaterialsNetworking.sendSnapshot(requester, false);
					syncPlayerOverlay(requester, true);
				}
			});
		});
	}

	public static void tick() {
		if (server == null) return;
		if (!AeternumCarpetSettings.commandProjects) {
			wasCommandProjectsEnabled = false;
			return;
		}
		if (!wasCommandProjectsEnabled) {
			wasCommandProjectsEnabled = true;
			enqueueAllLoadedUnverifiedChunks();
			syncAllPlayerOverlays(server, true);
		}

		tickCounter++;

		// 1) Tier 1 priority verification (loaded unverified chunks, fair round-robin)
		boolean tier1Ran = stepTier1Verification();
		// 2) Tier 2 lightweight self-healing reconciliation (only when Tier 1 is idle)
		if (!tier1Ran && (tickCounter % TIER2_INTERVAL_TICKS == 0)) {
			stepTier2Reconciliation();
		}
		// 3) Staggered storage scanning
		stepStorageScanner();

		long now = System.currentTimeMillis();

		// 4) Coalesced data broadcast
		if (pendingDataBroadcast && now - lastBroadcastTime >= DATA_BROADCAST_COALESCE_MS) {
			pendingDataBroadcast = false;
			lastBroadcastTime = now;
			MaterialsNetworking.broadcastData(server);
		}

		// 5) Reactive HUD overlay updates (inventory + dirty claimed items, coalesced at 250ms)
		if (!dirtyOverlayPlayerNames.isEmpty() && now - lastDirtyOverlayFlushTime >= DATA_BROADCAST_COALESCE_MS) {
			lastDirtyOverlayFlushTime = now;
			Set<String> dirtyNames = new HashSet<>(dirtyOverlayPlayerNames);
			dirtyOverlayPlayerNames.clear();
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				if (dirtyNames.contains(p.getName().getString())) {
					syncPlayerOverlay(p, false);
				}
			}
		}
		if (now - lastOverlayScanTime >= OVERLAY_SCAN_INTERVAL_MS) {
			lastOverlayScanTime = now;
			syncAllPlayerOverlays(server, false);
		}

		// 6) Debounced async persistence
		if (!dirtyProgressProjects.isEmpty() && now - lastProgressSaveTime >= PROGRESS_SAVE_INTERVAL_MS) {
			flushDirtyProgressAsync();
			lastProgressSaveTime = now;
		}
		if (!dirtyMetaProjects.isEmpty() && now - lastMetaSaveTime >= META_SAVE_INTERVAL_MS) {
			flushDirtyMetaAsync();
			lastMetaSaveTime = now;
		}
		if (dirtyStorageCache && now - lastStorageSaveTime >= STORAGE_SAVE_INTERVAL_MS) {
			dirtyStorageCache = false;
			saveStorageCacheAsync();
			lastStorageSaveTime = now;
		}
	}

	/** Real-time O(1) chunk lookup + O(log k) binary search hook called from ServerWorldMixin. */
	public static void onWorldBlockChanged(ServerWorld world, BlockPos pos, BlockState oldState, BlockState newState) {
		if (!AeternumCarpetSettings.commandProjects) return;
		if (oldState == newState || worldChunkIndex.isEmpty()) return;

		Long2ObjectOpenHashMap<ChunkBinding[]> byChunk = worldChunkIndex.get(world.getRegistryKey());
		if (byChunk == null || byChunk.isEmpty()) return;

		long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
		ChunkBinding[] bindings = byChunk.get(chunkKey);
		if (bindings == null) return;

		int y = pos.getY();
		if (y < -2048 || y > 2047) return;
		int localKey = ProjectRuntime.packLocalKey(pos.getX(), y, pos.getZ());

		for (ChunkBinding binding : bindings) {
			int changes = binding.chunk.onBlockChanged(
				localKey,
				newState,
				binding.runtime.palette,
				(subIdx, entryIdx, weight, delta) -> applyDelta(binding.projectName, binding.list, subIdx, entryIdx, weight, delta, true)
			);
			if (changes > 0) {
				dirtyProgressProjects.add(binding.projectName);
				pendingDataBroadcast = true;
			}
		}
	}

	/** Demand-driven verification hook called when any chunk finishes loading. */
	public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (!AeternumCarpetSettings.commandProjects || worldChunkIndex.isEmpty()) return;
		Long2ObjectOpenHashMap<ChunkBinding[]> byChunk = worldChunkIndex.get(world.getRegistryKey());
		if (byChunk == null) return;

		ChunkPos cp = chunk.getPos();
		long chunkKey = ChunkPos.toLong(cp.x, cp.z);
		ChunkBinding[] bindings = byChunk.get(chunkKey);
		if (bindings == null) return;

		for (ChunkBinding b : bindings) {
			if (!b.chunk.verified) {
				enqueueVerifyChunk(b.projectName, chunkKey);
			}
		}
	}

	public static void onPlayerJoin(ServerPlayerEntity player) {
		if (player == null) return;
		lastOverlaySignature.remove(player.getUuid());
		if (AeternumCarpetSettings.commandProjects) {
			syncPlayerOverlay(player, true);
		}
	}

	public static void onPlayerWorldChange(ServerPlayerEntity player) {
		if (player == null) return;
		lastOverlaySignature.remove(player.getUuid());
		if (AeternumCarpetSettings.commandProjects) {
			syncPlayerOverlay(player, true);
		}
	}

	public static void onPlayerRespawn(ServerPlayerEntity player) {
		if (player == null) return;
		lastOverlaySignature.remove(player.getUuid());
		if (AeternumCarpetSettings.commandProjects) {
			syncPlayerOverlay(player, true);
		}
	}

	public static void onPlayerDisconnect(ServerPlayerEntity player) {
		if (player == null) return;
		lastOverlaySignature.remove(player.getUuid());
	}

	private static boolean stepTier1Verification() {
		if (activeProjectNames.isEmpty()) return false;
		int projectCount = activeProjectNames.size();
		int blocksProcessed = 0;
		long startNanos = System.nanoTime();
		boolean processedAny = false;

		int emptyPasses = 0;
		while (blocksProcessed < TIER1_MAX_BLOCKS_PER_TICK
			&& (System.nanoTime() - startNanos) < TIER1_MAX_NANOS_PER_TICK
			&& emptyPasses < projectCount) {
			if (tier1ProjectCursor >= projectCount) tier1ProjectCursor = 0;
			String projName = activeProjectNames.get(tier1ProjectCursor);
			tier1ProjectCursor = (tier1ProjectCursor + 1) % projectCount;

			LongArrayFIFOQueue queue = verifyQueues.get(projName);
			if (queue == null || queue.isEmpty()) {
				emptyPasses++;
				continue;
			}
			emptyPasses = 0;

			ProjectRuntime rt = runtimes.get(projName);
			MaterialList list = lists.get(projName);
			if (rt == null || list == null) {
				queue.clear();
				LongOpenHashSet set = verifyQueuedSet.get(projName);
				if (set != null) set.clear();
				continue;
			}

			ServerWorld world = server.getWorld(rt.dimensionKey);
			if (world == null) {
				queue.clear();
				LongOpenHashSet set = verifyQueuedSet.get(projName);
				if (set != null) set.clear();
				continue;
			}

			long chunkKey = queue.dequeueLong();
			LongOpenHashSet set = verifyQueuedSet.get(projName);
			if (set != null) set.remove(chunkKey);

			ProjectRuntime.ChunkBlueprint cb = rt.chunks.get(chunkKey);
			if (cb == null) continue;

			WorldChunk chunk = world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(chunkKey), ChunkPos.getPackedZ(chunkKey));
			if (chunk == null) continue; // Unloaded before verification; CHUNK_LOAD will re-enqueue when loaded again.

			int changes = cb.verifyAgainstChunk(
				chunk,
				rt.palette,
				(subIdx, entryIdx, weight, delta) -> applyDelta(projName, list, subIdx, entryIdx, weight, delta, true)
			);
			if (!cb.verified) {
				cb.verified = true;
				rt.verifiedChunkCount++;
			}
			blocksProcessed += cb.size();
			processedAny = true;

			if (changes > 0) {
				dirtyProgressProjects.add(projName);
				pendingDataBroadcast = true;
			}
		}
		return processedAny;
	}

	private static void stepTier2Reconciliation() {
		if (activeProjectNames.isEmpty()) return;
		int projectCount = activeProjectNames.size();
		for (int attempt = 0; attempt < projectCount; attempt++) {
			if (tier2ProjectCursor >= projectCount) tier2ProjectCursor = 0;
			String projName = activeProjectNames.get(tier2ProjectCursor);
			tier2ProjectCursor = (tier2ProjectCursor + 1) % projectCount;

			ProjectRuntime rt = runtimes.get(projName);
			MaterialList list = lists.get(projName);
			if (rt == null || list == null || rt.chunkKeys.length == 0) continue;

			ServerWorld world = server.getWorld(rt.dimensionKey);
			if (world == null) continue;

			// Probe up to 16 chunks in O(1) without loading anything to find 1 loaded chunk
			int maxProbes = Math.min(16, rt.chunkKeys.length);
			for (int p = 0; p < maxProbes; p++) {
				if (rt.reconcileCursor >= rt.chunkKeys.length) rt.reconcileCursor = 0;
				long chunkKey = rt.chunkKeys[rt.reconcileCursor++];
				WorldChunk chunk = world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(chunkKey), ChunkPos.getPackedZ(chunkKey));
				if (chunk == null) continue;

				ProjectRuntime.ChunkBlueprint cb = rt.chunks.get(chunkKey);
				if (cb == null) continue;

				int changes = cb.verifyAgainstChunk(
					chunk,
					rt.palette,
					(subIdx, entryIdx, weight, delta) -> applyDelta(projName, list, subIdx, entryIdx, weight, delta, true)
				);
				if (!cb.verified) {
					cb.verified = true;
					rt.verifiedChunkCount++;
				}
				if (changes > 0) {
					dirtyProgressProjects.add(projName);
					pendingDataBroadcast = true;
				}
				return; // Done: 1 chunk per Tier 2 pulse
			}
		}
	}

	private static void applyDelta(
		String projectName,
		MaterialList list,
		int subIdx,
		int itemEntryIdx,
		int itemWeight,
		int delta,
		boolean trackOverlay
	) {
		list.placedBlocks = Math.max(0, list.placedBlocks + delta);
		if (subIdx >= 0 && subIdx < list.subregions.size()) {
			MaterialList.SubregionInfo sub = list.subregions.get(subIdx);
			sub.placedBlocks = Math.max(0, sub.placedBlocks + delta);
			evaluateSubregionAutoDone(projectName, sub);
		}
		if (itemEntryIdx >= 0 && itemEntryIdx < list.entries.size() && itemWeight > 0) {
			MaterialEntry entry = list.entries.get(itemEntryIdx);
			entry.placed = Math.max(0, entry.placed + delta * itemWeight);
			if (trackOverlay && entry.claimedBy != null && !entry.claimedBy.isEmpty()) {
				dirtyOverlayPlayerNames.add(entry.claimedBy);
			}
		}
	}

	private static void evaluateSubregionAutoDone(String projectName, MaterialList.SubregionInfo sub) {
		if (sub == null || sub.totalBlocks <= 0) return;
		ProjectMeta meta = metas.computeIfAbsent(projectName, k -> new ProjectMeta());
		SubregionMeta sm = meta.subregions.get(sub.name);

		if (sub.placedBlocks >= sub.totalBlocks) {
			Set<String> reopened = manuallyReopenedAtFull.get(projectName);
			if (reopened != null && reopened.contains(sub.name)) return;
			if (!"DONE".equals(sub.status)) {
				sub.status = "DONE";
				if (sm == null) {
					sm = new SubregionMeta();
					meta.subregions.put(sub.name, sm);
				}
				sm.status = "DONE";
				sm.claimedBy = sub.claimedBy;
				sm.autoCompleted = true;
				dirtyMetaProjects.add(projectName);
			}
		} else {
			Set<String> reopened = manuallyReopenedAtFull.get(projectName);
			if (reopened != null) reopened.remove(sub.name);
			if ("DONE".equals(sub.status) && sm != null && sm.autoCompleted) {
				String reverted = (sub.claimedBy != null && !sub.claimedBy.isEmpty()) ? "CLAIMED" : "NONE";
				sub.status = reverted;
				sm.status = reverted;
				sm.autoCompleted = false;
				dirtyMetaProjects.add(projectName);
			}
		}
	}

	// ==================== Storage Scanner (Staggered & Item-Indexed) ====================

	private static void stepStorageScanner() {
		if (storageScanList.isEmpty()) return;
		long now = System.currentTimeMillis();
		if (storageCycleIdle) {
			if (now - storageCycleStartedAt < STORAGE_CYCLE_INTERVAL_MS) return;
			storageCycleIdle = false;
			storageCycleStartedAt = now;
			storageScanCursor = 0;
		}

		int scanned = 0;
		long startNanos = System.nanoTime();
		while (storageScanCursor < storageScanList.size()
			&& scanned < STORAGE_MAX_CHUNKS_PER_TICK
			&& (System.nanoTime() - startNanos) < STORAGE_MAX_NANOS_PER_TICK) {
			StorageChunkTarget target = storageScanList.get(storageScanCursor++);
			if (scanStorageChunkTarget(target)) {
				dirtyStorageCache = true;
				pendingDataBroadcast = true;
			}
			scanned++;
		}

		if (storageScanCursor >= storageScanList.size()) {
			storageCycleIdle = true;
		}
	}

	private static boolean runImmediateStoragePass() {
		boolean changed = false;
		long startNanos = System.nanoTime();
		for (StorageChunkTarget target : storageScanList) {
			if (System.nanoTime() - startNanos > 2_000_000L) break; // Cap at 2ms on manual refresh
			if (scanStorageChunkTarget(target)) {
				dirtyStorageCache = true;
				changed = true;
			}
		}
		return changed;
	}

	private static boolean scanStorageChunkTarget(StorageChunkTarget target) {
		ServerWorld world = server.getWorld(target.dimKey);
		if (world == null) return false;
		WorldChunk chunk = world.getChunkManager().getWorldChunk(target.cx, target.cz);
		// Keep cached counts intact when a chunk is unloaded so HUD doesn't reset to 0.
		if (chunk == null) return false;

		StorageIndex idx = target.index;
		int entryCount = idx.entryItemIds.length;
		if (entryCount == 0) return false;

		int[] fresh = new int[entryCount];
		boolean anyItem = false;
		for (BlockEntity be : chunk.getBlockEntities().values()) {
			if (!(be instanceof Inventory inv)) continue;
			if (!isInsideAny(be.getPos(), target.dimRegions)) continue;
			int size = inv.size();
			for (int s = 0; s < size; s++) {
				ItemStack stack = inv.getStack(s);
				if (!stack.isEmpty()) {
					if (countStackFiltered(stack, 1, idx.itemToEntryIdx, fresh)) {
						anyItem = true;
					}
				}
			}
		}

		if (target.lastCounts != null && Arrays.equals(fresh, target.lastCounts)) {
			return false;
		}
		target.lastCounts = fresh;

		Map<String, Map<String, Integer>> listCache = storageCache.computeIfAbsent(idx.listName, k -> new LinkedHashMap<>());
		if (!anyItem) {
			listCache.remove(target.chunkKey);
		} else {
			Map<String, Integer> map = new LinkedHashMap<>();
			for (int i = 0; i < entryCount; i++) {
				if (fresh[i] > 0) map.put(idx.entryItemIds[i], fresh[i]);
			}
			listCache.put(target.chunkKey, map);
		}

		recomputeAvailable(idx.listName, true);
		return true;
	}

	private static boolean countStackFiltered(
		ItemStack stack,
		int multiplier,
		Reference2IntOpenHashMap<Item> filter,
		int[] counts
	) {
		if (stack.isEmpty()) return false;
		boolean matched = false;
		int totalCount = stack.getCount() * multiplier;
		int idx = filter.getInt(stack.getItem());
		if (idx >= 0) {
			counts[idx] += totalCount;
			matched = true;
		}
		ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
		if (container != null) {
			for (ItemStack nested : container.iterateNonEmpty()) {
				if (countStackFiltered(nested, totalCount, filter, counts)) matched = true;
			}
		}
		BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
		if (bundle != null && !bundle.isEmpty()) {
			for (ItemStack nested : bundle.iterate()) {
				if (countStackFiltered(nested, totalCount, filter, counts)) matched = true;
			}
		}
		return matched;
	}

	private static void rebuildAllStorageIndices() {
		storageIndices.clear();
		storageScanList.clear();
		storageScanCursor = 0;
		storageCycleIdle = false;
		for (var entry : regions.entrySet()) {
			rebuildStorageIndexFor(entry.getKey());
		}
	}

	private static void rebuildStorageIndexFor(String listName) {
		storageIndices.remove(listName);
		storageScanList.removeIf(t -> t.index.listName.equals(listName));

		MaterialList list = lists.get(listName);
		List<StorageRegion> listRegions = regions.get(listName);
		if (list == null || listRegions == null || listRegions.isEmpty()) return;

		Reference2IntOpenHashMap<Item> itemToEntryIdx = new Reference2IntOpenHashMap<>(Math.max(8, list.entries.size()));
		itemToEntryIdx.defaultReturnValue(-1);
		String[] entryItemIds = new String[list.entries.size()];
		Map<String, Integer> idToIdx = new HashMap<>(list.entries.size() * 2);
		for (int i = 0; i < list.entries.size(); i++) {
			String itemId = list.entries.get(i).itemId;
			entryItemIds[i] = itemId;
			idToIdx.put(itemId, i);
			Identifier id = Identifier.tryParse(itemId);
			if (id != null) {
				Registries.ITEM.getOptionalValue(id).ifPresent(item -> itemToEntryIdx.put(item, idToIdx.get(itemId)));
			}
		}

		StorageIndex index = new StorageIndex(listName, itemToEntryIdx, entryItemIds);
		storageIndices.put(listName, index);

		Map<String, List<StorageRegion>> byDimension = new LinkedHashMap<>();
		for (StorageRegion r : listRegions) {
			if (r.dimension != null) {
				byDimension.computeIfAbsent(r.dimension, k -> new ArrayList<>()).add(r);
			}
		}

		Map<String, Map<String, Integer>> listCache = storageCache.get(listName);
		for (var dimEntry : byDimension.entrySet()) {
			String dim = dimEntry.getKey();
			Identifier dimId = Identifier.tryParse(dim);
			if (dimId == null) continue;
			RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
			List<StorageRegion> dimRegions = dimEntry.getValue();

			LongOpenHashSet seenChunks = new LongOpenHashSet();
			for (StorageRegion r : dimRegions) {
				int minCX = Math.min(r.x1, r.x2) >> 4;
				int maxCX = Math.max(r.x1, r.x2) >> 4;
				int minCZ = Math.min(r.z1, r.z2) >> 4;
				int maxCZ = Math.max(r.z1, r.z2) >> 4;
				for (int cx = minCX; cx <= maxCX; cx++) {
					for (int cz = minCZ; cz <= maxCZ; cz++) {
						long cLong = ChunkPos.toLong(cx, cz);
						if (!seenChunks.add(cLong)) continue;
						String cKey = chunkKey(dim, cx, cz);
						int[] cachedCounts = new int[entryItemIds.length];
						if (listCache != null) {
							Map<String, Integer> saved = listCache.get(cKey);
							if (saved != null) {
								for (var se : saved.entrySet()) {
									Integer eIdx = idToIdx.get(se.getKey());
									if (eIdx != null) cachedCounts[eIdx] = se.getValue();
								}
							}
						}
						StorageChunkTarget target = new StorageChunkTarget(index, dimKey, cx, cz, cKey, dimRegions, cachedCounts);
						storageScanList.add(target);
					}
				}
			}
		}
	}

	// ==================== Reactive HUD Overlay (No Ender Chest) ====================

	private static void syncAllPlayerOverlays(MinecraftServer srv, boolean force) {
		if (srv == null) return;
		for (ServerPlayerEntity player : srv.getPlayerManager().getPlayerList()) {
			syncPlayerOverlay(player, force);
		}
	}

	public static void syncPlayerOverlay(ServerPlayerEntity player, boolean force) {
		if (player == null || player.isDisconnected()) return;
		if (!MaterialsNetworking.canSendOverlay(player)) return;

		String playerName = player.getName().getString();
		UUID uuid = player.getUuid();
		ReferenceOpenHashSet<Item> claimedItems = claimedItemsByPlayer.get(playerName);

		if (claimedItems == null || claimedItems.isEmpty()) {
			int[] prev = lastOverlaySignature.remove(uuid);
			if (force || (prev != null && prev.length > 0)) {
				MaterialsNetworking.sendOverlayJson(player, "[]");
			}
			return;
		}

		// Count only claimed items in player's main inventory (shulkers + bundles, NO Ender Chest)
		Reference2IntOpenHashMap<Item> carried = new Reference2IntOpenHashMap<>(claimedItems.size());
		Inventory inv = player.getInventory();
		int size = inv.size();
		for (int s = 0; s < size; s++) {
			ItemStack stack = inv.getStack(s);
			if (!stack.isEmpty()) {
				countPlayerClaimedStack(stack, 1, claimedItems, carried);
			}
		}

		IntArrayList sig = new IntArrayList();
		List<WireOverlayList> payloadLists = null;

		for (MaterialList list : lists.values()) {
			List<MaterialEntry> missingEntries = null;
			for (MaterialEntry e : list.entries) {
				if (!playerName.equals(e.claimedBy)) continue;
				int carriedCount = 0;
				Identifier id = Identifier.tryParse(e.itemId);
				if (id != null) {
					Item item = Registries.ITEM.get(id);
					carriedCount = carried.getInt(item);
				}
				int avail = e.available + carriedCount;
				int missing = Math.max(0, e.total - e.placed - avail);
				if (missing <= 0) continue;

				sig.add(list.name.hashCode());
				sig.add(e.itemId.hashCode());
				sig.add(e.total);
				sig.add(avail);
				sig.add(e.placed);

				if (missingEntries == null) missingEntries = new ArrayList<>();
				MaterialEntry ce = new MaterialEntry(e.itemName, e.itemId, e.total, avail);
				ce.placed = e.placed;
				ce.claimedBy = e.claimedBy;
				missingEntries.add(ce);
			}
			if (missingEntries != null) {
				if (payloadLists == null) payloadLists = new ArrayList<>();
				payloadLists.add(new WireOverlayList(list.name, missingEntries));
			}
		}

		int[] newSig = sig.toIntArray();
		int[] prevSig = lastOverlaySignature.get(uuid);
		if (!force && prevSig != null && Arrays.equals(newSig, prevSig)) {
			return;
		}

		if (newSig.length == 0) {
			lastOverlaySignature.remove(uuid);
			MaterialsNetworking.sendOverlayJson(player, "[]");
		} else {
			lastOverlaySignature.put(uuid, newSig);
			String json = COMPACT_GSON.toJson(payloadLists);
			if (json.length() > 98_000) {
				AeternumCarpet.LOGGER.warn("MaterialsOverlayPayload for {} exceeded 98k chars ({}); truncating", playerName, json.length());
				json = "[]";
			}
			MaterialsNetworking.sendOverlayJson(player, json);
		}
	}

	private static void countPlayerClaimedStack(
		ItemStack stack,
		int multiplier,
		ReferenceOpenHashSet<Item> claimedItems,
		Reference2IntOpenHashMap<Item> counts
	) {
		if (stack.isEmpty()) return;
		int totalCount = stack.getCount() * multiplier;
		Item item = stack.getItem();
		if (claimedItems.contains(item)) {
			counts.addTo(item, totalCount);
		}
		ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
		if (container != null) {
			for (ItemStack nested : container.iterateNonEmpty()) {
				countPlayerClaimedStack(nested, totalCount, claimedItems, counts);
			}
		}
		BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
		if (bundle != null && !bundle.isEmpty()) {
			for (ItemStack nested : bundle.iterate()) {
				countPlayerClaimedStack(nested, totalCount, claimedItems, counts);
			}
		}
	}

	private static void rebuildClaimedItemsIndex() {
		claimedItemsByPlayer.clear();
		for (MaterialList list : lists.values()) {
			for (MaterialEntry e : list.entries) {
				if (e.claimedBy == null || e.claimedBy.isEmpty()) continue;
				Identifier id = Identifier.tryParse(e.itemId);
				if (id == null) continue;
				Registries.ITEM.getOptionalValue(id).ifPresent(item ->
					claimedItemsByPlayer.computeIfAbsent(e.claimedBy, k -> new ReferenceOpenHashSet<>()).add(item)
				);
			}
		}
	}

	// ==================== Project Lifecycle (Upload / Placement / Subregions / Delete) ====================

	public static void uploadProjectAsync(
		String rawName,
		byte[] bytes,
		String uploadedBy,
		MaterialList.PlacementInfo placement,
		Consumer<String> onSuccess,
		Consumer<String> onError
	) {
		String name = sanitizeName(rawName);
		if (name.isEmpty()) {
			onError.accept("Nombre de proyecto inválido.");
			return;
		}
		if (server == null || projectsDir == null) {
			onError.accept("Servidor no inicializado.");
			return;
		}
		MinecraftServer srv = server;
		Path pDir = projectsDir;
		Path litematicPath = pDir.resolve(name + ".litematic");
		Path oldProgressPath = pDir.resolve(name + ".progress.json");

		MaterialsAsyncIO.execute(() -> {
			try {
				LitematicParser.RawSchematic raw = LitematicParser.parseRaw(bytes);
				MaterialsAsyncIO.writeBytesAtomicSync(litematicPath, bytes);
				try { Files.deleteIfExists(oldProgressPath); } catch (IOException ignored) {}
				BasicFileAttributes attrs = Files.readAttributes(litematicPath, BasicFileAttributes.class);
				FileStamp stamp = new FileStamp(attrs.lastModifiedTime().toMillis(), attrs.size());

				srv.execute(() -> {
					ProjectMeta meta = metas.get(name);
					if (meta == null) meta = loadMetaFromDisk(pDir.resolve(name + ".meta.json"));
					meta.uploadedBy = uploadedBy;
					if (placement != null && placement.configured) {
						meta.placement = placement;
					}
					dirtyProgressProjects.remove(name);
					manuallyReopenedAtFull.remove(name);

					installCompiledProject(name, stamp, raw, meta, null);
					saveMetaAsync(name, meta);
					rebuildWorldChunkIndex();
					rebuildClaimedItemsIndex();
					rebuildStorageIndexFor(name);
					recomputeAvailable(name, false);
					enqueueLoadedUnverifiedChunksFor(name);

					MaterialsNetworking.broadcastData(srv);
					syncAllPlayerOverlays(srv, false);
					onSuccess.accept(name);
				});
			} catch (Throwable t) {
				AeternumCarpet.LOGGER.error("Failed to upload project {}", rawName, t);
				srv.execute(() -> onError.accept(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
			}
		});
	}

	public static boolean updatePlacement(String projectName, MaterialList.PlacementInfo placement) {
		MaterialList list = lists.get(projectName);
		if (list == null || !list.hasSchematic || projectsDir == null) return false;

		// Preserve existing subregionsJson if the incoming placement didn't specify one
		if (list.placement != null
			&& (placement.subregionsJson == null || placement.subregionsJson.isBlank() || "[]".equals(placement.subregionsJson.trim()))
			&& list.placement.subregionsJson != null && !list.placement.subregionsJson.isBlank()) {
			placement.subregionsJson = list.placement.subregionsJson;
		}

		ProjectMeta meta = metas.computeIfAbsent(projectName, k -> new ProjectMeta());
		meta.placement = placement;
		saveMetaAsync(projectName, meta);

		dirtyProgressProjects.remove(projectName);
		manuallyReopenedAtFull.remove(projectName);
		MaterialsAsyncIO.deleteAsync(projectsDir.resolve(projectName + ".progress.json"));

		// Reset immediate visible placement & recompile asynchronously from disk
		list.placement = placement;
		list.placedBlocks = 0;
		for (MaterialEntry e : list.entries) e.placed = 0;
		for (var sub : list.subregions) {
			sub.placedBlocks = 0;
			evaluateSubregionAutoDone(projectName, sub);
		}

		MinecraftServer srv = server;
		Path litematicPath = projectsDir.resolve(projectName + ".litematic");
		if (srv != null && Files.isRegularFile(litematicPath)) {
			MaterialsAsyncIO.execute(() -> {
				try {
					BasicFileAttributes attrs = Files.readAttributes(litematicPath, BasicFileAttributes.class);
					FileStamp stamp = new FileStamp(attrs.lastModifiedTime().toMillis(), attrs.size());
					LitematicParser.RawSchematic raw = LitematicParser.parseRaw(litematicPath);
					srv.execute(() -> {
						if (!lists.containsKey(projectName)) return;
						ProjectMeta currentMeta = metas.getOrDefault(projectName, meta);
						installCompiledProject(projectName, stamp, raw, currentMeta, null);
						rebuildWorldChunkIndex();
						rebuildClaimedItemsIndex();
						rebuildStorageIndexFor(projectName);
						recomputeAvailable(projectName, false);
						enqueueLoadedUnverifiedChunksFor(projectName);
						MaterialsNetworking.broadcastData(srv);
						syncAllPlayerOverlays(srv, false);
					});
				} catch (Exception e) {
					AeternumCarpet.LOGGER.error("Failed to recompile project {} after placement update", projectName, e);
				}
			});
		}
		return true;
	}

	public static boolean subregionAction(String projectName, String subregionName, String action, String player, boolean isOp) {
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

		ProjectMeta meta = metas.computeIfAbsent(projectName, k -> new ProjectMeta());
		SubregionMeta sm = meta.subregions.computeIfAbsent(subregionName, k -> new SubregionMeta());

		switch (action) {
			case "claim" -> {
				if (target.claimedBy != null && !target.claimedBy.isEmpty() && !player.equals(target.claimedBy) && !isOp) {
					return false;
				}
				target.claimedBy = player;
				if ("NONE".equals(target.status)) target.status = "CLAIMED";
			}
			case "done" -> {
				target.status = "DONE";
				if (target.claimedBy == null || target.claimedBy.isEmpty()) target.claimedBy = player;
				sm.autoCompleted = false;
				Set<String> reopened = manuallyReopenedAtFull.get(projectName);
				if (reopened != null) reopened.remove(subregionName);
			}
			case "reopen" -> {
				target.status = (target.claimedBy != null && !target.claimedBy.isEmpty()) ? "CLAIMED" : "NONE";
				sm.autoCompleted = false;
				if (target.totalBlocks > 0 && target.placedBlocks >= target.totalBlocks) {
					manuallyReopenedAtFull.computeIfAbsent(projectName, k -> new HashSet<>()).add(subregionName);
				}
			}
			case "unclaim" -> {
				if (target.claimedBy == null || target.claimedBy.isEmpty()) return false;
				if (!isOp && !player.equals(target.claimedBy)) return false;
				target.claimedBy = null;
				if ("CLAIMED".equals(target.status)) target.status = "NONE";
			}
			default -> {
				return false;
			}
		}

		sm.status = target.status;
		sm.claimedBy = target.claimedBy;
		saveMetaAsync(projectName, meta);
		return true;
	}

	public static boolean claim(String listName, String itemId, String player) {
		MaterialList list = lists.get(listName);
		if (list == null) return false;
		Map<String, String> listClaims = claims.computeIfAbsent(listName, k -> new LinkedHashMap<>());
		String existing = listClaims.get(itemId);
		if (existing != null && !existing.isEmpty() && !existing.equals(player)) {
			return false;
		}
		listClaims.put(itemId, player);
		updateEntryClaim(list, itemId, player);
		rebuildClaimedItemsIndex();
		dirtyOverlayPlayerNames.add(player);
		saveClaimsAsync();
		return true;
	}

	public static boolean unclaim(String listName, String itemId, String actor, boolean isOp) {
		MaterialList list = lists.get(listName);
		Map<String, String> listClaims = claims.get(listName);
		if (listClaims == null) return false;
		String currentOwner = listClaims.get(itemId);
		if (currentOwner == null || currentOwner.isEmpty()) return false;
		if (!isOp && !currentOwner.equals(actor)) return false;

		listClaims.remove(itemId);
		updateEntryClaim(list, itemId, null);
		rebuildClaimedItemsIndex();
		dirtyOverlayPlayerNames.add(currentOwner);
		saveClaimsAsync();
		return true;
	}

	public static void delete(String listName) {
		removeProjectFromMemory(listName);
		claims.remove(listName);
		regions.remove(listName);
		storageCache.remove(listName);
		rebuildWorldChunkIndex();
		rebuildClaimedItemsIndex();
		rebuildAllStorageIndices();

		if (projectsDir != null) {
			MaterialsAsyncIO.deleteAsync(
				projectsDir.resolve(listName + ".litematic"),
				projectsDir.resolve(listName + ".meta.json"),
				projectsDir.resolve(listName + ".progress.json")
			);
		}
		saveClaimsAsync();
		saveRegionsAsync();
		saveStorageCacheAsync();
		if (server != null) syncAllPlayerOverlays(server, false);
	}

	private static void removeProjectFromMemory(String listName) {
		lists.remove(listName);
		runtimes.remove(listName);
		metas.remove(listName);
		fileStamps.remove(listName);
		verifyQueues.remove(listName);
		verifyQueuedSet.remove(listName);
		activeProjectNames.remove(listName);
		manuallyReopenedAtFull.remove(listName);
		dirtyProgressProjects.remove(listName);
		dirtyMetaProjects.remove(listName);
		storageIndices.remove(listName);
		storageScanList.removeIf(t -> t.index.listName.equals(listName));
	}

	public static void forceScan() {
		if (server == null || !AeternumCarpetSettings.commandProjects) return;
		runImmediateStoragePass();
		enqueueAllLoadedUnverifiedChunks();
		pendingDataBroadcast = true;
		lastBroadcastTime = 0;
		tick();
	}

	/** OP command helper: resets verified flag and re-enqueues all loaded chunks. */
	public static int rescanProject(String projectNameOrNull) {
		int queued = 0;
		if (projectNameOrNull != null) {
			ProjectRuntime rt = runtimes.get(projectNameOrNull);
			MaterialList list = lists.get(projectNameOrNull);
			if (rt == null || list == null) return -1;
			queued += resetAndEnqueueRuntime(projectNameOrNull, rt);
		} else {
			for (var entry : runtimes.entrySet()) {
				queued += resetAndEnqueueRuntime(entry.getKey(), entry.getValue());
			}
		}
		return queued;
	}

	private static int resetAndEnqueueRuntime(String name, ProjectRuntime rt) {
		rt.verifiedChunkCount = 0;
		rt.reconcileCursor = 0;
		for (ProjectRuntime.ChunkBlueprint cb : rt.chunks.values()) {
			cb.verified = false;
		}
		return enqueueLoadedUnverifiedChunksFor(name);
	}

	public static List<String> formatStatusLines(String projectNameOrNull) {
		List<String> lines = new ArrayList<>();
		Collection<MaterialList> targetLists;
		if (projectNameOrNull != null) {
			MaterialList one = lists.get(projectNameOrNull);
			if (one == null) return List.of();
			targetLists = List.of(one);
		} else {
			targetLists = lists.values();
		}

		long totalBytes = 0;
		int totalPending = 0;
		for (MaterialList list : targetLists) {
			ProjectRuntime rt = runtimes.get(list.name);
			int buildPct = list.totalBlocks <= 0 ? 100 : Math.min(100, Math.round(list.placedBlocks * 100f / list.totalBlocks));
			long matTotal = 0, matHave = 0;
			for (MaterialEntry e : list.entries) {
				int t = Math.max(0, e.total);
				matTotal += t;
				matHave += Math.min(t, Math.max(0, e.placed + e.available));
			}
			int matPct = matTotal == 0 ? 100 : Math.min(100, Math.round(matHave * 100f / matTotal));

			if (rt == null) {
				lines.add(list.name + ": Obra " + buildPct + "% · Mat " + matPct + "% · sin placement configurado");
				continue;
			}
			int loadedChunks = countLoadedChunks(rt);
			LongArrayFIFOQueue q = verifyQueues.get(list.name);
			int queued = q != null ? q.size() : 0;
			long bytes = rt.estimatedBytes();
			totalBytes += bytes;
			totalPending += queued;

			lines.add(String.format(
				java.util.Locale.ROOT,
				"%s: Obra %d%% (%d/%d) · Mat %d%% · Chunks verif %d/%d (cargados %d, cola %d) · %.1f KB",
				list.name, buildPct, list.placedBlocks, list.totalBlocks, matPct,
				rt.verifiedChunkCount, rt.chunks.size(), loadedChunks, queued, bytes / 1024.0
			));
		}
		if (projectNameOrNull == null && !lines.isEmpty()) {
			lines.add(String.format(
				java.util.Locale.ROOT,
				"Total: %d proyecto(s) · cola=%d chunk(s) · memoria índice=%.2f MB · commandProjects=%s",
				lists.size(), totalPending, totalBytes / (1024.0 * 1024.0), AeternumCarpetSettings.commandProjects
			));
		}
		return lines;
	}

	private static int countLoadedChunks(ProjectRuntime rt) {
		if (server == null) return 0;
		ServerWorld world = server.getWorld(rt.dimensionKey);
		if (world == null) return 0;
		int loaded = 0;
		for (long ck : rt.chunkKeys) {
			if (world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(ck), ChunkPos.getPackedZ(ck)) != null) {
				loaded++;
			}
		}
		return loaded;
	}

	// ==================== Internal Loading & Indexing ====================

	private static void loadProjectsSync() {
		try { Files.createDirectories(projectsDir); } catch (IOException e) { return; }
		try (var files = Files.list(projectsDir)) {
			files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic")).forEach(p -> {
				String fileName = p.getFileName().toString();
				String name = fileName.substring(0, fileName.length() - ".litematic".length());
				try {
					BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
					FileStamp stamp = new FileStamp(attrs.lastModifiedTime().toMillis(), attrs.size());
					LitematicParser.RawSchematic raw = LitematicParser.parseRaw(p);
					ProjectMeta meta = loadMetaFromDisk(projectsDir.resolve(name + ".meta.json"));
					ProgressCacheV2 progress = loadProgressCacheV2FromDisk(projectsDir.resolve(name + ".progress.json"));
					installCompiledProject(name, stamp, raw, meta, progress);
				} catch (Exception e) {
					AeternumCarpet.LOGGER.error("Could not load project schematic {}", p, e);
				}
			});
		} catch (IOException e) {
			AeternumCarpet.LOGGER.error("Could not list project schematics", e);
		}
	}

	private static void installCompiledProject(
		String name,
		FileStamp stamp,
		LitematicParser.RawSchematic raw,
		ProjectMeta meta,
		ProgressCacheV2 progressCache
	) {
		LitematicParser.CompiledProject compiled = LitematicParser.compile(name, raw, meta.placement, meta);
		MaterialList list = compiled.list();
		ProjectRuntime runtime = compiled.runtime();

		// Apply existing claims for this project
		Map<String, String> listClaims = claims.get(name);
		if (listClaims != null) {
			for (MaterialEntry entry : list.entries) {
				entry.claimedBy = listClaims.get(entry.itemId);
			}
		}

		lists.put(name, list);
		metas.put(name, meta);
		fileStamps.put(name, stamp);
		verifyQueues.remove(name);
		verifyQueuedSet.remove(name);

		if (runtime != null) {
			runtimes.put(name, runtime);
			if (!activeProjectNames.contains(name)) activeProjectNames.add(name);
			restoreProgressCacheV2(name, list, runtime, progressCache);
		} else {
			runtimes.remove(name);
			activeProjectNames.remove(name);
		}
	}

	private static void restoreProgressCacheV2(
		String name,
		MaterialList list,
		ProjectRuntime rt,
		ProgressCacheV2 cache
	) {
		if (cache == null
			|| cache.formatVersion != 2
			|| !rt.placementKey.equals(cache.placementKey)
			|| !rt.schematicHash.equals(cache.schematicHash)
			|| cache.chunks == null) {
			return;
		}
		for (var entry : rt.chunks.long2ObjectEntrySet()) {
			long ck = entry.getLongKey();
			ProjectRuntime.ChunkBlueprint cb = entry.getValue();
			String key = ChunkPos.getPackedX(ck) + "," + ChunkPos.getPackedZ(ck);
			ChunkProgress cp = cache.chunks.get(key);
			if (cp == null || cp.size != cb.size()) continue;

			if (cp.bitsBase64 != null && !cp.bitsBase64.isEmpty()) {
				byte[] raw = Base64.getDecoder().decode(cp.bitsBase64);
				cb.importBits(
					raw,
					rt.palette,
					(subIdx, entryIdx, weight, delta) -> applyDelta(name, list, subIdx, entryIdx, weight, delta, false)
				);
			}
			cb.verified = true;
			rt.verifiedChunkCount++;
		}
	}

	private static void rebuildWorldChunkIndex() {
		worldChunkIndex.clear();
		Map<RegistryKey<World>, Long2ObjectOpenHashMap<List<ChunkBinding>>> temp = new IdentityHashMap<>();
		for (var entry : runtimes.entrySet()) {
			String name = entry.getKey();
			ProjectRuntime rt = entry.getValue();
			MaterialList list = lists.get(name);
			if (rt == null || list == null) continue;
			Long2ObjectOpenHashMap<List<ChunkBinding>> byChunk = temp.computeIfAbsent(rt.dimensionKey, k -> new Long2ObjectOpenHashMap<>());
			for (var ce : rt.chunks.long2ObjectEntrySet()) {
				long ck = ce.getLongKey();
				List<ChunkBinding> bucket = byChunk.get(ck);
				if (bucket == null) {
					bucket = new ArrayList<>(1);
					byChunk.put(ck, bucket);
				}
				bucket.add(new ChunkBinding(name, list, rt, ce.getValue()));
			}
		}
		for (var dimEntry : temp.entrySet()) {
			Long2ObjectOpenHashMap<List<ChunkBinding>> src = dimEntry.getValue();
			Long2ObjectOpenHashMap<ChunkBinding[]> dst = new Long2ObjectOpenHashMap<>(src.size());
			for (var ce : src.long2ObjectEntrySet()) {
				dst.put(ce.getLongKey(), ce.getValue().toArray(new ChunkBinding[0]));
			}
			worldChunkIndex.put(dimEntry.getKey(), dst);
		}
	}

	private static void enqueueAllLoadedUnverifiedChunks() {
		for (String name : activeProjectNames) {
			enqueueLoadedUnverifiedChunksFor(name);
		}
	}

	private static int enqueueLoadedUnverifiedChunksFor(String name) {
		if (server == null) return 0;
		ProjectRuntime rt = runtimes.get(name);
		if (rt == null) return 0;
		ServerWorld world = server.getWorld(rt.dimensionKey);
		if (world == null) return 0;
		int queued = 0;
		for (long ck : rt.chunkKeys) {
			ProjectRuntime.ChunkBlueprint cb = rt.chunks.get(ck);
			if (cb == null || cb.verified) continue;
			if (world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(ck), ChunkPos.getPackedZ(ck)) != null) {
				if (enqueueVerifyChunk(name, ck)) queued++;
			}
		}
		return queued;
	}

	private static boolean enqueueVerifyChunk(String projectName, long chunkKey) {
		LongOpenHashSet set = verifyQueuedSet.computeIfAbsent(projectName, k -> new LongOpenHashSet());
		if (!set.add(chunkKey)) return false;
		verifyQueues.computeIfAbsent(projectName, k -> new LongArrayFIFOQueue()).enqueue(chunkKey);
		return true;
	}

	// ==================== Storage Regions & Public Queries ====================

	public static MaterialList get(String name) { return lists.get(name); }

	public static Collection<MaterialList> all() {
		return lists.values();
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

	public static void addStorageRegion(String listName, String dimension, BlockPos pos1, BlockPos pos2) {
		regions.computeIfAbsent(listName, k -> new ArrayList<>()).add(new StorageRegion(dimension, pos1, pos2));
		saveRegionsAsync();
		rebuildStorageIndexFor(listName);
		if (runImmediateStoragePassFor(listName)) {
			dirtyStorageCache = true;
			saveStorageCacheAsync();
		}
		if (server != null) {
			MaterialsNetworking.broadcastData(server);
			syncAllPlayerOverlays(server, false);
		}
	}

	public static void removeStorageRegion(String listName, int index) {
		List<StorageRegion> listRegions = regions.get(listName);
		if (listRegions != null && index >= 0 && index < listRegions.size()) {
			listRegions.remove(index);
			if (listRegions.isEmpty()) regions.remove(listName);
			saveRegionsAsync();
			pruneStorageCache(listName);
			rebuildStorageIndexFor(listName);
			if (regions.containsKey(listName)) runImmediateStoragePassFor(listName);
			recomputeAvailable(listName, true);
			saveStorageCacheAsync();
			if (server != null) {
				MaterialsNetworking.broadcastData(server);
				syncAllPlayerOverlays(server, false);
			}
		}
	}

	private static boolean runImmediateStoragePassFor(String listName) {
		boolean changed = false;
		for (StorageChunkTarget target : storageScanList) {
			if (!target.index.listName.equals(listName)) continue;
			if (scanStorageChunkTarget(target)) changed = true;
		}
		return changed;
	}

	public static List<StorageRegion> getRegions(String listName) {
		return regions.getOrDefault(listName, List.of());
	}

	public static String getClaim(String listName, String itemId) {
		Map<String, String> listClaims = claims.get(listName);
		return listClaims == null ? null : listClaims.get(itemId);
	}

	private static void updateEntryClaim(MaterialList list, String itemId, String player) {
		if (list == null) return;
		for (MaterialEntry e : list.entries) {
			if (e.itemId.equals(itemId)) e.claimedBy = player;
		}
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
			if (!isChunkKeyCovered(it.next(), listRegions)) it.remove();
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
			recomputeAvailable(listName, false);
		}
	}

	private static void recomputeAvailable(String listName, boolean markOverlayDirty) {
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
		int sumRequired = 0;
		for (MaterialEntry entry : list.entries) {
			int next = totals.getOrDefault(entry.itemId, 0);
			if (markOverlayDirty && next != entry.available && entry.claimedBy != null && !entry.claimedBy.isEmpty()) {
				dirtyOverlayPlayerNames.add(entry.claimedBy);
			}
			entry.available = next;
			sumRequired += next;
		}
		list.scannedAvailable = sumRequired;
	}

	// ==================== Persistence (Atomic Async) ====================

	public static void onServerStopping() {
		flushDirtyStateNow();
		MaterialsAsyncIO.flushAndShutdown();
	}

	private static void flushDirtyStateNow() {
		flushDirtyProgressAsync();
		flushDirtyMetaAsync();
		if (dirtyStorageCache) {
			dirtyStorageCache = false;
			saveStorageCacheAsync();
		}
	}

	private static void flushDirtyProgressAsync() {
		if (dirtyProgressProjects.isEmpty() || projectsDir == null) return;
		List<String> names = new ArrayList<>(dirtyProgressProjects);
		dirtyProgressProjects.clear();
		for (String name : names) {
			ProjectRuntime rt = runtimes.get(name);
			if (rt == null) continue;
			ProgressCacheV2 cache = new ProgressCacheV2();
			cache.placementKey = rt.placementKey;
			cache.schematicHash = rt.schematicHash;
			for (var entry : rt.chunks.long2ObjectEntrySet()) {
				ProjectRuntime.ChunkBlueprint cb = entry.getValue();
				if (!cb.verified) continue;
				long ck = entry.getLongKey();
				ChunkProgress cp = new ChunkProgress();
				cp.size = cb.size();
				byte[] rawBits = cb.exportBits();
				cp.bitsBase64 = rawBits.length == 0 ? "" : Base64.getEncoder().encodeToString(rawBits);
				cache.chunks.put(ChunkPos.getPackedX(ck) + "," + ChunkPos.getPackedZ(ck), cp);
			}
			String json = COMPACT_GSON.toJson(cache);
			MaterialsAsyncIO.writeAtomicAsync(projectsDir.resolve(name + ".progress.json"), json);
		}
	}

	private static void flushDirtyMetaAsync() {
		if (dirtyMetaProjects.isEmpty() || projectsDir == null) return;
		List<String> names = new ArrayList<>(dirtyMetaProjects);
		dirtyMetaProjects.clear();
		for (String name : names) {
			ProjectMeta meta = metas.get(name);
			if (meta != null) saveMetaAsync(name, meta);
		}
	}

	private static void saveMetaAsync(String name, ProjectMeta meta) {
		if (projectsDir == null) return;
		MaterialsAsyncIO.writeAtomicAsync(projectsDir.resolve(name + ".meta.json"), PRETTY_GSON.toJson(meta));
	}

	private static void saveClaimsAsync() {
		if (claimsFile == null) return;
		MaterialsAsyncIO.writeAtomicAsync(claimsFile, PRETTY_GSON.toJson(claims));
	}

	private static void saveRegionsAsync() {
		if (regionsFile == null) return;
		MaterialsAsyncIO.writeAtomicAsync(regionsFile, PRETTY_GSON.toJson(regions));
	}

	private static void saveStorageCacheAsync() {
		if (storageCacheFile == null) return;
		MaterialsAsyncIO.writeAtomicAsync(storageCacheFile, COMPACT_GSON.toJson(storageCache));
	}

	private static ProjectMeta loadMetaFromDisk(Path metaPath) {
		if (metaPath == null || !Files.isRegularFile(metaPath)) return new ProjectMeta();
		try {
			ProjectMeta meta = PRETTY_GSON.fromJson(Files.readString(metaPath), ProjectMeta.class);
			return meta != null ? meta : new ProjectMeta();
		} catch (Exception e) {
			return new ProjectMeta();
		}
	}

	private static ProgressCacheV2 loadProgressCacheV2FromDisk(Path progressPath) {
		if (progressPath == null || !Files.isRegularFile(progressPath)) return null;
		try {
			return COMPACT_GSON.fromJson(Files.readString(progressPath), ProgressCacheV2.class);
		} catch (Exception e) {
			AeternumCarpet.LOGGER.warn("Could not read progress cache {}", progressPath, e);
			return null;
		}
	}

	private static void loadClaims() {
		if (claimsFile == null || !Files.isRegularFile(claimsFile)) { claims = new LinkedHashMap<>(); return; }
		try {
			Map<String, Map<String, String>> loaded = PRETTY_GSON.fromJson(Files.readString(claimsFile), CLAIMS_TYPE);
			claims = loaded != null ? new LinkedHashMap<>(loaded) : new LinkedHashMap<>();
		} catch (Exception e) {
			claims = new LinkedHashMap<>();
		}
	}

	private static void loadRegions() {
		if (regionsFile == null || !Files.isRegularFile(regionsFile)) { regions = new LinkedHashMap<>(); return; }
		try {
			Map<String, List<StorageRegion>> loaded = PRETTY_GSON.fromJson(Files.readString(regionsFile), REGIONS_TYPE);
			regions = loaded != null ? new LinkedHashMap<>(loaded) : new LinkedHashMap<>();
		} catch (Exception e) {
			regions = new LinkedHashMap<>();
		}
	}

	private static void loadStorageCache() {
		if (storageCacheFile == null || !Files.isRegularFile(storageCacheFile)) { storageCache = new LinkedHashMap<>(); return; }
		try {
			Map<String, Map<String, Map<String, Integer>>> loaded = COMPACT_GSON.fromJson(Files.readString(storageCacheFile), STORAGE_CACHE_TYPE);
			storageCache = loaded != null ? new LinkedHashMap<>(loaded) : new LinkedHashMap<>();
		} catch (Exception e) {
			storageCache = new LinkedHashMap<>();
		}
	}

	public static String placementKey(MaterialList.PlacementInfo p) {
		if (p == null || !p.configured) return "";
		String subs = (p.subregionsJson != null && !p.subregionsJson.isBlank()) ? p.subregionsJson : "[]";
		return p.dimension + "|" + p.x + "," + p.y + "," + p.z + "|" + p.rotation + "|" + p.mirror
			+ "|" + subs.length() + ":" + Integer.toHexString(subs.hashCode());
	}

	private static String sanitizeName(String raw) {
		if (raw == null) return "";
		String s = raw.trim().replaceAll("(?i)\\.litematic$", "");
		return s.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("^_+|_+$", "");
	}

	// ==================== DTOs & Internal Records ====================

	private record ChunkBinding(
		String projectName,
		MaterialList list,
		ProjectRuntime runtime,
		ProjectRuntime.ChunkBlueprint chunk
	) {}

	private record FileStamp(long mtime, long size) {}

	private record LoadedRawFile(
		String name,
		FileStamp stamp,
		LitematicParser.RawSchematic raw,
		ProjectMeta meta,
		ProgressCacheV2 progress
	) {}

	private static final class StorageIndex {
		final String listName;
		final Reference2IntOpenHashMap<Item> itemToEntryIdx;
		final String[] entryItemIds;

		StorageIndex(String listName, Reference2IntOpenHashMap<Item> itemToEntryIdx, String[] entryItemIds) {
			this.listName = listName;
			this.itemToEntryIdx = itemToEntryIdx;
			this.entryItemIds = entryItemIds;
		}
	}

	private static final class StorageChunkTarget {
		final StorageIndex index;
		final RegistryKey<World> dimKey;
		final int cx, cz;
		final String chunkKey;
		final List<StorageRegion> dimRegions;
		int[] lastCounts;

		StorageChunkTarget(
			StorageIndex index,
			RegistryKey<World> dimKey,
			int cx,
			int cz,
			String chunkKey,
			List<StorageRegion> dimRegions,
			int[] lastCounts
		) {
			this.index = index;
			this.dimKey = dimKey;
			this.cx = cx;
			this.cz = cz;
			this.chunkKey = chunkKey;
			this.dimRegions = dimRegions;
			this.lastCounts = lastCounts;
		}
	}

	private static final class WireOverlayList {
		final String name;
		final List<MaterialEntry> entries;

		WireOverlayList(String name, List<MaterialEntry> entries) {
			this.name = name;
			this.entries = entries;
		}
	}

	private static final class ProgressCacheV2 {
		int formatVersion = 2;
		String placementKey = "";
		String schematicHash = "";
		Map<String, ChunkProgress> chunks = new LinkedHashMap<>();
	}

	private static final class ChunkProgress {
		int size;
		String bitsBase64 = "";
	}

	public static class ProjectMeta {
		public String uploadedBy;
		public MaterialList.PlacementInfo placement = new MaterialList.PlacementInfo();
		public Map<String, SubregionMeta> subregions = new LinkedHashMap<>();
	}

	public static class SubregionMeta {
		public String status = "NONE";
		public String claimedBy;
		public boolean autoCompleted;
	}

	public static class StorageRegion {
		public String dimension;
		public int x1, y1, z1, x2, y2, z2;
		public StorageRegion() {}
		public StorageRegion(String dimension, BlockPos pos1, BlockPos pos2) {
			this.dimension = dimension; this.x1 = pos1.getX(); this.y1 = pos1.getY(); this.z1 = pos1.getZ();
			this.x2 = pos2.getX(); this.y2 = pos2.getY(); this.z2 = pos2.getZ();
		}
	}
}
