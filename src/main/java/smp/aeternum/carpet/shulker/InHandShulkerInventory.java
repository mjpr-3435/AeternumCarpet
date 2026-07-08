package smp.aeternum.carpet.shulker;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public class InHandShulkerInventory extends SimpleInventory {
    private final ServerPlayerEntity player;
    private final HandSlotSource source;
    private final ItemStack trackedStack;

    public InHandShulkerInventory(ServerPlayerEntity player, HandSlotSource source, ItemStack trackedStack) {
        super(27);
        this.player = player;
        this.source = source;
        this.trackedStack = trackedStack;
        trackedStack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(getHeldStacks());
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        saveToTrackedStack();
    }

    public void saveToTrackedStack() {
        ItemStack currentStack = source.getCurrentStack(player);
        if (currentStack != trackedStack || !InHandShulkerOpener.isShulkerBox(currentStack)) {
            return;
        }

        currentStack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(getHeldStacks()));
    }
}
