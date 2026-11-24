package smp.aeternum.carpet.mixin;

import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.protect.ProtectedBlockStore;
import smp.aeternum.carpet.shulker.ShulkerStackHelper;

/** Stops only pull operations: hoppers may still insert items into a protected inventory. */
@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {
    @Inject(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z", at = @At("HEAD"), cancellable = true)
    private static void aeternum$doNotExtractProtectedInventory(World world, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (!AeternumCarpetSettings.commandProtect) return;
        BlockPos source = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY() + 1.0, hopper.getHopperZ());
        if (ProtectedBlockStore.isProtectedInventory(world, source)) cir.setReturnValue(false);
    }

    @Inject(method = "canMergeItems", at = @At("HEAD"), cancellable = true)
    private static void aeternum$neverStackShulkersInHopper(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
        if (AeternumCarpetSettings.aeStackableShulkersFixes && ShulkerStackHelper.isShulkerBox(first)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void aeternum$extractSingleShulkerFromGround(Inventory to, ItemEntity itemEntity, CallbackInfoReturnable<Boolean> cir) {
        if (!AeternumCarpetSettings.aeStackableShulkersFixes || itemEntity == null) return;
        ItemStack stack = itemEntity.getStack();
        if (stack.getCount() <= 1 || !ShulkerStackHelper.isShulkerBox(stack)) return;

        ItemStack single = stack.copyWithCount(1);
        ItemStack remainder = HopperBlockEntity.transfer(null, to, single, null);
        if (remainder.isEmpty()) {
            ItemStack updated = stack.copy();
            updated.decrement(1);
            if (updated.isEmpty()) {
                itemEntity.setStack(ItemStack.EMPTY);
                itemEntity.discard();
            } else {
                itemEntity.setStack(updated);
            }
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }
}
