package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.util.PlayerSitManager;

public final class SitBehaviourCommand {
    private SitBehaviourCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("sitbehaviour")
            .requires(src -> AeternumCarpetSettings.playerSit && src.hasPermissionLevel(2))
            .then(CommandManager.literal("clean")
                .executes(ctx -> clean(ctx.getSource()))
                .then(CommandManager.literal("armor_stands")
                    .executes(ctx -> clean(ctx.getSource())))
                .then(CommandManager.literal("armorstands")
                    .executes(ctx -> clean(ctx.getSource())))));
    }

    private static int clean(ServerCommandSource source) {
        int removed = PlayerSitManager.cleanOrphanedArmorStands(source.getServer());
        source.sendFeedback(
            () -> Text.literal("SitBehaviour: se eliminaron " + removed + " armor stand(s) huérfano(s)."),
            true
        );
        return removed;
    }
}
