package smp.aeternum.carpet.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.AeternumCarpetSettings;

public final class CommandAvailabilityRefresher {
    private static boolean lastTodoEnabled;
    private static boolean lastExplorationEnabled;
    private static boolean lastQuickShulkerEnabled;
    private static boolean lastQuickEnderChestEnabled;

    private CommandAvailabilityRefresher() {
    }

    public static void initialize() {
        lastTodoEnabled = AeternumCarpetSettings.commandTodoList;
        lastExplorationEnabled = AeternumCarpetSettings.allowPlayerExploration;
        lastQuickShulkerEnabled = AeternumCarpetSettings.openShulkerInHand;
        lastQuickEnderChestEnabled = AeternumCarpetSettings.openEnderChestInHand;
    }

    public static void tick(MinecraftServer server) {
        boolean todoEnabled = AeternumCarpetSettings.commandTodoList;
        boolean explorationEnabled = AeternumCarpetSettings.allowPlayerExploration;
        boolean quickShulkerEnabled = AeternumCarpetSettings.openShulkerInHand;
        boolean quickEnderChestEnabled = AeternumCarpetSettings.openEnderChestInHand;
        if (todoEnabled == lastTodoEnabled
            && explorationEnabled == lastExplorationEnabled
            && quickShulkerEnabled == lastQuickShulkerEnabled
            && quickEnderChestEnabled == lastQuickEnderChestEnabled) {
            return;
        }

        lastTodoEnabled = todoEnabled;
        lastExplorationEnabled = explorationEnabled;
        lastQuickShulkerEnabled = quickShulkerEnabled;
        lastQuickEnderChestEnabled = quickEnderChestEnabled;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getPlayerManager().sendCommandTree(player);
        }
    }
}
