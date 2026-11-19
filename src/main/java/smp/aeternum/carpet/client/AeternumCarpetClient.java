package smp.aeternum.carpet.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import smp.aeternum.carpet.network.BlockerDataPayload;
import smp.aeternum.carpet.network.CommandPacksDataPayload;
import smp.aeternum.carpet.network.GuideDataPayload;
import smp.aeternum.carpet.network.ExplorationMapOpenPayload;
import smp.aeternum.carpet.network.ExplorationMapStatusPayload;
import smp.aeternum.carpet.network.ExplorationMapTilePayload;
import smp.aeternum.carpet.network.MaterialsDataPayload;
import smp.aeternum.carpet.network.MaterialsOverlayPayload;
import smp.aeternum.carpet.network.ProjectDownloadDataPayload;
import smp.aeternum.carpet.network.CarpetRulesDataPayload;
import smp.aeternum.carpet.network.DockStatePayload;
import smp.aeternum.carpet.bot.ModScreenHandlers;

public final class AeternumCarpetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.BOT_INVENTORY, BotInventoryScreen::new);
        MaterialsHudOverlay.load();
        BlockerGuide.register();
        ScreenKeybinds.register();
        // Guides and list selections belong to this connection only, never to the next /server target.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearClientSession(client, false));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearClientSession(client, false));
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> clearClientSession(client, true));
        ClientPlayNetworking.registerGlobalReceiver(BlockerDataPayload.ID, (payload, context) ->
            context.client().execute(() -> {
                BlockerGuide.reconcile(payload.json());
                BlockerSelectionStore.reconcile(payload.json());
                if (context.client().currentScreen instanceof BlockersScreen screen) screen.replaceData(payload.json(), payload.canDelete());
                else if (payload.openScreen()) context.client().setScreen(new BlockersScreen(payload.json(), payload.canDelete()));
            })
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
        BlockerGuide.clear();
        BlockerSelectionStore.clearAll();
        MaterialsHudOverlay.clear();
        if (refreshDock) AeternumDockOverlay.onWorldChange(client);
        else AeternumDockOverlay.clearState();
    }
}
