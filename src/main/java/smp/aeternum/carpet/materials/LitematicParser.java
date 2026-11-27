package smp.aeternum.carpet.materials;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.MultifaceBlock;
import net.minecraft.block.SeaPickleBlock;
import net.minecraft.block.Segmented;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import smp.aeternum.carpet.AeternumCarpet;

/**
 * Two-stage .litematic parser:
 *  1) parseRaw(byte[]) runs safely off-thread (GZIP/NBT + bit-unpacking + BlockState Properties).
 *  2) compile(...) runs on the server thread to resolve blocks/items, apply root + subregion transforms,
 *     and produce both MaterialList metadata and compact chunk-partitioned ProjectRuntime.
 */
public final class LitematicParser {
	private static final Gson GSON = new Gson();
	private static final Type SUBREGION_OVERRIDE_LIST_TYPE = new TypeToken<List<SubregionOverride>>() {}.getType();

	private static final long MAX_REGION_VOLUME = 200_000_000L;
	private static final int MAX_TOTAL_BLOCKS = 4_000_000;

	private static final Set<String> SKIPPED_BLOCKS = Set.of(
		"minecraft:air",
		"minecraft:cave_air",
		"minecraft:void_air",
		"minecraft:piston_head",
		"minecraft:moving_piston",
		"minecraft:bubble_column",
		"minecraft:fire",
		"minecraft:soul_fire",
		"minecraft:nether_portal"
	);

	private static final String[] MULTIFACE_KEYS = { "north", "south", "east", "west", "up", "down" };

	private LitematicParser() {}

	public static RawSchematic parseRaw(Path file) throws IOException {
		return parseRaw(Files.readAllBytes(file));
	}

	/**
	 * Thread-safe stage 1: decompresses NBT and unpacks all subregions into compact primitive arrays
	 * without touching Minecraft registries.
	 */
	public static RawSchematic parseRaw(byte[] bytes) throws IOException {
		String hash = computeHash(bytes);
		NbtCompound root;
		try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
			root = NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
		}
		NbtCompound regionsNbt = root.getCompoundOrEmpty("Regions");
		if (regionsNbt.isEmpty()) {
			throw new IOException("El archivo .litematic no contiene subregiones.");
		}

		List<RawSubregion> subregions = new ArrayList<>();
		int grandNonAir = 0;

		for (String regionName : regionsNbt.getKeys()) {
			NbtCompound rData = regionsNbt.getCompoundOrEmpty(regionName);
			NbtCompound sizeNbt = rData.getCompoundOrEmpty("Size");
			NbtCompound posNbt = rData.getCompoundOrEmpty("Position");

			int sx = sizeNbt.getInt("x", 0);
			int sy = sizeNbt.getInt("y", 0);
			int sz = sizeNbt.getInt("z", 0);
			int dx = Math.abs(sx);
			int dy = Math.abs(sy);
			int dz = Math.abs(sz);
			long volLong = (long) dx * dy * dz;
			if (dx == 0 || dy == 0 || dz == 0) continue;
			if (volLong > MAX_REGION_VOLUME) {
				AeternumCarpet.LOGGER.warn("Skipping subregion '{}' due to extreme volume {} (> {})", regionName, volLong, MAX_REGION_VOLUME);
				continue;
			}
			int vol = (int) volLong;

			int px = posNbt.getInt("x", 0);
			int py = posNbt.getInt("y", 0);
			int pz = posNbt.getInt("z", 0);

			NbtList paletteNbt = rData.getListOrEmpty("BlockStatePalette");
			if (paletteNbt.isEmpty()) continue;

			RawPaletteEntry[] palette = new RawPaletteEntry[paletteNbt.size()];
			for (int i = 0; i < paletteNbt.size(); i++) {
				NbtCompound entry = paletteNbt.getCompoundOrEmpty(i);
				String blockId = entry.getString("Name", "minecraft:air");
				NbtCompound props = entry.getCompoundOrEmpty("Properties");
				palette[i] = parseRawPaletteEntry(blockId, props);
			}

			int offX = sx >= 0 ? 0 : sx + 1;
			int offY = sy >= 0 ? 0 : sy + 1;
			int offZ = sz >= 0 ? 0 : sz + 1;

			IntArrayList lx = new IntArrayList();
			IntArrayList ly = new IntArrayList();
			IntArrayList lz = new IntArrayList();
			ShortArrayList pIdx = new ShortArrayList();

			boolean truncated = false;
			if (palette.length == 1) {
				if (!palette[0].skipped) {
					int cap = Math.min(vol, MAX_TOTAL_BLOCKS - grandNonAir);
					if (cap < vol) truncated = true;
					if (cap > 0) {
						lx.ensureCapacity(cap);
						ly.ensureCapacity(cap);
						lz.ensureCapacity(cap);
						pIdx.ensureCapacity(cap);
						int added = 0;
						outer:
						for (int y = 0; y < dy; y++) {
							for (int z = 0; z < dz; z++) {
								for (int x = 0; x < dx; x++) {
									if (added >= cap) break outer;
									lx.add(offX + x);
									ly.add(offY + y);
									lz.add(offZ + z);
									pIdx.add((short) 0);
									added++;
								}
							}
						}
						grandNonAir += added;
					}
				}
			} else {
				int bitsPerBlock = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
				long mask = (1L << bitsPerBlock) - 1L;
				long[] rawLongs = rData.getLongArray("BlockStates").orElse(new long[0]);
				int layerSize = dx * dz;

				for (int i = 0; i < vol; i++) {
					long startBit = (long) i * bitsPerBlock;
					int startIdx = (int) (startBit >>> 6);
					int startOffset = (int) (startBit & 63L);
					int endIdx = (int) ((startBit + bitsPerBlock - 1L) >>> 6);

					int val;
					if (startIdx >= rawLongs.length) {
						val = 0;
					} else if (startIdx == endIdx) {
						val = (int) ((rawLongs[startIdx] >>> startOffset) & mask);
					} else {
						long part1 = rawLongs[startIdx] >>> startOffset;
						long part2 = endIdx < rawLongs.length ? (rawLongs[endIdx] << (64 - startOffset)) : 0L;
						val = (int) ((part1 | part2) & mask);
					}

					if (val < 0 || val >= palette.length || palette[val].skipped) continue;
					if (grandNonAir >= MAX_TOTAL_BLOCKS) {
						truncated = true;
						break;
					}

					int y = i / layerSize;
					int rem = i - y * layerSize;
					int z = rem / dx;
					int x = rem - z * dx;

					lx.add(offX + x);
					ly.add(offY + y);
					lz.add(offZ + z);
					pIdx.add((short) val);
					grandNonAir++;
				}
			}

			if (truncated) {
				AeternumCarpet.LOGGER.warn("Subregion '{}' exceeded max non-air block cap ({}); remaining blocks skipped", regionName, MAX_TOTAL_BLOCKS);
			}
			if (pIdx.isEmpty()) continue;

			subregions.add(new RawSubregion(
				regionName,
				px, py, pz,
				palette,
				lx.toIntArray(),
				ly.toIntArray(),
				lz.toIntArray(),
				pIdx.toShortArray()
			));
		}

		subregions.sort(Comparator.comparing(s -> s.name, String.CASE_INSENSITIVE_ORDER));
		return new RawSchematic(hash, subregions);
	}

	/**
	 * Stage 2 (server main thread): resolves registries, computes accurate material counts &
	 * builds the compact chunk-partitioned ProjectRuntime applying root + subregion transforms.
	 */
	public static CompiledProject compile(
		String projectName,
		RawSchematic raw,
		MaterialList.PlacementInfo placement,
		MaterialsStore.ProjectMeta meta
	) {
		Map<String, SubregionOverride> overrides = parseSubregionOverrides(
			placement != null ? placement.subregionsJson : null
		);

		List<RawSubregion> activeSubs = new ArrayList<>(raw.subregions.size());
		for (RawSubregion sub : raw.subregions) {
			SubregionOverride ov = overrides.get(sub.name);
			if (ov != null && !ov.enabled) continue;
			activeSubs.add(sub);
		}

		// Resolve per-subregion palettes and aggregate material counts
		List<ResolvedPaletteEntry[]> resolvedPerSub = new ArrayList<>(activeSubs.size());
		Map<String, Integer> globalCounts = new LinkedHashMap<>();
		Map<String, String> globalNames = new LinkedHashMap<>();
		int grandTotalBlocks = 0;

		for (RawSubregion sub : activeSubs) {
			ResolvedPaletteEntry[] resolved = new ResolvedPaletteEntry[sub.palette.length];
			for (int i = 0; i < sub.palette.length; i++) {
				resolved[i] = resolvePaletteEntry(sub.palette[i]);
			}
			resolvedPerSub.add(resolved);

			int blockCount = sub.paletteIdx.length;
			grandTotalBlocks += blockCount;
			for (int i = 0; i < blockCount; i++) {
				ResolvedPaletteEntry re = resolved[sub.paletteIdx[i] & 0xFFFF];
				if (re.itemWeight > 0 && !re.itemId.isEmpty()) {
					globalCounts.merge(re.itemId, (int) re.itemWeight, Integer::sum);
					globalNames.putIfAbsent(re.itemId, re.itemName);
				}
			}
		}

		List<MaterialEntry> entries = toEntries(globalCounts, globalNames);
		Map<String, Integer> itemIdToEntryIdx = new HashMap<>(entries.size() * 2);
		for (int i = 0; i < entries.size(); i++) {
			itemIdToEntryIdx.put(entries.get(i).itemId, i);
		}

		MaterialList list = new MaterialList(projectName, entries);
		list.hasSchematic = true;
		list.uploadedBy = meta != null ? meta.uploadedBy : null;
		list.totalBlocks = grandTotalBlocks;
		list.placement = placement != null ? placement : new MaterialList.PlacementInfo();
		list.subregions = new ArrayList<>(activeSubs.size());
		for (RawSubregion sub : activeSubs) {
			MaterialList.SubregionInfo info = new MaterialList.SubregionInfo(sub.name, sub.paletteIdx.length, List.of());
			if (meta != null && meta.subregions != null) {
				MaterialsStore.SubregionMeta sm = meta.subregions.get(sub.name);
				if (sm != null) {
					info.status = sm.status != null ? sm.status : "NONE";
					info.claimedBy = sm.claimedBy;
				}
			}
			list.subregions.add(info);
		}

		// If placement isn't configured, we keep 0 bytes of block data in RAM.
		if (list.placement == null || !list.placement.configured || activeSubs.isEmpty()) {
			return new CompiledProject(list, null);
		}

		Identifier dimId = Identifier.tryParse(list.placement.dimension);
		if (dimId == null) {
			return new CompiledProject(list, null);
		}
		RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId);

		// Deduplicate project-wide compact palette
		Map<PaletteDedupKey, Short> paletteDedup = new HashMap<>();
		List<ProjectRuntime.PaletteEntry> compactPalette = new ArrayList<>();

		BlockMirror rootMirror = parseMirror(list.placement.mirror);
		BlockRotation rootRot = parseRotation(list.placement.rotation);
		int rootX = list.placement.x;
		int rootY = list.placement.y;
		int rootZ = list.placement.z;

		boolean multiSub = activeSubs.size() > 1;
		Long2ObjectOpenHashMap<ChunkBuilder> chunkBuilders = new Long2ObjectOpenHashMap<>();
		int compiledBlocks = 0;

		for (int s = 0; s < activeSubs.size(); s++) {
			RawSubregion sub = activeSubs.get(s);
			ResolvedPaletteEntry[] resolved = resolvedPerSub.get(s);
			short[] remappedPalette = new short[resolved.length];
			for (int i = 0; i < resolved.length; i++) {
				ResolvedPaletteEntry re = resolved[i];
				int entryIdx = re.itemId.isEmpty() ? -1 : itemIdToEntryIdx.getOrDefault(re.itemId, -1);
				PaletteDedupKey dk = new PaletteDedupKey(re.block, entryIdx, re.itemWeight, re.checkKind, re.checkParam);
				Short existing = paletteDedup.get(dk);
				if (existing == null) {
					short nextId = (short) compactPalette.size();
					compactPalette.add(new ProjectRuntime.PaletteEntry(re.block, entryIdx, re.itemWeight, re.checkKind, re.checkParam));
					paletteDedup.put(dk, nextId);
					remappedPalette[i] = nextId;
				} else {
					remappedPalette[i] = existing;
				}
			}

			SubregionOverride ov = overrides.get(sub.name);
			int subPosX = ov != null ? ov.x : sub.posX;
			int subPosY = ov != null ? ov.y : sub.posY;
			int subPosZ = ov != null ? ov.z : sub.posZ;
			BlockMirror subMirror = ov != null ? parseMirror(ov.mirror) : BlockMirror.NONE;
			BlockRotation subRot = ov != null ? parseRotation(ov.rotation) : BlockRotation.NONE;
			boolean hasSubTransform = subMirror != BlockMirror.NONE || subRot != BlockRotation.NONE;

			int[] lxArr = sub.lx;
			int[] lyArr = sub.ly;
			int[] lzArr = sub.lz;
			short[] pIdxArr = sub.paletteIdx;
			int count = pIdxArr.length;
			short subId = (short) s;

			for (int i = 0; i < count; i++) {
				int lx = lxArr[i];
				int ly = lyArr[i];
				int lz = lzArr[i];

				int slx = lx;
				int slz = lz;
				if (hasSubTransform) {
					int mx = (subMirror == BlockMirror.FRONT_BACK) ? -lx : lx;
					int mz = (subMirror == BlockMirror.LEFT_RIGHT) ? -lz : lz;
					switch (subRot) {
						case CLOCKWISE_90 -> { slx = -mz; slz = mx; }
						case CLOCKWISE_180 -> { slx = -mx; slz = -mz; }
						case COUNTERCLOCKWISE_90 -> { slx = mz; slz = -mx; }
						default -> { slx = mx; slz = mz; }
					}
				}

				int rx = subPosX + slx;
				int ry = subPosY + ly;
				int rz = subPosZ + slz;

				int mx = (rootMirror == BlockMirror.FRONT_BACK) ? -rx : rx;
				int mz = (rootMirror == BlockMirror.LEFT_RIGHT) ? -rz : rz;
				int tx;
				int tz;
				switch (rootRot) {
					case CLOCKWISE_90 -> { tx = -mz; tz = mx; }
					case CLOCKWISE_180 -> { tx = -mx; tz = -mz; }
					case COUNTERCLOCKWISE_90 -> { tx = mz; tz = -mx; }
					default -> { tx = mx; tz = mz; }
				}

				int wx = rootX + tx;
				int wy = rootY + ry;
				int wz = rootZ + tz;
				if (wy < -2048 || wy > 2047) continue;

				long cKey = ChunkPos.toLong(wx >> 4, wz >> 4);
				ChunkBuilder cb = chunkBuilders.get(cKey);
				if (cb == null) {
					cb = new ChunkBuilder(multiSub);
					chunkBuilders.put(cKey, cb);
				}
				cb.add(ProjectRuntime.packLocalKey(wx, wy, wz), remappedPalette[pIdxArr[i] & 0xFFFF], subId);
				compiledBlocks++;
			}
		}

		Long2ObjectOpenHashMap<ProjectRuntime.ChunkBlueprint> chunks = new Long2ObjectOpenHashMap<>(chunkBuilders.size());
		long[] chunkKeys = new long[chunkBuilders.size()];
		int ki = 0;
		for (var e : chunkBuilders.long2ObjectEntrySet()) {
			long cKey = e.getLongKey();
			chunks.put(cKey, e.getValue().buildSorted(cKey));
			chunkKeys[ki++] = cKey;
		}
		Arrays.sort(chunkKeys);

		String placementKey = MaterialsStore.placementKey(list.placement);
		ProjectRuntime runtime = new ProjectRuntime(
			projectName,
			raw.schematicHash,
			placementKey,
			dimKey,
			compactPalette.toArray(new ProjectRuntime.PaletteEntry[0]),
			chunks,
			chunkKeys,
			compiledBlocks
		);
		return new CompiledProject(list, runtime);
	}

	private static RawPaletteEntry parseRawPaletteEntry(String blockId, NbtCompound props) {
		if (SKIPPED_BLOCKS.contains(blockId)) {
			return RawPaletteEntry.SKIPPED;
		}
		if ("minecraft:water".equals(blockId) || "minecraft:lava".equals(blockId)) {
			String level = props.getString("level", "0");
			if (!"0".equals(level)) {
				return RawPaletteEntry.SKIPPED;
			}
		}
		String slabType = props.getString("type", "");
		String half = props.getString("half", "");
		String bedPart = props.getString("part", "");
		int candles = parsePositiveInt(props.getString("candles", ""), 1);
		int pickles = parsePositiveInt(props.getString("pickles", ""), 1);
		int eggs = parsePositiveInt(props.getString("eggs", ""), 1);
		int layers = parsePositiveInt(props.getString("layers", ""), 1);
		int flowerAmt = parsePositiveInt(props.getString("flower_amount", ""), 0);
		int segAmt = parsePositiveInt(props.getString("segment_amount", ""), 0);
		int segmentedCount = Math.max(1, Math.max(flowerAmt, segAmt));

		int mf = 0;
		for (String k : MULTIFACE_KEYS) {
			if ("true".equals(props.getString(k, ""))) mf++;
		}
		int multifaceCount = Math.max(1, mf);

		return new RawPaletteEntry(
			blockId,
			false,
			slabType,
			half,
			bedPart,
			candles,
			pickles,
			eggs,
			layers,
			segmentedCount,
			multifaceCount
		);
	}

	private static ResolvedPaletteEntry resolvePaletteEntry(RawPaletteEntry raw) {
		if (raw.skipped) {
			return new ResolvedPaletteEntry(Blocks.AIR, "", "", (byte) 0, ProjectRuntime.CHECK_BLOCK_ONLY, (byte) 0);
		}
		Identifier bid = Identifier.tryParse(raw.blockId);
		Block block = bid != null ? Registries.BLOCK.get(bid) : Blocks.AIR;
		String itemId = resolveItemId(raw.blockId, block);
		String itemName = resolveItemName(itemId, raw.blockId);

		byte weight = 1;
		byte checkKind = ProjectRuntime.CHECK_BLOCK_ONLY;
		byte checkParam = 0;

		if (block instanceof FluidBlock) {
			weight = 0;
			checkKind = ProjectRuntime.CHECK_FLUID_SOURCE;
		} else if (block instanceof SlabBlock && "double".equals(raw.slabType)) {
			weight = 2;
			checkKind = ProjectRuntime.CHECK_DOUBLE_SLAB;
		} else if ((block instanceof DoorBlock || block instanceof TallPlantBlock) && "upper".equals(raw.half)) {
			weight = 0;
		} else if (block instanceof BedBlock && "head".equals(raw.bedPart)) {
			weight = 0;
		} else if (block instanceof CandleBlock) {
			weight = (byte) Math.min(4, Math.max(1, raw.candles));
			checkKind = ProjectRuntime.CHECK_CANDLES;
			checkParam = weight;
		} else if (block instanceof SeaPickleBlock) {
			weight = (byte) Math.min(4, Math.max(1, raw.pickles));
			checkKind = ProjectRuntime.CHECK_PICKLES;
			checkParam = weight;
		} else if (block instanceof TurtleEggBlock) {
			weight = (byte) Math.min(4, Math.max(1, raw.eggs));
			checkKind = ProjectRuntime.CHECK_EGGS;
			checkParam = weight;
		} else if (block instanceof SnowBlock) {
			weight = (byte) Math.min(8, Math.max(1, raw.layers));
			checkKind = ProjectRuntime.CHECK_SNOW_LAYERS;
			checkParam = weight;
		} else if (block instanceof Segmented) {
			weight = (byte) Math.min(4, Math.max(1, raw.segmentedCount));
			checkKind = ProjectRuntime.CHECK_SEGMENTED;
			checkParam = weight;
		} else if (block instanceof MultifaceBlock) {
			weight = (byte) Math.min(6, Math.max(1, raw.multifaceCount));
			checkKind = ProjectRuntime.CHECK_MULTIFACE;
			checkParam = weight;
		}

		if (itemId.isEmpty()) weight = 0;
		return new ResolvedPaletteEntry(block, itemId, itemName, weight, checkKind, checkParam);
	}

	private static int parsePositiveInt(String raw, int fallback) {
		if (raw == null || raw.isEmpty()) return fallback;
		try {
			int v = Integer.parseInt(raw);
			return v > 0 ? v : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static Map<String, SubregionOverride> parseSubregionOverrides(String json) {
		if (json == null || json.isBlank() || "[]".equals(json.trim())) return Map.of();
		try {
			List<SubregionOverride> list = GSON.fromJson(json, SUBREGION_OVERRIDE_LIST_TYPE);
			if (list == null || list.isEmpty()) return Map.of();
			Map<String, SubregionOverride> out = new HashMap<>(list.size() * 2);
			for (SubregionOverride ov : list) {
				if (ov != null && ov.name != null && !ov.name.isBlank()) {
					out.put(ov.name, ov);
				}
			}
			return out;
		} catch (Exception e) {
			AeternumCarpet.LOGGER.warn("Could not parse placement subregionsJson", e);
			return Map.of();
		}
	}

	private static String computeHash(byte[] bytes) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(bytes);
			StringBuilder sb = new StringBuilder(16);
			for (int i = 0; i < 8; i++) {
				sb.append(String.format("%02x", digest[i] & 0xFF));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(Arrays.hashCode(bytes)) + "_" + bytes.length;
		}
	}

	private static List<MaterialEntry> toEntries(Map<String, Integer> counts, Map<String, String> names) {
		List<MaterialEntry> entries = new ArrayList<>(counts.size());
		for (var e : counts.entrySet()) {
			entries.add(new MaterialEntry(names.getOrDefault(e.getKey(), e.getKey()), e.getKey(), e.getValue(), 0));
		}
		entries.sort(Comparator.comparingInt((MaterialEntry m) -> m.total).reversed());
		return entries;
	}

	private static String resolveItemId(String blockId, Block block) {
		if ("minecraft:water".equals(blockId) || "minecraft:lava".equals(blockId) || SKIPPED_BLOCKS.contains(blockId)) {
			return "";
		}
		if (block != Blocks.AIR) {
			Item item = block.asItem();
			if (item != Items.AIR) {
				return Registries.ITEM.getId(item).toString();
			}
		}
		return "";
	}

	private static String resolveItemName(String itemId, String blockId) {
		if (!itemId.isEmpty()) {
			Identifier id = Identifier.tryParse(itemId);
			if (id != null) {
				Item item = Registries.ITEM.get(id);
				if (item != Items.AIR) {
					return item.getName().getString();
				}
			}
		}
		String raw = blockId.contains(":") ? blockId.split(":", 2)[1] : blockId;
		String[] parts = raw.split("_");
		StringBuilder sb = new StringBuilder();
		for (String p : parts) {
			if (p.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
		}
		return sb.toString();
	}

	public static BlockPos transform(int rx, int ry, int rz, MaterialList.PlacementInfo placement) {
		BlockMirror mirror = parseMirror(placement.mirror);
		BlockRotation rotation = parseRotation(placement.rotation);

		int mx = mirror == BlockMirror.FRONT_BACK ? -rx : rx;
		int mz = mirror == BlockMirror.LEFT_RIGHT ? -rz : rz;

		int tx;
		int tz;
		switch (rotation) {
			case CLOCKWISE_90 -> { tx = -mz; tz = mx; }
			case CLOCKWISE_180 -> { tx = -mx; tz = -mz; }
			case COUNTERCLOCKWISE_90 -> { tx = mz; tz = -mx; }
			default -> { tx = mx; tz = mz; }
		}
		return new BlockPos(placement.x + tx, placement.y + ry, placement.z + tz);
	}

	public static BlockRotation parseRotation(String s) {
		if (s == null) return BlockRotation.NONE;
		return switch (s) {
			case "CLOCKWISE_90" -> BlockRotation.CLOCKWISE_90;
			case "CLOCKWISE_180" -> BlockRotation.CLOCKWISE_180;
			case "COUNTERCLOCKWISE_90" -> BlockRotation.COUNTERCLOCKWISE_90;
			default -> BlockRotation.NONE;
		};
	}

	public static BlockMirror parseMirror(String s) {
		if (s == null) return BlockMirror.NONE;
		return switch (s) {
			case "LEFT_RIGHT" -> BlockMirror.LEFT_RIGHT;
			case "FRONT_BACK" -> BlockMirror.FRONT_BACK;
			default -> BlockMirror.NONE;
		};
	}

	private static final class ChunkBuilder {
		private final IntArrayList localKeys = new IntArrayList();
		private final ShortArrayList paletteIdx = new ShortArrayList();
		private final ShortArrayList subIdx;

		ChunkBuilder(boolean trackSub) {
			this.subIdx = trackSub ? new ShortArrayList() : null;
		}

		void add(int localKey, short palIdx, short sIdx) {
			localKeys.add(localKey);
			paletteIdx.add(palIdx);
			if (subIdx != null) subIdx.add(sIdx);
		}

		ProjectRuntime.ChunkBlueprint buildSorted(long chunkKey) {
			int[] keys = localKeys.toIntArray();
			short[] pals = paletteIdx.toShortArray();
			short[] subs = subIdx != null ? subIdx.toShortArray() : null;
			int n = keys.length;
			if (n > 1) {
				it.unimi.dsi.fastutil.Arrays.quickSort(
					0,
					n,
					(a, b) -> Integer.compare(keys[a], keys[b]),
					(a, b) -> {
						int tk = keys[a]; keys[a] = keys[b]; keys[b] = tk;
						short tp = pals[a]; pals[a] = pals[b]; pals[b] = tp;
						if (subs != null) {
							short ts = subs[a]; subs[a] = subs[b]; subs[b] = ts;
						}
					}
				);
			}
			return new ProjectRuntime.ChunkBlueprint(chunkKey, keys, pals, subs);
		}
	}

	private record PaletteDedupKey(Block block, int entryIdx, byte weight, byte checkKind, byte checkParam) {}

	private static final class SubregionOverride {
		String name;
		boolean enabled = true;
		int x;
		int y;
		int z;
		String rotation = "NONE";
		String mirror = "NONE";
	}

	private record ResolvedPaletteEntry(
		Block block,
		String itemId,
		String itemName,
		byte itemWeight,
		byte checkKind,
		byte checkParam
	) {}

	public static final class RawPaletteEntry {
		static final RawPaletteEntry SKIPPED = new RawPaletteEntry("minecraft:air", true, "", "", "", 1, 1, 1, 1, 1, 1);

		final String blockId;
		final boolean skipped;
		final String slabType;
		final String half;
		final String bedPart;
		final int candles;
		final int pickles;
		final int eggs;
		final int layers;
		final int segmentedCount;
		final int multifaceCount;

		RawPaletteEntry(
			String blockId,
			boolean skipped,
			String slabType,
			String half,
			String bedPart,
			int candles,
			int pickles,
			int eggs,
			int layers,
			int segmentedCount,
			int multifaceCount
		) {
			this.blockId = blockId;
			this.skipped = skipped;
			this.slabType = slabType;
			this.half = half;
			this.bedPart = bedPart;
			this.candles = candles;
			this.pickles = pickles;
			this.eggs = eggs;
			this.layers = layers;
			this.segmentedCount = segmentedCount;
			this.multifaceCount = multifaceCount;
		}
	}

	public static final class RawSubregion {
		public final String name;
		public final int posX;
		public final int posY;
		public final int posZ;
		public final RawPaletteEntry[] palette;
		public final int[] lx;
		public final int[] ly;
		public final int[] lz;
		public final short[] paletteIdx;

		public RawSubregion(
			String name,
			int posX,
			int posY,
			int posZ,
			RawPaletteEntry[] palette,
			int[] lx,
			int[] ly,
			int[] lz,
			short[] paletteIdx
		) {
			this.name = name;
			this.posX = posX;
			this.posY = posY;
			this.posZ = posZ;
			this.palette = palette;
			this.lx = lx;
			this.ly = ly;
			this.lz = lz;
			this.paletteIdx = paletteIdx;
		}
	}

	public record RawSchematic(String schematicHash, List<RawSubregion> subregions) {}

	public record CompiledProject(MaterialList list, ProjectRuntime runtime) {}
}
