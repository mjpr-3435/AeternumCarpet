package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.network.PerimeterNetworking;
import smp.aeternum.carpet.perimeters.Perimeter;
import smp.aeternum.carpet.perimeters.PerimeterScanner;
import smp.aeternum.carpet.perimeters.PerimeterStore;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PerimeterCommand {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");
    private static final long MAX_SCAN_VOLUME = 200_000_000L;

    private PerimeterCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("perimeter")
            .requires(src -> AeternumCarpetSettings.commandPerimeters)
            .executes(ctx -> {
                if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    PerimeterNetworking.sendSnapshot(player, true);
                } else {
                    ctx.getSource().sendFeedback(() -> Text.literal("Este comando abre la interfaz solo para jugadores."), false);
                }
                return 1;
            })
            .then(argument("name", StringArgumentType.word())
                .then(argument("pos1", BlockPosArgumentType.blockPos())
                    .then(argument("pos2", BlockPosArgumentType.blockPos())
                        .executes(PerimeterCommand::define)))));
    }

    private static int define(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String rawName = StringArgumentType.getString(ctx, "name");
        String id = rawName.toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            ctx.getSource().sendError(Text.literal("El nombre debe usar minúsculas, números, _ o - (máx. 48 caracteres)."));
            return 0;
        }
        BlockPos pos1 = BlockPosArgumentType.getBlockPos(ctx, "pos1");
        BlockPos pos2 = BlockPosArgumentType.getBlockPos(ctx, "pos2");
        long volume = ((long) Math.abs(pos2.getX() - pos1.getX()) + 1)
            * ((long) Math.abs(pos2.getY() - pos1.getY()) + 1)
            * ((long) Math.abs(pos2.getZ() - pos1.getZ()) + 1);
        if (volume > MAX_SCAN_VOLUME) {
            ctx.getSource().sendError(Text.literal("El área supera el límite de 200.000.000 bloques."));
            return 0;
        }

        ServerWorld world = ctx.getSource().getWorld();
        Perimeter existing = PerimeterStore.get(id);
        boolean isNew = existing == null;
        if (isNew) {
            existing = new Perimeter(id, rawName, world.getRegistryKey(), pos1, pos2);
            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                existing.requestedBy = player.getUuidAsString();
            }
        } else {
            existing.setBounds(world.getRegistryKey(), pos1, pos2);
            existing.complete = false;
            existing.scannedChunks = 0;
            existing.clusters.clear();
            existing.totalMatchedBlocks = 0;
        }
        PerimeterStore.put(existing);
        PerimeterNetworking.broadcastAll(ctx.getSource().getServer());

        final String finalName = existing.name;
        final boolean hasPalette = existing.palette != null && !existing.palette.isEmpty();
        ctx.getSource().sendFeedback(() -> Text.empty()
            .append(Text.literal("[Perimeters] ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(isNew ? "Perímetro creado: " : "Límites actualizados: ").formatted(Formatting.GRAY))
            .append(Text.literal(finalName).formatted(Formatting.AQUA))
            .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(hasPalette ? "Ejecutando análisis..." : "Abre /perimeter para armar la paleta.")
                .setStyle(Style.EMPTY.withColor(hasPalette ? Formatting.GREEN : Formatting.WHITE)
                    .withClickEvent(new ClickEvent.RunCommand("/perimeter"))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Abrir Perimeters"))))),
            false);

        if (hasPalette) {
            String requester = ctx.getSource().getEntity() instanceof ServerPlayerEntity p ? p.getUuidAsString() : null;
            PerimeterScanner.start(ctx.getSource().getServer(), existing, requester);
        }
        return 1;
    }
}
