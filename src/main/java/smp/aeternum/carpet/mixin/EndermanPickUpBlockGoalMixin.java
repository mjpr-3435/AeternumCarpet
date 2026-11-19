package smp.aeternum.carpet.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.AeternumCarpetSettings;

@Mixin(targets = "net.minecraft.entity.mob.EndermanEntity$PickUpBlockGoal")
public abstract class EndermanPickUpBlockGoalMixin {
    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void aeternum$disablePickUp(CallbackInfoReturnable<Boolean> cir) {
        if ("disable".equals(AeternumCarpetSettings.endermanGriefing)) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"
        )
    )
    private boolean aeternum$filterFarmPickUp(BlockState state, TagKey<Block> tag, Operation<Boolean> original) {
        if (!original.call(state, tag)) {
            return false;
        }
        if ("farm_only".equals(AeternumCarpetSettings.endermanGriefing) && BlockTags.ENDERMAN_HOLDABLE.equals(tag)) {
            return aeternum$isFarmCropBlock(state);
        }
        return true;
    }

    @Unique
    private static boolean aeternum$isFarmCropBlock(BlockState state) {
        return state.isOf(Blocks.MELON)
            || state.isOf(Blocks.PUMPKIN)
            || state.isOf(Blocks.CARVED_PUMPKIN)
            || state.isOf(Blocks.CACTUS)
            || state.isOf(Blocks.BROWN_MUSHROOM)
            || state.isOf(Blocks.RED_MUSHROOM)
            || state.isOf(Blocks.CRIMSON_FUNGUS)
            || state.isOf(Blocks.WARPED_FUNGUS);
    }
}
