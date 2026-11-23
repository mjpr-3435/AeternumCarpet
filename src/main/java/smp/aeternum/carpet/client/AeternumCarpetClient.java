package smp.aeternum.carpet.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.google.gson.Gson;
import smp.aeternum.carpet.network.PerimeterDataPayload;
import smp.aeternum.carpet.network.PerimeterHudPayload;
import smp.aeternum.carpet.network.PerimeterNetworking.DataSnapshot;
import smp.aeternum.carpet.network.CommandPacksDataPayload;
import smp.aeternum.carpet.network.GuideDataPayload;
import smp.aeternum.carpet.network.ExplorationMapOpenPayload;
import smp.aeternum.carpet.network.ExplorationMapStatusPayload;
import smp.aeternum.carpet.network.ExplorationMapTilePayload;
import smp.aeternum.carpet.network.MaterialsDataPayload;
import smp.aeternum.carpet.network.MaterialsOverlayPayload;
import smp.aeternum.carpet.network.ProjectDownloadDataPayload;
import smp.aeternum.carpet.network.AreaContourPreviewPayload;
import smp.aeternum.carpet.network.CarpetRulesDataPayload;
import smp.aeternum.carpet.network.PlayerDetailPayload;
import smp.aeternum.carpet.network.ScoreboardDetailPayload;
import smp.aeternum.carpet.network.StatsDataPayload;
import smp.aeternum.carpet.network.DockStatePayload;
import smp.aeternum.carpet.network.BotOpenPayload;
import smp.aeternum.carpet.bot.BotOpenData;

public final class AeternumCarpetClient implements ClientModInitializer {
    private static int pendingBotSyncId = -1;
    private static BotOpenData pendingBotData;

    public static synchronized void setPendingBotOpen(int syncId, BotOpenData data) {
        pendingBotSyncId = syncId;
        pendingBotData = data;
    }

    public static synchronized BotOpenData consumePendingBotOpen(int syncId) {
        if (pendingBotSyncId == syncId && pendingBotData != null) {
            BotOpenData data = pendingBotData;
            pendingBotSyncId = -1;
            pendingBotData = null;
            return data;
        }
        return null;
    }

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BotOpenPayload.ID, (payload, context) ->
            setPendingBotOpen(payload.syncId(), payload.data())
        );
        MaterialsHudOverlay.load();
        PerimeterGuide.register();
        ScreenKeybinds.register();
        // Guides and list selections belong to this connection only, never to the next /server target.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearClientSession(client, false));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearClientSession(client, false));
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> clearClientSession(client, true));
        final Gson perimeterGson = new Gson();
        ClientPlayNetworking.registerGlobalReceiver(PerimeterDataPayload.ID, (payload, context) ->
            context.client().execute(() -> {
                DataSnapshot snap = perimeterGson.fromJson(payload.json(), DataSnapshot.class);
                if (snap != null) PerimeterGuide.reconcile(snap.perimeters);
                if (context.client().currentScreen instanceof PerimetersScreen screen) {
                    screen.replaceData(payload.json(), payload.canDelete());
                } else if (context.client().currentScreen instanceof BlockPalettePickerScreen picker) {
                    picker.onServerSnapshot(payload.json(), payload.canDelete());
                } else if (payload.openScreen()) {
                    context.client().setScreen(new PerimetersScreen(payload.json(), payload.canDelete()));
                }
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(PerimeterHudPayload.ID, (payload, context) ->
            context.client().execute(() -> PerimeterHudOverlay.update(payload.json()))
        );
        ClientPlayNetworking.registerGlobalReceiver(CommandPacksDataPayload.ID, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof CommandPacksScreen screen) screen.replaceData(payload.json(), payload.canEdit());
                else if (payload.openScreen()) context.client().setScreen(new CommandPacksScreen(payload.json(), payload.canEdit()));
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(GuideDataPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof GuideScreen screen) screen.replaceData(payload.json(), payload.canEdit(), payload.lang());
            else if (payload.open()) context.client().setScreen(new GuideScreen(payload.json(), payload.canEdit(), payload.lang()));
        }));
        ClientPlayNetworking.registerGlobalReceiver(ExplorationMapOpenPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof ExplorationMapScreen screen) screen.replaceData(payload);
            else context.client().setScreen(new ExplorationMapScreen(payload));
        }));
        ClientPlayNetworking.registerGlobalReceiver(ExplorationMapTilePayload.ID, (payload, context) -> context.client().execute(() -> { if (context.client().currentScreen instanceof ExplorationMapScreen screen) screen.accept(payload); }));
        ClientPlayNetworking.registerGlobalReceiver(ExplorationMapStatusPayload.ID, (payload, context) -> context.client().execute(() -> { if (context.client().currentScreen instanceof ExplorationMapScreen screen) screen.acceptMspt(payload.mspt()); }));
        ClientPlayNetworking.registerGlobalReceiver(MaterialsDataPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof MaterialsDetailScreen screen) screen.replaceFromJson(payload.json());
            else if (context.client().currentScreen instanceof MaterialsScreen screen) screen.replaceData(payload.json());
            else if (payload.open()) context.client().setScreen(new MaterialsScreen(payload.json()));
        }));
        ClientPlayNetworking.registerGlobalReceiver(MaterialsOverlayPayload.ID, (payload, context) -> context.client().execute(() ->
            MaterialsHudOverlay.update(context.client().player != null ? context.client().player.getName().getString() : "", payload.json())
        ));
        ClientPlayNetworking.registerGlobalReceiver(CarpetRulesDataPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof CarpetRulesScreen screen) screen.replaceData(payload.json(), payload.statusMessage(), payload.statusError());
            else if (payload.openScreen()) context.client().setScreen(new CarpetRulesScreen(payload.json()));
        }));
        ClientPlayNetworking.registerGlobalReceiver(StatsDataPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof StatsScreen screen) screen.replaceData(payload.json(), payload.statusMessage(), payload.statusError());
            else if (payload.openScreen()) context.client().setScreen(new StatsScreen(payload.json()));
        }));
        ClientPlayNetworking.registerGlobalReceiver(ScoreboardDetailPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof StatsScreen screen) {
                screen.replaceScoreboardDetail(payload.objectiveName(), payload.json());
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(AreaContourPreviewPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof StatsScreen screen) {
                screen.acceptAreaContourPreview(payload);
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(PlayerDetailPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof StatsScreen screen) {
                screen.acceptPlayerDetail(payload.uuid(), payload.json());
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(ProjectDownloadDataPayload.ID, (payload, context) -> context.client().execute(() ->
            LitematicaBridge.handleDownload(payload)
        ));
        ClientPlayNetworking.registerGlobalReceiver(DockStatePayload.ID, (payload, context) -> context.client().execute(() -> {
            AeternumDockOverlay.updateState(payload.json());
            if (context.client().currentScreen instanceof MotdTasksScreen screen) {
                screen.syncFromServer(AeternumDockOverlay.cachedTasks());
            }
        }));
    }

    private static void clearClientSession(net.minecraft.client.MinecraftClient client, boolean refreshDock) {
        PerimeterGuide.clear();
        PerimeterHudOverlay.clear();
        MaterialsHudOverlay.clear();
        if (refreshDock) AeternumDockOverlay.onWorldChange(client);
        else AeternumDockOverlay.clearState();
    }
}
