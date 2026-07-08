package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.quickaccess.QuickAccessSubscriptionStore;

import static net.minecraft.server.command.CommandManager.literal;

public final class QuickAccessCommand {
    private QuickAccessCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("quickshulker")
            .requires(source -> AeternumCarpetSettings.openShulkerInHand)
            .executes(QuickAccessCommand::toggleQuickShulker));

        dispatcher.register(literal("quickenderchest")
            .requires(source -> AeternumCarpetSettings.openEnderChestInHand)
            .executes(QuickAccessCommand::toggleQuickEnderChest));
    }

    private static int toggleQuickShulker(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        boolean enabled = QuickAccessSubscriptionStore.toggleQuickShulker(player.getUuid());
        context.getSource().sendFeedback(
            () -> Text.literal(enabled
                    ? "Quick shulker activado. Ahora puedes abrir shulkers en la mano."
                    : "Quick shulker desactivado.")
                .formatted(Formatting.GRAY),
            false
        );
        return 1;
    }

    private static int toggleQuickEnderChest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        boolean enabled = QuickAccessSubscriptionStore.toggleQuickEnderChest(player.getUuid());
        context.getSource().sendFeedback(
            () -> Text.literal(enabled
                    ? "Quick ender chest activado. Ahora puedes abrir ender chests en la mano."
                    : "Quick ender chest desactivado.")
                .formatted(Formatting.GRAY),
            false
        );
        return 1;
    }
}
