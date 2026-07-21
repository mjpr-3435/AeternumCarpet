package smp.aeternum.carpet.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import smp.aeternum.carpet.network.BlockerDataPayload;

public final class AeternumCarpetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockerGuide.register();
        // Guides and list selections belong to this connection only, never to the next /server target.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearBlockerSession());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearBlockerSession());
        ClientPlayNetworking.registerGlobalReceiver(BlockerDataPayload.ID, (payload, context) ->
            context.client().execute(() -> {
                BlockerGuide.reconcile(payload.json());
                BlockerSelectionStore.reconcile(payload.json());
                if (context.client().currentScreen instanceof BlockersScreen screen) screen.replaceData(payload.json(), payload.canDelete());
                else if (payload.openScreen()) context.client().setScreen(new BlockersScreen(payload.json(), payload.canDelete()));
            })
        );
    }

    private static void clearBlockerSession() {
        BlockerGuide.clear();
        BlockerSelectionStore.clearAll();
    }
}
