package smp.aeternum.carpet.network;

import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.config.AeternumCarpetConfig;
import smp.aeternum.carpet.config.AllowedGenerationArea;
import smp.aeternum.carpet.exploration.ExplorationMapService;

public final class ExplorationMapNetworking {
    private static volatile int maxRequests = 2;
    private ExplorationMapNetworking(){}
    public static void registerPayloads(){PayloadTypeRegistry.playS2C().register(ExplorationMapOpenPayload.ID,ExplorationMapOpenPayload.CODEC);PayloadTypeRegistry.playS2C().register(ExplorationMapTilePayload.ID,ExplorationMapTilePayload.CODEC);PayloadTypeRegistry.playC2S().register(ExplorationMapTileRequestPayload.ID,ExplorationMapTileRequestPayload.CODEC);PayloadTypeRegistry.playC2S().register(ExplorationMapCreateRegionPayload.ID,ExplorationMapCreateRegionPayload.CODEC);PayloadTypeRegistry.playC2S().register(ExplorationMapUpdateRegionPayload.ID,ExplorationMapUpdateRegionPayload.CODEC);ServerPlayNetworking.registerGlobalReceiver(ExplorationMapTileRequestPayload.ID,(payload,context)->ExplorationMapService.request(context.server(),context.player(),payload.dimension(),payload.regionX(),payload.regionZ()));ServerPlayNetworking.registerGlobalReceiver(ExplorationMapCreateRegionPayload.ID,(p,c)->{if(!valid(c.player(),p.name(),p.size()))return;boolean added=AeternumCarpetConfig.addAllowedGenerationArea(new AllowedGenerationArea(p.name(),p.dimension(),p.chunkX()*16+8,p.chunkZ()*16+8,p.size()));if(added)open(c.player());});ServerPlayNetworking.registerGlobalReceiver(ExplorationMapUpdateRegionPayload.ID,(p,c)->{if(!c.player().hasPermissionLevel(2)||p.targetName().isBlank())return;boolean changed;if(p.delete())changed=AeternumCarpetConfig.removeAllowedGenerationAreaByName(p.targetName())!=null;else if(!valid(c.player(),p.name(),p.size()))changed=false;else {AllowedGenerationArea current=AeternumCarpetConfig.getAllowedGenerationAreas().stream().filter(a->a.name().equals(p.targetName())).findFirst().orElse(null);changed=current!=null&&AeternumCarpetConfig.replaceAllowedGenerationArea(p.targetName(),new AllowedGenerationArea(p.name(),current.normalizedDimension(),current.centerX(),current.centerZ(),p.size()));}if(changed)open(c.player());});}
    private static boolean valid(ServerPlayerEntity player,String name,int size){return player.hasPermissionLevel(2)&&size>=0&&size<=100000&&!name.isBlank();}
    public static void open(ServerPlayerEntity player){String dimension=player.getWorld().getRegistryKey().getValue().toString();List<ExplorationMapOpenPayload.Area> areas=AeternumCarpetConfig.getAllowedGenerationAreas().stream().map(ExplorationMapNetworking::area).toList();if(ServerPlayNetworking.canSend(player,ExplorationMapOpenPayload.ID))ServerPlayNetworking.send(player,new ExplorationMapOpenPayload(dimension,Math.floorDiv(player.getBlockX(),16),Math.floorDiv(player.getBlockZ(),16),maxRequests,areas));}
    public static void setMaxRequests(int value){maxRequests=Math.clamp(value,1,16);}
    public static int maxRequests(){return maxRequests;}
    private static ExplorationMapOpenPayload.Area area(AllowedGenerationArea a){return new ExplorationMapOpenPayload.Area(a.name(),a.normalizedDimension(),a.minChunkX(),a.minChunkZ(),a.maxChunkX(),a.maxChunkZ());}
}
