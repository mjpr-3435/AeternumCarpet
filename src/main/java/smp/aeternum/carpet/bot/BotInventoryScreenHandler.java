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
import net.minecraft.world.GameMode;

/** Player-inventory-style ScreenHandler backed by a Carpet fake player's live inventory. */
public final class BotInventoryScreenHandler extends ScreenHandler {
    public static final int MODE_SURVIVAL = 0;
    public static final int MODE_CREATIVE = 1;
    public static final int MODE_SPECTATOR = 2;
    public static final int MODE_BUTTON_BASE = 10;

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
            resolveClientEntity(viewerInventory, data.entityId()), new ArrayPropertyDelegate(3));
        this.properties.set(0, Math.max(0, Math.min(8, data.selectedSlot())));
        this.properties.set(1, data.gameModeCode());
        this.properties.set(2, data.canSetCreative() ? 1 : 0);
    }

    /** Server constructor backed by the live fake player. */
    public BotInventoryScreenHandler(int syncId, PlayerInventory viewerInventory, EntityPlayerMPFake bot, boolean canSetCreative) {
        this(syncId, viewerInventory, new BotInventoryAdapter(bot), bot, bot, createServerProperties(bot, canSetCreative));
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
                this.addSlot(new Slot(botInventory, 9 + row * 9 + col, 8 + col * 18, 100 + row * 18));
            }
        }

        // Bot hotbar (slots 32..40 -> bot inventory 0..8)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(botInventory, col, 8 + col * 18, 157));
        }

        // Viewer main inventory (slots 41..67)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(viewerInventory, 9 + row * 9 + col, 8 + col * 18, 189 + row * 18));
            }
        }

        // Viewer hotbar (slots 68..76)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(viewerInventory, col, 8 + col * 18, 247));
        }
    }

    private static LivingEntity resolveClientEntity(PlayerInventory viewerInventory, int entityId) {
        if (viewerInventory.player == null || viewerInventory.player.getWorld() == null) return null;
        Entity entity = viewerInventory.player.getWorld().getEntityById(entityId);
        return entity instanceof LivingEntity living ? living : null;
    }

    public static int toModeCode(GameMode mode) {
        if (mode == null) return -1;
        return switch (mode) {
            case SURVIVAL -> MODE_SURVIVAL;
            case CREATIVE -> MODE_CREATIVE;
            case SPECTATOR -> MODE_SPECTATOR;
            default -> -1;
        };
    }

    private static GameMode fromModeCode(int code) {
        return switch (code) {
            case MODE_SURVIVAL -> GameMode.SURVIVAL;
            case MODE_CREATIVE -> GameMode.CREATIVE;
            case MODE_SPECTATOR -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static PropertyDelegate createServerProperties(EntityPlayerMPFake bot, boolean canSetCreative) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> bot.getInventory().getSelectedSlot();
                    case 1 -> toModeCode(bot.interactionManager.getGameMode());
                    case 2 -> canSetCreative ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    bot.getInventory().setSelectedSlot(Math.max(0, Math.min(8, value)));
                } else if (index == 1) {
                    GameMode mode = fromModeCode(value);
                    if (mode != null) bot.changeGameMode(mode);
                }
            }

            @Override
            public int size() {
                return 3;
            }
        };
    }

    public LivingEntity getClientBot() {
        return clientBot;
    }

    public int getSelectedHotbarSlot() {
        return Math.max(0, Math.min(8, properties.get(0)));
    }

    public int getGameModeCode() {
        return properties.get(1);
    }

    public boolean canSetCreative() {
        return properties.get(2) != 0;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (serverBot == null) return false;
        if (id >= 0 && id < 9) {
            serverBot.getInventory().setSelectedSlot(id);
            properties.set(0, id);
            sendContentUpdates();
            return true;
        }
        int modeCode = id - MODE_BUTTON_BASE;
        GameMode target = fromModeCode(modeCode);
        if (target != null) {
            if (target == GameMode.CREATIVE && !player.hasPermissionLevel(2)) {
                return false;
            }
            serverBot.changeGameMode(target);
            FakePlayerReloadManager.markDirty();
            properties.set(1, modeCode);
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
