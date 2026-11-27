package smp.aeternum.carpet.materials;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.BitSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.MultifaceBlock;
import net.minecraft.block.SeaPickleBlock;
import net.minecraft.block.Segmented;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Compact chunk-partitioned runtime representation of a configured project schematic.
 * Uses sorted primitive arrays per chunk (~6-8 bytes/block) for both O(log k) real-time
 * block updates and fast direct ChunkSection scans without allocating BlockPos instances.
 */
public final class ProjectRuntime {
	public static final byte CHECK_BLOCK_ONLY = 0;
	public static final byte CHECK_DOUBLE_SLAB = 1;
	public static final byte CHECK_CANDLES = 2;
	public static final byte CHECK_PICKLES = 3;
	public static final byte CHECK_EGGS = 4;
	public static final byte CHECK_SNOW_LAYERS = 5;
	public static final byte CHECK_SEGMENTED = 6;
	public static final byte CHECK_MULTIFACE = 7;
	public static final byte CHECK_FLUID_SOURCE = 8;

	private static final Direction[] DIRECTIONS = Direction.values();

	public final String projectName;
	public final String schematicHash;
	public final String placementKey;
	public final RegistryKey<World> dimensionKey;
	public final PaletteEntry[] palette;
	public final Long2ObjectOpenHashMap<ChunkBlueprint> chunks;
	public final long[] chunkKeys;
	public final int totalTemplateBlocks;
	public int verifiedChunkCount;
	public int reconcileCursor;

	public ProjectRuntime(
		String projectName,
		String schematicHash,
		String placementKey,
		RegistryKey<World> dimensionKey,
		PaletteEntry[] palette,
		Long2ObjectOpenHashMap<ChunkBlueprint> chunks,
		long[] chunkKeys,
		int totalTemplateBlocks
	) {
		this.projectName = projectName;
		this.schematicHash = schematicHash;
		this.placementKey = placementKey;
		this.dimensionKey = dimensionKey;
		this.palette = palette;
		this.chunks = chunks;
		this.chunkKeys = chunkKeys;
		this.totalTemplateBlocks = totalTemplateBlocks;
	}

	public static int packLocalKey(int wx, int wy, int wz) {
		return ((wy + 2048) << 8) | ((wz & 15) << 4) | (wx & 15);
	}

	public static int unpackY(int localKey) {
		return (localKey >>> 8) - 2048;
	}

	public static int unpackLocalX(int localKey) {
		return localKey & 15;
	}

	public static int unpackLocalZ(int localKey) {
		return (localKey >>> 4) & 15;
	}

	public long estimatedBytes() {
		long bytes = 256L + (long) chunkKeys.length * 8L + (long) palette.length * 48L;
		// Approximate fastutil map array overhead (~24 bytes per entry at default load factor)
		bytes += (long) chunks.size() * 64L;
		for (ChunkBlueprint cb : chunks.values()) {
			bytes += cb.estimatedBytes();
		}
		return bytes;
	}

	@FunctionalInterface
	public interface DeltaConsumer {
		void accept(int subIdx, int itemEntryIdx, int itemWeight, int delta);
	}

	public static final class PaletteEntry {
		public final Block expectedBlock;
		public final int itemEntryIdx; // -1 if no item in MaterialList.entries
		public final byte itemWeight;
		public final byte checkKind;
		public final byte checkParam;
		private final IntProperty segmentedProp;

		public PaletteEntry(Block expectedBlock, int itemEntryIdx, byte itemWeight, byte checkKind, byte checkParam) {
			this.expectedBlock = expectedBlock;
			this.itemEntryIdx = itemEntryIdx;
			this.itemWeight = itemWeight;
			this.checkKind = checkKind;
			this.checkParam = checkParam;
			this.segmentedProp = (checkKind == CHECK_SEGMENTED && expectedBlock instanceof Segmented seg)
				? seg.getAmountProperty()
				: null;
		}

		public boolean matches(BlockState state) {
			if (state == null || !state.isOf(expectedBlock)) return false;
			return switch (checkKind) {
				case CHECK_BLOCK_ONLY -> true;
				case CHECK_DOUBLE_SLAB -> state.contains(SlabBlock.TYPE) && state.get(SlabBlock.TYPE) == SlabType.DOUBLE;
				case CHECK_CANDLES -> state.contains(CandleBlock.CANDLES) && state.get(CandleBlock.CANDLES) >= checkParam;
				case CHECK_PICKLES -> state.contains(SeaPickleBlock.PICKLES) && state.get(SeaPickleBlock.PICKLES) >= checkParam;
				case CHECK_EGGS -> state.contains(TurtleEggBlock.EGGS) && state.get(TurtleEggBlock.EGGS) >= checkParam;
				case CHECK_SNOW_LAYERS -> state.contains(SnowBlock.LAYERS) && state.get(SnowBlock.LAYERS) >= checkParam;
				case CHECK_SEGMENTED -> segmentedProp != null && state.contains(segmentedProp) && state.get(segmentedProp) >= checkParam;
				case CHECK_MULTIFACE -> countMultiface(state) >= checkParam;
				case CHECK_FLUID_SOURCE -> state.contains(FluidBlock.LEVEL) && state.get(FluidBlock.LEVEL) == 0;
				default -> true;
			};
		}

		private static int countMultiface(BlockState state) {
			int count = 0;
			for (Direction dir : DIRECTIONS) {
				if (MultifaceBlock.hasDirection(state, dir)) count++;
			}
			return count;
		}
	}

	public static final class ChunkBlueprint {
		public final long chunkKey;
		public final int[] localKeys;       // sorted ascending (groups blocks by ChunkSection Y)
		public final short[] paletteIdx;    // index into ProjectRuntime.palette
		public final short[] subIdx;        // nullable if single subregion (always index 0)
		public final long[] matchedWords;   // 1 bit per block
		public boolean verified;

		public ChunkBlueprint(long chunkKey, int[] localKeys, short[] paletteIdx, short[] subIdx) {
			this.chunkKey = chunkKey;
			this.localKeys = localKeys;
			this.paletteIdx = paletteIdx;
			this.subIdx = subIdx;
			this.matchedWords = new long[(localKeys.length + 63) >>> 6];
		}

		public int size() {
			return localKeys.length;
		}

		public int subregionAt(int i) {
			return subIdx == null ? 0 : (subIdx[i] & 0xFFFF);
		}

		public boolean isMatched(int i) {
			return (matchedWords[i >>> 6] & (1L << (i & 63))) != 0L;
		}

		private void setMatched(int i, boolean value) {
			int word = i >>> 6;
			long bit = 1L << (i & 63);
			if (value) matchedWords[word] |= bit;
			else matchedWords[word] &= ~bit;
		}

		public byte[] exportBits() {
			BitSet bs = BitSet.valueOf(matchedWords);
			return bs.toByteArray();
		}

		public void importBits(byte[] raw, PaletteEntry[] palette, DeltaConsumer deltaConsumer) {
			Arrays.fill(matchedWords, 0L);
			if (raw == null || raw.length == 0) return;
			BitSet bs = BitSet.valueOf(raw);
			int len = localKeys.length;
			for (int i = bs.nextSetBit(0); i >= 0 && i < len; i = bs.nextSetBit(i + 1)) {
				setMatched(i, true);
				PaletteEntry pe = palette[paletteIdx[i] & 0xFFFF];
				deltaConsumer.accept(subregionAt(i), pe.itemEntryIdx, pe.itemWeight, 1);
			}
		}

		public void clearMatches() {
			Arrays.fill(matchedWords, 0L);
			verified = false;
		}

		/**
		 * Fast O(log k) update on a single world block change.
		 * Supports multiple subregions overlapping at the same coordinate.
		 */
		public int onBlockChanged(int localKey, BlockState newState, PaletteEntry[] palette, DeltaConsumer deltaConsumer) {
			int idx = Arrays.binarySearch(localKeys, localKey);
			if (idx < 0) return 0;

			int start = idx;
			while (start > 0 && localKeys[start - 1] == localKey) start--;

			int changes = 0;
			int len = localKeys.length;
			for (int i = start; i < len && localKeys[i] == localKey; i++) {
				PaletteEntry pe = palette[paletteIdx[i] & 0xFFFF];
				boolean matches = pe.matches(newState);
				if (isMatched(i) != matches) {
					setMatched(i, matches);
					deltaConsumer.accept(subregionAt(i), pe.itemEntryIdx, pe.itemWeight, matches ? 1 : -1);
					changes++;
				}
			}
			return changes;
		}

		/**
		 * Linear cache-friendly scan directly against WorldChunk's ChunkSection array.
		 * Avoids BlockPos allocations, skips empty sections, and only resolves section index
		 * when crossing 16-block Y boundaries.
		 */
		public int verifyAgainstChunk(WorldChunk chunk, PaletteEntry[] palette, DeltaConsumer deltaConsumer) {
			ChunkSection[] sections = chunk.getSectionArray();
			BlockState air = Blocks.AIR.getDefaultState();
			int lastSectionY = Integer.MIN_VALUE;
			ChunkSection currentSection = null;
			boolean currentEmpty = true;

			int changes = 0;
			int len = localKeys.length;
			for (int i = 0; i < len; i++) {
				int key = localKeys[i];
				int y = (key >>> 8) - 2048;
				int sectionY = y >> 4;
				if (sectionY != lastSectionY) {
					lastSectionY = sectionY;
					int secIdx = chunk.getSectionIndex(y);
					if (secIdx >= 0 && secIdx < sections.length) {
						currentSection = sections[secIdx];
						currentEmpty = (currentSection == null || currentSection.isEmpty());
					} else {
						currentSection = null;
						currentEmpty = true;
					}
				}

				BlockState state = currentEmpty
					? air
					: currentSection.getBlockState(key & 15, y & 15, (key >>> 4) & 15);
				PaletteEntry pe = palette[paletteIdx[i] & 0xFFFF];
				boolean matches = !currentEmpty && pe.matches(state);
				if (isMatched(i) != matches) {
					setMatched(i, matches);
					deltaConsumer.accept(subregionAt(i), pe.itemEntryIdx, pe.itemWeight, matches ? 1 : -1);
					changes++;
				}
			}
			return changes;
		}

		public long estimatedBytes() {
			long bytes = 64L
				+ (long) localKeys.length * 4L
				+ (long) paletteIdx.length * 2L
				+ (long) matchedWords.length * 8L;
			if (subIdx != null) bytes += (long) subIdx.length * 2L;
			return bytes;
		}
	}
}
