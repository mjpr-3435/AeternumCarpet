package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smp.aeternum.carpet.blockers.BlockerScanStore;

public final class BlockerNetworking {
    private static final Gson GSON = new GsonBuilder().create();

    private BlockerNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(BlockerDataPayload.ID, BlockerDataPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlockerDeletePayload.ID, BlockerDeletePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BlockerDeletePayload.ID, (payload, context) -> {
            if (!context.player().hasPermissionLevel(2)) return;
            if (BlockerScanStore.remove(payload.id())) {
                context.player().sendMessage(Text.empty()
                    .append(Text.literal("[Blockers] ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("Scan eliminado: ").formatted(Formatting.GRAY))
                    .append(Text.literal(payload.id()).formatted(Formatting.AQUA)), false);
                broadcastSnapshot(context.server());
            }
        });
    }

    public static void sendSnapshot(ServerPlayerEntity player, boolean openScreen) {
        if (ServerPlayNetworking.canSend(player, BlockerDataPayload.ID)) {
            ServerPlayNetworking.send(player, new BlockerDataPayload(snapshot(), openScreen, player.hasPermissionLevel(2)));
        }
    }

    public static void broadcastSnapshot(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) sendSnapshot(player, false);
    }

    private static String snapshot() {
        return GSON.toJson(BlockerScanStore.all());
    }
}
