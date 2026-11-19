package smp.aeternum.carpet.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.util.UpdateSuppressionException;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @WrapOperation(
        method = "tickWorlds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;tick(Ljava/util/function/BooleanSupplier;)V"
        )
    )
    private void aeternum$catchWorldTickUpdateSuppression(
        ServerWorld serverWorld,
        BooleanSupplier shouldKeepTicking,
        Operation<Void> original
    ) {
        if ("false".equals(AeternumCarpetSettings.updateSuppressionCrashFix)) {
            original.call(serverWorld, shouldKeepTicking);
            return;
        }
        try {
            original.call(serverWorld, shouldKeepTicking);
        } catch (UpdateSuppressionException e) {
            UpdateSuppressionException.handle((MinecraftServer) (Object) this, e);
        }
    }
}
