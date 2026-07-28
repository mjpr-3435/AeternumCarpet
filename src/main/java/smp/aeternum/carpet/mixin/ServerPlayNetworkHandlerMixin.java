package smp.aeternum.carpet.mixin;

import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.enderchest.InHandEnderChestOpener;
import smp.aeternum.carpet.quickaccess.QuickAccessSubscriptionStore;
import smp.aeternum.carpet.shulker.InHandShulkerOpener;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow @Final public ServerPlayerEntity player;

    @Inject(method = "shouldCheckMovement", at = @At("HEAD"), cancellable = true)
    private void aeternum$allowUnlimitedPlayerSpeed(boolean elytra, CallbackInfoReturnable<Boolean> cir) {
        if (AeternumCarpetSettings.allowUnlimitedPlayerSpeed) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
        method = "onPlayerInteractItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerInteractionManager;interactItem(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;"
        ),
        cancellable = true
    )
    private void aeternum$openInHandContainers(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        boolean shulker = AeternumCarpetSettings.openShulkerInHand && QuickAccessSubscriptionStore.isQuickShulkerEnabled(player.getUuid());
        boolean ender   = AeternumCarpetSettings.openEnderChestInHand && QuickAccessSubscriptionStore.isQuickEnderChestEnabled(player.getUuid());
        Hand openedHand = null;

        if (shulker) { openedHand = InHandShulkerOpener.tryOpenMain(player); }
        if (openedHand == null && ender) { openedHand = InHandEnderChestOpener.tryOpenMain(player); }
        if (openedHand == null && shulker) { openedHand = InHandShulkerOpener.tryOpenOff(player); }
        if (openedHand == null && ender)   { openedHand = InHandEnderChestOpener.tryOpenOff(player); }

        if (openedHand == null) return;
        player.swingHand(openedHand, true);
        ci.cancel();
    }
}
