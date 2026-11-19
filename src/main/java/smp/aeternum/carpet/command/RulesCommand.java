package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import smp.aeternum.carpet.network.CarpetRulesNetworking;

import static net.minecraft.server.command.CommandManager.literal;

public final class RulesCommand {
    private RulesCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("rules")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> {
                if (context.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    CarpetRulesNetworking.sendSnapshot(player, true);
                } else {
                    context.getSource().sendFeedback(() -> Text.literal("Este comando abre la interfaz solo para jugadores."), false);
                }
                return 1;
            }));
    }
}
