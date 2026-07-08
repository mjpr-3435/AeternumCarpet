package smp.aeternum.carpet.config;

public record AllowedGenerationArea(String name, String dimension, int centerX, int centerZ, int size) {
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";

    public boolean hasValidSize() {
        return size >= 0;
    }

    public String normalizedDimension() {
        return dimension == null || dimension.isBlank() ? DEFAULT_DIMENSION : dimension;
    }

    public int centerChunkX() {
        return Math.floorDiv(centerX, 16);
    }

    public int centerChunkZ() {
        return Math.floorDiv(centerZ, 16);
    }

    public int minChunkX() {
        return centerChunkX() - size;
    }

    public int maxChunkX() {
        return centerChunkX() + size;
    }

    public int minChunkZ() {
        return centerChunkZ() - size;
    }

    public int maxChunkZ() {
        return centerChunkZ() + size;
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        return chunkX >= minChunkX() && chunkX <= maxChunkX() && chunkZ >= minChunkZ() && chunkZ <= maxChunkZ();
    }

    public boolean matchesDimension(String dimension) {
        return normalizedDimension().equals(dimension);
    }
}
