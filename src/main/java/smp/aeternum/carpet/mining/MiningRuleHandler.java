package smp.aeternum.carpet.mining;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import smp.aeternum.carpet.AeternumCarpetSettings;

public final class MiningRuleHandler {
    private static final float INSTANT_MINE_SPEED = 1024.0F;

    private MiningRuleHandler() {
    }

    public static float getMiningSpeed(PlayerEntity player, BlockState state, ItemStack stack, float originalSpeed) {
        if (canInstamineDeepslate(player, state, stack)) {
            return Math.max(originalSpeed, INSTANT_MINE_SPEED);
        }
        return originalSpeed;
    }

    public static boolean canInstamineDeepslate(PlayerEntity player, BlockState state, ItemStack stack) {
        if (!AeternumCarpetSettings.instamineDeepslate) {
            return false;
        }
        if (!state.isOf(Blocks.DEEPSLATE)) {
            return false;
        }
        if (!stack.isOf(Items.NETHERITE_PICKAXE)) {
            return false;
        }

        StatusEffectInstance haste = player.getStatusEffect(StatusEffects.HASTE);
        return haste != null && haste.getAmplifier() >= 1;
    }
}
