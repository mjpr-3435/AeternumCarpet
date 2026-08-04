package smp.aeternum.carpet.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.AeternumCarpetSettings;

public final class ScreenOpenNetworking {
    private ScreenOpenNetworking() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ScreenOpenRequestPayload.ID, ScreenOpenRequestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ScreenOpenRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            switch (payload.screen()) {
                case "blockers" -> { if (AeternumCarpetSettings.commandBlockers) BlockerNetworking.sendSnapshot(player, true); }
                case "guide" -> { if (AeternumCarpetSettings.commandGuide) GuideNetworking.send(player, true); }
                case "materials" -> { if (AeternumCarpetSettings.commandMaterials) MaterialsNetworking.send(player); }
                case "exploration" -> { if (AeternumCarpetSettings.explorationManager) ExplorationMapNetworking.open(player); }
                case "actions" -> { if (AeternumCarpetSettings.commandActions) CommandPacksNetworking.sendSnapshot(player, true); }
            }
        });
    }
}
