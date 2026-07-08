package smp.aeternum.carpet.shulker;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

public record HandSlotSource(Hand hand, int selectedSlot) {
    public ItemStack getCurrentStack(ServerPlayerEntity player) {
        if (hand == Hand.MAIN_HAND) {
            return player.getInventory().getStack(selectedSlot);
        }
        return player.getOffHandStack();
    }

    public int getBlockedScreenSlot() {
        if (hand != Hand.MAIN_HAND) {
            return -1;
        }
        return 54 + selectedSlot;
    }
}
