package smp.aeternum.carpet.shulker;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

public final class InHandShulkerOpener {
    private InHandShulkerOpener() {
    }

    public static Hand tryOpen(ServerPlayerEntity player, Hand packetHand) {
        Hand opened = tryOpenMain(player); if (opened != null) return opened;
        if (packetHand == Hand.OFF_HAND) return tryOpenOff(player);
        return null;
    }
    public static Hand tryOpenMain(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (canOpenInHand(stack)) { open(player, new HandSlotSource(Hand.MAIN_HAND, player.getInventory().getSelectedSlot()), stack); return Hand.MAIN_HAND; }
        return null;
    }
    public static Hand tryOpenOff(ServerPlayerEntity player) {
        ItemStack stack = player.getOffHandStack();
        if (canOpenInHand(stack)) { open(player, new HandSlotSource(Hand.OFF_HAND, -1), stack); return Hand.OFF_HAND; }
        return null;
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    public static boolean canOpenInHand(ItemStack stack) {
        return isShulkerBox(stack) && stack.getCount() == 1;
    }

    private static void open(ServerPlayerEntity player, HandSlotSource source, ItemStack trackedStack) {
        InHandShulkerInventory inventory = new InHandShulkerInventory(player, source, trackedStack);
        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return trackedStack.getName();
            }

            @Override
            public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity playerEntity) {
                return new InHandShulkerScreenHandler(syncId, playerInventory, inventory, source);
            }
        };
        player.openHandledScreen(factory);
    }
}
