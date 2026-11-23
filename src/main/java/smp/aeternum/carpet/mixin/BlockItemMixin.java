package smp.aeternum.carpet.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.stats.ScoreboardConfigStore;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"))
    private void aeternum$beforePlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (context != null && context.getWorld() != null && !context.getWorld().isClient) {
            ScoreboardConfigStore.setBlockPos(context.getBlockPos());
        }
    }

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void aeternum$afterPlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        ScoreboardConfigStore.clearBlockPos();
    }
}
