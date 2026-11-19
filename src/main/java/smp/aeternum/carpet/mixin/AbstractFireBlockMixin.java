package smp.aeternum.carpet.mixin;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import smp.aeternum.carpet.AeternumCarpetSettings;

@Mixin(AbstractFireBlock.class)
public final class AbstractFireBlockMixin {
    @Redirect(
        method = "canPlaceAt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;canPlaceAt(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z"
        )
    )
    private static boolean aeternum$oldFlintAndSteelBehavior(BlockState state, WorldView world, BlockPos pos) {
        if (AeternumCarpetSettings.oldFlintAndSteelBehavior) {
            return true;
        }
        return state.canPlaceAt(world, pos);
    }
}
