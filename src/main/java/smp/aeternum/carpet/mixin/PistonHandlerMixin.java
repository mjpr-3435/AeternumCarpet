package smp.aeternum.carpet.mixin;

import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.protect.ProtectedBlockStore;

@Mixin(PistonHandler.class)
public abstract class PistonHandlerMixin {
    @Shadow @Final private World world;
    @Shadow public abstract java.util.List<BlockPos> getMovedBlocks();
    @Shadow public abstract java.util.List<BlockPos> getBrokenBlocks();

    @Inject(method = "calculatePush", at = @At("RETURN"), cancellable = true)
    private void aeternum$doNotMoveProtectedBlocks(CallbackInfoReturnable<Boolean> cir) {
        if (!AeternumCarpetSettings.commandProtect || !cir.getReturnValue()) return;
        if (getMovedBlocks().stream().anyMatch(pos -> ProtectedBlockStore.isProtected(world, pos))
            || getBrokenBlocks().stream().anyMatch(pos -> ProtectedBlockStore.isProtected(world, pos))) {
            cir.setReturnValue(false);
        }
    }
}
