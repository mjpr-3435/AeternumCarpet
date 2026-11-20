package smp.aeternum.carpet.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.bot.BotInventoryScreenHandler;
import smp.aeternum.carpet.bot.BotOpenData;
import smp.aeternum.carpet.client.AeternumCarpetClient;
import smp.aeternum.carpet.client.BotInventoryScreen;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(
        method = "onOpenScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreens;open(Lnet/minecraft/screen/ScreenHandlerType;Lnet/minecraft/client/MinecraftClient;ILnet/minecraft/text/Text;)V"
        ),
        cancellable = true
    )
    private void aeternum$openBotInventoryScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        BotOpenData data = AeternumCarpetClient.consumePendingBotOpen(packet.getSyncId());
        if (data == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        BotInventoryScreenHandler handler = new BotInventoryScreenHandler(packet.getSyncId(), client.player.getInventory(), data);
        client.player.currentScreenHandler = handler;
        client.setScreen(new BotInventoryScreen(handler, client.player.getInventory(), packet.getName()));
        ci.cancel();
    }
}
