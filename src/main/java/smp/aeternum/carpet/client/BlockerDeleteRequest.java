package smp.aeternum.carpet.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import smp.aeternum.carpet.network.BlockerDeletePayload;

final class BlockerDeleteRequest {
    private BlockerDeleteRequest() {}
    static void send(String id) { ClientPlayNetworking.send(new BlockerDeletePayload(id)); }
}
