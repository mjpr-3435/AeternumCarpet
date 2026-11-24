package smp.aeternum.carpet.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the protocol version advertised by connected AeternumCarpet clients
 * so legacy clients (e.g. 2.0.0) receive friendly chat notices instead of decoder crashes.
 */
public final class ProtocolHandshake {
    public static final String UPDATE_LOCATION = "Discord: Resources/AeternumCarpet";
    private static final Map<UUID, Integer> CLIENT_PROTOCOLS = new ConcurrentHashMap<>();

    private ProtocolHandshake() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ClientHelloPayload.ID, ClientHelloPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ClientHelloPayload.ID, (payload, context) ->
            context.server().execute(() ->
                CLIENT_PROTOCOLS.put(context.player().getUuid(), payload.protocolVersion())
            )
        );
    }

    public static boolean hasVersion(ServerPlayerEntity player, int minVersion) {
        if (player == null) return false;
        return CLIENT_PROTOCOLS.getOrDefault(player.getUuid(), 1) >= minVersion;
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player != null) {
            CLIENT_PROTOCOLS.remove(player.getUuid());
        }
    }

    public static void onServerStopping() {
        CLIENT_PROTOCOLS.clear();
    }
}
