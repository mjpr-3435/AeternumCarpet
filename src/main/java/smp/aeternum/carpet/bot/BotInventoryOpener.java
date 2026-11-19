package smp.aeternum.carpet.bot;

import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smp.aeternum.carpet.network.DockStatePayload;

public final class BotInventoryOpener {
    private BotInventoryOpener() {}

    public static void open(ServerPlayerEntity viewer, EntityPlayerMPFake bot) {
        if (ServerPlayNetworking.canSend(viewer, DockStatePayload.ID)) {
            openPlayerStyleUi(viewer, bot);
        } else {
            openVanillaFallbackUi(viewer, bot);
        }
    }

    private static void openPlayerStyleUi(ServerPlayerEntity viewer, EntityPlayerMPFake bot) {
        viewer.openHandledScreen(new ExtendedScreenHandlerFactory<BotOpenData>() {
            @Override
            public BotOpenData getScreenOpeningData(ServerPlayerEntity player) {
                return new BotOpenData(bot.getId(), bot.getInventory().getSelectedSlot());
            }

            @Override
            public Text getDisplayName() {
                return Text.literal(bot.getName().getString());
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory viewerInventory, PlayerEntity playerEntity) {
                return new BotInventoryScreenHandler(syncId, viewerInventory, bot);
            }
        });
    }

    private static void openVanillaFallbackUi(ServerPlayerEntity viewer, EntityPlayerMPFake bot) {
        FallbackInventory adapter = new FallbackInventory(bot);
        viewer.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Bot: " + bot.getName().getString());
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory viewerInventory, PlayerEntity playerEntity) {
                return new FallbackScreenHandler(syncId, viewerInventory, adapter);
            }
        });
    }

    private static final class FallbackInventory implements Inventory {
        private static final int SLOT_COUNT = 54;
        private static final int HAND_SELECTOR_SLOT = 7;
        private final EntityPlayerMPFake bot;

        FallbackInventory(EntityPlayerMPFake bot) {
            this.bot = bot;
        }

        static int toPlayerSlot(int chestSlot) {
            if (chestSlot >= 0 && chestSlot <= 3) return 39 - chestSlot;
            if (chestSlot == 5) return PlayerInventory.OFF_HAND_SLOT;
            if (chestSlot >= 18 && chestSlot <= 44) return chestSlot - 9;
            if (chestSlot >= 45 && chestSlot <= 53) return chestSlot - 45;
            return -1;
        }

        static boolean isControlSlot(int chestSlot) {
            return toPlayerSlot(chestSlot) == -1;
        }

        @Override public int size() { return SLOT_COUNT; }
        @Override public boolean isEmpty() { return bot.getInventory().isEmpty(); }
        @Override public void markDirty() { bot.getInventory().markDirty(); }
        @Override public void clear() { bot.getInventory().clear(); }
        @Override public boolean canPlayerUse(PlayerEntity player) { return !bot.isRemoved() && bot.isAlive(); }
        @Override public boolean isValid(int slot, ItemStack stack) { return toPlayerSlot(slot) >= 0; }

        @Override
        public ItemStack getStack(int slot) {
            int ps = toPlayerSlot(slot);
            if (ps >= 0) return bot.getInventory().getStack(ps);
            if (slot == HAND_SELECTOR_SLOT) {
                ItemStack icon = new ItemStack(Items.BLAZE_ROD);
                icon.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Mano principal: Slot " + (bot.getInventory().getSelectedSlot() + 1) + " (clic)").formatted(Formatting.GOLD));
                return icon;
            }
            ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            String label = switch (slot) {
                case 9, 10, 11, 12 -> "\u2191 Armadura";
                case 14 -> "\u2191 Offhand";
                case 16 -> "\u2191 Mano principal";
                default -> " ";
            };
            pane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(label).formatted(Formatting.DARK_GRAY));
            return pane;
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            int ps = toPlayerSlot(slot);
            return ps < 0 ? ItemStack.EMPTY : bot.getInventory().removeStack(ps, amount);
        }

        @Override
        public ItemStack removeStack(int slot) {
            int ps = toPlayerSlot(slot);
            return ps < 0 ? ItemStack.EMPTY : bot.getInventory().removeStack(ps);
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            int ps = toPlayerSlot(slot);
            if (ps >= 0) bot.getInventory().setStack(ps, stack);
        }
    }

    private static final class FallbackScreenHandler extends GenericContainerScreenHandler {
        private final FallbackInventory adapter;

        FallbackScreenHandler(int syncId, PlayerInventory viewerInventory, FallbackInventory adapter) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, viewerInventory, adapter, 6);
            this.adapter = adapter;
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return adapter.canPlayerUse(player);
        }

        @Override
        public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
            return !(slot.inventory == adapter && FallbackInventory.isControlSlot(slot.id)) && super.canInsertIntoSlot(stack, slot);
        }

        @Override
        public boolean canInsertIntoSlot(Slot slot) {
            return !(slot.inventory == adapter && FallbackInventory.isControlSlot(slot.id)) && super.canInsertIntoSlot(slot);
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (adapter.bot.isRemoved() || !adapter.bot.isAlive()) {
                if (player instanceof ServerPlayerEntity sp) sp.closeHandledScreen();
                return;
            }
            if (slotIndex >= 0 && slotIndex < FallbackInventory.SLOT_COUNT && FallbackInventory.isControlSlot(slotIndex)) {
                if (slotIndex == FallbackInventory.HAND_SELECTOR_SLOT
                    && (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE)) {
                    PlayerInventory inv = adapter.bot.getInventory();
                    inv.setSelectedSlot((inv.getSelectedSlot() + (button == 1 ? 8 : 1)) % 9);
                    sendContentUpdates();
                }
                return;
            }
            super.onSlotClick(slotIndex, button, actionType, player);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slotIndex) {
            if (slotIndex >= 0 && slotIndex < FallbackInventory.SLOT_COUNT && FallbackInventory.isControlSlot(slotIndex)) {
                return ItemStack.EMPTY;
            }
            Slot slot = this.slots.get(slotIndex);
            if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;
            ItemStack original = slot.getStack();
            ItemStack copy = original.copy();
            if (slotIndex < FallbackInventory.SLOT_COUNT) {
                if (!this.insertItem(original, FallbackInventory.SLOT_COUNT, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!this.insertItem(original, 18, FallbackInventory.SLOT_COUNT, false)) return ItemStack.EMPTY;
            }
            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
            return copy;
        }
    }
}
