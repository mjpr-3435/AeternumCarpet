package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.materials.MaterialsStore;

public final class MaterialsNetworking {
	private static final Gson GSON = new Gson();
	private MaterialsNetworking() {}

	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(MaterialsDataPayload.ID, MaterialsDataPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MaterialsActionPayload.ID, MaterialsActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MaterialsRefreshPayload.ID, MaterialsRefreshPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MaterialsDeletePayload.ID, MaterialsDeletePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MaterialsActionPayload.ID, (p, c) -> {
			if (p.claim()) MaterialsStore.claim(p.listName(), p.itemId(), p.player());
			else MaterialsStore.unclaim(p.listName(), p.itemId());
			broadcast(c.server());
		});
		ServerPlayNetworking.registerGlobalReceiver(MaterialsRefreshPayload.ID, (p, c) -> {
			MaterialsStore.reload();
			broadcast(c.server());
		});
		ServerPlayNetworking.registerGlobalReceiver(MaterialsDeletePayload.ID, (p, c) -> {
			if (!c.player().hasPermissionLevel(2)) return;
			MaterialsStore.delete(p.listName());
			broadcast(c.server());
		});
	}

	public static void send(ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, MaterialsDataPayload.ID))
			ServerPlayNetworking.send(player, new MaterialsDataPayload(GSON.toJson(MaterialsStore.all())));
	}

	public static void broadcast(MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) send(p);
	}
}
