package smp.aeternum.carpet.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.mining.MiningRuleHandler;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void aeternum$applyMiningRules(BlockState state, CallbackInfoReturnable<Float> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        ItemStack stack = player.getMainHandStack();
        float originalSpeed = cir.getReturnValueF();
        cir.setReturnValue(MiningRuleHandler.getMiningSpeed(player, state, stack, originalSpeed));
    }
}
