package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.network.CommandPacksNetworking;
import static net.minecraft.server.command.CommandManager.literal;

public final class ActionsCommand {
    private ActionsCommand() { }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("actions").requires(source -> AeternumCarpetSettings.commandActions && source.hasPermissionLevel(2)).executes(context -> {
            ServerPlayerEntity player;
            try { player = context.getSource().getPlayerOrThrow(); }
            catch (Exception exception) { context.getSource().sendError(Text.literal("Este panel solo puede abrirse desde el juego.")); return 0; }
            CommandPacksNetworking.sendSnapshot(player, true); return 1;
        }));
    }
}
