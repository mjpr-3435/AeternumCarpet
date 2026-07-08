package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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
            context.getSource().sendError(Text.literal("Region size must be 0 or greater."));
            return 0;
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            context.getSource().sendError(Text.literal("Region name may only contain letters, numbers, underscores, and hyphens."));
            return 0;
        }

        AllowedGenerationArea area = new AllowedGenerationArea(name, dimension, player.getBlockX(), player.getBlockZ(), size);
        if (!AeternumCarpetConfig.addAllowedGenerationArea(area)) {
            context.getSource().sendError(Text.literal("A region with that name already exists or the region is invalid."));
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Added region to the registry: " + area.name()), false);
        return 1;
    }

    private static int deleteRegion(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        AllowedGenerationArea removedArea = AeternumCarpetConfig.removeAllowedGenerationAreaByName(name);
        if (removedArea == null) {
            context.getSource().sendError(Text.literal("No region exists with name '" + name + "'."));
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Removed region from the registry: " + removedArea.name()), false);
        return 1;
    }

    private static int listRegions(CommandContext<ServerCommandSource> context) {
        List<AllowedGenerationArea> areas = AeternumCarpetConfig.getAllowedGenerationAreas();
        if (areas.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("There are no registered exploration regions."), false);
            return 1;
        }

        context.getSource().sendFeedback(() -> Text.literal("These are the registered exploration regions:"), false);
        for (AllowedGenerationArea area : areas) {
            context.getSource().sendFeedback(() -> Text.literal(formatAreaSummary(area)), false);
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
            context.getSource().sendFeedback(() -> Text.literal("You are not inside any registered exploration region."), false);
            return 1;
        }

        context.getSource().sendFeedback(() -> Text.literal("These are the regions that contain your current chunk:"), false);
        for (AllowedGenerationArea area : containingAreas) {
            context.getSource().sendFeedback(() -> Text.literal(formatAreaSummary(area)), false);
        }
        return 1;
    }

    private static String formatAreaSummary(AllowedGenerationArea area) {
        return "[" + area.name() + ", (" + area.centerX() + ", " + area.centerZ() + "), " + area.size() + "]";
    }
}
