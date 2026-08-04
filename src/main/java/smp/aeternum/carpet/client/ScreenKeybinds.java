package smp.aeternum.carpet.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import smp.aeternum.carpet.network.ScreenOpenRequestPayload;

public final class ScreenKeybinds {
    private static final String CATEGORY = "category.aeternum_carpet";
    private static final KeyBinding BLOCKERS = register("blockers");
    private static final KeyBinding GUIDE = register("guide");
    private static final KeyBinding MATERIALS = register("materials");
    private static final KeyBinding EXPLORATION = register("exploration");
    private static final KeyBinding ACTIONS = register("actions");

    private ScreenKeybinds() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getNetworkHandler() == null) return;
            if (BLOCKERS.wasPressed()) ClientPlayNetworking.send(new ScreenOpenRequestPayload("blockers"));
            if (GUIDE.wasPressed()) ClientPlayNetworking.send(new ScreenOpenRequestPayload("guide"));
            if (MATERIALS.wasPressed()) ClientPlayNetworking.send(new ScreenOpenRequestPayload("materials"));
            if (EXPLORATION.wasPressed()) ClientPlayNetworking.send(new ScreenOpenRequestPayload("exploration"));
            if (ACTIONS.wasPressed()) ClientPlayNetworking.send(new ScreenOpenRequestPayload("actions"));
        });
    }

    private static KeyBinding register(String name) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aeternum_carpet." + name, InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY));
    }
}
