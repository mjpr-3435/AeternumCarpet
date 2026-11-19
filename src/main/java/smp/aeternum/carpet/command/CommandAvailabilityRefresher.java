package smp.aeternum.carpet.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.network.ScreenOpenNetworking;

public final class CommandAvailabilityRefresher {
    private static boolean lastTodoEnabled;
    private static boolean lastExplorationManagerEnabled;
    private static boolean lastExplorationGuardEnabled;
    private static boolean lastQuickShulkerEnabled;
    private static boolean lastQuickEnderChestEnabled;
    private static boolean lastBlockersEnabled;
    private static boolean lastFinderEnabled;
    private static boolean lastProtectEnabled;
    private static boolean lastActionsEnabled;
    private static boolean lastJoinMotdEnabled;
    private static boolean lastQuickDockEnabled;
    private static boolean lastGuideEnabled;
    private static boolean lastProjectsEnabled;

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
        lastActionsEnabled = AeternumCarpetSettings.commandActions;
        lastJoinMotdEnabled = AeternumCarpetSettings.joinMotd;
        lastQuickDockEnabled = AeternumCarpetSettings.quickDock;
        lastGuideEnabled = AeternumCarpetSettings.commandGuide;
        lastProjectsEnabled = AeternumCarpetSettings.commandProjects;
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
        boolean actionsEnabled = AeternumCarpetSettings.commandActions;
        boolean joinMotdEnabled = AeternumCarpetSettings.joinMotd;
        boolean quickDockEnabled = AeternumCarpetSettings.quickDock;
        boolean guideEnabled = AeternumCarpetSettings.commandGuide;
        boolean projectsEnabled = AeternumCarpetSettings.commandProjects;
        if (todoEnabled == lastTodoEnabled
            && explorationManagerEnabled == lastExplorationManagerEnabled
            && explorationGuardEnabled == lastExplorationGuardEnabled
            && quickShulkerEnabled == lastQuickShulkerEnabled
            && quickEnderChestEnabled == lastQuickEnderChestEnabled
            && blockersEnabled == lastBlockersEnabled && finderEnabled == lastFinderEnabled && protectEnabled == lastProtectEnabled && actionsEnabled == lastActionsEnabled && joinMotdEnabled == lastJoinMotdEnabled && quickDockEnabled == lastQuickDockEnabled && guideEnabled == lastGuideEnabled && projectsEnabled == lastProjectsEnabled) {
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
        lastActionsEnabled = actionsEnabled;
        lastJoinMotdEnabled = joinMotdEnabled;
        lastQuickDockEnabled = quickDockEnabled;
        lastGuideEnabled = guideEnabled;
        lastProjectsEnabled = projectsEnabled;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getPlayerManager().sendCommandTree(player);
        }
        ScreenOpenNetworking.broadcastDockState(server);
    }
}
