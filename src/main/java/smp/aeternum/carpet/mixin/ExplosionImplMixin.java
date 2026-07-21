package smp.aeternum.carpet.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.protect.ProtectedBlockStore;

import java.util.List;

@Mixin(ExplosionImpl.class)
public class ExplosionImplMixin {
    @Shadow @Final private ServerWorld world;

    @Inject(method = "destroyBlocks", at = @At("HEAD"))
    private void aeternum$keepProtectedBlocks(List<BlockPos> blocks, CallbackInfo ci) {
        if (AeternumCarpetSettings.commandProtect) blocks.removeIf(pos -> ProtectedBlockStore.isProtected(world, pos));
    }
}
