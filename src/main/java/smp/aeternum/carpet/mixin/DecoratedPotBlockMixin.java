package smp.aeternum.carpet.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.AeternumCarpetSettings;

@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotBlockMixin {
	@Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
	private void aeternum$protectFromElytraFirework(World world, BlockState state, BlockHitResult hit, ProjectileEntity projectile, CallbackInfo ci) {
		if (AeternumCarpetSettings.protectedPots && projectile instanceof FireworkRocketEntity) ci.cancel();
	}
}
