package smp.aeternum.carpet.config;

public record AllowedGenerationArea(String name, String dimension, int centerX, int centerZ, int size) {
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";

    public boolean hasValidSize() {
        return size > 0;
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

    public int halfBefore() {
        return size / 2;
    }

    public int halfAfter() {
        return size - halfBefore() - 1;
    }

    public int minChunkX() {
        return centerChunkX() - halfBefore();
    }

    public int maxChunkX() {
        return centerChunkX() + halfAfter();
    }

    public int minChunkZ() {
        return centerChunkZ() - halfBefore();
    }

    public int maxChunkZ() {
        return centerChunkZ() + halfAfter();
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        return chunkX >= minChunkX() && chunkX <= maxChunkX() && chunkZ >= minChunkZ() && chunkZ <= maxChunkZ();
    }

    public boolean matchesDimension(String dimension) {
        return normalizedDimension().equals(dimension);
    }
}
