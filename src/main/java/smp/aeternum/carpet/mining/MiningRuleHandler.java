package smp.aeternum.carpet.mining;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import smp.aeternum.carpet.AeternumCarpetSettings;

public final class MiningRuleHandler {
    private static final float INSTANT_MINE_SPEED = 1024.0F;
    private static final float FIFTEEN_SECONDS_BREAK_DELTA = 1.0F / 300.0F;

    private MiningRuleHandler() {
    }

    public static float getMiningSpeed(PlayerEntity player, BlockState state, ItemStack stack, float originalSpeed) {
        if (canInstamineDeepslate(player, state, stack)) {
            return Math.max(originalSpeed, INSTANT_MINE_SPEED);
        }
        return originalSpeed;
    }

    public static float getBlockBreakingDelta(PlayerEntity player, BlockState state, ItemStack stack, BlockView world, BlockPos pos, float originalDelta) {
        if (canBreakEndPortalFrame(player, state, stack)) {
            return Math.max(originalDelta, FIFTEEN_SECONDS_BREAK_DELTA);
        }
        return originalDelta;
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
        if (getEnchantmentLevel(player, stack, Enchantments.EFFICIENCY) < 5) {
            return false;
        }

        StatusEffectInstance haste = player.getStatusEffect(StatusEffects.HASTE);
        return haste != null && haste.getAmplifier() >= 1;
    }

    public static boolean canBreakEndPortalFrame(PlayerEntity player, BlockState state, ItemStack stack) {
        if (!AeternumCarpetSettings.breakEndPortalFrames) {
            return false;
        }
        if (!state.isOf(Blocks.END_PORTAL_FRAME)) {
            return false;
        }
        if (player.isCreative() || player.isSpectator() || !player.getAbilities().allowModifyWorld) {
            return false;
        }
        if (!stack.isOf(Items.MACE)) {
            return false;
        }
        if (getEnchantmentLevel(player, stack, Enchantments.BREACH) < 4) {
            return false;
        }

        StatusEffectInstance haste = player.getStatusEffect(StatusEffects.HASTE);
        if (haste == null || haste.getAmplifier() < 1) {
            return false;
        }

        StatusEffectInstance strength = player.getStatusEffect(StatusEffects.STRENGTH);
        return strength != null && strength.getAmplifier() >= 1;
    }

    private static int getEnchantmentLevel(PlayerEntity player, ItemStack stack, net.minecraft.registry.RegistryKey<Enchantment> enchantmentKey) {
        return player.getRegistryManager()
            .getOrThrow(RegistryKeys.ENCHANTMENT)
            .getEntry(enchantmentKey)
            .map(entry -> stack.getEnchantments().getLevel((RegistryEntry<Enchantment>) entry))
            .orElse(0);
    }
}
