package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.blockers.BlockerScan;
import smp.aeternum.carpet.blockers.BlockerScanner;
import smp.aeternum.carpet.blockers.BlockerScanStore;
import smp.aeternum.carpet.blockers.BlockerBlockList;
import smp.aeternum.carpet.network.BlockerNetworking;

import java.util.regex.Pattern;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class BlockersCommand {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");

    private BlockersCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("blockers")
            .requires(source -> AeternumCarpetSettings.commandBlockers && source.hasPermissionLevel(2))
            .executes(context -> {
                if (context.getSource().getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                    BlockerNetworking.sendSnapshot(player, true);
                } else {
                    context.getSource().sendFeedback(() -> Text.literal("Este comando abre la interfaz solo para jugadores."), false);
                }
                return 1;
            })
            .then(literal("list")
                .executes(BlockersCommand::listBlocks)
                .then(literal("add").then(argument("block", StringArgumentType.word()).executes(context -> changeBlock(context, true))))
                .then(literal("remove").then(argument("block", StringArgumentType.word()).executes(context -> changeBlock(context, false)))))
            .then(argument("coord1", BlockPosArgumentType.blockPos())
                .then(argument("coord2", BlockPosArgumentType.blockPos())
                    .then(argument("nombre", StringArgumentType.word())
                        .executes(BlockersCommand::start)))));
    }

    private static int listBlocks(CommandContext<ServerCommandSource> context) {
        var blocks = BlockerBlockList.all();
        context.getSource().sendFeedback(() -> Text.empty()
            .append(Text.literal("[Blockers] ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(blocks.isEmpty() ? "La lista está vacía." : "Lista de bloqueadores: " + String.join(", ", blocks)).formatted(blocks.isEmpty() ? Formatting.GRAY : Formatting.AQUA)), false);
        return blocks.size();
    }

    private static int changeBlock(CommandContext<ServerCommandSource> context, boolean add) {
        String raw = StringArgumentType.getString(context, "block");
        Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        if (id == null || !Registries.BLOCK.containsId(id)) {
            context.getSource().sendError(Text.literal("Bloque inválido: " + raw));
            return 0;
        }
        boolean changed = add ? BlockerBlockList.add(id.toString()) : BlockerBlockList.remove(id.toString());
        String text = changed
            ? (add ? "Añadido a la lista: " : "Quitado de la lista: ") + id
            : (add ? "Ese bloque ya estaba en la lista." : "Ese bloque no estaba en la lista.");
        context.getSource().sendFeedback(() -> Text.empty()
            .append(Text.literal("[Blockers] ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(text).formatted(changed ? Formatting.AQUA : Formatting.GRAY)), false);
        return changed ? 1 : 0;
    }

    private static int start(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "nombre").toLowerCase();
        if (!ID.matcher(id).matches()) {
            context.getSource().sendError(Text.literal("El nombre debe usar letras minúsculas, números, _ o -, con máximo 48 caracteres."));
            return 0;
        }
        BlockPos first = BlockPosArgumentType.getBlockPos(context, "coord1");
        BlockPos second = BlockPosArgumentType.getBlockPos(context, "coord2");
        long volume = ((long) Math.abs(second.getX() - first.getX()) + 1)
            * ((long) Math.abs(second.getY() - first.getY()) + 1)
            * ((long) Math.abs(second.getZ() - first.getZ()) + 1);
        if (volume > 32_000_000L) {
            context.getSource().sendError(Text.literal("El área supera el límite de 32.000.000 bloques."));
            return 0;
        }
        ServerWorld world = context.getSource().getWorld();
        if (BlockerScanStore.get(id) != null) {
            context.getSource().sendError(Text.literal("Ya existe el análisis '" + id + "'. Bórralo desde /blockers antes de reutilizar ese id."));
            return 0;
        }
        BlockerScan scan = new BlockerScan(id, world.getRegistryKey(), first, second);
        if (context.getSource().getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity player) {
            scan.requestedBy = player.getUuidAsString();
        }
        BlockerScanStore.put(scan);
        if (!BlockerScanner.start(world, scan)) {
            context.getSource().sendError(Text.literal("Ya hay un análisis activo con ese id."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.empty()
            .append(Text.literal("[Blockers] ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("Analizando ").formatted(Formatting.GRAY))
            .append(Text.literal(id).formatted(Formatting.AQUA))
            .append(Text.literal(" → ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(scan.totalChunks + " chunks").formatted(Formatting.WHITE)), false);
        return 1;
    }

}
