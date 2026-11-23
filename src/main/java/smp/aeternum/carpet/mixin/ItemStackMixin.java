package smp.aeternum.carpet.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.shulker.ShulkerStackHelper;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Inject(method = "getMaxCount", at = @At("RETURN"), cancellable = true)
    private void aeternum$stackableEmptyShulkers(CallbackInfoReturnable<Integer> cir) {
        if (!AeternumCarpetSettings.aeStackableShulkers) {
            return;
        }
        ItemStack self = (ItemStack) (Object) this;
        if (ShulkerStackHelper.isEmptyShulker(self)) {
            cir.setReturnValue(64);
        }
    }
}
