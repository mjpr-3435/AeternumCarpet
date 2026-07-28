package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smp.aeternum.carpet.config.AeternumCarpetConfig;
import smp.aeternum.carpet.config.AllowedGenerationArea;
import smp.aeternum.carpet.exploration.ExplorationPregenerator;
import smp.aeternum.carpet.network.ExplorationMapNetworking;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ExplorationCommand {
    private static final Path EXPORT_DIRECTORY = FabricLoader.getInstance().getConfigDir();
    private static final List<SelectionExportTarget> EXPORT_TARGETS = List.of(
        new SelectionExportTarget("minecraft:overworld", "ExplorerManagerOverworldSelection.csv", "Overworld"),
        new SelectionExportTarget("minecraft:the_nether", "ExplorerManagerNetherSelection.csv", "Nether"),
        new SelectionExportTarget("minecraft:the_end", "ExplorerManagerEndSelection.csv", "End")
    );

    private ExplorationCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("exploration")
            .requires(source -> smp.aeternum.carpet.AeternumCarpetSettings.explorationManager)
            .then(literal("map")
                .executes(context -> { try { ExplorationMapNetworking.open(context.getSource().getPlayerOrThrow()); return 1; } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { context.getSource().sendError(Text.literal("Este comando solo puede abrirse dentro del juego.")); return 0; } }))
            .then(literal("pregen")
                .then(literal("start").requires(source -> source.hasPermissionLevel(2)).executes(context -> { ExplorationPregenerator.start(); context.getSource().sendFeedback(() -> Text.literal("Pregeneracion iniciada.").formatted(Formatting.GRAY), false); return 1; }))
                .then(literal("pause").requires(source -> source.hasPermissionLevel(2)).executes(context -> { ExplorationPregenerator.pause(); context.getSource().sendFeedback(() -> Text.literal("Pregeneracion pausada.").formatted(Formatting.GRAY), false); return 1; }))
                .then(literal("stop").requires(source -> source.hasPermissionLevel(2)).executes(context -> { ExplorationPregenerator.stop(); context.getSource().sendFeedback(() -> Text.literal("Pregeneracion detenida y progreso descartado.").formatted(Formatting.GRAY), false); return 1; }))
                .then(literal("speed").requires(source -> source.hasPermissionLevel(2)).then(argument("chunks_per_minute", IntegerArgumentType.integer(1, 1200)).executes(context -> { int speed=IntegerArgumentType.getInteger(context,"chunks_per_minute"); ExplorationPregenerator.setSpeed(speed); context.getSource().sendFeedback(() -> Text.literal("Maximo de pregeneracion: " + speed + " chunks/minuto.").formatted(Formatting.GRAY), false); return 1; })))
                .then(literal("status").requires(source -> source.hasPermissionLevel(2)).executes(context -> { for (String line : ExplorationPregenerator.statusLines()) context.getSource().sendFeedback(() -> Text.literal(line).formatted(Formatting.GRAY), false); return 1; }))));
    }

    private static int exportSelection(CommandContext<ServerCommandSource> context) {
        List<String> exportedFiles = new ArrayList<>();

        try {
            Files.createDirectories(EXPORT_DIRECTORY);
            for (SelectionExportTarget exportTarget : EXPORT_TARGETS) {
                Path exportPath = EXPORT_DIRECTORY.resolve(exportTarget.fileName());
                writeSelectionFile(exportPath, collectSelectionEntries(exportTarget.dimension()));
                exportedFiles.add(exportTarget.label() + ": " + exportPath.toAbsolutePath());
            }
        } catch (IOException exception) {
            context.getSource().sendError(Text.literal("No se pudo exportar la seleccion: " + exception.getMessage()));
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Exportaste las selecciones por dimension a:").formatted(Formatting.GRAY), false);
        for (String exportedFile : exportedFiles) {
            context.getSource().sendFeedback(() -> Text.literal(exportedFile).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    private static List<SelectionEntry> collectSelectionEntries(String dimension) {
        TreeSet<SelectionEntry> selectionEntries = new TreeSet<>(Comparator
            .comparingInt(SelectionEntry::regionX)
            .thenComparingInt(SelectionEntry::regionZ)
            .thenComparingInt(SelectionEntry::chunkX)
            .thenComparingInt(SelectionEntry::chunkZ));

        for (AllowedGenerationArea area : AeternumCarpetConfig.findAreasByDimension(dimension)) {
            for (int chunkX = area.minChunkX(); chunkX <= area.maxChunkX(); chunkX++) {
                for (int chunkZ = area.minChunkZ(); chunkZ <= area.maxChunkZ(); chunkZ++) {
                    selectionEntries.add(new SelectionEntry(Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32), chunkX, chunkZ));
                }
            }
        }

        return List.copyOf(selectionEntries);
    }

    private static void writeSelectionFile(Path exportPath, List<SelectionEntry> selectionEntries) throws IOException {
        try (Writer writer = Files.newBufferedWriter(exportPath)) {
            for (SelectionEntry selectionEntry : selectionEntries) {
                writer.write(selectionEntry.toCsvLine());
                writer.write(System.lineSeparator());
            }
        }
    }

    private record SelectionEntry(int regionX, int regionZ, int chunkX, int chunkZ) {
        private String toCsvLine() {
            return regionX + ";" + regionZ + ";" + chunkX + ";" + chunkZ;
        }
    }

    private record SelectionExportTarget(String dimension, String fileName, String label) {
    }
}
