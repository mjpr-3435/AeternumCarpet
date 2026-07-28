package smp.aeternum.carpet.enderchest;

import net.minecraft.block.EnderChestBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.inventory.EnderChestInventory;

public final class InHandEnderChestOpener {
    private InHandEnderChestOpener() {
    }

    public static Hand tryOpen(ServerPlayerEntity player, Hand packetHand) {
        Hand opened = tryOpenMain(player); if (opened != null) return opened;
        if (packetHand == Hand.OFF_HAND) return tryOpenOff(player);
        return null;
    }
    public static Hand tryOpenMain(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (isEnderChest(stack)) { open(player, stack); return Hand.MAIN_HAND; }
        return null;
    }
    public static Hand tryOpenOff(ServerPlayerEntity player) {
        ItemStack stack = player.getOffHandStack();
        if (isEnderChest(stack)) { open(player, stack); return Hand.OFF_HAND; }
        return null;
    }

    public static boolean isEnderChest(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof EnderChestBlock;
    }

    private static void open(ServerPlayerEntity player, ItemStack trackedStack) {
        EnderChestInventory inventory = player.getEnderChestInventory();
        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return trackedStack.getName();
            }

            @Override
            public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity playerEntity) {
                return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, inventory);
            }
        };
        player.openHandledScreen(factory);
    }
}
