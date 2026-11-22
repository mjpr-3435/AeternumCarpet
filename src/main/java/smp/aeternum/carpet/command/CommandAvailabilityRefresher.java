package smp.aeternum.carpet.command;

import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.network.ScreenOpenNetworking;
import smp.aeternum.carpet.stats.PlayerRegistryStore;

public final class CommandAvailabilityRefresher {
    private static boolean lastTodoEnabled;
    private static boolean lastExplorationManagerEnabled;
    private static boolean lastExplorationGuardEnabled;
    private static boolean lastQuickShulkerEnabled;
    private static boolean lastQuickEnderChestEnabled;
    private static boolean lastPerimetersEnabled;
    private static boolean lastFinderEnabled;
    private static boolean lastProtectEnabled;
    private static boolean lastActionsEnabled;
    private static boolean lastJoinMotdEnabled;
    private static boolean lastQuickDockEnabled;
    private static boolean lastGuideEnabled;
    private static boolean lastProjectsEnabled;
    private static boolean lastDimensionEnabled;
    private static boolean lastBotInventoryEnabled;
    private static String lastCameraMode;
    private static boolean lastHatEnabled;
    private static boolean lastCarefulBreakEnabled;
    private static boolean lastPlayerSitEnabled;
    private static boolean lastViewInventoryEnabled;
    private static boolean lastStatsEnabled;

    private CommandAvailabilityRefresher() {
    }

    public static void initialize() {
        lastTodoEnabled = AeternumCarpetSettings.todoList;
        lastExplorationManagerEnabled = AeternumCarpetSettings.explorationManager;
        lastExplorationGuardEnabled = AeternumCarpetSettings.allowPlayerExploration;
        lastQuickShulkerEnabled = AeternumCarpetSettings.openShulkerInHand;
        lastQuickEnderChestEnabled = AeternumCarpetSettings.openEnderChestInHand;
        lastPerimetersEnabled = AeternumCarpetSettings.commandPerimeters;
        lastFinderEnabled = AeternumCarpetSettings.commandFinder;
        lastProtectEnabled = AeternumCarpetSettings.commandProtect;
        lastActionsEnabled = AeternumCarpetSettings.commandActions;
        lastJoinMotdEnabled = AeternumCarpetSettings.joinMotd;
        lastQuickDockEnabled = AeternumCarpetSettings.quickDock;
        lastGuideEnabled = AeternumCarpetSettings.commandGuide;
        lastProjectsEnabled = AeternumCarpetSettings.commandProjects;
        lastDimensionEnabled = AeternumCarpetSettings.commandDimension;
        lastBotInventoryEnabled = AeternumCarpetSettings.editBotInventory;
        lastCameraMode = AeternumCarpetSettings.cameraMode;
        lastHatEnabled = AeternumCarpetSettings.commandHat;
        lastCarefulBreakEnabled = AeternumCarpetSettings.carefulBreak;
        lastPlayerSitEnabled = AeternumCarpetSettings.playerSit;
        lastViewInventoryEnabled = AeternumCarpetSettings.commandViewInventory;
        lastStatsEnabled = AeternumCarpetSettings.commandStats;
    }

    public static void tick(MinecraftServer server) {
        boolean todoEnabled = AeternumCarpetSettings.todoList;
        boolean explorationManagerEnabled = AeternumCarpetSettings.explorationManager;
        boolean explorationGuardEnabled = AeternumCarpetSettings.allowPlayerExploration;
        boolean quickShulkerEnabled = AeternumCarpetSettings.openShulkerInHand;
        boolean quickEnderChestEnabled = AeternumCarpetSettings.openEnderChestInHand;
        boolean perimetersEnabled = AeternumCarpetSettings.commandPerimeters;
        boolean finderEnabled = AeternumCarpetSettings.commandFinder;
        boolean protectEnabled = AeternumCarpetSettings.commandProtect;
        boolean actionsEnabled = AeternumCarpetSettings.commandActions;
        boolean joinMotdEnabled = AeternumCarpetSettings.joinMotd;
        boolean quickDockEnabled = AeternumCarpetSettings.quickDock;
        boolean guideEnabled = AeternumCarpetSettings.commandGuide;
        boolean projectsEnabled = AeternumCarpetSettings.commandProjects;
        boolean dimensionEnabled = AeternumCarpetSettings.commandDimension;
        boolean botInventoryEnabled = AeternumCarpetSettings.editBotInventory;
        String cameraMode = AeternumCarpetSettings.cameraMode;
        boolean hatEnabled = AeternumCarpetSettings.commandHat;
        boolean carefulBreakEnabled = AeternumCarpetSettings.carefulBreak;
        boolean playerSitEnabled = AeternumCarpetSettings.playerSit;
        boolean viewInventoryEnabled = AeternumCarpetSettings.commandViewInventory;
        boolean statsEnabled = AeternumCarpetSettings.commandStats;
        if (todoEnabled == lastTodoEnabled
            && explorationManagerEnabled == lastExplorationManagerEnabled
            && explorationGuardEnabled == lastExplorationGuardEnabled
            && quickShulkerEnabled == lastQuickShulkerEnabled
            && quickEnderChestEnabled == lastQuickEnderChestEnabled
            && perimetersEnabled == lastPerimetersEnabled && finderEnabled == lastFinderEnabled && protectEnabled == lastProtectEnabled && actionsEnabled == lastActionsEnabled && joinMotdEnabled == lastJoinMotdEnabled && quickDockEnabled == lastQuickDockEnabled && guideEnabled == lastGuideEnabled && projectsEnabled == lastProjectsEnabled && dimensionEnabled == lastDimensionEnabled && botInventoryEnabled == lastBotInventoryEnabled && Objects.equals(cameraMode, lastCameraMode) && hatEnabled == lastHatEnabled && carefulBreakEnabled == lastCarefulBreakEnabled && playerSitEnabled == lastPlayerSitEnabled && viewInventoryEnabled == lastViewInventoryEnabled && statsEnabled == lastStatsEnabled) {
            return;
        }

        boolean statsTurnedOn = !lastStatsEnabled && statsEnabled;
        lastTodoEnabled = todoEnabled;
        lastExplorationManagerEnabled = explorationManagerEnabled;
        lastExplorationGuardEnabled = explorationGuardEnabled;
        lastQuickShulkerEnabled = quickShulkerEnabled;
        lastQuickEnderChestEnabled = quickEnderChestEnabled;
        lastPerimetersEnabled = perimetersEnabled;
        lastFinderEnabled = finderEnabled;
        lastProtectEnabled = protectEnabled;
        lastActionsEnabled = actionsEnabled;
        lastJoinMotdEnabled = joinMotdEnabled;
        lastQuickDockEnabled = quickDockEnabled;
        lastGuideEnabled = guideEnabled;
        lastProjectsEnabled = projectsEnabled;
        lastDimensionEnabled = dimensionEnabled;
        lastBotInventoryEnabled = botInventoryEnabled;
        lastCameraMode = cameraMode;
        lastHatEnabled = hatEnabled;
        lastCarefulBreakEnabled = carefulBreakEnabled;
        lastPlayerSitEnabled = playerSitEnabled;
        lastViewInventoryEnabled = viewInventoryEnabled;
        lastStatsEnabled = statsEnabled;
        if (statsTurnedOn) {
            PlayerRegistryStore.scanFull(server);
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getPlayerManager().sendCommandTree(player);
        }
        ScreenOpenNetworking.broadcastDockState(server);
    }
}
