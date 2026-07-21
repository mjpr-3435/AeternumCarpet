package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.network.CommandPacksNetworking;
import smp.aeternum.carpet.actions.CommandPack;
import smp.aeternum.carpet.actions.CommandPackStore;
import smp.aeternum.carpet.actions.CommandRunner;
import static net.minecraft.server.command.CommandManager.literal;

public final class ActionsCommand {
    private ActionsCommand() { }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("actions").requires(source -> AeternumCarpetSettings.commandActions).executes(context -> {
            ServerPlayerEntity player;
            try { player = context.getSource().getPlayerOrThrow(); }
            catch (Exception exception) { context.getSource().sendError(Text.literal("Este panel solo puede abrirse desde el juego.")); return 0; }
            CommandPacksNetworking.sendSnapshot(player, true); return 1;
        }));
    }
    /** Handles the legacy-style !!<full package title> shortcut before chat is broadcast. */
    public static boolean handleChat(ServerPlayerEntity player, String message) {
        if (!AeternumCarpetSettings.commandActions || !message.startsWith("!!")) return false;
        String title = message.substring(2).trim();
        if (title.isEmpty()) return false;
        CommandPack pack = CommandPackStore.getByExactName(title);
        if (pack == null) return false;
        if (!CommandRunner.start(player.getServer(), player, pack.id)) player.sendMessage(Text.literal("[Actions] Ese paquete ya se está ejecutando."), false);
        return true;
    }
}
