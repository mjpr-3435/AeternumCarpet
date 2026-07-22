package smp.aeternum.carpet.network;

import com.google.gson.Gson;import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;import net.minecraft.server.MinecraftServer;import net.minecraft.server.network.ServerPlayerEntity;import smp.aeternum.carpet.guides.GuideStore;
public final class GuideNetworking {private static final Gson GSON=new Gson();private GuideNetworking(){}
    public static void registerPayloads(){PayloadTypeRegistry.playS2C().register(GuideDataPayload.ID,GuideDataPayload.CODEC);PayloadTypeRegistry.playC2S().register(GuideEditPayload.ID,GuideEditPayload.CODEC);ServerPlayNetworking.registerGlobalReceiver(GuideEditPayload.ID,(p,c)->{if(!c.player().hasPermissionLevel(2))return;boolean changed=switch(p.operation()){case "save"->GuideStore.save(p.title(),p.value());case "create"->GuideStore.create(p.title());case "rename"->GuideStore.rename(p.title(),p.value());case "delete"->GuideStore.delete(p.title());case "move_up"->GuideStore.move(p.title(),-1);case "move_down"->GuideStore.move(p.title(),1);case "reload"->{GuideStore.reload();yield true;}default->false;};if(changed)broadcast(c.server());});}
    public static void send(ServerPlayerEntity player,boolean open){if(ServerPlayNetworking.canSend(player,GuideDataPayload.ID))ServerPlayNetworking.send(player,new GuideDataPayload(GSON.toJson(GuideStore.all()),open,player.hasPermissionLevel(2)));}
    public static void broadcast(MinecraftServer server){for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())send(p,false);}
}
