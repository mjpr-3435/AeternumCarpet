package smp.aeternum.carpet.mixin;

import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.shulker.ShulkerStackHelper;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.lithium.common.hopper.HopperHelper", remap = false)
public class LithiumHopperHelperMixin {
    @Inject(
        method = "tryMoveSingleItem(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/SidedInventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;ILnet/minecraft/util/math/Direction;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void aeternum$neverStackShulkersInLithiumHopper(
        Inventory to,
        SidedInventory toSided,
        ItemStack transferStack,
        ItemStack transferChecker,
        int targetSlot,
        Direction fromDirection,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!AeternumCarpetSettings.aeStackableShulkersFixes) {
            return;
        }
        if (ShulkerStackHelper.isShulkerBox(transferStack) && !to.getStack(targetSlot).isEmpty()) {
            cir.setReturnValue(false);
        }
    }
}
