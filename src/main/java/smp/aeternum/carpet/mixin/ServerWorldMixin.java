package smp.aeternum.carpet.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.materials.MaterialsStore;
import smp.aeternum.carpet.perimeters.PerimeterScanner;

@Mixin(net.minecraft.server.world.ServerWorld.class)
public class ServerWorldMixin {
    @Redirect(
        method = "tickIceAndSnow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/biome/Biome;canSetIce(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z"
        )
    )
    private boolean aeternum$applyNaturalIceFormationRule(Biome biome, WorldView world, BlockPos pos) {
        return AeternumCarpetSettings.naturalIceFormation && biome.canSetIce(world, pos);
    }

    @Inject(method = "onBlockStateChanged", at = @At("HEAD"))
    private void aeternum$trackPerimeterDirtyChunk(BlockPos pos, BlockState oldBlock, BlockState newBlock, CallbackInfo ci) {
        ServerWorld self = (ServerWorld) (Object) this;
        PerimeterScanner.onBlockStateChange(self, pos, oldBlock, newBlock);
        MaterialsStore.onWorldBlockChanged(self, pos, oldBlock, newBlock);
    }
}
