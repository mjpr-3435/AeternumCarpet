package smp.aeternum.carpet.bot;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.EntityEquipment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.StackWithSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.storage.ReadView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import smp.aeternum.carpet.AeternumCarpet;

/** Loads and saves an offline player's inventory, ender chest, hotbar slot, and gamemode from playerdata/<uuid>.dat. */
public final class OfflinePlayerSession {
    private final MinecraftServer server;
    private final UUID uuid;
    private final Path datPath;
    private final NbtCompound rootNbt;
    public final SimpleInventory inventory = new SimpleInventory(41);
    public final SimpleInventory enderChest = new SimpleInventory(27);
    public int selectedSlot;
    public GameMode gameMode;
    private boolean savedOnClose;

    private OfflinePlayerSession(MinecraftServer server, UUID uuid, Path datPath, NbtCompound rootNbt) {
        this.server = server;
        this.uuid = uuid;
        this.datPath = datPath;
        this.rootNbt = rootNbt;
    }

    public static OfflinePlayerSession load(MinecraftServer server, GameProfile profile) {
        Path datPath = server.getSavePath(WorldSavePath.PLAYERDATA).resolve(profile.getId().toString() + ".dat");
        return loadFromPath(server, profile.getId(), datPath, profile.getName());
    }

    /** Loads an offline player session from an arbitrary playerdata .dat file path. */
    public static OfflinePlayerSession loadFromPath(MinecraftServer server, UUID uuid, Path datPath, String label) {
        if (server == null || uuid == null || datPath == null || !Files.isRegularFile(datPath)) {
            return null;
        }
        try {
            NbtCompound root = NbtIo.readCompressed(datPath, NbtSizeTracker.ofUnlimitedBytes());
            OfflinePlayerSession session = new OfflinePlayerSession(server, uuid, datPath, root);
            ReadView view = NbtReadView.create(ErrorReporter.EMPTY, server.getRegistryManager(), root);

            view.getOptionalTypedListView("Inventory", StackWithSlot.CODEC).ifPresent(list -> {
                for (StackWithSlot entry : list) {
                    int slot = entry.slot();
                    if (slot >= 0 && slot < 36) {
                        session.inventory.setStack(slot, entry.stack());
                    }
                }
            });

            view.read("equipment", EntityEquipment.CODEC).ifPresent(eq -> {
                session.inventory.setStack(36, eq.get(EquipmentSlot.FEET).copy());
                session.inventory.setStack(37, eq.get(EquipmentSlot.LEGS).copy());
                session.inventory.setStack(38, eq.get(EquipmentSlot.CHEST).copy());
                session.inventory.setStack(39, eq.get(EquipmentSlot.HEAD).copy());
                session.inventory.setStack(40, eq.get(EquipmentSlot.OFFHAND).copy());
            });

            view.getOptionalTypedListView("EnderItems", StackWithSlot.CODEC).ifPresent(list -> {
                for (StackWithSlot entry : list) {
                    int slot = entry.slot();
                    if (slot >= 0 && slot < session.enderChest.size()) {
                        session.enderChest.setStack(slot, entry.stack());
                    }
                }
            });

            session.selectedSlot = Math.max(0, Math.min(8, view.getInt("SelectedItemSlot", 0)));
            session.gameMode = GameMode.byIndex(view.getInt("playerGameType", GameMode.SURVIVAL.getIndex()));
            return session;
        } catch (Exception e) {
            AeternumCarpet.LOGGER.error("Failed to read offline playerdata for {}", label, e);
            return null;
        }
    }

    public boolean canUse(PlayerEntity viewer) {
        return server.getPlayerManager().getPlayer(uuid) == null;
    }

    public void save() {
        if (savedOnClose || server.getPlayerManager().getPlayer(uuid) != null) {
            return;
        }
        savedOnClose = true;
        try {
            NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, server.getRegistryManager());
            var invList = writeView.getListAppender("Inventory", StackWithSlot.CODEC);
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty()) {
                    invList.add(new StackWithSlot(i, stack));
                }
            }

            EntityEquipment eq = new EntityEquipment();
            eq.put(EquipmentSlot.FEET, inventory.getStack(36));
            eq.put(EquipmentSlot.LEGS, inventory.getStack(37));
            eq.put(EquipmentSlot.CHEST, inventory.getStack(38));
            eq.put(EquipmentSlot.HEAD, inventory.getStack(39));
            eq.put(EquipmentSlot.OFFHAND, inventory.getStack(40));
            writeView.put("equipment", EntityEquipment.CODEC, eq);

            var enderList = writeView.getListAppender("EnderItems", StackWithSlot.CODEC);
            for (int i = 0; i < enderChest.size(); i++) {
                ItemStack stack = enderChest.getStack(i);
                if (!stack.isEmpty()) {
                    enderList.add(new StackWithSlot(i, stack));
                }
            }

            writeView.putInt("SelectedItemSlot", selectedSlot);
            writeView.putInt("playerGameType", gameMode.getIndex());

            NbtCompound updated = writeView.getNbt();
            for (String key : List.of("Inventory", "EnderItems", "equipment", "SelectedItemSlot", "playerGameType")) {
                NbtElement el = updated.get(key);
                if (el != null) {
                    rootNbt.put(key, el);
                } else {
                    rootNbt.remove(key);
                }
            }
            NbtIo.writeCompressed(rootNbt, datPath);
        } catch (IOException e) {
            AeternumCarpet.LOGGER.error("Failed to write offline playerdata to {}", datPath, e);
        }
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Inventory getEnderChest() {
        return enderChest;
    }
}
