package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.motd.JoinMotd;
import smp.aeternum.carpet.motd.MotdTaskStore;

public final class ScreenOpenNetworking {
    private static final Gson GSON = new Gson();

    private ScreenOpenNetworking() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(DockStatePayload.ID, DockStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ScreenOpenRequestPayload.ID, ScreenOpenRequestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ScreenOpenRequestPayload.ID, (payload, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            switch (payload.screen()) {
                case "dock" -> sendDockState(player);
                case "blockers" -> { if (AeternumCarpetSettings.commandBlockers) BlockerNetworking.sendSnapshot(player, true); }
                case "guide" -> { if (AeternumCarpetSettings.commandGuide) GuideNetworking.send(player, true); }
                case "materials", "projects" -> { if (AeternumCarpetSettings.commandProjects) MaterialsNetworking.send(player); }
                case "exploration" -> { if (AeternumCarpetSettings.explorationManager) ExplorationMapNetworking.open(player); }
                case "actions" -> { if (AeternumCarpetSettings.commandActions) CommandPacksNetworking.sendSnapshot(player, true); }
                case "rules" -> { if (player.hasPermissionLevel(2)) CarpetRulesNetworking.sendSnapshot(player, true); }
            }
        }));
    }

    public static void sendDockState(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, DockStatePayload.ID)) return;
        DockState state = new DockState();
        state.activeTime = JoinMotd.activeTime();
        state.tasks = MotdTaskStore.all();
        state.isOp = player.hasPermissionLevel(2);
        state.projects = AeternumCarpetSettings.commandProjects;
        state.guide = AeternumCarpetSettings.commandGuide;
        state.actions = AeternumCarpetSettings.commandActions;
        state.blockers = AeternumCarpetSettings.commandBlockers;
        state.exploration = AeternumCarpetSettings.explorationManager;
        ServerPlayNetworking.send(player, new DockStatePayload(GSON.toJson(state)));
    }

    public static void broadcastDockState(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendDockState(player);
        }
    }

    public static final class DockState {
        public String activeTime = "";
        public List<String> tasks = List.of();
        public boolean isOp;
        public boolean projects;
        public boolean guide;
        public boolean actions;
        public boolean blockers;
        public boolean exploration;
    }
}
