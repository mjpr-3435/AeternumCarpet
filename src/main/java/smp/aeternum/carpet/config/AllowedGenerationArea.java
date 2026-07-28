package smp.aeternum.carpet.config;

public record AllowedGenerationArea(String name, String dimension, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
	public static final String DEFAULT_DIMENSION = "minecraft:overworld";

	public boolean hasValidSize() {
		return minChunkX <= maxChunkX && minChunkZ <= maxChunkZ;
	}

	public String normalizedDimension() {
		return dimension == null || dimension.isBlank() ? DEFAULT_DIMENSION : dimension;
	}

	public int centerBlockX() { return (minChunkX * 16 + maxChunkX * 16 + 16) / 2; }
	public int centerBlockZ() { return (minChunkZ * 16 + maxChunkZ * 16 + 16) / 2; }

	public int centerChunkX() { return Math.floorDiv(centerBlockX(), 16); }
	public int centerChunkZ() { return Math.floorDiv(centerBlockZ(), 16); }

	public int sizeChunksX() { return Math.max(0, maxChunkX - minChunkX); }
	public int sizeChunksZ() { return Math.max(0, maxChunkZ - minChunkZ); }
	public int maxRadius() { return (Math.max(sizeChunksX(), sizeChunksZ()) + 1) / 2; }

	public boolean containsChunk(int chunkX, int chunkZ) {
		return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
	}

	public boolean matchesDimension(String dimension) {
		return normalizedDimension().equals(dimension);
	}

	/** Backward-compatible factory from old center+size format. */
	public static AllowedGenerationArea fromCenter(String name, String dimension, int centerX, int centerZ, int size) {
		int cx = Math.floorDiv(centerX, 16), cz = Math.floorDiv(centerZ, 16);
		return new AllowedGenerationArea(name, dimension, cx - size, cz - size, cx + size, cz + size);
	}
}
