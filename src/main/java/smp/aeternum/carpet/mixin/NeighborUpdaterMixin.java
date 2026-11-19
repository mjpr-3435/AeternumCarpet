package smp.aeternum.carpet.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.NeighborUpdater;
import net.minecraft.world.block.WireOrientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.util.UpdateSuppressionException;

@Mixin(NeighborUpdater.class)
public interface NeighborUpdaterMixin {
    @Inject(
        method = "tryNeighborUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/crash/CrashReport;create(Ljava/lang/Throwable;Ljava/lang/String;)Lnet/minecraft/util/crash/CrashReport;"
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void aeternum$catchUpdateSuppression(
        World world,
        BlockState state,
        BlockPos pos,
        Block sourceBlock,
        WireOrientation wireOrientation,
        boolean notify,
        CallbackInfo ci,
        Throwable throwable
    ) {
        if (!"false".equals(AeternumCarpetSettings.updateSuppressionCrashFix)
            && UpdateSuppressionException.isSuppression(throwable)) {
            throw new UpdateSuppressionException(world, pos, throwable);
        }
    }
}
