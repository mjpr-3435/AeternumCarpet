package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
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

import java.util.List;
import java.util.regex.Pattern;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ExplorationCommand {
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private ExplorationCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("exploration")
            .requires(source -> !smp.aeternum.carpet.AeternumCarpetSettings.allowPlayerExploration)
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
                    .executes(ExplorationCommand::regionInfo))));
    }

    private static int addRegion(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int size = IntegerArgumentType.getInteger(context, "size");
        String name = StringArgumentType.getString(context, "name");
        String dimension = player.getWorld().getRegistryKey().getValue().toString();

        if (size < 0) {
            context.getSource().sendError(Text.literal("El tamaño de la región debe ser 0 o mayor."));
            return 0;
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            context.getSource().sendError(Text.literal("El nombre de la región solo puede tener letras, números, guiones bajos y guiones medios."));
            return 0;
        }

        AllowedGenerationArea area = new AllowedGenerationArea(name, dimension, player.getBlockX(), player.getBlockZ(), size);
        if (!AeternumCarpetConfig.addAllowedGenerationArea(area)) {
            context.getSource().sendError(Text.literal("Ya existe una región con ese nombre o la región no es válida."));
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Añadiste una región al registro: " + area.name()).formatted(Formatting.GRAY), false);
        context.getSource().sendFeedback(() -> buildAreaLine(area), false);
        return 1;
    }

    private static int deleteRegion(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        AllowedGenerationArea removedArea = AeternumCarpetConfig.removeAllowedGenerationAreaByName(name);
        if (removedArea == null) {
            context.getSource().sendError(Text.literal("No existe una región con el nombre '" + name + "'."));
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Eliminaste una región del registro: " + removedArea.name()).formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int listRegions(CommandContext<ServerCommandSource> context) {
        List<AllowedGenerationArea> areas = AeternumCarpetConfig.getAllowedGenerationAreas();
        if (areas.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No hay regiones de exploración registradas.").formatted(Formatting.GRAY), false);
            return 1;
        }

        context.getSource().sendFeedback(() -> Text.literal("Estas son las regiones de exploración registradas:").formatted(Formatting.GRAY), false);
        for (AllowedGenerationArea area : areas) {
            context.getSource().sendFeedback(() -> buildAreaLine(area), false);
        }
        return 1;
    }

    private static int regionInfo(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int chunkX = Math.floorDiv(player.getBlockX(), 16);
        int chunkZ = Math.floorDiv(player.getBlockZ(), 16);
        String dimension = player.getWorld().getRegistryKey().getValue().toString();

        List<AllowedGenerationArea> containingAreas = AeternumCarpetConfig.findAreasContainingChunk(dimension, chunkX, chunkZ);
        if (containingAreas.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No estás dentro de ninguna región de exploración registrada.").formatted(Formatting.GRAY), false);
            return 1;
        }

        context.getSource().sendFeedback(() -> Text.literal("Estas son las regiones que contienen tu chunk actual:").formatted(Formatting.GRAY), false);
        for (AllowedGenerationArea area : containingAreas) {
            context.getSource().sendFeedback(() -> buildAreaLine(area), false);
        }
        return 1;
    }

    private static Text buildAreaLine(AllowedGenerationArea area) {
        MutableText deleteButton = Text.literal("[x]")
            .setStyle(Style.EMPTY
                .withColor(Formatting.RED)
                .withClickEvent(new ClickEvent.RunCommand("/exploration region del " + area.name()))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Borrar región"))));

        return Text.empty()
            .append(deleteButton)
            .append(Text.literal(" ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("[" + area.name() + ", (" + area.centerX() + ", " + area.centerZ() + "), tamaño " + area.size() + "]").formatted(Formatting.GRAY));
    }
}
