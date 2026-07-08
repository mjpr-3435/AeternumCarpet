package smp.aeternum.carpet.shulker;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class InHandShulkerScreenHandler extends ShulkerBoxScreenHandler {
    private final int blockedSlotIndex;
    private final int blockedHotbarButton;
    private final InHandShulkerInventory inventory;

    public InHandShulkerScreenHandler(int syncId, PlayerInventory playerInventory, InHandShulkerInventory inventory, HandSlotSource source) {
        super(syncId, playerInventory, inventory);
        this.inventory = inventory;
        this.blockedSlotIndex = source.getBlockedScreenSlot();
        this.blockedHotbarButton = source.hand() == net.minecraft.util.Hand.MAIN_HAND ? source.selectedSlot() : -1;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == blockedSlotIndex) {
            return;
        }
        if (actionType == SlotActionType.SWAP && button == blockedHotbarButton) {
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        if (slot == blockedSlotIndex) {
            return ItemStack.EMPTY;
        }
        return super.quickMove(player, slot);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        inventory.saveToTrackedStack();
        super.onClosed(player);
    }
}
