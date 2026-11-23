package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import java.io.IOException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.materials.MaterialList;
import smp.aeternum.carpet.materials.MaterialsStore;

public final class MaterialsNetworking {
	private static final Gson GSON = new Gson();
	private MaterialsNetworking() {}

	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(MaterialsDataPayload.ID, MaterialsDataPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MaterialsOverlayPayload.ID, MaterialsOverlayPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ProjectDownloadDataPayload.ID, ProjectDownloadDataPayload.CODEC);

		PayloadTypeRegistry.playC2S().register(MaterialsActionPayload.ID, MaterialsActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MaterialsRefreshPayload.ID, MaterialsRefreshPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MaterialsDeletePayload.ID, MaterialsDeletePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MaterialsScanPayload.ID, MaterialsScanPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ProjectUploadPayload.ID, ProjectUploadPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ProjectSubregionActionPayload.ID, ProjectSubregionActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ProjectPlacementPayload.ID, ProjectPlacementPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ProjectDownloadRequestPayload.ID, ProjectDownloadRequestPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(MaterialsActionPayload.ID, (p, c) -> c.server().execute(() -> {
			if (p.claim()) MaterialsStore.claim(p.listName(), p.itemId(), p.player());
			else MaterialsStore.unclaim(p.listName(), p.itemId());
			broadcast(c.server());
		}));
		ServerPlayNetworking.registerGlobalReceiver(MaterialsRefreshPayload.ID, (p, c) -> c.server().execute(() -> {
			MaterialsStore.reload();
			broadcast(c.server());
		}));
		ServerPlayNetworking.registerGlobalReceiver(MaterialsDeletePayload.ID, (p, c) -> c.server().execute(() -> {
			if (!c.player().hasPermissionLevel(2)) return;
			MaterialsStore.delete(p.listName());
			broadcast(c.server());
		}));
		ServerPlayNetworking.registerGlobalReceiver(MaterialsScanPayload.ID, (p, c) -> c.server().execute(() -> {
			MaterialsStore.forceScan();
			broadcast(c.server());
		}));

		ServerPlayNetworking.registerGlobalReceiver(ProjectUploadPayload.ID, (p, c) -> c.server().execute(() -> {
			ServerPlayerEntity player = c.player();
			if (!player.hasPermissionLevel(2)) {
				player.sendMessage(Text.literal("[Proyectos] Solo administradores pueden subir proyectos.").formatted(Formatting.RED), false);
				return;
			}
			try {
				MaterialList.PlacementInfo placement = p.hasPlacement()
					? new MaterialList.PlacementInfo(p.dimension(), p.x(), p.y(), p.z(), p.rotation(), p.mirror(), p.subregionsJson())
					: new MaterialList.PlacementInfo();
				String savedName = MaterialsStore.uploadProject(p.name(), p.bytes(), player.getName().getString(), placement);
				player.sendMessage(Text.literal("[Proyectos] Proyecto '" + savedName + "' cargado correctamente.").formatted(Formatting.GREEN), false);
				broadcast(c.server());
			} catch (IOException | RuntimeException e) {
				AeternumCarpet.LOGGER.error("Failed to upload project {}", p.name(), e);
				player.sendMessage(Text.literal("[Proyectos] Error al leer el .litematic: " + e.getMessage()).formatted(Formatting.RED), false);
			}
		}));

		ServerPlayNetworking.registerGlobalReceiver(ProjectSubregionActionPayload.ID, (p, c) -> c.server().execute(() -> {
			String playerName = c.player().getName().getString();
			if (MaterialsStore.subregionAction(p.projectName(), p.subregionName(), p.action(), playerName)) {
				broadcast(c.server());
			}
		}));

		ServerPlayNetworking.registerGlobalReceiver(ProjectPlacementPayload.ID, (p, c) -> c.server().execute(() -> {
			if (!c.player().hasPermissionLevel(2)) return;
			MaterialList.PlacementInfo info = new MaterialList.PlacementInfo(
				p.dimension(), p.x(), p.y(), p.z(), p.rotation(), p.mirror()
			);
			if (MaterialsStore.updatePlacement(p.projectName(), info)) {
				broadcast(c.server());
			}
		}));

		ServerPlayNetworking.registerGlobalReceiver(ProjectDownloadRequestPayload.ID, (p, c) -> c.server().execute(() -> {
			ServerPlayerEntity player = c.player();
			MaterialList project = MaterialsStore.get(p.projectName());
			byte[] bytes = MaterialsStore.readSchematicBytes(p.projectName());
			if (project == null || bytes == null) {
				player.sendMessage(Text.literal("[Proyectos] No se encontró el archivo .litematic de '" + p.projectName() + "'.").formatted(Formatting.RED), false);
				return;
			}
			MaterialList.PlacementInfo pl = project.placement != null ? project.placement : new MaterialList.PlacementInfo();
			if (ServerPlayNetworking.canSend(player, ProjectDownloadDataPayload.ID)) {
				ServerPlayNetworking.send(player, new ProjectDownloadDataPayload(
					project.name,
					bytes,
					pl.configured,
					pl.dimension,
					pl.x,
					pl.y,
					pl.z,
					pl.rotation,
					pl.mirror,
					p.loadInLitematica(),
					pl.subregionsJson != null ? pl.subregionsJson : "[]"
				));
			}
		}));
	}

	public static void send(ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, MaterialsDataPayload.ID))
			ServerPlayNetworking.send(player, new MaterialsDataPayload(GSON.toJson(MaterialsStore.all()), true));
		sendOverlay(player);
	}

	private static void send(ServerPlayerEntity player, boolean open) {
		if (ServerPlayNetworking.canSend(player, MaterialsDataPayload.ID))
			ServerPlayNetworking.send(player, new MaterialsDataPayload(GSON.toJson(MaterialsStore.all()), open));
		sendOverlay(player);
	}

	private static void sendOverlay(ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, MaterialsOverlayPayload.ID))
			ServerPlayNetworking.send(player, new MaterialsOverlayPayload(MaterialsStore.overlayJsonFor(player)));
	}

	public static void broadcast(MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) send(p, false);
	}
}
