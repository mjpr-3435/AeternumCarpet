package smp.aeternum.carpet.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import smp.aeternum.carpet.AeternumCarpetSettings;

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
}
