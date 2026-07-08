package smp.aeternum.carpet.mixin;

import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.exploration.PortalBlockBreakHandler;

@Mixin(ProjectileEntity.class)
public class ProjectileEntityMixin {
    @Inject(method = "onCollision", at = @At("HEAD"))
    private void aeternum$breakEndPortalsWithWindCharge(HitResult hitResult, CallbackInfo ci) {
        ProjectileEntity projectile = (ProjectileEntity) (Object) this;
        if (!(projectile instanceof AbstractWindChargeEntity) || !(projectile.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        PortalBlockBreakHandler.breakEndPortalsAround(serverWorld, hitResult.getPos());
    }
}
