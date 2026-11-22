package smp.aeternum.carpet.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatHandler;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.stats.ScoreboardConfigStore;

@Mixin(StatHandler.class)
public abstract class StatHandlerMixin {
    @Inject(method = "increaseStat", at = @At("TAIL"))
    private void aeternum$onIncreaseStat(PlayerEntity player, Stat<?> stat, int amount, CallbackInfo ci) {
        if (amount <= 0 || !(player instanceof ServerPlayerEntity serverPlayer) || !ScoreboardConfigStore.hasAnyTracker()) {
            return;
        }
        if (stat.getType() != Stats.USED || !(stat.getValue() instanceof Item item)) {
            return;
        }
        if (isDigTool(item)) {
            ScoreboardConfigStore.addTrackerScore(serverPlayer, ScoreboardConfigStore.TRACKER_DIG_ALL, amount);
        }
        if (item instanceof BlockItem) {
            ScoreboardConfigStore.addTrackerScore(serverPlayer, ScoreboardConfigStore.TRACKER_PLACED_BLOCKS, amount);
        }
    }

    private static boolean isDigTool(Item item) {
        var stack = item.getDefaultStack();
        return stack.isIn(ItemTags.PICKAXES)
            || stack.isIn(ItemTags.SHOVELS)
            || stack.isIn(ItemTags.AXES)
            || stack.isIn(ItemTags.HOES)
            || item == Items.SHEARS;
    }
}
