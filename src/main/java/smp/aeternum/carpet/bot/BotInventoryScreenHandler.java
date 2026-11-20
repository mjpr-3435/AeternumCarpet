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
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/** Player-inventory-style ScreenHandler with side-by-side Ender Chest for bots, online players, and offline .dat files. */
public final class BotInventoryScreenHandler extends ScreenHandler {
    public static final int MODE_SURVIVAL = 0;
    public static final int MODE_CREATIVE = 1;
    public static final int MODE_SPECTATOR = 2;
    public static final int MODE_BUTTON_BASE = 10;

    private static final int TARGET_INV_SLOTS = 41;
    private static final int ENDER_SLOTS = 27;
    private static final int TOTAL_TARGET_SLOTS = TARGET_INV_SLOTS + ENDER_SLOTS;
    private static final Identifier[] ARMOR_SPRITES = {
        PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE,
        PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
        PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE,
        PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE
    };

    private final Inventory targetInventory;
    private final ServerPlayerEntity serverTarget;
    private final OfflinePlayerSession offlineSession;
    private final int targetEntityId;
    private final World clientWorld;
    private LivingEntity clientTarget;
    private final PropertyDelegate properties;
    private final String statusLabel;

    /** Client constructor invoked via ExtendedScreenHandlerType. */
    public BotInventoryScreenHandler(int syncId, PlayerInventory viewerInventory, BotOpenData data) {
        this(
            syncId,
            viewerInventory,
            new SimpleInventory(TARGET_INV_SLOTS),
            new SimpleInventory(ENDER_SLOTS),
            null,
            null,
            data.entityId(),
            viewerInventory.player != null ? viewerInventory.player.getWorld() : null,
            resolveClientEntity(viewerInventory, data.entityId()),
            new ArrayPropertyDelegate(3),
            data.statusLabel()
        );
        this.properties.set(0, Math.max(0, Math.min(8, data.selectedSlot())));
        this.properties.set(1, data.gameModeCode());
        this.properties.set(2, data.canSetCreative() ? 1 : 0);
    }

    /** Server constructor backed by an online player or Carpet fake player. */
    public BotInventoryScreenHandler(int syncId, PlayerInventory viewerInventory, ServerPlayerEntity target, boolean canSetCreative) {
        this(
            syncId,
            viewerInventory,
            new BotInventoryAdapter(target),
            target.getEnderChestInventory(),
            target,
            null,
            target.getId(),
            null,
            target,
            createOnlineProperties(target, canSetCreative),
            target instanceof EntityPlayerMPFake ? "Bot" : "Online"
        );
    }

    /** Server constructor backed by an offline playerdata/<uuid>.dat session. */
    public BotInventoryScreenHandler(int syncId, PlayerInventory viewerInventory, OfflinePlayerSession session, boolean canSetCreative) {
        this(
            syncId,
            viewerInventory,
            session.getInventory(),
            session.getEnderChest(),
            null,
            session,
            -1,
            null,
            null,
            createOfflineProperties(session, canSetCreative),
            "Offline"
        );
    }

    private BotInventoryScreenHandler(
        int syncId,
        PlayerInventory viewerInventory,
        Inventory targetInventory,
        Inventory enderInventory,
        ServerPlayerEntity serverTarget,
        OfflinePlayerSession offlineSession,
        int targetEntityId,
        World clientWorld,
        LivingEntity clientTarget,
        PropertyDelegate properties,
        String statusLabel
    ) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);
        this.targetInventory = targetInventory;
        this.serverTarget = serverTarget;
        this.offlineSession = offlineSession;
        this.targetEntityId = targetEntityId;
        this.clientWorld = clientWorld;
        this.clientTarget = clientTarget;
        this.properties = properties;
        this.statusLabel = statusLabel != null ? statusLabel : "Bot";
        this.addProperties(properties);

        // Armor column (slots 0..3 -> target inventory 39..36)
        for (int i = 0; i < 4; i++) {
            final Identifier sprite = ARMOR_SPRITES[i];
            this.addSlot(new Slot(targetInventory, 39 - i, 8, 8 + i * 18) {
                @Override
                public Identifier getBackgroundSprite() {
                    return sprite;
                }
            });
        }

        // Offhand (slot 4 -> target inventory 40)
        this.addSlot(new Slot(targetInventory, PlayerInventory.OFF_HAND_SLOT, 86, 20) {
            @Override
            public Identifier getBackgroundSprite() {
                return PlayerScreenHandler.EMPTY_OFF_HAND_SLOT_TEXTURE;
            }
        });

        // Target main inventory (slots 5..31 -> target inventory 9..35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(targetInventory, 9 + row * 9 + col, 8 + col * 18, 82 + row * 18));
            }
        }

        // Target hotbar (slots 32..40 -> target inventory 0..8)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(targetInventory, col, 8 + col * 18, 139));
        }

        // Target Ender Chest (slots 41..67 -> ender inventory 0..26, side-by-side on the right)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(enderInventory, row * 9 + col, 180 + col * 18, 82 + row * 18));
            }
        }

        // Viewer main inventory (slots 68..94)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(viewerInventory, 9 + row * 9 + col, 8 + col * 18, 171 + row * 18));
            }
        }

        // Viewer hotbar (slots 95..103)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(viewerInventory, col, 8 + col * 18, 229));
        }
    }

    private static LivingEntity resolveClientEntity(PlayerInventory viewerInventory, int entityId) {
        if (entityId < 0 || viewerInventory.player == null || viewerInventory.player.getWorld() == null) return null;
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

    private static PropertyDelegate createOnlineProperties(ServerPlayerEntity target, boolean canSetCreative) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> target.getInventory().getSelectedSlot();
                    case 1 -> toModeCode(target.interactionManager.getGameMode());
                    case 2 -> canSetCreative ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    target.getInventory().setSelectedSlot(Math.max(0, Math.min(8, value)));
                } else if (index == 1) {
                    GameMode mode = fromModeCode(value);
                    if (mode != null) target.changeGameMode(mode);
                }
            }

            @Override
            public int size() {
                return 3;
            }
        };
    }

    private static PropertyDelegate createOfflineProperties(OfflinePlayerSession session, boolean canSetCreative) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> session.selectedSlot;
                    case 1 -> toModeCode(session.gameMode);
                    case 2 -> canSetCreative ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    session.selectedSlot = Math.max(0, Math.min(8, value));
                } else if (index == 1) {
                    GameMode mode = fromModeCode(value);
                    if (mode != null) session.gameMode = mode;
                }
            }

            @Override
            public int size() {
                return 3;
            }
        };
    }

    public LivingEntity getClientBot() {
        if (targetEntityId >= 0 && (clientTarget == null || clientTarget.isRemoved()) && clientWorld != null) {
            Entity entity = clientWorld.getEntityById(targetEntityId);
            if (entity instanceof LivingEntity living) {
                clientTarget = living;
            }
        }
        return clientTarget;
    }

    public String getStatusLabel() {
        return statusLabel;
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
        if (serverTarget == null && offlineSession == null) return false;
        if (id >= 0 && id < 9) {
            if (serverTarget != null) {
                serverTarget.getInventory().setSelectedSlot(id);
            } else {
                offlineSession.selectedSlot = id;
            }
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
            if (serverTarget != null) {
                if (serverTarget.interactionManager.getGameMode() == GameMode.SPECTATOR
                    && (target == GameMode.SURVIVAL || target == GameMode.CREATIVE)) {
                    serverTarget.requestTeleport(serverTarget.getX(), serverTarget.getY() + 0.20, serverTarget.getZ());
                }
                serverTarget.changeGameMode(target);
                if (serverTarget instanceof EntityPlayerMPFake) {
                    FakePlayerReloadManager.markDirty();
                }
            } else {
                offlineSession.gameMode = target;
            }
            properties.set(1, modeCode);
            sendContentUpdates();
            return true;
        }
        return false;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (offlineSession != null) {
            offlineSession.save();
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (offlineSession != null) {
            return offlineSession.canUse(player);
        }
        return targetInventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getStack();
        ItemStack copy = original.copy();
        if (slotIndex < TOTAL_TARGET_SLOTS) {
            if (!this.insertItem(original, TOTAL_TARGET_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.insertItem(original, 5, TARGET_INV_SLOTS, false)) {
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
