package smp.aeternum.carpet.bot;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/** Live 41-slot Inventory wrapper directly backed by a Carpet fake player's PlayerInventory. */
public final class BotInventoryAdapter implements Inventory {
    public static final int SLOT_COUNT = 41;
    private final EntityPlayerMPFake bot;

    public BotInventoryAdapter(EntityPlayerMPFake bot) {
        this.bot = bot;
    }

    public EntityPlayerMPFake bot() {
        return bot;
    }

    @Override
    public int size() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return bot.getInventory().isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return bot.getInventory().getStack(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return bot.getInventory().removeStack(slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return bot.getInventory().removeStack(slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        bot.getInventory().setStack(slot, stack);
    }

    @Override
    public void markDirty() {
        bot.getInventory().markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return !bot.isRemoved() && bot.isAlive();
    }

    @Override
    public void clear() {
        bot.getInventory().clear();
    }
}
