package smp.aeternum.carpet.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import smp.aeternum.carpet.network.BlockerDataPayload;
import smp.aeternum.carpet.network.CommandPacksDataPayload;

public final class AeternumCarpetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockerGuide.register();
        // Guides and list selections belong to this connection only, never to the next /server target.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearBlockerSession());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearBlockerSession());
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> clearBlockerSession());
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
                if (context.client().currentScreen instanceof CommandPacksScreen screen) screen.replaceData(payload.json());
                else if (payload.openScreen()) context.client().setScreen(new CommandPacksScreen(payload.json()));
            })
        );
    }

    private static void clearBlockerSession() {
        BlockerGuide.clear();
        BlockerSelectionStore.clearAll();
    }
}
