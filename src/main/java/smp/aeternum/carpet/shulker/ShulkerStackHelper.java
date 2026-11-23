package smp.aeternum.carpet.shulker;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

public final class ShulkerStackHelper {
    private ShulkerStackHelper() {
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    public static boolean isEmptyShulker(ItemStack stack) {
        if (!isShulkerBox(stack)) {
            return false;
        }
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        return container == null || !container.iterateNonEmpty().iterator().hasNext();
    }
}
