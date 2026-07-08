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
        ItemStack mainHandStack = player.getMainHandStack();
        if (packetHand == Hand.MAIN_HAND && isShulkerBox(mainHandStack)) {
            open(player, new HandSlotSource(Hand.MAIN_HAND, player.getInventory().getSelectedSlot()), mainHandStack);
            return Hand.MAIN_HAND;
        }

        ItemStack offHandStack = player.getOffHandStack();
        if (!isShulkerBox(mainHandStack) && packetHand == Hand.OFF_HAND && isShulkerBox(offHandStack)) {
            open(player, new HandSlotSource(Hand.OFF_HAND, -1), offHandStack);
            return Hand.OFF_HAND;
        }

        return null;
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
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
