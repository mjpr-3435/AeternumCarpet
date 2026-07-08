package smp.aeternum.carpet.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.mining.MiningRuleHandler;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class AbstractBlockStateMixin {
    @Inject(method = "calcBlockBreakingDelta", at = @At("RETURN"), cancellable = true)
    private void aeternum$applyMiningDeltaRules(PlayerEntity player, BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        BlockState state = (BlockState) (Object) this;
        ItemStack stack = player.getMainHandStack();
        float originalDelta = cir.getReturnValueF();
        cir.setReturnValue(MiningRuleHandler.getBlockBreakingDelta(player, state, stack, world, pos, originalDelta));
    }
}
