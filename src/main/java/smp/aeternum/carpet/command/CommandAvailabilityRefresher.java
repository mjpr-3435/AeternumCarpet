package smp.aeternum.carpet.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.AeternumCarpetSettings;

public final class CommandAvailabilityRefresher {
    private static boolean lastTodoEnabled;
    private static boolean lastExplorationEnabled;

    private CommandAvailabilityRefresher() {
    }

    public static void initialize() {
        lastTodoEnabled = AeternumCarpetSettings.commandTodoList;
        lastExplorationEnabled = AeternumCarpetSettings.allowPlayerExploration;
    }

    public static void tick(MinecraftServer server) {
        boolean todoEnabled = AeternumCarpetSettings.commandTodoList;
        boolean explorationEnabled = AeternumCarpetSettings.allowPlayerExploration;
        if (todoEnabled == lastTodoEnabled && explorationEnabled == lastExplorationEnabled) {
            return;
        }

        lastTodoEnabled = todoEnabled;
        lastExplorationEnabled = explorationEnabled;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getPlayerManager().sendCommandTree(player);
        }
    }
}
