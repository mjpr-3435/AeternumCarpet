package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.motd.JoinMotd;
import static net.minecraft.server.command.CommandManager.literal;

public final class MotdCommand {
    private MotdCommand() { }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("motd").requires(source -> AeternumCarpetSettings.joinMotd).executes(context -> {
            ServerPlayerEntity player;
            try { player = context.getSource().getPlayerOrThrow(); }
            catch (Exception exception) { context.getSource().sendError(Text.literal("Este comando solo puede usarse dentro del juego.")); return 0; }
            JoinMotd.send(player); return 1;
        }));
    }
}
