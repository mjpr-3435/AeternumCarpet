package smp.aeternum.carpet.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class ScreenKeybinds {
    private static final String CATEGORY = "category.aeternum_carpet";
    private static final KeyBinding QUICK_DOCK = KeyBindingHelper.registerKeyBinding(
        new KeyBinding("key.aeternum_carpet.quick_dock", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY)
    );

    private ScreenKeybinds() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (QUICK_DOCK.wasPressed()) {
                // Drain tap queue; dock visibility is strictly hold-key driven.
            }
            AeternumDockOverlay.tick(client, QUICK_DOCK.isPressed());
        });
    }

    public static String quickDockKeyName() {
        return QUICK_DOCK.getBoundKeyLocalizedText().getString();
    }
}
