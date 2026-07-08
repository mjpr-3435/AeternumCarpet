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

public final class AeternumCommand {
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private AeternumCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("aeternum")
            .then(literal("region")
                .then(literal("add")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("size", IntegerArgumentType.integer())
                        .then(argument("name", StringArgumentType.word())
                            .executes(AeternumCommand::addRegion))))
                .then(literal("del")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("name", StringArgumentType.word())
                        .executes(AeternumCommand::deleteRegion)))
                .then(literal("info")
                    .executes(AeternumCommand::regionInfo))));
    }

    private static int addRegion(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int size = IntegerArgumentType.getInteger(context, "size");
        String name = StringArgumentType.getString(context, "name");

        if (size <= 0) {
            context.getSource().sendError(Text.literal("Region size must be greater than 0."));
            return 0;
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            context.getSource().sendError(Text.literal("Region name may only contain letters, numbers, underscores, and hyphens."));
            return 0;
        }

        AllowedGenerationArea area = new AllowedGenerationArea(name, player.getBlockX(), player.getBlockZ(), size);
        if (!AeternumCarpetConfig.addAllowedGenerationArea(area)) {
            context.getSource().sendError(Text.literal("A region with that name already exists or the region is invalid."));
            return 0;
        }

        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> Text.literal("Added allowed region '" + area.name() + "'."), false);
        sendAreaDetails(source, area);
        return 1;
    }

    private static int deleteRegion(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        AllowedGenerationArea removedArea = AeternumCarpetConfig.removeAllowedGenerationAreaByName(name);
        if (removedArea == null) {
            context.getSource().sendError(Text.literal("No region exists with name '" + name + "'."));
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Deleted allowed region '" + removedArea.name() + "'."), false);
        return 1;
    }

    private static int regionInfo(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int chunkX = Math.floorDiv(player.getBlockX(), 16);
        int chunkZ = Math.floorDiv(player.getBlockZ(), 16);

        List<AllowedGenerationArea> containingAreas = AeternumCarpetConfig.findAreasContainingChunk(chunkX, chunkZ);
        if (containingAreas.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("Current chunk: (" + chunkX + ", " + chunkZ + ")."), false);
            context.getSource().sendFeedback(() -> Text.literal("You are not inside any allowed generation region."), false);
            return 1;
        }

        context.getSource().sendFeedback(() -> Text.literal("Current chunk: (" + chunkX + ", " + chunkZ + "). Inside " + containingAreas.size() + " allowed region(s)."), false);
        for (AllowedGenerationArea area : containingAreas) {
            sendAreaDetails(context.getSource(), area);
            context.getSource().sendFeedback(() -> Text.literal(
                "minChunkX=" + area.minChunkX()
                    + ", maxChunkX=" + area.maxChunkX()
                    + ", minChunkZ=" + area.minChunkZ()
                    + ", maxChunkZ=" + area.maxChunkZ()
            ), false);
        }
        return 1;
    }

    private static void sendAreaDetails(ServerCommandSource source, AllowedGenerationArea area) {
        source.sendFeedback(() -> Text.literal(
            "name=" + area.name()
                + ", centerX=" + area.centerX()
                + ", centerZ=" + area.centerZ()
                + ", centerChunkX=" + area.centerChunkX()
                + ", centerChunkZ=" + area.centerChunkZ()
                + ", size=" + area.size()
        ), false);
    }
}
