package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.materials.MaterialEntry;
import smp.aeternum.carpet.materials.MaterialList;
import smp.aeternum.carpet.materials.MaterialsStore;

public final class MaterialsNetworking {
	private static final Gson GSON = new Gson();
	private static final int MAX_DATA_JSON_CHARS = 895_000;

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
		PayloadTypeRegistry.playC2S().register(ProjectUploadV2Payload.ID, ProjectUploadV2Payload.CODEC);
		PayloadTypeRegistry.playC2S().register(ProjectSubregionActionPayload.ID, ProjectSubregionActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ProjectPlacementPayload.ID, ProjectPlacementPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ProjectDownloadRequestPayload.ID, ProjectDownloadRequestPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(MaterialsActionPayload.ID, (p, c) -> c.server().execute(() -> {
			ServerPlayerEntity player = c.player();
			String actor = player.getName().getString();
			boolean isOp = player.hasPermissionLevel(2);
			boolean changed = p.claim()
				? MaterialsStore.claim(p.listName(), p.itemId(), actor)
				: MaterialsStore.unclaim(p.listName(), p.itemId(), actor, isOp);
			if (changed) {
				broadcastData(c.server());
				MaterialsStore.syncPlayerOverlay(player, false);
			} else {
				// Reconcile optimistic client UI if rejected (e.g. claimed by someone else)
				sendSnapshot(player, false);
			}
		}));

		ServerPlayNetworking.registerGlobalReceiver(MaterialsRefreshPayload.ID, (p, c) -> c.server().execute(() ->
			MaterialsStore.refresh(c.player())
		));

		ServerPlayNetworking.registerGlobalReceiver(MaterialsDeletePayload.ID, (p, c) -> c.server().execute(() -> {
			if (!c.player().hasPermissionLevel(2)) return;
			MaterialsStore.delete(p.listName());
			broadcastData(c.server());
		}));

		ServerPlayNetworking.registerGlobalReceiver(MaterialsScanPayload.ID, (p, c) -> c.server().execute(() -> {
			MaterialsStore.forceScan();
			broadcastData(c.server());
		}));

		// Legacy 2.0.0 upload guard — keep friendly notice intact
		ServerPlayNetworking.registerGlobalReceiver(ProjectUploadPayload.ID, (p, c) -> c.server().execute(() -> {
			ServerPlayerEntity player = c.player();
			player.closeHandledScreen();
			player.sendMessage(
				Text.literal("[Proyectos] Necesitas actualizar AeternumCarpet a 2.1.0 para subir proyectos (disponible en " + ProtocolHandshake.UPDATE_LOCATION + ").")
					.formatted(Formatting.YELLOW),
				false
			);
		}));

		ServerPlayNetworking.registerGlobalReceiver(ProjectUploadV2Payload.ID, (p, c) -> c.server().execute(() -> {
			ServerPlayerEntity player = c.player();
			if (!player.hasPermissionLevel(2)) {
				player.sendMessage(Text.literal("[Proyectos] Solo administradores pueden subir proyectos.").formatted(Formatting.RED), false);
				return;
			}
			MaterialList.PlacementInfo placement = p.hasPlacement()
				? new MaterialList.PlacementInfo(p.dimension(), p.x(), p.y(), p.z(), p.rotation(), p.mirror(), p.subregionsJson())
				: new MaterialList.PlacementInfo();
			String uploader = player.getName().getString();
			MaterialsStore.uploadProjectAsync(
				p.name(),
				p.bytes(),
				uploader,
				placement,
				savedName -> {
					if (!player.isDisconnected()) {
						player.sendMessage(Text.literal("[Proyectos] Proyecto '" + savedName + "' cargado correctamente.").formatted(Formatting.GREEN), false);
					}
				},
				errorMsg -> {
					if (!player.isDisconnected()) {
						player.sendMessage(Text.literal("[Proyectos] Error al leer el .litematic: " + errorMsg).formatted(Formatting.RED), false);
					}
				}
			);
		}));

		ServerPlayNetworking.registerGlobalReceiver(ProjectSubregionActionPayload.ID, (p, c) -> c.server().execute(() -> {
			ServerPlayerEntity player = c.player();
			String playerName = player.getName().getString();
			boolean isOp = player.hasPermissionLevel(2);
			if (MaterialsStore.subregionAction(p.projectName(), p.subregionName(), p.action(), playerName, isOp)) {
				broadcastData(c.server());
			} else {
				sendSnapshot(player, false);
			}
		}));

		ServerPlayNetworking.registerGlobalReceiver(ProjectPlacementPayload.ID, (p, c) -> c.server().execute(() -> {
			if (!c.player().hasPermissionLevel(2)) return;
			MaterialList.PlacementInfo info = new MaterialList.PlacementInfo(
				p.dimension(), p.x(), p.y(), p.z(), p.rotation(), p.mirror()
			);
			if (MaterialsStore.updatePlacement(p.projectName(), info)) {
				broadcastData(c.server());
			}
		}));

		// Legacy 2.0.0 download/load guard — keep friendly notice intact
		ServerPlayNetworking.registerGlobalReceiver(ProjectDownloadRequestPayload.ID, (p, c) -> c.server().execute(() -> {
			ServerPlayerEntity player = c.player();
			if (!ProtocolHandshake.hasVersion(player, 2)) {
				player.closeHandledScreen();
				player.sendMessage(
					Text.literal("[Proyectos] Necesitas actualizar AeternumCarpet a 2.1.0 para descargar/cargar proyectos en Litematica (está en el canal de " + ProtocolHandshake.UPDATE_LOCATION + ").")
						.formatted(Formatting.YELLOW),
					false
				);
				return;
			}
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

	/** Opens the Projects screen on the player and refreshes their HUD overlay. */
	public static void send(ServerPlayerEntity player) {
		sendSnapshot(player, true);
		MaterialsStore.syncPlayerOverlay(player, true);
	}

	/** Sends compact MaterialsDataPayload to a single player. */
	public static void sendSnapshot(ServerPlayerEntity player, boolean open) {
		if (player == null || player.isDisconnected()) return;
		if (!ServerPlayNetworking.canSend(player, MaterialsDataPayload.ID)) return;
		String json = encodeWireDataJson();
		if (json == null) return;
		ServerPlayNetworking.send(player, new MaterialsDataPayload(json, open));
	}

	/** Serializes MaterialsDataPayload once and broadcasts it to all connected capable clients. */
	public static void broadcastData(MinecraftServer server) {
		if (server == null) return;
		List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
		if (players.isEmpty()) return;

		String json = null;
		MaterialsDataPayload payload = null;
		for (ServerPlayerEntity p : players) {
			if (p.isDisconnected() || !ServerPlayNetworking.canSend(p, MaterialsDataPayload.ID)) continue;
			if (payload == null) {
				json = encodeWireDataJson();
				if (json == null) return;
				payload = new MaterialsDataPayload(json, false);
			}
			ServerPlayNetworking.send(p, payload);
		}
	}

	public static boolean canSendOverlay(ServerPlayerEntity player) {
		return player != null && !player.isDisconnected() && ServerPlayNetworking.canSend(player, MaterialsOverlayPayload.ID);
	}

	public static void sendOverlayJson(ServerPlayerEntity player, String json) {
		if (!canSendOverlay(player) || json == null) return;
		ServerPlayNetworking.send(player, new MaterialsOverlayPayload(json));
	}

	/**
	 * Compact DTO matching MaterialList[] expected by both 2.0.0 and 2.1.0 clients.
	 * Intentionally omits placement/subregionsJson, uploadedBy, scannedAvailable, nextScanAt,
	 * and empty subregion entries which are never read by MaterialsScreen/MaterialsDetailScreen.
	 */
	private static String encodeWireDataJson() {
		Collection<MaterialList> all = MaterialsStore.all();
		List<WireMaterialList> wire = new ArrayList<>(all.size());
		for (MaterialList list : all) {
			List<WireSubregion> wireSubs = new ArrayList<>(list.subregions != null ? list.subregions.size() : 0);
			if (list.subregions != null) {
				for (MaterialList.SubregionInfo s : list.subregions) {
					wireSubs.add(new WireSubregion(s.name, s.totalBlocks, s.placedBlocks, s.status, s.claimedBy));
				}
			}
			wire.add(new WireMaterialList(
				list.name,
				list.hasSchematic,
				list.totalBlocks,
				list.placedBlocks,
				list.entries != null ? list.entries : List.of(),
				wireSubs
			));
		}
		String json = GSON.toJson(wire);
		if (json.length() > MAX_DATA_JSON_CHARS) {
			AeternumCarpet.LOGGER.error(
				"MaterialsDataPayload JSON exceeded safe limit ({} > {} chars); skipping send to prevent client disconnect",
				json.length(), MAX_DATA_JSON_CHARS
			);
			return null;
		}
		return json;
	}

	private static final class WireMaterialList {
		final String name;
		final boolean hasSchematic;
		final int totalBlocks;
		final int placedBlocks;
		final List<MaterialEntry> entries;
		final List<WireSubregion> subregions;

		WireMaterialList(
			String name,
			boolean hasSchematic,
			int totalBlocks,
			int placedBlocks,
			List<MaterialEntry> entries,
			List<WireSubregion> subregions
		) {
			this.name = name;
			this.hasSchematic = hasSchematic;
			this.totalBlocks = totalBlocks;
			this.placedBlocks = placedBlocks;
			this.entries = entries;
			this.subregions = subregions;
		}
	}

	private static final class WireSubregion {
		final String name;
		final int totalBlocks;
		final int placedBlocks;
		final String status;
		final String claimedBy;

		WireSubregion(String name, int totalBlocks, int placedBlocks, String status, String claimedBy) {
			this.name = name;
			this.totalBlocks = totalBlocks;
			this.placedBlocks = placedBlocks;
			this.status = status;
			this.claimedBy = claimedBy;
		}
	}
}
