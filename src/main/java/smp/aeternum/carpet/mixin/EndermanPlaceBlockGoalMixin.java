package smp.aeternum.carpet.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.AeternumCarpetSettings;

@Mixin(targets = "net.minecraft.entity.mob.EndermanEntity$PlaceBlockGoal")
public abstract class EndermanPlaceBlockGoalMixin {
    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void aeternum$disablePlace(CallbackInfoReturnable<Boolean> cir) {
        if (!"vanilla".equals(AeternumCarpetSettings.endermanGriefing)) {
            cir.setReturnValue(false);
        }
    }
}
