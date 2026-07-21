package smp.aeternum.carpet.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import smp.aeternum.carpet.actions.CommandPackStore;
import smp.aeternum.carpet.actions.CommandRunner;

public final class CommandPacksNetworking {
    private CommandPacksNetworking() { }
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(CommandPacksDataPayload.ID, CommandPacksDataPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CommandPacksUpdatePayload.ID, CommandPacksUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CommandActionRunPayload.ID, CommandActionRunPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CommandPacksUpdatePayload.ID, (payload, context) -> {
            if (!context.player().hasPermissionLevel(2)) return;
            if (!CommandPackStore.replace(payload.json())) context.player().sendMessage(Text.literal("[Actions] Datos rechazados: revisa nombres, comandos y esperas."), false);
            broadcast(context.server());
        });
        ServerPlayNetworking.registerGlobalReceiver(CommandActionRunPayload.ID, (payload, context) -> {
            if (!CommandRunner.start(context.server(), context.player(), payload.packId()))
                context.player().sendMessage(Text.literal("[Actions] Ese paquete no existe o ya se está ejecutando."), false);
        });
    }
    public static void sendSnapshot(ServerPlayerEntity player, boolean open) {
        if (ServerPlayNetworking.canSend(player, CommandPacksDataPayload.ID))
            ServerPlayNetworking.send(player, new CommandPacksDataPayload(CommandPackStore.snapshot(), open, player.hasPermissionLevel(2)));
    }
    public static void broadcast(MinecraftServer server) { for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) sendSnapshot(player, false); }
}
