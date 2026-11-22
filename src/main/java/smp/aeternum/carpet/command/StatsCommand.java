package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.network.StatsNetworking;

import static net.minecraft.server.command.CommandManager.literal;

public final class StatsCommand {
    private StatsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("stats")
            .requires(source -> AeternumCarpetSettings.commandStats && source.hasPermissionLevel(2))
            .executes(context -> {
                if (context.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    StatsNetworking.sendSnapshot(player, true);
                } else {
                    context.getSource().sendFeedback(() -> Text.literal("Este comando abre la interfaz solo para jugadores."), false);
                }
                return 1;
            }));
    }
}
