package smp.aeternum.carpet.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import smp.aeternum.carpet.network.BlockerDataPayload;
import smp.aeternum.carpet.network.CommandPacksDataPayload;
import smp.aeternum.carpet.network.GuideDataPayload;
import smp.aeternum.carpet.network.ExplorationMapOpenPayload;
import smp.aeternum.carpet.network.ExplorationMapTilePayload;
import smp.aeternum.carpet.network.MaterialsDataPayload;
import smp.aeternum.carpet.network.MaterialsOverlayPayload;

public final class AeternumCarpetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockerGuide.register();
        // Guides and list selections belong to this connection only, never to the next /server target.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearClientSession());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearClientSession());
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> clearClientSession());
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
        ClientPlayNetworking.registerGlobalReceiver(MaterialsDataPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof MaterialsDetailScreen screen) screen.replaceFromJson(payload.json());
            else if (context.client().currentScreen instanceof MaterialsScreen screen) screen.replaceData(payload.json());
            else if (payload.open()) context.client().setScreen(new MaterialsScreen(payload.json()));
        }));
        ClientPlayNetworking.registerGlobalReceiver(MaterialsOverlayPayload.ID, (payload, context) -> context.client().execute(() ->
            MaterialsHudOverlay.update(context.client().player != null ? context.client().player.getName().getString() : "", payload.json())
        ));
    }

    private static void clearClientSession() {
        BlockerGuide.clear();
        BlockerSelectionStore.clearAll();
        MaterialsHudOverlay.clear();
    }
}
