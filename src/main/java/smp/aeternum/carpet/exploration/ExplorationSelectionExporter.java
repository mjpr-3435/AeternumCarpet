package smp.aeternum.carpet.exploration;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import smp.aeternum.carpet.config.AeternumCarpetConfig;
import smp.aeternum.carpet.config.AllowedGenerationArea;

/** Keeps the ExplorerManager selection files as a derived view of configured regions. */
public final class ExplorationSelectionExporter {
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir();
    private static final List<Target> TARGETS = List.of(
        new Target("minecraft:overworld", "ExplorerManagerOverworldSelection.csv"),
        new Target("minecraft:the_nether", "ExplorerManagerNetherSelection.csv"),
        new Target("minecraft:the_end", "ExplorerManagerEndSelection.csv")
    );

    private ExplorationSelectionExporter() {}

    public static void exportAll() throws IOException {
        Files.createDirectories(DIRECTORY);
        for (Target target : TARGETS) write(DIRECTORY.resolve(target.fileName()), target.dimension());
    }

    private static void write(Path file, String dimension) throws IOException {
        TreeSet<Entry> entries = new TreeSet<>(Comparator.comparingInt(Entry::regionX).thenComparingInt(Entry::regionZ)
            .thenComparingInt(Entry::chunkX).thenComparingInt(Entry::chunkZ));
        for (AllowedGenerationArea area : AeternumCarpetConfig.findAreasByDimension(dimension)) {
            for (int chunkX = area.minChunkX(); chunkX <= area.maxChunkX(); chunkX++)
                for (int chunkZ = area.minChunkZ(); chunkZ <= area.maxChunkZ(); chunkZ++)
                    entries.add(new Entry(Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32), chunkX, chunkZ));
        }
        try (Writer writer = Files.newBufferedWriter(file)) {
            for (Entry entry : entries) {
                writer.write(entry.regionX + ";" + entry.regionZ + ";" + entry.chunkX + ";" + entry.chunkZ);
                writer.write(System.lineSeparator());
            }
        }
    }

    private record Entry(int regionX, int regionZ, int chunkX, int chunkZ) {}
    private record Target(String dimension, String fileName) {}
}
