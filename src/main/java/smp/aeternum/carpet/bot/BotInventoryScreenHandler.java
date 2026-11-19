package smp.aeternum.carpet.bot;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

/** Player-inventory-style ScreenHandler backed by a Carpet fake player's live inventory. */
public final class BotInventoryScreenHandler extends ScreenHandler {
    private static final int BOT_SLOT_COUNT = 41;
    private static final Identifier[] ARMOR_SPRITES = {
        PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE,
        PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
        PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE,
        PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE
    };

    private final Inventory botInventory;
    private final EntityPlayerMPFake serverBot;
    private final LivingEntity clientBot;
    private final PropertyDelegate properties;

    /** Client constructor invoked via ExtendedScreenHandlerType. */
    public BotInventoryScreenHandler(int syncId, PlayerInventory viewerInventory, BotOpenData data) {
        this(syncId, viewerInventory, new SimpleInventory(BOT_SLOT_COUNT), null,
            resolveClientEntity(viewerInventory, data.entityId()), new ArrayPropertyDelegate(1));
        this.properties.set(0, Math.max(0, Math.min(8, data.selectedSlot())));
    }

    /** Server constructor backed by the live fake player. */
    public BotInventoryScreenHandler(int syncId, PlayerInventory viewerInventory, EntityPlayerMPFake bot) {
        this(syncId, viewerInventory, new BotInventoryAdapter(bot), bot, bot, createServerProperties(bot));
    }

    private BotInventoryScreenHandler(
        int syncId,
        PlayerInventory viewerInventory,
        Inventory botInventory,
        EntityPlayerMPFake serverBot,
        LivingEntity clientBot,
        PropertyDelegate properties
    ) {
        super(ModScreenHandlers.BOT_INVENTORY, syncId);
        this.botInventory = botInventory;
        this.serverBot = serverBot;
        this.clientBot = clientBot;
        this.properties = properties;
        this.addProperties(properties);

        // Armor column (slots 0..3 -> bot inventory 39..36)
        for (int i = 0; i < 4; i++) {
            final Identifier sprite = ARMOR_SPRITES[i];
            this.addSlot(new Slot(botInventory, 39 - i, 8, 8 + i * 18) {
                @Override
                public Identifier getBackgroundSprite() {
                    return sprite;
                }
            });
        }

        // Offhand (slot 4 -> bot inventory 40)
        this.addSlot(new Slot(botInventory, PlayerInventory.OFF_HAND_SLOT, 86, 26) {
            @Override
            public Identifier getBackgroundSprite() {
                return PlayerScreenHandler.EMPTY_OFF_HAND_SLOT_TEXTURE;
            }
        });

        // Bot main inventory (slots 5..31 -> bot inventory 9..35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(botInventory, 9 + row * 9 + col, 8 + col * 18, 82 + row * 18));
            }
        }

        // Bot hotbar (slots 32..40 -> bot inventory 0..8)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(botInventory, col, 8 + col * 18, 139));
        }

        // Viewer main inventory (slots 41..67)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(viewerInventory, 9 + row * 9 + col, 8 + col * 18, 171 + row * 18));
            }
        }

        // Viewer hotbar (slots 68..76)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(viewerInventory, col, 8 + col * 18, 229));
        }
    }

    private static LivingEntity resolveClientEntity(PlayerInventory viewerInventory, int entityId) {
        if (viewerInventory.player == null || viewerInventory.player.getWorld() == null) return null;
        Entity entity = viewerInventory.player.getWorld().getEntityById(entityId);
        return entity instanceof LivingEntity living ? living : null;
    }

    private static PropertyDelegate createServerProperties(EntityPlayerMPFake bot) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                return index == 0 ? bot.getInventory().getSelectedSlot() : 0;
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) bot.getInventory().setSelectedSlot(Math.max(0, Math.min(8, value)));
            }

            @Override
            public int size() {
                return 1;
            }
        };
    }

    public LivingEntity getClientBot() {
        return clientBot;
    }

    public int getSelectedHotbarSlot() {
        return Math.max(0, Math.min(8, properties.get(0)));
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (serverBot != null && id >= 0 && id < 9) {
            serverBot.getInventory().setSelectedSlot(id);
            properties.set(0, id);
            sendContentUpdates();
            return true;
        }
        return false;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return botInventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getStack();
        ItemStack copy = original.copy();
        if (slotIndex < BOT_SLOT_COUNT) {
            if (!this.insertItem(original, BOT_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.insertItem(original, 5, BOT_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (original.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return copy;
    }
}
