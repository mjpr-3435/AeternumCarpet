package smp.aeternum.carpet.mixin;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.shulker.ShulkerStackHelper;

@Mixin(value = ScreenHandler.class, priority = 500)
public class ScreenHandlerMixin {
    @Inject(method = "calculateComparatorOutput(Lnet/minecraft/inventory/Inventory;)I", at = @At("HEAD"), cancellable = true)
    private static void aeternum$shulkerComparatorOutput(Inventory inventory, CallbackInfoReturnable<Integer> cir) {
        if (!AeternumCarpetSettings.aeStackableShulkersFixes || inventory == null) {
            return;
        }

        boolean hasEmptyShulker = false;
        boolean hasStackedEmptyShulker = false;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (ShulkerStackHelper.isEmptyShulker(stack)) {
                hasEmptyShulker = true;
                if (stack.getCount() > 1) {
                    hasStackedEmptyShulker = true;
                    break;
                }
            }
        }

        // If at least one slot has stacked empty shulkers (count > 1), use stackable (64) format.
        if (!hasEmptyShulker || hasStackedEmptyShulker) {
            return;
        }

        // Otherwise (only unstacked empty shulkers), keep vanilla non-stackable (1) signal strength.
        float f = 0.0F;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                int itemMax = ShulkerStackHelper.isEmptyShulker(stack) ? 1 : stack.getMaxCount();
                int denom = Math.max(1, Math.min(inventory.getMaxCountPerStack(), itemMax));
                f += (float) stack.getCount() / (float) denom;
            }
        }
        f /= (float) Math.max(1, inventory.size());
        cir.setReturnValue(MathHelper.lerpPositive(f, 0, 15));
    }
}
