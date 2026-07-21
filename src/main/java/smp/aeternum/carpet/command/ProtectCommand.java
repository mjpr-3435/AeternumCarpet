package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.protect.ProtectedBlockStore;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ProtectCommand {
    private ProtectCommand() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("protect").requires(source -> AeternumCarpetSettings.commandProtect && source.hasPermissionLevel(2))
            .then(literal("add").executes(context -> change(context, true, null))
                .then(argument("pos", BlockPosArgumentType.blockPos()).executes(context -> change(context, true, BlockPosArgumentType.getBlockPos(context, "pos")))))
            .then(literal("remove").executes(context -> change(context, false, null))
                .then(argument("pos", BlockPosArgumentType.blockPos()).executes(context -> change(context, false, BlockPosArgumentType.getBlockPos(context, "pos")))))
            .then(literal("list").executes(ProtectCommand::list)));
    }

    private static int change(CommandContext<ServerCommandSource> context, boolean add, BlockPos supplied) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        BlockPos pos = supplied != null ? supplied : lookedAt(player);
        if (pos == null || (add && player.getWorld().getBlockState(pos).isAir())) {
            context.getSource().sendError(Text.literal(add ? "Mira un bloque o indica sus coordenadas." : "Mira el bloque protegido o indica sus coordenadas."));
            return 0;
        }
        boolean changed = add ? ProtectedBlockStore.add(player.getWorld(), pos) : ProtectedBlockStore.remove(player.getWorld(), pos);
        String message = changed
            ? (add ? "Bloque protegido: " : "Protección retirada: ") + pos.toShortString()
            : (add ? "Ese bloque ya estaba protegido." : "Ese bloque no estaba protegido.");
        context.getSource().sendFeedback(() -> Text.empty().append(Text.literal("[Protect] ").formatted(Formatting.DARK_GRAY)).append(Text.literal(message).formatted(changed ? Formatting.AQUA : Formatting.GRAY)), false);
        if (changed) list(context);
        return changed ? 1 : 0;
    }

    private static BlockPos lookedAt(ServerPlayerEntity player) {
        HitResult hit = player.raycast(6.0, 0.0f, false);
        return hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos() : null;
    }

    private static int list(CommandContext<ServerCommandSource> context) {
        var entries = ProtectedBlockStore.all();
        String message = entries.isEmpty() ? "No hay bloques protegidos." : entries.stream().map(entry -> entry.dimension() + " " + entry.x() + ", " + entry.y() + ", " + entry.z()).collect(java.util.stream.Collectors.joining(" | "));
        context.getSource().sendFeedback(() -> Text.empty().append(Text.literal("[Protect] ").formatted(Formatting.DARK_GRAY)).append(Text.literal(message).formatted(entries.isEmpty() ? Formatting.GRAY : Formatting.AQUA)), false);
        return entries.size();
    }
}
