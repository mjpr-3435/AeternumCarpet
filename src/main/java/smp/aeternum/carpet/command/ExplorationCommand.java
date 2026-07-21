package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smp.aeternum.carpet.config.AeternumCarpetConfig;
import smp.aeternum.carpet.config.AllowedGenerationArea;
import smp.aeternum.carpet.exploration.ExplorationPregenerator;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ExplorationCommand {
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
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
            .then(literal("region")
                .then(literal("add")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("size", IntegerArgumentType.integer())
                        .then(argument("name", StringArgumentType.word())
                            .executes(ExplorationCommand::addRegion))))
                .then(literal("del")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("name", StringArgumentType.word())
                        .executes(ExplorationCommand::deleteRegion)))
                .then(literal("list")
                    .executes(ExplorationCommand::listRegions))
                .then(literal("info")
                    .executes(ExplorationCommand::regionInfo)))
            .then(literal("pregen")
                .then(literal("start").requires(source -> source.hasPermissionLevel(2)).executes(context -> { ExplorationPregenerator.start(); context.getSource().sendFeedback(() -> Text.literal("Pregeneracion iniciada.").formatted(Formatting.GRAY), false); return 1; }))
                .then(literal("pause").requires(source -> source.hasPermissionLevel(2)).executes(context -> { ExplorationPregenerator.pause(); context.getSource().sendFeedback(() -> Text.literal("Pregeneracion pausada.").formatted(Formatting.GRAY), false); return 1; }))
                .then(literal("stop").requires(source -> source.hasPermissionLevel(2)).executes(context -> { ExplorationPregenerator.stop(); context.getSource().sendFeedback(() -> Text.literal("Pregeneracion detenida y progreso descartado.").formatted(Formatting.GRAY), false); return 1; }))
                .then(literal("speed").requires(source -> source.hasPermissionLevel(2)).then(argument("chunks_per_minute", IntegerArgumentType.integer(1, 1200)).executes(context -> { int speed=IntegerArgumentType.getInteger(context,"chunks_per_minute"); ExplorationPregenerator.setSpeed(speed); context.getSource().sendFeedback(() -> Text.literal("Maximo de pregeneracion: " + speed + " chunks/minuto.").formatted(Formatting.GRAY), false); return 1; })))
                .then(literal("status").executes(context -> { for (String line : ExplorationPregenerator.statusLines()) context.getSource().sendFeedback(() -> Text.literal(line).formatted(Formatting.GRAY), false); return 1; }))));
    }

    private static int addRegion(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int size = IntegerArgumentType.getInteger(context, "size");
        String name = StringArgumentType.getString(context, "name");
        String dimension = player.getWorld().getRegistryKey().getValue().toString();

        if (size < 0) {
            context.getSource().sendError(Text.literal("El tamano de la region debe ser 0 o mayor."));
            return 0;
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            context.getSource().sendError(Text.literal("El nombre de la region solo puede tener letras, numeros, guiones bajos y guiones medios."));
            return 0;
        }

        AllowedGenerationArea area = new AllowedGenerationArea(name, dimension, player.getBlockX(), player.getBlockZ(), size);
        if (!AeternumCarpetConfig.addAllowedGenerationArea(area)) {
            context.getSource().sendError(Text.literal("Ya existe una region con ese nombre o la region no es valida."));
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Anadiste una region al registro: " + area.name()).formatted(Formatting.GRAY), false);
        sendRegionsList(context.getSource());
        return 1;
    }

    private static int deleteRegion(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        AllowedGenerationArea removedArea = AeternumCarpetConfig.removeAllowedGenerationAreaByName(name);
        if (removedArea == null) {
            context.getSource().sendError(Text.literal("No existe una region con el nombre '" + name + "'."));
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Eliminaste una region del registro: " + removedArea.name()).formatted(Formatting.GRAY), false);
        sendRegionsList(context.getSource());
        return 1;
    }

    private static int listRegions(CommandContext<ServerCommandSource> context) {
        sendRegionsList(context.getSource());
        return 1;
    }

    private static int regionInfo(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int chunkX = Math.floorDiv(player.getBlockX(), 16);
        int chunkZ = Math.floorDiv(player.getBlockZ(), 16);
        String dimension = player.getWorld().getRegistryKey().getValue().toString();

        List<AllowedGenerationArea> containingAreas = AeternumCarpetConfig.findAreasContainingChunk(dimension, chunkX, chunkZ);
        if (containingAreas.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No estas dentro de ninguna region de exploracion registrada.").formatted(Formatting.GRAY), false);
            return 1;
        }

        context.getSource().sendFeedback(() -> Text.literal("Estas son las regiones que contienen tu chunk actual:").formatted(Formatting.GRAY), false);
        for (AllowedGenerationArea area : containingAreas) {
            context.getSource().sendFeedback(() -> buildAreaLine(area), false);
        }
        return 1;
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

    private static Text buildAreaLine(AllowedGenerationArea area) {
        MutableText deleteButton = Text.literal("[x]")
            .setStyle(Style.EMPTY
                .withColor(Formatting.RED)
                .withClickEvent(new ClickEvent.RunCommand("/exploration region del " + area.name()))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Borrar region"))));

        return Text.empty()
            .append(deleteButton)
            .append(Text.literal(" ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("[" + area.name() + ", " + area.normalizedDimension() + ", (" + area.centerX() + ", " + area.centerZ() + "), tamano " + area.size() + "]").formatted(Formatting.GRAY));
    }

    private static void sendRegionsList(ServerCommandSource source) {
        List<AllowedGenerationArea> areas = AeternumCarpetConfig.getAllowedGenerationAreas();
        if (areas.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No hay regiones de exploracion registradas.").formatted(Formatting.GRAY), false);
            return;
        }

        source.sendFeedback(() -> Text.literal("Estas son las regiones de exploracion registradas:").formatted(Formatting.GRAY), false);
        for (AllowedGenerationArea area : areas) {
            source.sendFeedback(() -> buildAreaLine(area), false);
        }
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
