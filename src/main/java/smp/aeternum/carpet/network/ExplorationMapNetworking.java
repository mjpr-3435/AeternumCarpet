package smp.aeternum.carpet.network;

import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.config.AeternumCarpetConfig;
import smp.aeternum.carpet.config.AllowedGenerationArea;
import smp.aeternum.carpet.exploration.ExplorationMapService;

public final class ExplorationMapNetworking {
    private ExplorationMapNetworking(){}
    public static void registerPayloads(){PayloadTypeRegistry.playS2C().register(ExplorationMapOpenPayload.ID,ExplorationMapOpenPayload.CODEC);PayloadTypeRegistry.playS2C().register(ExplorationMapTilePayload.ID,ExplorationMapTilePayload.CODEC);PayloadTypeRegistry.playC2S().register(ExplorationMapTileRequestPayload.ID,ExplorationMapTileRequestPayload.CODEC);ServerPlayNetworking.registerGlobalReceiver(ExplorationMapTileRequestPayload.ID,(payload,context)->ExplorationMapService.request(context.server(),context.player(),payload.dimension(),payload.regionX(),payload.regionZ()));}
    public static void open(ServerPlayerEntity player){String dimension=player.getWorld().getRegistryKey().getValue().toString();List<ExplorationMapOpenPayload.Area> areas=AeternumCarpetConfig.getAllowedGenerationAreas().stream().map(ExplorationMapNetworking::area).toList();if(ServerPlayNetworking.canSend(player,ExplorationMapOpenPayload.ID))ServerPlayNetworking.send(player,new ExplorationMapOpenPayload(dimension,Math.floorDiv(player.getBlockX(),16),Math.floorDiv(player.getBlockZ(),16),areas));}
    private static ExplorationMapOpenPayload.Area area(AllowedGenerationArea a){return new ExplorationMapOpenPayload.Area(a.name(),a.normalizedDimension(),a.minChunkX(),a.minChunkZ(),a.maxChunkX(),a.maxChunkZ());}
}
