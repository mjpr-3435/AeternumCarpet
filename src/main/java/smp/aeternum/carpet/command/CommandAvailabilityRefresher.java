package smp.aeternum.carpet.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.AeternumCarpetSettings;

public final class CommandAvailabilityRefresher {
    private static boolean lastTodoEnabled;
    private static boolean lastExplorationManagerEnabled;
    private static boolean lastExplorationGuardEnabled;
    private static boolean lastQuickShulkerEnabled;
    private static boolean lastQuickEnderChestEnabled;
    private static boolean lastBlockersEnabled;
    private static boolean lastFinderEnabled;
    private static boolean lastProtectEnabled;

    private CommandAvailabilityRefresher() {
    }

    public static void initialize() {
        lastTodoEnabled = AeternumCarpetSettings.commandTodoList;
        lastExplorationManagerEnabled = AeternumCarpetSettings.explorationManager;
        lastExplorationGuardEnabled = AeternumCarpetSettings.allowPlayerExploration;
        lastQuickShulkerEnabled = AeternumCarpetSettings.openShulkerInHand;
        lastQuickEnderChestEnabled = AeternumCarpetSettings.openEnderChestInHand;
        lastBlockersEnabled = AeternumCarpetSettings.commandBlockers;
        lastFinderEnabled = AeternumCarpetSettings.commandFinder;
        lastProtectEnabled = AeternumCarpetSettings.commandProtect;
    }

    public static void tick(MinecraftServer server) {
        boolean todoEnabled = AeternumCarpetSettings.commandTodoList;
        boolean explorationManagerEnabled = AeternumCarpetSettings.explorationManager;
        boolean explorationGuardEnabled = AeternumCarpetSettings.allowPlayerExploration;
        boolean quickShulkerEnabled = AeternumCarpetSettings.openShulkerInHand;
        boolean quickEnderChestEnabled = AeternumCarpetSettings.openEnderChestInHand;
        boolean blockersEnabled = AeternumCarpetSettings.commandBlockers;
        boolean finderEnabled = AeternumCarpetSettings.commandFinder;
        boolean protectEnabled = AeternumCarpetSettings.commandProtect;
        if (todoEnabled == lastTodoEnabled
            && explorationManagerEnabled == lastExplorationManagerEnabled
            && explorationGuardEnabled == lastExplorationGuardEnabled
            && quickShulkerEnabled == lastQuickShulkerEnabled
            && quickEnderChestEnabled == lastQuickEnderChestEnabled
            && blockersEnabled == lastBlockersEnabled && finderEnabled == lastFinderEnabled && protectEnabled == lastProtectEnabled) {
            return;
        }

        lastTodoEnabled = todoEnabled;
        lastExplorationManagerEnabled = explorationManagerEnabled;
        lastExplorationGuardEnabled = explorationGuardEnabled;
        lastQuickShulkerEnabled = quickShulkerEnabled;
        lastQuickEnderChestEnabled = quickEnderChestEnabled;
        lastBlockersEnabled = blockersEnabled;
        lastFinderEnabled = finderEnabled;
        lastProtectEnabled = protectEnabled;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getPlayerManager().sendCommandTree(player);
        }
    }
}
