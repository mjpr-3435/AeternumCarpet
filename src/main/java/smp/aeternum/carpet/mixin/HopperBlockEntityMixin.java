package smp.aeternum.carpet.mixin;

import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.protect.ProtectedBlockStore;

/** Stops only pull operations: hoppers may still insert items into a protected inventory. */
@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {
    @Inject(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z", at = @At("HEAD"), cancellable = true)
    private static void aeternum$doNotExtractProtectedInventory(World world, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (!AeternumCarpetSettings.commandProtect) return;
        BlockPos source = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY() + 1.0, hopper.getHopperZ());
        if (ProtectedBlockStore.isProtectedInventory(world, source)) cir.setReturnValue(false);
    }
}
