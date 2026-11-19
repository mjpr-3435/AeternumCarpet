package smp.aeternum.carpet.bot;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Live 41-slot Inventory wrapper directly backed by a ServerPlayerEntity's PlayerInventory. */
public final class BotInventoryAdapter implements Inventory {
    public static final int SLOT_COUNT = 41;
    private final ServerPlayerEntity target;

    public BotInventoryAdapter(ServerPlayerEntity target) {
        this.target = target;
    }

    public ServerPlayerEntity target() {
        return target;
    }

    @Override
    public int size() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return target.getInventory().isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return target.getInventory().getStack(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return target.getInventory().removeStack(slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return target.getInventory().removeStack(slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        target.getInventory().setStack(slot, stack);
    }

    @Override
    public void markDirty() {
        target.getInventory().markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return !target.isRemoved() && target.isAlive();
    }

    @Override
    public void clear() {
        target.getInventory().clear();
    }
}
