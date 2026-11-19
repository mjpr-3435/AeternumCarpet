package smp.aeternum.carpet.mixin;

import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.bot.FakePlayerReloadManager;

@Mixin(value = EntityPlayerActionPack.class, remap = false)
public abstract class EntityPlayerActionPackMixin {
    @Shadow @Final private ServerPlayerEntity player;

    @Inject(
        method = {"start", "setSneaking", "setSprinting", "setForward", "setStrafing", "stopMovement", "stopAll"},
        at = @At("RETURN")
    )
    private void aeternum$markDirtyOnAction(CallbackInfoReturnable<EntityPlayerActionPack> cir) {
        if (player instanceof EntityPlayerMPFake) {
            FakePlayerReloadManager.markDirty();
        }
    }
}
