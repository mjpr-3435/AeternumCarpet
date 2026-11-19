package smp.aeternum.carpet.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import smp.aeternum.carpet.AeternumCarpetSettings;

@Mixin(AbstractFireBlock.class)
public final class AbstractFireBlockMixin {
    @WrapOperation(
        method = "canPlaceAt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;canPlaceAt(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z"
        )
    )
    private static boolean aeternum$oldFlintAndSteelBehavior(
        BlockState state,
        WorldView world,
        BlockPos pos,
        Operation<Boolean> original
    ) {
        if (AeternumCarpetSettings.oldFlintAndSteelBehavior) {
            return true;
        }
        return original.call(state, world, pos);
    }
}
