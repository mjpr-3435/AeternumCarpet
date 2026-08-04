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
    public static void registerPayloads(){
        PayloadTypeRegistry.playS2C().register(ExplorationMapOpenPayload.ID,ExplorationMapOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExplorationMapTilePayload.ID,ExplorationMapTilePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExplorationMapStatusPayload.ID,ExplorationMapStatusPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ExplorationMapTileRequestPayload.ID,ExplorationMapTileRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ExplorationMapStatusRequestPayload.ID,ExplorationMapStatusRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ExplorationMapCreateRegionPayload.ID,ExplorationMapCreateRegionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ExplorationMapUpdateRegionPayload.ID,ExplorationMapUpdateRegionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ExplorationMapClearCachePayload.ID,ExplorationMapClearCachePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ExplorationMapMaxRequestsPayload.ID,ExplorationMapMaxRequestsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ExplorationMapTileRequestPayload.ID,(payload,context)->ExplorationMapService.request(context.server(),context.player(),payload.dimension(),payload.regionX(),payload.regionZ()));
        ServerPlayNetworking.registerGlobalReceiver(ExplorationMapStatusRequestPayload.ID,(payload,context)->{if(ServerPlayNetworking.canSend(context.player(),ExplorationMapStatusPayload.ID))ServerPlayNetworking.send(context.player(),new ExplorationMapStatusPayload(context.server().getAverageTickTime()));});
        ServerPlayNetworking.registerGlobalReceiver(ExplorationMapCreateRegionPayload.ID,(p,c)->{if(!valid(c.player(),p.name(),p.minChunkX(),p.minChunkZ(),p.maxChunkX(),p.maxChunkZ()))return;boolean added=AeternumCarpetConfig.addAllowedGenerationArea(new AllowedGenerationArea(p.name(),p.dimension(),p.minChunkX(),p.minChunkZ(),p.maxChunkX(),p.maxChunkZ()));if(added)open(c.player());});
        ServerPlayNetworking.registerGlobalReceiver(ExplorationMapUpdateRegionPayload.ID,(p,c)->{if(!c.player().hasPermissionLevel(2)||p.targetName().isBlank())return;boolean changed;if(p.delete())changed=AeternumCarpetConfig.removeAllowedGenerationAreaByName(p.targetName())!=null;else if(!valid(c.player(),p.name(),p.minChunkX(),p.minChunkZ(),p.maxChunkX(),p.maxChunkZ()))changed=false;else changed=AeternumCarpetConfig.replaceAllowedGenerationArea(p.targetName(),new AllowedGenerationArea(p.name(),p.dimension(),p.minChunkX(),p.minChunkZ(),p.maxChunkX(),p.maxChunkZ()));if(changed)open(c.player());});
        ServerPlayNetworking.registerGlobalReceiver(ExplorationMapClearCachePayload.ID,(p,c)->{if(!c.player().hasPermissionLevel(2))return;if(p.visibleOnly()){for(var t:p.tiles())ExplorationMapService.clearTile(c.server(),p.dimension(),t.x(),t.z());}else{ExplorationMapService.clearCache(c.server(),p.dimension());}open(c.player());});
        ServerPlayNetworking.registerGlobalReceiver(ExplorationMapMaxRequestsPayload.ID,(p,c)->{if(!c.player().hasPermissionLevel(2))return;ExplorationMapNetworking.setMaxRequests(p.value());open(c.player());});
    }
    private static boolean valid(ServerPlayerEntity player,String name,int minCX,int minCZ,int maxCX,int maxCZ){return player.hasPermissionLevel(2)&&minCX<=maxCX&&minCZ<=maxCZ&&(maxCX-minCX)<=100000&&(maxCZ-minCZ)<=100000&&!name.isBlank();}
    public static void open(ServerPlayerEntity player){String dimension=player.getWorld().getRegistryKey().getValue().toString();List<ExplorationMapOpenPayload.Area> areas=AeternumCarpetConfig.getAllowedGenerationAreas().stream().map(ExplorationMapNetworking::area).toList();if(ServerPlayNetworking.canSend(player,ExplorationMapOpenPayload.ID))ServerPlayNetworking.send(player,new ExplorationMapOpenPayload(dimension,Math.floorDiv(player.getBlockX(),16),Math.floorDiv(player.getBlockZ(),16),AeternumCarpetConfig.getMapMaxRequests(),areas,player.getServer()==null?0f:player.getServer().getAverageTickTime()));}
    public static void setMaxRequests(int value){AeternumCarpetConfig.setMapMaxRequests(value);}
    public static int maxRequests(){return AeternumCarpetConfig.getMapMaxRequests();}
    private static ExplorationMapOpenPayload.Area area(AllowedGenerationArea a){return new ExplorationMapOpenPayload.Area(a.name(),a.normalizedDimension(),a.minChunkX(),a.minChunkZ(),a.maxChunkX(),a.maxChunkZ());}
}
